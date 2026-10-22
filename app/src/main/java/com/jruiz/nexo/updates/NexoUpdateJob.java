package com.jruiz.nexo.updates;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import com.jruiz.nexo.MainActivity;

/** Comprueba diariamente con red disponible. Android decide la hora efectiva. */
public final class NexoUpdateJob extends JobService {
    private static final int JOB_ID = 173041;
    private static final int NOTIFICATION_ID = 173042;
    private static final String CHANNEL = "nexo_app_updates";
    private static final String CRITICAL_CHANNEL = "nexo_app_updates_critical";

    public static void schedule(Context context) {
        try {
            JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (jobs == null) return;
            JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(context, NexoUpdateJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(24L * 60 * 60 * 1000)
                    .setPersisted(true)
                    .build();
            jobs.schedule(info);
        } catch (Exception ignored) {}
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                NexoUpdateManager.Release release = NexoUpdateManager.fetchRelease();
                if (!NexoUpdateManager.isNewer(release) ||
                        !NexoUpdateManager.isInstalledSignatureOfficial(this)) return;
                SharedPreferences pref = getSharedPreferences("nexo_updates", MODE_PRIVATE);
                if (pref.getInt("last_notified_code", 0) == release.versionCode &&
                        pref.getBoolean("last_notified_critical", false) == release.critical) return;
                if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager == null) return;
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL, "Actualizaciones de Nexo", NotificationManager.IMPORTANCE_DEFAULT);
                    manager.createNotificationChannel(channel);
                    if (release.critical) {
                        NotificationChannel criticalChannel = new NotificationChannel(
                                CRITICAL_CHANNEL, "Actualizaciones críticas de Nexo",
                                NotificationManager.IMPORTANCE_HIGH);
                        manager.createNotificationChannel(criticalChannel);
                    }
                }
                Intent open = new Intent(this, MainActivity.class);
                open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                open.putExtra("nexo_check_updates", true);
                PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(this, release.critical ? CRITICAL_CHANNEL : CHANNEL)
                        : new Notification.Builder(this);
                String title = release.critical ? "Actualización CRÍTICA de Nexo" : "Actualización opcional de Nexo";
                String detail = release.critical
                        ? "Nexo " + release.versionName + " es obligatoria. " + release.criticalReason
                        : "Nexo " + release.versionName + " disponible. Puedes actualizar cuando quieras.";
                Notification notification = builder.setContentTitle(title)
                        .setContentText(detail)
                        .setStyle(new Notification.BigTextStyle().bigText(detail))
                        .setSmallIcon(com.jruiz.nexo.R.mipmap.ic_launcher)
                        .setContentIntent(pending).setAutoCancel(true).build();
                manager.notify(NOTIFICATION_ID, notification);
                pref.edit().putInt("last_notified_code", release.versionCode)
                        .putBoolean("last_notified_critical", release.critical).apply();
            } catch (Exception ignored) {
                // Sin conexión o sin canal publicado: reintentar en la próxima ventana de Android.
            } finally {
                jobFinished(params, false);
            }
        }, "nexo-background-update-check").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return true;
    }
}
