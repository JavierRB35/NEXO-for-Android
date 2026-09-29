package com.jruiz.nexo.calendar;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class CalendarWatchJob extends JobService {
    private volatile boolean stopped;

    @Override public boolean onStartJob(JobParameters params) {
        stopped = false;
        new Thread(() -> {
            try {
                if (!stopped && CalendarWatchScheduler.canRun(this)) {
                    CalendarWatchEngine.scan(this);
                }
            } finally {
                if (!stopped) {
                    jobFinished(params, false);
                    CalendarWatchScheduler.schedule(this);
                }
            }
        }, "nexo-calendar-watch").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        return true;
    }
}
