package com.frozendevs.cache.cleaner.model;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.frozendevs.cache.cleaner.R;

public class BroadcastReceiver extends android.content.BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        String action = intent.getAction();

        if (action != null) {
            if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                if (sharedPreferences.getBoolean(context.getString(
                        R.string.clean_on_system_startup_key), false)) {
                    Intent serviceIntent = new Intent(context, CleanerService.class);
                    serviceIntent.setAction(CleanerService.ACTION_CLEAN_AND_EXIT);
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
