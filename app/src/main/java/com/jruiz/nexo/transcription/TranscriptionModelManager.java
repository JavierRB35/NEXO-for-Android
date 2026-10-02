package com.jruiz.nexo.transcription;

import android.app.ActivityManager;
import android.content.Context;
import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Gestiona los modelos Whisper multilingües INT8 de Nexo fuera del APK.
 *
 * Mantiene compatibilidad con la ubicación histórica de Whisper Base y añade
 * Small/Medium bajo demanda. La selección queda en preferencias y cada carga
 * vuelve a comprobar que el dispositivo conserva margen de RAM suficiente.
 */
public final class TranscriptionModelManager {
    public static final String QUALITY_FAST="fast";
    public static final String QUALITY_HIGH="high";
    public static final String QUALITY_MAX="max";

    // Alias históricos conservados para no romper instalaciones ni validaciones.
    public static final String MODEL_NAME="Whisper Base multilingüe INT8";
    public static final String ENCODER="base-encoder.int8.onnx";
    public static final String DECODER="base-decoder.int8.onnx";
    public static final String TOKENS="base-tokens.txt";

    private static final long MIB=1024L*1024L;
    private static final long GIB=1024L*MIB;
    private static final long DOWNLOAD_MARGIN=128L*MIB;
    private static final String PREFS="nexo_transcription";
    private static final String PREF_QUALITY="quality";
    private static final String VERIFIED_MARKER=".verified-v2";

    public static final class Spec {
        public final String id,label,modelName,dirName,repo,encoder,decoder,tokens,encoderSha,decoderSha,description;
        public final long downloadBytes,estimatedRamBytes,minTotalRamBytes,minAvailableRamBytes;
        private Spec(String id,String label,String modelName,String dirName,String repo,
                     String encoder,String decoder,String tokens,String encoderSha,String decoderSha,
                     long downloadBytes,long estimatedRamBytes,long minTotalRamBytes,long minAvailableRamBytes,
                     String description){
            this.id=id;this.label=label;this.modelName=modelName;this.dirName=dirName;this.repo=repo;
            this.encoder=encoder;this.decoder=decoder;this.tokens=tokens;this.encoderSha=encoderSha;this.decoderSha=decoderSha;
            this.downloadBytes=downloadBytes;this.estimatedRamBytes=estimatedRamBytes;
            this.minTotalRamBytes=minTotalRamBytes;this.minAvailableRamBytes=minAvailableRamBytes;
            this.description=description;
        }
    }

    private static final Spec FAST=new Spec(
            QUALITY_FAST,"Rápida","Whisper Base multilingüe INT8","sherpa-onnx-whisper-base",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/",
            "base-encoder.int8.onnx","base-decoder.int8.onnx","base-tokens.txt",
            "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11",
            "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d",
            161_000_000L,512L*MIB,3L*GIB,800L*MIB,
            "Menor consumo y mayor velocidad. Recomendada para notas, conversaciones sencillas y cuando prima la rapidez.");

    private static final Spec HIGH=new Spec(
            QUALITY_HIGH,"Alta precisión","Whisper Small multilingüe INT8","sherpa-onnx-whisper-small",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/8f3c18b358db4d1f2fc1eae49d75cd20989e4309/",
            "small-encoder.int8.onnx","small-decoder.int8.onnx","small-tokens.txt",
            "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9",
            "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee",
            376_000_000L,1150L*MIB,6L*GIB,2L*GIB,
            "Mejor reconocimiento de reuniones, nombres y frases complejas. Recomendada para reuniones importantes.");

    private static final Spec MAX=new Spec(
            QUALITY_MAX,"Máxima precisión","Whisper Medium multilingüe INT8","sherpa-onnx-whisper-medium",
            "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-medium/resolve/5ce006117ee462612717129f32b0b23a88af0c96/",
            "medium-encoder.int8.onnx","medium-decoder.int8.onnx","medium-tokens.txt",
            "3a357b9d1ece665fac58ef714dd7e00306bab7cdfb0b39146457e5a19ca71752",
            "9de5f1b0c420e6bb01a7bcc966f2c73dbc75383b4c6005b4c024d44f8cda7bb5",
            947_000_000L,2400L*MIB,10L*GIB,4L*GIB,
            "Mayor calidad, con bastante más memoria, batería y tiempo de procesamiento. Solo para dispositivos con recursos suficientes.");

