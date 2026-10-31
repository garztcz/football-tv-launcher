package com.gathin.tvhome.livefeed;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

/** Arms the periodic refresh: every 6 hours, any network, survives reboot. */
final class FeedScheduler {

    static final int JOB_PERIODIC = 42;
    static final int JOB_ONCE = 43;
    private static final long PERIOD_MS = 6L * 3600L * 1000L;

    private FeedScheduler() {
    }

    static void schedulePeriodic(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo info = new JobInfo.Builder(JOB_PERIODIC,
                new ComponentName(ctx, RefreshJobService.class))
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        js.schedule(info);
    }

    static void runOnce(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo info = new JobInfo.Builder(JOB_ONCE,
                new ComponentName(ctx, RefreshJobService.class))
                .setMinimumLatency(0)
                .setOverrideDeadline(60_000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        js.schedule(info);
    }
}
