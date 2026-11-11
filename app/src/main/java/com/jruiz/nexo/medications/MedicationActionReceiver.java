package com.jruiz.nexo.medications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.jruiz.nexo.data.DatabaseHelper;

public class MedicationActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        long medId=intent.getLongExtra("medicationId",0),scheduledAt=intent.getLongExtra("scheduledAt",0);
        String action=intent.getStringExtra("medAction");
        if(medId<=0||scheduledAt<=0||(!"taken".equals(action)&&!"skipped".equals(action)&&!"snooze".equals(action)))return;
        boolean dismissNotification=true;
        try(DatabaseHelper db=new DatabaseHelper(context.getApplicationContext())){
            String status=db.getMedicationDoseStatusPublic(medId,scheduledAt);
            if("taken".equals(status)||"skipped".equals(status)){
                MedicationScheduler.cancelDoseAlerts(context,medId,scheduledAt);
            } else if("snooze".equals(action)){
                if(!db.getMedication(medId).optBoolean("active",false)){
                    MedicationScheduler.cancelDoseAlerts(context,medId,scheduledAt);
                } else {
                    boolean scheduled=MedicationScheduler.scheduleSnooze(context,medId,scheduledAt,10);
                    if(scheduled&&db.setMedicationDoseStatus(medId,scheduledAt,"snoozed")){
                        MedicationScheduler.pauseDoseRepeats(context,medId,scheduledAt);
                    } else {
                        if(scheduled)MedicationScheduler.cancelSnoozeAlert(context,medId,scheduledAt);
                        dismissNotification=false;
                    }
                }
            } else if(db.setMedicationDoseStatus(medId,scheduledAt,action)){
                MedicationScheduler.cancelDoseAlerts(context,medId,scheduledAt);
            } else dismissNotification=false;
        }
        if(dismissNotification){
            NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(nm!=null)nm.cancel(MedicationAlarmReceiver.notificationId(medId,scheduledAt));
        }
    }
}
