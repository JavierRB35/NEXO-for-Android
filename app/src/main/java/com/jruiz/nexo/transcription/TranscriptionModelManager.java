package com.jruiz.nexo.transcription;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Gestiona Whisper base multilingüe INT8 fuera del APK. */
public final class TranscriptionModelManager {
    public static final String MODEL_NAME="Whisper Base multilingüe INT8";
    private static final String REPO="https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/";
    public static final String ENCODER="base-encoder.int8.onnx";
    public static final String DECODER="base-decoder.int8.onnx";
    public static final String TOKENS="base-tokens.txt";
    private static final String ENC_SHA="0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11";
    private static final String DEC_SHA="9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d";
    private TranscriptionModelManager(){}

    public interface Progress { void onProgress(int percent,String message) throws Exception; }

    public static File modelDir(Context c){File d=new File(c.getFilesDir(),"models/sherpa-onnx-whisper-base");if(!d.exists())d.mkdirs();return d;}
    public static File find(Context c,String name){
        File preferred=new File(modelDir(c),name); if(preferred.isFile()&&preferred.length()>1024)return preferred;
        File found=findRecursive(c.getFilesDir(),name,0); return found!=null?found:preferred;
    }
    private static File findRecursive(File dir,String name,int depth){
        if(dir==null||depth>4||!dir.isDirectory())return null;File[] xs=dir.listFiles();if(xs==null)return null;
        for(File x:xs)if(x.isFile()&&name.equals(x.getName())&&x.length()>1024)return x;
        for(File x:xs)if(x.isDirectory()){File f=findRecursive(x,name,depth+1);if(f!=null)return f;}return null;
    }
    public static boolean installed(Context c){return find(c,ENCODER).isFile()&&find(c,DECODER).isFile()&&find(c,TOKENS).isFile();}
    public static JSONObject status(Context c){JSONObject o=new JSONObject();try{o.put("installed",installed(c));o.put("name",MODEL_NAME);o.put("encoderBytes",find(c,ENCODER).length());o.put("decoderBytes",find(c,DECODER).length());o.put("tokensBytes",find(c,TOKENS).length());}catch(Exception ignored){}return o;}

    public static void download(Context c,Progress progress) throws Exception{
        File d=modelDir(c);downloadOne(REPO+ENCODER+"?download=true",new File(d,ENCODER),ENC_SHA,0,18,progress,"Descargando encoder…");
        downloadOne(REPO+DECODER+"?download=true",new File(d,DECODER),DEC_SHA,18,98,progress,"Descargando decoder…");
        downloadOne(REPO+TOKENS+"?download=true",new File(d,TOKENS),null,98,100,progress,"Descargando vocabulario…");
        if(!installed(c))throw new IOException("El modelo quedó incompleto");
        progress.onProgress(100,"Modelo preparado");
    }
    private static void downloadOne(String url,File target,String sha,int from,int to,Progress p,String label)throws Exception{
        if(target.isFile()&&target.length()>1024&&(sha==null||sha.equalsIgnoreCase(sha256(target)))){p.onProgress(to,label);return;}
        File tmp=new File(target.getPath()+".part");if(tmp.exists())tmp.delete();
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();con.setInstanceFollowRedirects(true);con.setConnectTimeout(30000);con.setReadTimeout(60000);con.setRequestProperty("User-Agent","Nexo-Android/3.4.9");
        int code=con.getResponseCode();if(code<200||code>=400)throw new IOException("Descarga HTTP "+code);
        long len=con.getContentLengthLong(),done=0;byte[] buf=new byte[64*1024];int last=-1;
        try(InputStream in=new BufferedInputStream(con.getInputStream());OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);done+=n;if(len>0){int local=(int)Math.min(100,done*100/len),pct=from+(to-from)*local/100;if(pct!=last){last=pct;p.onProgress(pct,label);}}}}
        finally{con.disconnect();}
        if(sha!=null&&!sha.equalsIgnoreCase(sha256(tmp))){tmp.delete();throw new IOException("Verificación SHA-256 fallida: "+target.getName());}
        if(target.exists()&&!target.delete())throw new IOException("No se pudo sustituir "+target.getName());
        if(!tmp.renameTo(target))throw new IOException("No se pudo guardar "+target.getName());
    }
    private static String sha256(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[64*1024];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.ROOT,"%02x",x));return s.toString();}
}
