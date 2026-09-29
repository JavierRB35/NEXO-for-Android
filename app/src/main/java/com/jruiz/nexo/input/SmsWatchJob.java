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
                    if (params.getJobId() == SmsWatchScheduler.CONTENT_JOB_ID) {
                        SmsWatchScheduler.scheduleContentWatcher(this);
                    } else {
                        jobFinished(params, false);
                    }
                }
            }
        }, "nexo-sms-watch").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        return false;
    }
}