    private static final Spec[] SPECS={FAST,HIGH,MAX};
    private TranscriptionModelManager(){}

    public interface Progress { void onProgress(int percent,String message) throws Exception; }

    public static Spec spec(String id){
        String q=normalizeQuality(id);
        for(Spec s:SPECS)if(s.id.equals(q))return s;
        return FAST;
    }
    public static String normalizeQuality(String id){
        if(QUALITY_HIGH.equals(id))return QUALITY_HIGH;
        if(QUALITY_MAX.equals(id))return QUALITY_MAX;
        return QUALITY_FAST;
    }
    public static String selectedQuality(Context c){
        String q=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(PREF_QUALITY,QUALITY_FAST);
        return normalizeQuality(q);
    }
    public static Spec selectedSpec(Context c){return spec(selectedQuality(c));}
    public static boolean setSelected(Context c,String quality){
        if(!QUALITY_FAST.equals(quality)&&!QUALITY_HIGH.equals(quality)&&!QUALITY_MAX.equals(quality))return false;
        Spec s=spec(quality);
        DeviceResources r=deviceResources(c);
        if(!canRun(s,r))return false;
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(PREF_QUALITY,s.id).apply();
        return true;
    }

    public static File modelDir(Context c){return modelDir(c,QUALITY_FAST);}
    public static File modelDir(Context c,String quality){
        File root=new File(c.getFilesDir(),"models");
        File d=new File(root,spec(quality).dirName);
        if(!d.exists())d.mkdirs();
        return d;
    }
    public static File find(Context c,String name){
        File preferred=new File(modelDir(c),name);
        if(preferred.isFile()&&preferred.length()>1024)return preferred;
        File found=findRecursive(c.getFilesDir(),name,0);
        return found!=null?found:preferred;
    }
    private static File findRecursive(File dir,String name,int depth){
        if(dir==null||depth>5||!dir.isDirectory())return null;
        File[] xs=dir.listFiles();if(xs==null)return null;
        for(File x:xs)if(x.isFile()&&name.equals(x.getName())&&x.length()>1024)return x;
        for(File x:xs)if(x.isDirectory()){File f=findRecursive(x,name,depth+1);if(f!=null)return f;}
        return null;
    }
    private static File file(Context c,Spec s,String name){
        File preferred=new File(modelDir(c,s.id),name);
        if(preferred.isFile())return preferred;
        // Solo Base necesita el fallback histórico de búsqueda. Los perfiles
        // nuevos siempre se guardan en su directorio canónico.
        if(!QUALITY_FAST.equals(s.id))return preferred;
        File found=findRecursive(c.getFilesDir(),name,0);
        return found!=null?found:preferred;
    }
    public static File encoderFile(Context c,String quality){Spec s=spec(quality);return file(c,s,s.encoder);}
    public static File decoderFile(Context c,String quality){Spec s=spec(quality);return file(c,s,s.decoder);}
    public static File tokensFile(Context c,String quality){Spec s=spec(quality);return file(c,s,s.tokens);}

    public static boolean installed(Context c){return installed(c,selectedQuality(c));}
    public static boolean installed(Context c,String quality){
        Spec s=spec(quality);
        return usableSize(encoderFile(c,s.id),1024L*1024L)
                &&usableSize(decoderFile(c,s.id),1024L*1024L)
                &&usableSize(tokensFile(c,s.id),100L*1024L);
    }
    private static boolean usableSize(File f,long min){return f!=null&&f.isFile()&&f.length()>=min;}

