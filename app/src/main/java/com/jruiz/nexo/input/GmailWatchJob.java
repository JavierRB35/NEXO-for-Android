package com.jruiz.nexo.input;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class GmailWatchJob extends JobService {
    private volatile boolean stopped;

    @Override public boolean onStartJob(JobParameters params) {
        stopped = false;
        new Thread(() -> {
            try {
                if (!stopped && InputHubPrefs.gmailEnabled(this)) GmailWatchEngine.scan(this);
            } finally {
                if (!stopped) jobFinished(params, false);
            }
        }, "nexo-gmail-watch").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        return true;
    }
}
