package com.jruiz.nexo.transcription;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public final class TranscriptionStore {
    private TranscriptionStore(){}
    public static File dir(Context c){File d=new File(c.getFilesDir(),"transcriptions");if(!d.exists())d.mkdirs();return d;}
    public static File textFile(Context c,String id){validate(id);return new File(dir(c),id+".txt");}
    private static File statusFile(Context c,String id){validate(id);return new File(dir(c),id+".status.json");}
    public static File diagnosticFile(Context c,String id){validate(id);return new File(dir(c),id+".diagnostic.txt");}
    public static boolean exists(Context c,String id){return textFile(c,id).isFile();}
    public static String read(Context c,String id){File f=textFile(c,id);if(!f.isFile())return "";try{return new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    public static void writeText(Context c,String id,String text)throws Exception{atomicWrite(textFile(c,id),text==null?"":text);}
    public static void writeStatus(Context c,String id,String state,int progress,String message,String phase){JSONObject o=new JSONObject();try{o.put("id",id);o.put("state",state);o.put("progress",progress);o.put("message",message==null?"":message);o.put("phase",phase==null?"":phase);o.put("updatedAt",System.currentTimeMillis());atomicWrite(statusFile(c,id),o.toString());}catch(Exception ignored){}}
    public static JSONObject status(Context c,String id){File f=statusFile(c,id);if(!f.isFile()){JSONObject o=new JSONObject();try{o.put("id",id);o.put("state",exists(c,id)?"done":"idle");o.put("progress",exists(c,id)?100:0);}catch(Exception ignored){}return o;}try{return new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}catch(Exception e){return new JSONObject();}}
    public static void diagnostic(Context c,String id,String phase,String text){try{File f=diagnosticFile(c,id);String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.ROOT).format(new Date());try(FileOutputStream out=new FileOutputStream(f,true)){String line=ts+" | pid="+android.os.Process.myPid()+" | "+phase+" | "+(text==null?"":text)+"\n";out.write(line.getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}}
    public static String diagnostic(Context c,String id){File f=diagnosticFile(c,id);if(!f.isFile())return "";try{return new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    public static boolean delete(Context c,String id){boolean a=!textFile(c,id).exists()||textFile(c,id).delete();File st=statusFile(c,id),dg=diagnosticFile(c,id);if(st.exists())st.delete();if(dg.exists())dg.delete();return a;}
    public static void deleteForRecording(Context c,String id){delete(c,id);}
    private static void atomicWrite(File target,String text)throws Exception{File tmp=new File(target.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp,false)){out.write(text.getBytes(StandardCharsets.UTF_8));out.flush();try{out.getFD().sync();}catch(Exception ignored){}}if(target.exists()&&!target.delete())throw new IOException("No se pudo sustituir archivo");if(!tmp.renameTo(target))throw new IOException("No se pudo guardar archivo");}
    private static void validate(String id){if(id==null||!id.matches("rec_[0-9]{10,20}"))throw new IllegalArgumentException("Id no válido");}
}