    public static void ensureSafeToLoad(Context c,String quality)throws Exception{
        Spec s=spec(quality);DeviceResources r=deviceResources(c);
        if(!canRun(s,r))throw new IllegalStateException(resourceWarning(s,r));
        validateInstalled(c,s);
    }
    private static void validateInstalled(Context c,Spec s)throws Exception{
        if(!installed(c,s.id))throw new FileNotFoundException("El modelo "+s.label+" no está instalado o está incompleto.");
        File enc=encoderFile(c,s.id),dec=decoderFile(c,s.id),tok=tokensFile(c,s.id);
        File marker=new File(modelDir(c,s.id),VERIFIED_MARKER);
        String signature=verificationSignature(enc,dec,tok);
        if(marker.isFile()){
            try{
                String saved=new String(java.nio.file.Files.readAllBytes(marker.toPath()),java.nio.charset.StandardCharsets.UTF_8);
                if(signature.equals(saved))return;
            }catch(Exception ignored){}
        }
        if(!s.encoderSha.equalsIgnoreCase(sha256(enc)))throw new IOException("El encoder de "+s.label+" está dañado.");
        if(!s.decoderSha.equalsIgnoreCase(sha256(dec)))throw new IOException("El decoder de "+s.label+" está dañado.");
        if(tok.length()<100L*1024L)throw new IOException("El vocabulario de "+s.label+" está incompleto.");
        atomicText(marker,signature);
    }
    private static String verificationSignature(File a,File b,File c){
        return a.length()+":"+a.lastModified()+"\n"+b.length()+":"+b.lastModified()+"\n"+c.length()+":"+c.lastModified();
    }

    public static JSONObject status(Context c){
        JSONObject out=new JSONObject();
        try{
            DeviceResources r=deviceResources(c);
            String selected=safeSelectedQuality(c),recommended=recommendedQuality(r);
            Spec current=spec(selected);
            out.put("selected",selected);
            out.put("qualityLabel",current.label);
            out.put("modelName",current.modelName);
            out.put("name",current.modelName);
            out.put("installed",safeInstalled(c,selected));
            out.put("recommended",recommended);
            JSONObject device=new JSONObject();
            device.put("totalRamBytes",r.totalRam);
            device.put("availableRamBytes",r.availableRam);
            device.put("storageAvailableBytes",r.storageAvailable);
            device.put("lowMemory",r.lowMemory);
            out.put("device",device);
            JSONArray models=new JSONArray();
            for(Spec model:SPECS)models.put(modelStatus(c,model,r,selected,recommended));
            out.put("models",models);
            out.put("ok",true);
            return out;
        }catch(Throwable failure){
            return fallbackStatus(c,failure);
        }
    }

    public static JSONObject fallbackStatus(Context c,Throwable failure){
        JSONObject out=new JSONObject();
        try{
            String selected=safeSelectedQuality(c);
            Spec current=spec(selected);
            out.put("selected",selected);
            out.put("qualityLabel",current.label);
            out.put("modelName",current.modelName);
            out.put("name",current.modelName);
            out.put("installed",false);
            out.put("recommended","");
            out.put("ok",false);
            out.put("error",failure==null?"No se pudo leer el estado de los modelos.":safeMessage(failure));
            JSONObject device=new JSONObject();
            device.put("totalRamBytes",0);
            device.put("availableRamBytes",0);
            device.put("storageAvailableBytes",0);
            device.put("lowMemory",false);
            out.put("device",device);
            JSONArray models=new JSONArray();
            for(Spec model:SPECS){
                JSONObject o=baseModelStatus(model,selected,"");
                o.put("installed",false);
                o.put("installedBytes",0);
                o.put("canRun",QUALITY_FAST.equals(model.id));
                o.put("canDownload",QUALITY_FAST.equals(model.id));
                o.put("resourceWarning",QUALITY_FAST.equals(model.id)
                        ?"No se pudieron leer todos los recursos del dispositivo. Se mantiene disponible únicamente el perfil Rápida por seguridad."
                        :"No se pudieron leer los recursos del dispositivo; este perfil queda deshabilitado por seguridad.");
                o.put("storageWarning","");
                models.put(o);
            }
            out.put("models",models);
        }catch(Throwable ignored){}
        return out;
    }

