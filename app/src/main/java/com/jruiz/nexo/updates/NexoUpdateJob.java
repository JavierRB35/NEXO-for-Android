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
                if (pref.getInt("last_notified_code", 0) == release.versionCode) return;
                if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager == null) return;
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL, "Actualizaciones de Nexo", NotificationManager.IMPORTANCE_DEFAULT);
                    manager.createNotificationChannel(channel);
                }
                Intent open = new Intent(this, MainActivity.class);
                open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                open.putExtra("nexo_check_updates", true);
                PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
                Notification notification = builder.setContentTitle("Nueva versión de Nexo")
                        .setContentText("Nexo " + release.versionName + " disponible. Toca para actualizar.")
                        .setSmallIcon(com.jruiz.nexo.R.mipmap.ic_launcher)
                        .setContentIntent(pending).setAutoCancel(true).build();
                manager.notify(NOTIFICATION_ID, notification);
                pref.edit().putInt("last_notified_code", release.versionCode).apply();
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
