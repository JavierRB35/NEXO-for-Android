package com.jruiz.nexo.calendar;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.CalendarContract;

public final class CalendarWatchScheduler {
    private static final int JOB_ID = 7349;

    private CalendarWatchScheduler() {}

    public static boolean canRun(Context context) {
        return CalendarCapturePrefs.isEnabled(context) &&
                context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static void schedule(Context context) {
        if (!canRun(context)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, CalendarWatchJob.class)
        )
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        CalendarContract.Events.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                ))
                .setTriggerContentUpdateDelay(2500L)
                .setTriggerContentMaxDelay(12000L)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(JOB_ID);
    }
}
