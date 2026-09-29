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
                    if (params.getJobId() == CalendarWatchScheduler.CONTENT_JOB_ID) {
                        CalendarWatchScheduler.scheduleContentWatcher(this);
                    }
                    // onStartJob() devolvió true: el job actual debe finalizar
                    // siempre, también cuando rearmamos el TriggerContentUri.
                    jobFinished(params, false);
                }
            }
        }, "nexo-calendar-watch").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        return false;
    }
}
