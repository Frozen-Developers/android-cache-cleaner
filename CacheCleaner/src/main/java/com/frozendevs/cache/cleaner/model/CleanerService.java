package com.frozendevs.cache.cleaner.model;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.frozendevs.cache.cleaner.R;
import com.frozendevs.cache.cleaner.helper.CacheManager;

import java.util.List;

public class CleanerService extends Service implements CacheManager.OnActionListener {

    private static final String TAG = "CleanerService";

    private CacheManager mCacheManager;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        mCacheManager = new CacheManager(getPackageManager());
        mCacheManager.setOnActionListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mCacheManager.scanCache();
        
        return START_STICKY;
    }

    @Override
    public void onScanStarted(int appsCount) {

    }

    @Override
    public void onScanProgressUpdated(int current, int max) {

    }

    @Override
    public void onScanCompleted(List<AppsListItem> apps) {
        if (mCacheManager.getCacheSize() > 0) {
            mCacheManager.cleanCache();
        }
    }

    @Override
    public void onCleanStarted() {

    }

    @Override
    public void onCleanCompleted(long cacheSize) {
        String msg = getString(R.string.cleaned) + " (" +
                Formatter.formatShortFileSize(this, cacheSize) + ")";

        Log.d(TAG, msg);

        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                stopSelf();
            }
        }, 5000);
    }
}
