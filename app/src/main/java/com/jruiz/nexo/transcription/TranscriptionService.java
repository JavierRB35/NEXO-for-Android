package com.jruiz.nexo.transcription;

import android.app.*;
import android.content.*;
import android.media.MediaMetadataRetriever;
import android.os.*;

import com.jruiz.nexo.R;
import com.jruiz.nexo.recordings.RecordingStore;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Motor de transcripción local en proceso aislado :transcription. */
public class TranscriptionService extends Service {
    public static final String ACTION_START="com.jruiz.nexo.TRANSCRIBE_START";
    public static final String ACTION_CANCEL="com.jruiz.nexo.TRANSCRIBE_CANCEL";
    public static final String EXTRA_ID="recordingId";
    public static final String EXTRA_QUALITY="quality";
    public static final String CHANNEL="nexo_transcription";

    private volatile Thread worker;
    private final AtomicBoolean cancel=new AtomicBoolean(false);
    private String currentId="";
    private String currentQuality=TranscriptionModelManager.QUALITY_FAST;
    private String currentQualityLabel="Rápida";

    @Override public void onCreate(){super.onCreate();createChannel();}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"":intent.getAction();
        String id=intent==null?"":intent.getStringExtra(EXTRA_ID);
        if(ACTION_CANCEL.equals(action)){
            cancel.set(true);
            if(id!=null&&!id.isBlank())TranscriptionStore.diagnostic(this,id,"CANCEL","Cancelación solicitada");
            return START_NOT_STICKY;
        }
        if(ACTION_START.equals(action)&&id!=null&&!id.isBlank()&&(worker==null||!worker.isAlive())){
            currentId=id;
            currentQuality=TranscriptionModelManager.normalizeQuality(intent.getStringExtra(EXTRA_QUALITY));
            currentQualityLabel=TranscriptionModelManager.spec(currentQuality).label;
            cancel.set(false);
            startForeground(4901,notification("Preparando · "+currentQualityLabel,0));
            final String jobQuality=currentQuality;
            worker=new Thread(()->runTranscription(id,jobQuality),"NexoTranscription");
            worker.start();
        }
        return START_NOT_STICKY;
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}

    private void runTranscription(String id,String quality){
        String label=TranscriptionModelManager.spec(quality).label;
        try{
            TranscriptionStore.writeStatus(this,id,"running",1,"Comprobando modelo…","check_model",quality,label);
            TranscriptionStore.diagnostic(this,id,"START","id="+id+" quality="+quality+" label="+label);
            TranscriptionModelManager.ensureSafeToLoad(this,quality);

            File audio=RecordingStore.audioFile(this,id);
            if(!audio.isFile()||audio.length()==0)throw new IllegalArgumentException("No se encuentra el audio");
            TranscriptionStore.diagnostic(this,id,"AUDIO","path="+audio.getAbsolutePath()+" bytes="+audio.length());

            long duration=Math.max(1,durationMs(audio));
            StringBuilder result=new StringBuilder();
            phase(id,quality,label,1,"Preparando audio…","before_engine");
            TranscriptionStore.diagnostic(this,id,"ENGINE","Antes de crear SherpaWhisper · "+label);
            TranscriptionStore.diagnostic(this,id,"CONFIG_WHISPER","Creando configuración Whisper quality="+quality);

            try(SherpaWhisper whisper=new SherpaWhisper(this,quality)){
                TranscriptionStore.diagnostic(this,id,"ENGINE_READY","Recognizer creado correctamente · "+label);
                AudioChunkDecoder.decode(audio,16000,28,(samples,startMs)->{
                    if(cancel.get())throw new InterruptedException("Transcripción cancelada");
                    int pct=2+(int)Math.min(96,startMs*96/Math.max(1,duration));
                    phase(id,quality,label,pct,"Transcribiendo…","decode");
                    TranscriptionStore.diagnostic(this,id,"DECODE","startMs="+startMs+" samples="+samples.length+" quality="+quality);
                    String text=whisper.transcribe(samples);
                    if(text!=null&&!text.isBlank()){
                        if(result.length()>0)result.append('\n');
                        result.append('[').append(formatTime(startMs)).append("] ").append(text.trim());
                    }
                });
            }

            if(cancel.get())throw new InterruptedException("Transcripción cancelada");
            TranscriptionStore.writeText(this,id,result.toString().trim());
            TranscriptionStore.writeStatus(this,id,"done",100,"Transcripción terminada","done",quality,label);
            TranscriptionStore.diagnostic(this,id,"DONE","chars="+result.length()+" quality="+quality);
            notifyDone(id,label);
        }catch(InterruptedException e){
            TranscriptionStore.writeStatus(this,id,"cancelled",0,"Transcripción cancelada","cancelled",quality,label);
            TranscriptionStore.diagnostic(this,id,"CANCELLED",e.toString());
        }catch(Throwable e){
            String msg=friendlyError(e);
            TranscriptionStore.writeStatus(this,id,"error",0,"No se pudo transcribir: "+msg,"java_error",quality,label);
            TranscriptionStore.diagnostic(this,id,"JAVA_ERROR",e.getClass().getName()+": "+rootMessage(e)+" quality="+quality);
        }finally{
            try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Exception ignored){}
            stopSelf();
            worker=null;currentId="";
            currentQuality=TranscriptionModelManager.QUALITY_FAST;currentQualityLabel="Rápida";
        }
    }

    private void phase(String id,String quality,String label,int pct,String msg,String phase){
        TranscriptionStore.writeStatus(this,id,"running",pct,msg,phase,quality,label);
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(nm!=null)nm.notify(4901,notification(msg+" · "+label,pct));
        }catch(Exception ignored){}
    }

    private Notification notification(String text,int pct){
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification_nexo)
                .setContentTitle("Nexo · Transcripción")
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_PROGRESS);
        if(pct>0)b.setProgress(100,pct,false);else b.setProgress(0,0,true);
        return b.build();
    }
    private void notifyDone(String id,String label){
        try{
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(nm==null)return;
            Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
            b.setSmallIcon(R.drawable.ic_notification_nexo)
                    .setContentTitle("Nexo · Transcripción terminada")
                    .setContentText(label+" · ya puedes abrir el texto de la grabación.")
                    .setAutoCancel(true);
            nm.notify(4902,b.build());
        }catch(Exception ignored){}
    }
    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(nm!=null){
                NotificationChannel ch=new NotificationChannel(CHANNEL,"Transcripción",NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Procesamiento local de grabaciones en Nexo");
                nm.createNotificationChannel(ch);
            }
        }
    }
    private static long durationMs(File f){
        MediaMetadataRetriever m=new MediaMetadataRetriever();
        try{
            m.setDataSource(f.getAbsolutePath());
            String x=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return x==null?0:Long.parseLong(x);
        }catch(Exception e){return 0;}
        finally{try{m.release();}catch(Exception ignored){}}
    }
    private static String formatTime(long ms){
        long s=Math.max(0,ms/1000);
        return String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);
    }
    private static String friendlyError(Throwable e){
        if(e instanceof OutOfMemoryError)return "memoria insuficiente para el modelo seleccionado. Elige una calidad inferior o libera memoria.";
        String msg=rootMessage(e);
        String low=msg.toLowerCase(Locale.ROOT);
        if(low.contains("memory")||low.contains("alloc"))return "memoria insuficiente para inicializar el modelo seleccionado.";
        return msg;
    }
    private static String rootMessage(Throwable e){
        Throwable x=e;
        while(x.getCause()!=null&&x.getCause()!=x)x=x.getCause();
        String m=x.getMessage();
        return m==null||m.isBlank()?x.getClass().getSimpleName():m;
    }
}
