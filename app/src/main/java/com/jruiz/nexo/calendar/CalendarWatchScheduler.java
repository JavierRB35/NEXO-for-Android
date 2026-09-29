package com.jruiz.nexo.calendar;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.CalendarContract;

public final class CalendarWatchScheduler {
    static final int CONTENT_JOB_ID = 7349;
    static final int PERIODIC_JOB_ID = 7352;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    private CalendarWatchScheduler() {}

    public static boolean canRun(Context context) {
        return CalendarCapturePrefs.isEnabled(context) &&
                context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static void schedule(Context context) {
        if (!canRun(context)) return;
        scheduleContentWatcher(context);
        schedulePeriodicFallback(context);
    }

    static void scheduleContentWatcher(Context context) {
        if (!canRun(context)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo contentJob = new JobInfo.Builder(
                CONTENT_JOB_ID,
                new ComponentName(context, CalendarWatchJob.class)
        )
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        CalendarContract.Events.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                ))
                .setTriggerContentUpdateDelay(1500L)
                .setTriggerContentMaxDelay(6000L)
                .build();
        scheduler.schedule(contentJob);
    }

    private static void schedulePeriodicFallback(Context context) {
        if (!canRun(context)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo periodicJob = new JobInfo.Builder(
                PERIODIC_JOB_ID,
                new ComponentName(context, CalendarWatchJob.class)
        )
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build();
        scheduler.schedule(periodicJob);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(CONTENT_JOB_ID);
            scheduler.cancel(PERIODIC_JOB_ID);
        }
    }
}
