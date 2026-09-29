package com.jruiz.nexo.reminders;

import android.content.*;
import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.medications.MedicationScheduler;
import com.jruiz.nexo.calendar.CalendarWatchScheduler;
import com.jruiz.nexo.input.SmsWatchScheduler;
import com.jruiz.nexo.input.GmailWatchScheduler;
import org.json.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        DatabaseHelper db = new DatabaseHelper(context.getApplicationContext());
        JSONArray a = db.getFutureReminders();
        for (int i=0;i<a.length();i++) {
            JSONObject r = a.optJSONObject(i);
            if (r != null) ReminderScheduler.schedule(context, r.optLong("id"), r.optString("title"), r.optLong("triggerAt"));
        }
        JSONArray meds=db.getMedications("","active");
        for(int i=0;i<meds.length();i++){
            JSONObject m=meds.optJSONObject(i);
            if(m!=null)MedicationScheduler.scheduleNext(context,db,m.optLong("id"),System.currentTimeMillis()-1);
        }
        MedicationScheduler.restorePendingRepeats(context,db);
        db.close();
        CalendarWatchScheduler.schedule(context);
        SmsWatchScheduler.schedule(context);
        GmailWatchScheduler.schedule(context);
    }
}
