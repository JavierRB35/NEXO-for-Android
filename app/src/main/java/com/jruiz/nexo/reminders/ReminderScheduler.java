package com.jruiz.nexo.reminders;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Programa los avisos de recordatorios.
 *
 * En Android 12+ usa alarmas exactas cuando el usuario ha concedido el acceso
 * especial correspondiente. Si no está concedido, mantiene un aviso compatible
 * mediante setAndAllowWhileIdle() para no perder el recordatorio.
 */
public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static void schedule(Context context, long reminderId, String title, long triggerAt) {
        if (triggerAt <= System.currentTimeMillis()) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(context, AlarmReceiver.class);
        i.putExtra("reminderId", reminderId);
        i.putExtra("title", title);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode(reminderId),
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        setAlarm(am, triggerAt, pi);
    }

    public static void cancel(Context context, long reminderId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode(reminderId),
                i,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    /**
     * Estrategia de máxima puntualidad compatible con Android 8+:
     * - Android < 12: setExactAndAllowWhileIdle().
     * - Android 12+: setExactAndAllowWhileIdle() si existe permiso de alarma exacta.
     * - Sin permiso o ante SecurityException: setAndAllowWhileIdle() como respaldo.
     */
    private static void setAlarm(AlarmManager am, long triggerAt, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private static int requestCode(long reminderId) {
        return (int) (reminderId & 0x7fffffff);
    }
}
