package com.jruiz.nexo.medications;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.UUID;

public final class MedicationVoiceSpeaker {
    private MedicationVoiceSpeaker() {}

    public static void speak(Context context,String text,Runnable finished){
        Context app=context.getApplicationContext(); final TextToSpeech[] holder=new TextToSpeech[1]; final boolean[] done={false};
        Runnable finish=()->{if(done[0])return;done[0]=true;try{if(holder[0]!=null){holder[0].stop();holder[0].shutdown();}}catch(Exception ignored){}if(finished!=null)finished.run();};
        new Handler(Looper.getMainLooper()).postDelayed(finish,9000);
        holder[0]=new TextToSpeech(app,status->{
            if(status!=TextToSpeech.SUCCESS){finish.run();return;}
            try{
                holder[0].setLanguage(new Locale("es","ES"));
                holder[0].setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                String id="nexo-med-"+UUID.randomUUID();
                holder[0].setOnUtteranceProgressListener(new UtteranceProgressListener(){public void onStart(String u){}public void onDone(String u){finish.run();}public void onError(String u){finish.run();}});
                holder[0].speak(text,TextToSpeech.QUEUE_FLUSH,new Bundle(),id);
            }catch(Exception e){finish.run();}
        });
    }
}
