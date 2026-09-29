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
                    jobFinished(params, false);
                    SmsWatchScheduler.schedule(this);
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
