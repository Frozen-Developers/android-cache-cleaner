package com.frozendevs.cache.cleaner.helper;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.RemoteException;
import android.os.StatFs;

import com.frozendevs.cache.cleaner.model.AppsListItem;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CacheManager {

    private PackageManager mPackageManager;
    private Method mGetPackageSizeInfoMethod, mFreeStorageAndNotifyMethod;
    private OnActionListener mOnActionListener = null;
    private boolean mIsScanning = false;
    private boolean mIsCleaning = false;
    private long mCacheSize = 0;

    public static interface OnActionListener {
        public void onScanStarted(int appsCount);

        public void onScanProgressUpdated(int current, int max);

        public void onScanCompleted(List<AppsListItem> apps);

        public void onCleanStarted();

        public void onCleanCompleted(long cacheSize);
    }

    private class TaskScan extends AsyncTask<Void, Integer, List<AppsListItem>> {

        private List<ApplicationInfo> mPackages;
        private int mAppCount = 0;

        @Override
        protected void onPreExecute() {
            mPackages = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);

            if (mOnActionListener != null) {
                mOnActionListener.onScanStarted(mPackages.size());
            }
        }

        @Override
        protected List<AppsListItem> doInBackground(Void... params) {
            mCacheSize = 0;

            final CountDownLatch countDownLatch = new CountDownLatch(mPackages.size());

            final List<AppsListItem> apps = new ArrayList<AppsListItem>();

            try {
                for (ApplicationInfo pkg : mPackages) {
                    mGetPackageSizeInfoMethod.invoke(mPackageManager, pkg.packageName,
                            new IPackageStatsObserver.Stub() {

                                @Override
                                public void onGetStatsCompleted(PackageStats pStats, boolean succeeded)
                                        throws RemoteException {
                                    synchronized (apps) {
                                        publishProgress(++mAppCount);

                                        if (succeeded && pStats.cacheSize > 0) {
                                            try {
                                                apps.add(new AppsListItem(pStats.packageName,
                                                        mPackageManager.getApplicationLabel(
                                                                mPackageManager.getApplicationInfo(pStats.packageName,
                                                                        PackageManager.GET_META_DATA)
                                                        ).toString(),
                                                        mPackageManager.getApplicationIcon(pStats.packageName),
                                                        pStats.cacheSize
                                                ));

                                                mCacheSize += pStats.cacheSize;
                                            } catch (PackageManager.NameNotFoundException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }

                                    synchronized (countDownLatch) {
                                        countDownLatch.countDown();
                                    }
                                }
                            }
                    );
                }

                countDownLatch.await();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            return apps;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (mOnActionListener != null) {
                mOnActionListener.onScanProgressUpdated(values[0], mPackages.size());
            }
        }

        @Override
        protected void onPostExecute(List<AppsListItem> result) {
            if (mOnActionListener != null) {
                mOnActionListener.onScanCompleted(result);
            }

            mIsScanning = false;
        }
    }

    private class TaskClean extends AsyncTask<Void, Void, Long> {

        @Override
        protected void onPreExecute() {
            if (mOnActionListener != null) {
                mOnActionListener.onCleanStarted();
            }
        }

        @Override
        protected Long doInBackground(Void... params) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);

            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

            try {
                mFreeStorageAndNotifyMethod.invoke(mPackageManager,
                        (long) stat.getBlockCount() * (long) stat.getBlockSize(),
                        new IPackageDataObserver.Stub() {
                            @Override
                            public void onRemoveCompleted(String packageName, boolean succeeded)
                                    throws RemoteException {
                                countDownLatch.countDown();
                            }
                        }
                );

                countDownLatch.await();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            return mCacheSize;
        }

        @Override
        protected void onPostExecute(Long result) {
            mCacheSize = 0;

            if (mOnActionListener != null) {
                mOnActionListener.onCleanCompleted(result);
            }

            mIsCleaning = false;
        }
    }

    public CacheManager(PackageManager packageManager) {
        mPackageManager = packageManager;

        try {
            mGetPackageSizeInfoMethod = mPackageManager.getClass().getMethod("getPackageSizeInfo",
                    String.class, IPackageStatsObserver.class);

            mFreeStorageAndNotifyMethod = mPackageManager.getClass().getMethod("freeStorageAndNotify",
                    long.class, IPackageDataObserver.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
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
}
