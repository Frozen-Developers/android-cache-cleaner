package com.frozendevs.cache.cleaner.model;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.text.format.Formatter;
import android.widget.Toast;

import com.frozendevs.cache.cleaner.R;
import com.frozendevs.cache.cleaner.helper.CacheManager;

public class CleanerService extends Service implements CacheManager.OnActionListener {

    private CacheManager cacheManager = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        cacheManager = new CacheManager(getPackageManager());
        cacheManager.setOnActionListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        cacheManager.scanCache();
        return START_STICKY;
    }

    @Override
    public void onScanStarted() {
    }

    @Override
    public void onScanCompleted() {
        long size = 0;

        for(AppsListItem app : cacheManager.getAppsList())
            size += app.getCacheSize();

        cacheManager.cleanCache(size);
    }

    @Override
    public void onCleanStarted() {

    }

    @Override
    public void onCleanCompleted(long cacheSize) {
        Toast.makeText(this, getString(R.string.cleaned) + " (" +
                Formatter.formatShortFileSize(this, cacheSize) + ")", Toast.LENGTH_LONG).show();

        stopSelf();
    }
}