    private static JSONObject modelStatus(Context c,Spec s,DeviceResources r,String selected,String recommended){
        JSONObject o=baseModelStatus(s,selected,recommended);
        try{
            boolean isInstalled=safeInstalled(c,s.id),canRun=canRun(s,r);
            long onDisk=safeModelBytes(c,s);
            long requiredStorage=Math.max(64L*MIB,s.downloadBytes-Math.min(s.downloadBytes,onDisk)+DOWNLOAD_MARGIN);
            boolean canDownload=canRun&&r.storageAvailable>=requiredStorage;
            o.put("installed",isInstalled);
            o.put("installedBytes",onDisk);
            o.put("canRun",canRun);
            o.put("canDownload",canDownload);
            o.put("resourceWarning",canRun?"":resourceWarning(s,r));
            o.put("storageWarning",r.storageAvailable>=requiredStorage?"":"No hay espacio libre suficiente para descargar este modelo con margen de seguridad.");
        }catch(Throwable failure){
            try{
                o.put("installed",false);
                o.put("installedBytes",0);
                o.put("canRun",false);
                o.put("canDownload",false);
                o.put("resourceWarning","No se pudo comprobar este modelo: "+safeMessage(failure));
                o.put("storageWarning","");
            }catch(Throwable ignored){}
        }
        return o;
    }

    private static JSONObject baseModelStatus(Spec s,String selected,String recommended){
        JSONObject o=new JSONObject();
        try{
            o.put("id",s.id);
            o.put("label",s.label);
            o.put("modelName",s.modelName);
            o.put("description",s.description);
            o.put("installed",false);
            o.put("installedBytes",0);
            o.put("downloadBytes",s.downloadBytes);
            o.put("estimatedRamBytes",s.estimatedRamBytes);
            o.put("minTotalRamBytes",s.minTotalRamBytes);
            o.put("minAvailableRamBytes",s.minAvailableRamBytes);
            o.put("canRun",false);
            o.put("canDownload",false);
            o.put("selected",s.id.equals(selected));
            o.put("recommended",s.id.equals(recommended));
            o.put("resourceWarning","");
            o.put("storageWarning","");
        }catch(Throwable ignored){}
        return o;
    }

    private static String safeSelectedQuality(Context c){
        try{return selectedQuality(c);}catch(Throwable ignored){return QUALITY_FAST;}
    }
    private static boolean safeInstalled(Context c,String quality){
        try{return installed(c,quality);}catch(Throwable ignored){return false;}
    }
    private static long safeModelBytes(Context c,Spec s){
        try{return modelBytes(c,s);}catch(Throwable ignored){return 0L;}
    }
    private static String safeMessage(Throwable t){
        if(t==null)return "Error desconocido";
        String m=t.getMessage();
        return m==null||m.isBlank()?t.getClass().getSimpleName():m;
    }

    public static void download(Context c,Progress progress)throws Exception{download(c,selectedQuality(c),progress);}
    public static void download(Context c,String quality,Progress progress)throws Exception{
        Spec s=spec(quality);DeviceResources r=deviceResources(c);
        if(!canRun(s,r))throw new IllegalStateException(resourceWarning(s,r));
        long onDisk=modelBytes(c,s);
        long need=Math.max(64L*MIB,s.downloadBytes-Math.min(s.downloadBytes,onDisk)+DOWNLOAD_MARGIN);
        if(r.storageAvailable<need)throw new IOException("No hay espacio suficiente. Libera al menos "+formatGb(need)+" GB antes de descargar "+s.label+".");
        File d=modelDir(c,s.id);
        File marker=new File(d,VERIFIED_MARKER);if(marker.exists())marker.delete();
        downloadOne(s.repo+s.encoder+"?download=true",new File(d,s.encoder),s.encoderSha,0,32,progress,"Descargando encoder · "+s.label+"…");
        downloadOne(s.repo+s.decoder+"?download=true",new File(d,s.decoder),s.decoderSha,32,98,progress,"Descargando decoder · "+s.label+"…");
        downloadOne(s.repo+s.tokens+"?download=true",new File(d,s.tokens),null,98,100,progress,"Descargando vocabulario · "+s.label+"…");
        validateInstalled(c,s);
        progress.onProgress(100,s.label+" preparada");
    }
    private static void downloadOne(String url,File target,String sha,int from,int to,Progress p,String label)throws Exception{
        if(target.isFile()&&target.length()>1024&&(sha==null||sha.equalsIgnoreCase(sha256(target)))){p.onProgress(to,label);return;}
        File tmp=new File(target.getPath()+".part");if(tmp.exists())tmp.delete();
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();
        con.setInstanceFollowRedirects(true);con.setConnectTimeout(30000);con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent","Nexo-Android/3.7.6");
        int code=con.getResponseCode();if(code<200||code>=400)throw new IOException("Descarga HTTP "+code);
        long len=con.getContentLengthLong(),done=0;byte[] buf=new byte[64*1024];int last=-1;
        try(InputStream in=new BufferedInputStream(con.getInputStream());OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){
            int n;while((n=in.read(buf))!=-1){
                out.write(buf,0,n);done+=n;
                if(len>0){int local=(int)Math.min(100,done*100/len),pct=from+(to-from)*local/100;if(pct!=last){last=pct;p.onProgress(pct,label);}}
            }
        }catch(Exception e){
            if(tmp.exists())tmp.delete();
            throw e;
        }finally{con.disconnect();}
        if(tmp.length()<=1024){tmp.delete();throw new IOException("Descarga incompleta: "+target.getName());}
        if(sha!=null&&!sha.equalsIgnoreCase(sha256(tmp))){tmp.delete();throw new IOException("Verificación SHA-256 fallida: "+target.getName());}
        if(target.exists()&&!target.delete())throw new IOException("No se pudo sustituir "+target.getName());
        if(!tmp.renameTo(target))throw new IOException("No se pudo guardar "+target.getName());
    }

