package com.gathin.tvhome.livefeed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot: re-arm the 6-hourly refresh and fetch once right away. */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            FeedScheduler.schedulePeriodic(context);
            FeedScheduler.runOnce(context);
        }
    }
}
