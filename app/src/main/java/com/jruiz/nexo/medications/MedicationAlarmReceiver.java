package com.jruiz.nexo.medications;

import android.app.*;
import android.content.*;
import android.os.Build;

import com.jruiz.nexo.MainActivity;
import com.jruiz.nexo.R;
import com.jruiz.nexo.data.DatabaseHelper;

import org.json.JSONObject;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Recibe la serie fija -5, 0, +5, +10, +15 y +20 minutos. */
public class MedicationAlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID="nexo_medications";

    @Override public void onReceive(Context context,Intent intent){
        long medicationId=intent.getLongExtra("medicationId",0),scheduledAt=intent.getLongExtra("scheduledAt",0);
        String kind=intent.getStringExtra("kind");if(kind==null)kind="initial";
        if(medicationId<=0||scheduledAt<=0)return;
        JSONObject med;String status;long now=System.currentTimeMillis();
        try(DatabaseHelper db=new DatabaseHelper(context.getApplicationContext())){
            med=db.getMedication(medicationId);if(med.length()==0||!med.optBoolean("active",false))return;
            db.ensureMedicationDose(medicationId,scheduledAt);
            status=db.getMedicationDoseStatusPublic(medicationId,scheduledAt);
            if("taken".equals(status)||"skipped".equals(status)){MedicationScheduler.cancelDoseAlerts(context,medicationId,scheduledAt);return;}
            // No adelantar una toma pospuesta mediante un PendingIntent de repetición ya en tránsito.
            if("snoozed".equals(status)&&!"snooze".equals(kind))return;

            if("snooze".equals(kind)){
                // El aviso pospuesto se muestra aunque haya terminado la ventana fija de +20 min.
                if(!"snoozed".equals(status)||!db.resumeMedicationDoseAfterSnooze(medicationId,scheduledAt))return;
                if(MedicationRepeatPolicy.withinRepeatWindow(scheduledAt,now)){
                    long nextRepeat=MedicationRepeatPolicy.nextRepeatAt(scheduledAt,now);
                    if(nextRepeat>0)MedicationScheduler.scheduleRepeat(context,medicationId,scheduledAt,nextRepeat);
                }
            } else if("initial".equals(kind)) {
                // El aviso previo no bloquea la programación de la siguiente toma de la pauta.
                MedicationScheduler.scheduleNext(context,db,medicationId,scheduledAt);
                // Si Android entregó el aviso con más de 30 min de retraso, no reabrimos el ciclo.
                if(!MedicationRepeatPolicy.withinRepeatWindow(scheduledAt,now)){
                    MedicationScheduler.cancelDoseAlerts(context,medicationId,scheduledAt);
                    return;
                }
                long firstRepeat=MedicationRepeatPolicy.nextRepeatAt(scheduledAt,now);
                if(firstRepeat>0)MedicationScheduler.scheduleRepeat(context,medicationId,scheduledAt,firstRepeat);
            } else {
                // Una repetición o pospuesto que llegue fuera de la ventana máxima se descarta.
                if(!MedicationRepeatPolicy.withinRepeatWindow(scheduledAt,now)){
                    MedicationScheduler.cancelDoseAlerts(context,medicationId,scheduledAt);
                    return;
                }
                long nextRepeat=MedicationRepeatPolicy.nextRepeatAt(scheduledAt,now);
                if(nextRepeat>0)MedicationScheduler.scheduleRepeat(context,medicationId,scheduledAt,nextRepeat);
            }
        }
        postNotification(context,med,scheduledAt,kind);
        if(med.optBoolean("voiceEnabled",true)){
            PendingResult pending=goAsync();
            String label=medicationLabel(med);
            int offset=MedicationMessageConfig.nearestOffset(scheduledAt,System.currentTimeMillis());
            String phrase=MedicationMessageConfig.render(context,offset,label,scheduledAt);
            MedicationVoiceSpeaker.speak(context,phrase,pending::finish);
        }
    }

    private static void postNotification(Context context,JSONObject med,long scheduledAt,String kind){
        NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Medicamentos",NotificationManager.IMPORTANCE_HIGH);ch.setDescription("Avisos de tomas de medicamentos guardadas en Nexo");ch.enableVibration(true);nm.createNotificationChannel(ch);}
        long medId=med.optLong("id");String name=med.optString("name","Medicamento"),dose=med.optString("dose","");String time=Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        Intent open=new Intent(context,MainActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(context,(int)(medId&0x7fffffff),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent taken=actionPi(context,medId,scheduledAt,"taken",1),snooze=actionPi(context,medId,scheduledAt,"snooze",2),skip=actionPi(context,medId,scheduledAt,"skipped",3);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL_ID):new Notification.Builder(context);
        String label=dose.isBlank()?name:name+" "+dose;
        int offset=MedicationMessageConfig.nearestOffset(scheduledAt,System.currentTimeMillis());
        String body=MedicationMessageConfig.render(context,offset,label,scheduledAt);
        b.setSmallIcon(R.drawable.ic_notification_nexo).setContentTitle("Nexo · Medicación").setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(false).setOngoing(false).setContentIntent(content)
                .addAction(new Notification.Action.Builder(0,"✓ TOMADO",taken).build());
        b.addAction(new Notification.Action.Builder(0,"POSPONER 10 MIN",snooze).build());
        b.addAction(new Notification.Action.Builder(0,"OMITIR",skip).build());
        try { nm.notify(notificationId(medId,scheduledAt),b.build()); } catch (Exception ignored) { }
    }

    private static String medicationLabel(JSONObject med){
        String name=med.optString("name","Medicamento").trim(),dose=med.optString("dose","").trim();
        return dose.isEmpty()?name:name+" "+dose;
    }

    private static String spokenTime(long at){
        LocalTime t=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime();int h=t.getHour(),m=t.getMinute();
        if(m==0)return "a las "+h+" horas";return "a las "+h+" y "+m;
    }

    public static int notificationId(long medId,long scheduledAt){return (int)((medId*1009L+scheduledAt/60000L)&0x7fffffff);}
    private static PendingIntent actionPi(Context c,long id,long at,String action,int salt){Intent i=new Intent(c,MedicationActionReceiver.class);i.setAction("com.jruiz.nexo.MED_"+action.toUpperCase(Locale.ROOT));i.putExtra("medicationId",id);i.putExtra("scheduledAt",at);i.putExtra("medAction",action);return PendingIntent.getBroadcast(c,(int)((id*37+at/60000+salt)&0x7fffffff),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
