package com.jruiz.nexo.reminders;

import android.app.*;
import android.content.*;
import android.os.Build;

import com.jruiz.nexo.MainActivity;
import com.jruiz.nexo.R;
import com.jruiz.nexo.data.DatabaseHelper;
import com.jruiz.nexo.core.PlaceEntry;

import org.json.JSONObject;

import java.time.*;

public class AlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "nexo_reminders";

    @Override public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        if (title == null || title.isBlank()) title = "Recordatorio de Nexo";
        long id = intent.getLongExtra("reminderId", System.currentTimeMillis());
        JSONObject voiceData = new JSONObject();
        try (DatabaseHelper db = new DatabaseHelper(context.getApplicationContext())) { voiceData = db.getReminderVoiceData(id); } catch (Exception ignored) {}
        // An alarm created by an older version must not turn a saved place or document into a reminder.
        if (PlaceEntry.isPlace(voiceData.optString("type")) || "document".equals(voiceData.optString("type"))) {
            ReminderScheduler.cancel(context,id);
            try (DatabaseHelper db = new DatabaseHelper(context.getApplicationContext())) { db.setReminderEnabled(id,false); } catch(Exception ignored) {}
            return;
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Recordatorios", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Avisos y vencimientos guardados en Nexo");
                nm.createNotificationChannel(ch);
            }

            Intent open = new Intent(context, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
            b.setSmallIcon(R.drawable.ic_notification_nexo)
                    .setContentTitle("Nexo")
                    .setContentText(title)
                    .setStyle(new Notification.BigTextStyle().bigText(title))
                    .setAutoCancel(true)
                    .setContentIntent(content)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setVisibility(Notification.VISIBILITY_PRIVATE);
            nm.notify((int)(id & 0x7fffffff), b.build());
        }

        if (voiceData.optBoolean("voiceEnabled", false)) {
            PendingResult pending = goAsync();
            ReminderVoiceSpeaker.speak(context, spokenPhrase(voiceData, title), pending::finish);
        }

        // Una serie mantiene una única alarma física. Tras dispararla, la movemos a la siguiente aparición.
        try (DatabaseHelper db = new DatabaseHelper(context.getApplicationContext())) {
            JSONObject next = db.advanceRecurringReminder(id);
            if (next.length() > 0) {
                ReminderScheduler.schedule(context, next.optLong("id"), next.optString("title", title), next.optLong("triggerAt"));
            }
        } catch (Exception ignored) {}
    }


    private static String spokenPhrase(JSONObject data,String fallbackTitle) {
        String title=data.optString("title",fallbackTitle==null?"recordatorio":fallbackTitle);
        String type=data.optString("type","reminder");
        StringBuilder b=new StringBuilder("Atención. ");
        if ("event".equals(type)) b.append("Tienes ").append(title).append(".");
        else b.append("Recuerda: ").append(title).append(".");
        long due=data.optLong("dueAt",0);
        if (due>0 && data.optBoolean("hasTime",false)) {
            LocalTime t=Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalTime();
            b.append(" La hora señalada es a las ").append(t.getHour());
            if(t.getMinute()!=0)b.append(" y ").append(t.getMinute());
            b.append(".");
        }
        return b.toString();
    }
}
