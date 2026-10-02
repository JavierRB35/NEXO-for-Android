package com.jruiz.nexo.input;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

public final class GmailWatchScheduler {
    private static final int JOB_ID = 7351;
    private static final long PERIOD_MS = 15L * 60L * 1000L;

    private GmailWatchScheduler() {}

    public static void schedule(Context context) {
        if (!InputHubPrefs.autoScanEnabled(context) || !InputHubPrefs.gmailEnabled(context)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, GmailWatchJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build();
        scheduler.schedule(job);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(JOB_ID);
    }
}
