package com.frozendevs.cache.cleaner.model;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StatFs;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.frozendevs.cache.cleaner.R;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CleanerService extends Service {

    public static final String ACTION_CLEAN_AND_EXIT = "com.frozendevs.cache.cleaner.CLEAN_AND_EXIT";

    private static final String TAG = "CleanerService";

    private Method mGetPackageSizeInfoMethod, mFreeStorageAndNotifyMethod;
    private OnActionListener mOnActionListener;
    private boolean mIsScanning = false;
    private boolean mIsCleaning = false;
    private long mCacheSize = 0;

    public interface OnActionListener {
        void onScanStarted(Context context);

        void onScanProgressUpdated(Context context, int current, int max);

        void onScanCompleted(Context context, List<AppsListItem> apps);

        void onCleanStarted(Context context);

        void onCleanCompleted(Context context, boolean succeeded);
    }

    public class CleanerServiceBinder extends Binder {

        public CleanerService getService() {
            return CleanerService.this;
        }
    }

    private CleanerServiceBinder mBinder = new CleanerServiceBinder();

    private class TaskScan extends AsyncTask<Void, Integer, List<AppsListItem>> {

        private int mAppCount = 0;

        @Override
        protected void onPreExecute() {
            if (mOnActionListener != null) {
                mOnActionListener.onScanStarted(CleanerService.this);
            }
        }

        @Override
        protected List<AppsListItem> doInBackground(Void... params) {
            mCacheSize = 0;

            final List<ApplicationInfo> packages = getPackageManager().getInstalledApplications(
                    PackageManager.GET_META_DATA);

            publishProgress(0, packages.size());

            final CountDownLatch countDownLatch = new CountDownLatch(packages.size());

            final List<AppsListItem> apps = new ArrayList<>();

            try {
                for (ApplicationInfo pkg : packages) {
                    mGetPackageSizeInfoMethod.invoke(getPackageManager(), pkg.packageName,
                            new IPackageStatsObserver.Stub() {

                                @Override
                                public void onGetStatsCompleted(PackageStats pStats,
                                                                boolean succeeded)
                                        throws RemoteException {
                                    synchronized (apps) {
                                        publishProgress(++mAppCount, packages.size());

                                        mCacheSize += addPackage(apps, pStats, succeeded);
                                    }

                                    synchronized (countDownLatch) {
                                        countDownLatch.countDown();
                                    }
                                }
                            }
                    );
                }

                countDownLatch.await();
            } catch (InvocationTargetException | InterruptedException | IllegalAccessException e) {
                e.printStackTrace();
            }

            return new ArrayList<>(apps);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (mOnActionListener != null) {
                mOnActionListener.onScanProgressUpdated(CleanerService.this, values[0], values[1]);
            }
        }

        @Override
        protected void onPostExecute(List<AppsListItem> result) {
            if (mOnActionListener != null) {
                mOnActionListener.onScanCompleted(CleanerService.this, result);
            }

            mIsScanning = false;
        }

        private long addPackage(List<AppsListItem> apps, PackageStats pStats, boolean succeeded) {
            long cacheSize = 0;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                cacheSize += pStats.cacheSize;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                cacheSize += pStats.externalCacheSize;
            }

            if (!succeeded || cacheSize <= 0) {
                return 0;
            }

            try {
                PackageManager packageManager = getPackageManager();
                ApplicationInfo info = packageManager.getApplicationInfo(pStats.packageName,
                        PackageManager.GET_META_DATA);

                apps.add(new AppsListItem(pStats.packageName,
                        packageManager.getApplicationLabel(info).toString(),
                        packageManager.getApplicationIcon(pStats.packageName),
                        cacheSize));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            return cacheSize;
        }
    }

    private class TaskClean extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            if (mOnActionListener != null) {
                mOnActionListener.onCleanStarted(CleanerService.this);
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);

            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

            try {
                if (canCleanInternalCache(CleanerService.this)) {
                    mFreeStorageAndNotifyMethod.invoke(getPackageManager(),
                            (long) stat.getBlockCount() * (long) stat.getBlockSize(),
                            new IPackageDataObserver.Stub() {
                                @Override
                                public void onRemoveCompleted(String packageName, boolean succeeded)
                                        throws RemoteException {
                                    countDownLatch.countDown();
                                }
                            }
                    );
                } else {
                    countDownLatch.countDown();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    if (isExternalStorageWritable()) {
                        final File externalDataDirectory = new File(Environment
                                .getExternalStorageDirectory().getAbsolutePath() + "/Android/data");

                        final String externalCachePath = externalDataDirectory.getAbsolutePath() +
                                "/%s/cache";

                        if (externalDataDirectory.isDirectory()) {
                            final File[] files = externalDataDirectory.listFiles();

                            for (File file : files) {
                                if (!deleteDirectory(new File(String.format(externalCachePath,
                                        file.getName())), true)) {
                                    Log.e(TAG, "External storage suddenly becomes unavailable");

                                    return false;
                                }
                            }
                        } else {
                            Log.e(TAG, "External data directory is not a directory!");
                        }
                    } else {
                        Log.d(TAG, "External storage is unavailable");
                    }
                }

                countDownLatch.await();
            } catch (InterruptedException | IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                mCacheSize = 0;
            }

            if (mOnActionListener != null) {
                mOnActionListener.onCleanCompleted(CleanerService.this, result);
            }

            mIsCleaning = false;
        }

        private boolean deleteDirectory(File file, boolean directoryOnly) {
            if (!isExternalStorageWritable()) {
                return false;
            }

            if (file == null || !file.exists() || (directoryOnly && !file.isDirectory())) {
                return true;
            }

            if (file.isDirectory()) {
                final File[] children = file.listFiles();

                if (children != null) {
                    for (File child : children) {
                        if (!deleteDirectory(child, false)) {
                            return false;
                        }
                    }
                }
            }

            file.delete();

            return true;
        }

        private boolean isExternalStorageWritable() {
            return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        try {
            mGetPackageSizeInfoMethod = getPackageManager().getClass().getMethod(
                    "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

            mFreeStorageAndNotifyMethod = getPackageManager().getClass().getMethod(
                    "freeStorageAndNotify", long.class, IPackageDataObserver.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = null;

        if (intent != null) {
            action = intent.getAction();
        }

        if (action == null) {
            return START_NOT_STICKY;
        }

        if (action.equals(ACTION_CLEAN_AND_EXIT)) {
            if (!canCleanExternalCache(this)) {
                Log.e(TAG, "Could not clean the cache: Insufficient permissions");

                Toast.makeText(this, getString(R.string.toast_could_not_clean_reason,
                        getString(R.string.rationale_title)), Toast.LENGTH_LONG).show();

                return START_NOT_STICKY;
            }

            setOnActionListener(new OnActionListener() {
                @Override
                public void onScanStarted(Context context) {
                }

                @Override
                public void onScanProgressUpdated(Context context, int current, int max) {
                }

                @Override
                public void onScanCompleted(Context context, List<AppsListItem> apps) {
                }

                @Override
                public void onCleanStarted(Context context) {
                }

                @Override
                public void onCleanCompleted(Context context, boolean succeeded) {
                    if (succeeded) {
                        Log.d(TAG, "Cache cleaned");
                    }
                    else {
                        Log.e(TAG, "Could not clean the cache");
                    }

                    Toast.makeText(CleanerService.this, succeeded ? R.string.cleaned :
                            R.string.toast_could_not_clean, Toast.LENGTH_LONG).show();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            stopSelf();
                        }
                    }, 5000);
                }
            });

            cleanCache();
        }

        return START_NOT_STICKY;
    }

    public void scanCache() {
        mIsScanning = true;

        new TaskScan().execute();
    }

    public void cleanCache() {
        mIsCleaning = true;

        new TaskClean().execute();
    }

    public void setOnActionListener(OnActionListener listener) {
        mOnActionListener = listener;
    }

    public boolean isScanning() {
        return mIsScanning;
    }

    public boolean isCleaning() {
        return mIsCleaning;
    }

    public long getCacheSize() {
        return mCacheSize;
    }

    private static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canCleanInternalCache(Context context) {
        return hasPermission(context, Manifest.permission.CLEAR_APP_CACHE);
    }

    public static boolean canCleanExternalCache(Context context) {
        return hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
}
