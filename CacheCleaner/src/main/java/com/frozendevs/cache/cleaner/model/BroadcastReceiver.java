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

        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) &&
                sharedPreferences.getBoolean(context.getString(R.string.clean_on_device_startup_key), false)) {
            context.startService(new Intent(context, CleanerService.class));
        }
    }
}
