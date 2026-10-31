package com.gathin.tvhome.livefeed;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Runs one feed refresh off the main thread, then reports completion. */
public final class RefreshJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                FeedUpdater.update(getApplicationContext());
            } finally {
                jobFinished(params, false);
            }
        }, "livefeed-refresh").start();
        return true; // work continues on the background thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // worth retrying when the system stops us early
    }
}
