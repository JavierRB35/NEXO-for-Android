package com.jruiz.nexo.medications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONObject;

/** Programa la siguiente toma y, por separado, las repeticiones de una toma pendiente. */
public final class MedicationScheduler {
    private MedicationScheduler() {}
    public static final int REPEAT_MINUTES = MedicationRepeatPolicy.REPEAT_MINUTES;
    public static final int MAX_REPEAT_AFTER_MINUTES = MedicationRepeatPolicy.MAX_AFTER_MINUTES;

    public static void scheduleNext(Context context, DatabaseHelper db, long medicationId, long afterMillis) {
        cancelMain(context, medicationId);
        JSONObject next=db.getNextMedicationDoseInfo(medicationId, afterMillis);
        // Si esa toma ya tiene registro, su aviso previo ya ocurrió. No la reprogrames
        // al volver a abrir Nexo; las repeticiones pendientes se gestionan por separado.
        int guard=0;
        while(next.length()>0 && next.optBoolean("doseRecorded",false) && guard++<8){
            long recordedAt=next.optLong("scheduledAt",0);if(recordedAt<=0)break;
            next=db.getNextMedicationDoseInfo(medicationId,recordedAt);
        }
        if(next.length()==0)return;
        long scheduledAt=next.optLong("scheduledAt",0),alertAt=scheduledAt-5L*60000L;
        if(scheduledAt<=System.currentTimeMillis())return;
        if(alertAt<=System.currentTimeMillis())alertAt=System.currentTimeMillis()+1200;
        Intent i=baseIntent(context,medicationId,scheduledAt,"initial");
        PendingIntent pi=PendingIntent.getBroadcast(context,requestCode(medicationId),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        setAlarm(context,alertAt,pi);
    }

    /**
     * Programa un aviso repetitivo únicamente si cae dentro de los 30 minutos
     * posteriores a la hora prevista (máximo +20 min). Devuelve false cuando ya no corresponde avisar.
     */
    public static boolean scheduleRepeat(Context context,long medicationId,long scheduledAt,long whenMillis){
        if(scheduledAt<=0 || whenMillis<=0 || whenMillis>MedicationRepeatPolicy.windowEnd(scheduledAt))return false;
        long actualAt=Math.max(System.currentTimeMillis()+1000,whenMillis);
        if(actualAt>MedicationRepeatPolicy.windowEnd(scheduledAt))return false;
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(am==null)return false;
        Intent i=baseIntent(context,medicationId,scheduledAt,"repeat");
        PendingIntent pi=PendingIntent.getBroadcast(context,repeatRequestCode(medicationId,scheduledAt),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        setAlarm(context,actualAt,pi);
        return true;
    }

    public static boolean scheduleSnooze(Context context,long medicationId,long scheduledAt,int minutes){ return false; }

    public static boolean canSnooze(long scheduledAt,int minutes){ return false; }

    /** Reconstruye solo repeticiones de tomas pendientes que aún están dentro de su ventana de 20 minutos. */
    public static void restorePendingRepeats(Context context, DatabaseHelper db) {
        try {
            org.json.JSONArray a=db.getUnresolvedMedicationDoses();
            long now=System.currentTimeMillis();
            for(int i=0;i<a.length();i++){
                org.json.JSONObject d=a.optJSONObject(i);if(d==null)continue;
                long id=d.optLong("medicationId",0),at=d.optLong("scheduledAt",0);
                if(id<=0||at<=0)continue;
                if(!MedicationRepeatPolicy.withinRepeatWindow(at,now)){
                    cancelDoseAlerts(context,id,at);
                    continue;
                }
                long candidate=MedicationRepeatPolicy.nextRepeatAt(at,now);
                if(candidate>0)scheduleRepeat(context,id,at,candidate);else cancelDoseAlerts(context,id,at);
            }
        } catch(Exception ignored) {}
    }

    /** Cancela la programación de la serie, pero no necesariamente avisos pendientes de dosis ya vencidas. */
    public static void cancel(Context context,long medicationId){ cancelMain(context,medicationId); }

    public static void cancelDoseAlerts(Context context,long medicationId,long scheduledAt){
        cancelRepeat(context,medicationId,scheduledAt);
        cancelSnooze(context,medicationId,scheduledAt);
    }

    private static void cancelMain(Context context,long medicationId){
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
        PendingIntent pi=PendingIntent.getBroadcast(context,requestCode(medicationId),baseIntent(context,medicationId,0,"initial"),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null){am.cancel(pi);pi.cancel();}
    }
    private static void cancelRepeat(Context context,long id,long at){
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
        PendingIntent pi=PendingIntent.getBroadcast(context,repeatRequestCode(id,at),baseIntent(context,id,at,"repeat"),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null){am.cancel(pi);pi.cancel();}
    }
    private static void cancelSnooze(Context context,long id,long at){
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
        PendingIntent pi=PendingIntent.getBroadcast(context,snoozeRequestCode(id,at),baseIntent(context,id,at,"snooze"),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(pi!=null){am.cancel(pi);pi.cancel();}
    }

    private static Intent baseIntent(Context context,long medicationId,long scheduledAt,String kind){
        Intent i=new Intent(context,MedicationAlarmReceiver.class);
        i.putExtra("medicationId",medicationId);i.putExtra("scheduledAt",scheduledAt);i.putExtra("kind",kind);
        return i;
    }
    private static void setAlarm(Context context,long at,PendingIntent pi){
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
        try {
            if(Build.VERSION.SDK_INT>=31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
            else if(Build.VERSION.SDK_INT<31) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        } catch(SecurityException e){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);}
    }

    private static int requestCode(long id){return 0x4D000000 | ((int)id & 0x00ffffff);}
    private static int repeatRequestCode(long id,long at){return 0x4E000000 | ((int)(id*53L+at/60000L)&0x00ffffff);}
    private static int snoozeRequestCode(long id,long at){return 0x5A000000 | ((int)(id*31L+at/60000L)&0x00ffffff);}
}
