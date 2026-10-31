package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * restore_key_services at boot: TCL Google TV turns the accessibility master switch off during
 * boot (the enabled list survives), which leaves the Home helper dead until someone turns it back
 * on. MainActivity's repair only runs when Home is opened; this receiver runs it at boot instead,
 * so the helper revives by itself. BOOT_COMPLETED reaches a non-direct-boot-aware app only after
 * the user is unlocked, when the config and the helper records (credential storage) can be read.
 */
public class BootRepairReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Config cfg = Config.load(context);
        if (cfg.restoreKeyServices) ServiceRepair.restore(context);
    }
}