    public static boolean delete(Context c,String quality){
        Spec s=spec(quality);File d=modelDir(c,s.id);
        boolean ok=true;
        for(String n:new String[]{s.encoder,s.decoder,s.tokens,VERIFIED_MARKER,s.encoder+".part",s.decoder+".part",s.tokens+".part"}){
            File f=new File(d,n);if(f.exists()&&!f.delete())ok=false;
        }
        return ok;
    }

    public static String recommendedQuality(Context c){return recommendedQuality(deviceResources(c));}
    private static String recommendedQuality(DeviceResources r){
        if(canRun(MAX,r)&&r.totalRam>=12L*GIB&&r.availableRam>=5L*GIB)return QUALITY_MAX;
        if(canRun(HIGH,r)&&r.availableRam>=2500L*MIB)return QUALITY_HIGH;
        if(canRun(FAST,r))return QUALITY_FAST;
        return "";
    }
    private static boolean canRun(Spec s,DeviceResources r){
        return !r.lowMemory&&r.totalRam>=s.minTotalRamBytes&&r.availableRam>=s.minAvailableRamBytes;
    }
    private static String resourceWarning(Spec s,DeviceResources r){
        if(r.totalRam<s.minTotalRamBytes)return s.label+" requiere un dispositivo con al menos "+formatGb(s.minTotalRamBytes)+" GB de RAM total.";
        if(r.lowMemory||r.availableRam<s.minAvailableRamBytes)return "No hay RAM libre suficiente para "+s.label+". Necesita aproximadamente "+formatGb(s.minAvailableRamBytes)+" GB disponibles antes de cargar el modelo.";
        return "Este modelo no se recomienda para este dispositivo.";
    }

    private static long modelBytes(Context c,Spec s){
        long n=0;for(File f:new File[]{encoderFile(c,s.id),decoderFile(c,s.id),tokensFile(c,s.id)})if(f.isFile())n+=f.length();return n;
    }
    private static final class DeviceResources {
        long totalRam,availableRam,storageAvailable;boolean lowMemory;
    }
    private static DeviceResources deviceResources(Context c){
        DeviceResources r=new DeviceResources();
        try{
            ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
            if(am!=null){am.getMemoryInfo(mi);r.totalRam=mi.totalMem;r.availableRam=mi.availMem;r.lowMemory=mi.lowMemory;}
        }catch(Exception ignored){}
        try{r.storageAvailable=new StatFs(c.getFilesDir().getAbsolutePath()).getAvailableBytes();}catch(Exception ignored){}
        return r;
    }

    private static void atomicText(File target,String text)throws Exception{
        File tmp=new File(target.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp,false)){out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();try{out.getFD().sync();}catch(Exception ignored){}}
        if(target.exists()&&!target.delete())throw new IOException("No se pudo actualizar la verificación del modelo");
        if(!tmp.renameTo(target))throw new IOException("No se pudo guardar la verificación del modelo");
    }
    private static String sha256(File f)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.ROOT,"%02x",x));return s.toString();
    }
    private static String formatGb(long bytes){return String.format(Locale.ROOT,"%.1f",bytes/(double)GIB);}
}
