package com.jruiz.nexo.input;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Telephony;

public final class SmsWatchScheduler {
    private static final int JOB_ID = 7350;

    private SmsWatchScheduler() {}

    public static boolean canRun(Context context) {
        return InputHubPrefs.smsEnabled(context) &&
                context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void schedule(Context context) {
        if (!canRun(context)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, SmsWatchJob.class))
                .addTriggerContentUri(new JobInfo.TriggerContentUri(
                        Telephony.Sms.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                ))
                .setTriggerContentUpdateDelay(1200L)
                .setTriggerContentMaxDelay(5000L)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(JOB_ID);
    }
}
