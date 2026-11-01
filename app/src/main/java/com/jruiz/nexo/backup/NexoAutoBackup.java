package com.jruiz.nexo.backup;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jruiz.nexo.connect.vault.NexoConnectChatBackup;
import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single user-selected SAF document. On application launch overwrite it only if local data changed.
 * All writes are serialised, validated by NexoBackupManager, and never include stored credentials.
 */
public final class NexoAutoBackup {
    public enum Result { SAVED, UNCHANGED, RESTORING, DESTINATION_CHANGED }
    public interface Callback { void finished(Result result,JSONObject details,String error); }

    private static final String TAG="NexoAutoBackup";
    private static final String PREFS="nexo_backup";
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static final AtomicInteger PENDING=new AtomicInteger();
    private static final AtomicBoolean RESTORING=new AtomicBoolean();

    private NexoAutoBackup(){}
    private static SharedPreferences prefs(Context context){
        return context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    public static boolean configured(Context context){return !prefs(context).getString("uri","").isEmpty();}
    public static boolean isRunning(){return PENDING.get()>0;}
    public static void setRestoring(boolean restoring){RESTORING.set(restoring);}
    public static long lastSuccess(Context context){return prefs(context).getLong("lastExportAt",0);}
    public static String lastError(Context context){return prefs(context).getString("error","");}
    public static int failureCount(Context context){return prefs(context).getInt("failures",0);}
    public static void passwordChanged(Context context){prefs(context).edit().remove("fingerprint").putString("error","").apply();}
    public static void restored(Context context){
        prefs(context).edit().remove("fingerprint").putLong("lastExportAt",0).putString("error","").apply();
    }

    public static void selectDestination(Context context,Uri uri,int flags,DatabaseHelper db,Callback callback) throws Exception {
        int access=flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if((access&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0||(access&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0)
            throw new IOException("El archivo seleccionado necesita permisos de lectura y escritura.");
        context.getContentResolver().takePersistableUriPermission(uri,access);
        prefs(context).edit().putString("uri",uri.toString()).remove("fingerprint")
            .putLong("lastExportAt",0).putString("error","").putInt("failures",0).apply();
        backupAsync(context,db,true,callback);
    }

    public static void backupAsync(Context context,DatabaseHelper db,boolean force,Callback callback){
        Context app=context.getApplicationContext();
        String target=prefs(app).getString("uri","");
        if(target.isEmpty()){
            if(callback!=null)new Handler(Looper.getMainLooper()).post(()->
                callback.finished(null,null,"Selecciona primero un archivo de destino."));
            return;
        }
        PENDING.incrementAndGet();
        EXECUTOR.execute(()->{
            Result result=null;
            JSONObject details=null;
            String message=null;
            try {
                if(!target.equals(prefs(app).getString("uri","")))result=Result.DESTINATION_CHANGED;
                else if(RESTORING.get())result=Result.RESTORING;
                else {
                    String before=signature(app);
                    if(!force&&prefs(app).getLong("lastExportAt",0)>0&&
                            before.equals(prefs(app).getString("fingerprint",""))&&
                            accessible(app,Uri.parse(target))){
                        result=Result.UNCHANGED;
                    }else{
                        char[] password=null;
                        try{
                            if(NexoConnectChatBackup.hasHistory(app)){
                                password=NexoBackupPasswordStore.load(app);
                                if(password==null)
                                    throw new IOException("Configura una contraseña para proteger las conversaciones antes de activar la copia automática.");
                            }
                            if(RESTORING.get())result=Result.RESTORING;
                            else if(!target.equals(prefs(app).getString("uri","")))result=Result.DESTINATION_CHANGED;
                            else{
                                details=NexoBackupManager.exportToUri(app,db,Uri.parse(target),password);
                                prefs(app).edit().putString("fingerprint",before)
                                    .putLong("lastExportAt",details.optLong("createdAt",System.currentTimeMillis()))
                                    .putString("error","").putInt("failures",0).apply();
                                result=Result.SAVED;
                            }
                        }finally{if(password!=null)Arrays.fill(password,'\0');}
                    }
                }
            }catch(Exception failure){
                message=failure.getMessage()==null?"No se pudo verificar la copia de Nexo.":failure.getMessage();
                Log.w(TAG,"Error de copia",failure);
                if(target.equals(prefs(app).getString("uri",""))){
                    SharedPreferences p=prefs(app);
                    p.edit().putString("error",message).putInt("failures",p.getInt("failures",0)+1).apply();
                }
            }finally{PENDING.decrementAndGet();}
            if(callback!=null){
                Result completed=result;
                JSONObject info=details;
                String error=message;
                new Handler(Looper.getMainLooper()).post(()->callback.finished(completed,info,error));
            }
        });
    }

    private static boolean accessible(Context context,Uri uri){
        try(InputStream stream=context.getContentResolver().openInputStream(uri)){
            return stream!=null&&stream.read()!=-1;
        }catch(Exception ignored){return false;}
    }
    private static String signature(Context context) throws Exception {
        StringBuilder value=new StringBuilder();
        appendFile(value,"database",context.getDatabasePath(DatabaseHelper.databaseName()));
        appendFile(value,"connect",context.getDatabasePath("nexo-connect-pilot.db"));
        appendFolder(value,new File(context.getFilesDir(),"attachments"),"attachments");
        Map<String,?> profiles=new TreeMap<>(context.getSharedPreferences("nexo-connect-profiles",0).getAll());
        for(Map.Entry<String,?> entry:profiles.entrySet())
            value.append("profile:").append(entry.getKey()).append('=').append(String.valueOf(entry.getValue())).append(';');
        return hash(value.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static void appendFile(StringBuilder signature,String key,File file){
        for(String suffix:new String[]{"","-wal"}){
            File item=new File(file.getPath()+suffix);
            signature.append(key).append(suffix).append(':').append(item.isFile()?item.length():-1)
                .append(':').append(item.lastModified()).append(';');
        }
    }
    private static void appendFolder(StringBuilder value,File root,String prefix){
        if(!root.exists()){value.append(prefix).append(":missing;");return;}
        File[] files=root.listFiles();
        if(files==null){value.append(prefix).append(":unreadable;");return;}
        Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
        for(File file:files){
            String path=prefix+"/"+file.getName();
            if(file.isDirectory())appendFolder(value,file,path);
            else value.append(path).append(':').append(file.length()).append(':').append(file.lastModified()).append(';');
        }
    }
    private static String hash(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder text=new StringBuilder();
        for(byte b:digest)text.append(String.format(Locale.ROOT,"%02x",b&255));
        return text.toString();
    }
}
