package com.jruiz.nexo.medications;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Frases configurables de los seis avisos fijos de medicación. */
public final class MedicationMessageConfig {
    private MedicationMessageConfig() {}
    private static final String PREFS="nexo_medication_messages";
    public static final int[] OFFSETS={-5,0,5,10,15,20};
    public static final String[] DEFAULTS={
            "En 5 minutos te toca tomar {medicacion}.",
            "Es la hora de tomar {medicacion}.",
            "Pequeño recordatorio: {medicacion} sigue esperando.",
            "Eh… {medicacion} no se va a tomar solo 😄",
            "Vamos, que ya llevamos un rato negociando con {medicacion}.",
            "Último aviso: {medicacion} empieza a pensar que lo estás evitando."
    };

    public static String get(Context c,int offset){
        int i=index(offset); if(i<0)return DEFAULTS[1];
        String v=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(key(offset),DEFAULTS[i]);
        return v==null||v.trim().isEmpty()?DEFAULTS[i]:v.trim();
    }
    public static void set(Context c,int offset,String value){
        int i=index(offset); if(i<0)return;
        String v=value==null?"":value.trim(); if(v.isEmpty())v=DEFAULTS[i];
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(key(offset),v).apply();
    }
    public static void reset(Context c){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();}
    public static String render(Context c,int offset,String medication,long scheduledAt){
        String med=medication==null||medication.trim().isEmpty()?"la medicación":medication.trim();
        String hour=Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        return get(c,offset).replace("{medicacion}",med).replace("{hora}",hour);
    }
    public static int nearestOffset(long scheduledAt,long now){
        long mins=Math.round((now-scheduledAt)/60000.0);
        if(mins<=-2)return -5;
        if(mins<3)return 0;
        if(mins<8)return 5;
        if(mins<13)return 10;
        if(mins<18)return 15;
        return 20;
    }
    private static int index(int offset){for(int i=0;i<OFFSETS.length;i++)if(OFFSETS[i]==offset)return i;return -1;}
    private static String key(int offset){return "msg_"+(offset<0?"m"+(-offset):"p"+offset);}
}
