package com.jruiz.nexo.input;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class SmsWatchJob extends JobService {
    private volatile boolean stopped;

    @Override public boolean onStartJob(JobParameters params) {
        stopped = false;
        new Thread(() -> {
            try {
                if (!stopped && SmsWatchScheduler.canRun(this)) SmsWatchEngine.scan(this);
            } finally {
                if (!stopped) {
                    boolean contentJob = params.getJobId() == SmsWatchScheduler.CONTENT_JOB_ID;
                    // onStartJob() devolvió true: cerramos siempre el job actual.
                    jobFinished(params, false);
                    // Después de finalizarlo, rearmamos un nuevo TriggerContentUri.
                    if (contentJob) SmsWatchScheduler.scheduleContentWatcher(this);
                }
            }
        }, "nexo-sms-watch").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        return true;
    }
}
