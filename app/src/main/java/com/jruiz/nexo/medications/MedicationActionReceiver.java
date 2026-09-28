package com.jruiz.nexo.medications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jruiz.nexo.data.DatabaseHelper;

public class MedicationActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        long medId=intent.getLongExtra("medicationId",0),scheduledAt=intent.getLongExtra("scheduledAt",0);String action=intent.getStringExtra("medAction");if(medId<=0||scheduledAt<=0||action==null)return;
        boolean dismissNotification=true;
        try(DatabaseHelper db=new DatabaseHelper(context.getApplicationContext())){
            if("snooze".equals(action)){
                MedicationScheduler.cancelDoseAlerts(context,medId,scheduledAt);
                boolean scheduled=MedicationScheduler.scheduleSnooze(context,medId,scheduledAt,10);
                if(scheduled) db.setMedicationDoseStatus(medId,scheduledAt,"snoozed");
                else dismissNotification=false; // La ventana de 30 min ya terminó: deja visible el pendiente.
            } else {
                db.setMedicationDoseStatus(medId,scheduledAt,"taken".equals(action)?"taken":"skipped");
                MedicationScheduler.cancelDoseAlerts(context,medId,scheduledAt);
            }
        }
        if(dismissNotification){
            NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(MedicationAlarmReceiver.notificationId(medId,scheduledAt));
        }
    }
}
