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

    public static interface OnActionListener {
        public void onScanStarted(int appsCount);

        public void onScanProgressUpdated(int current, int max);

        public void onScanCompleted(List<AppsListItem> apps);

        public void onCleanStarted();

        public void onCleanCompleted(long cacheSize);
    }

    private class TaskScan extends AsyncTask<Void, Integer, List<AppsListItem>> {

        private CountDownLatch countDownLatch;
        private List<ApplicationInfo> packages;
        private int appCount = 0;

        @Override
        protected void onPreExecute() {
            packages = mPackageManager.getInstalledApplications(PackageManager.GET_META_DATA);

            countDownLatch = new CountDownLatch(packages.size());

            if (mOnActionListener != null)
                mOnActionListener.onScanStarted(packages.size());
        }

        @Override
        protected List<AppsListItem> doInBackground(Void... params) {
            final List<AppsListItem> apps = new ArrayList<AppsListItem>();

            try {
                for (ApplicationInfo pkg : packages) {
                    mGetPackageSizeInfoMethod.invoke(mPackageManager, pkg.packageName,
                            new IPackageStatsObserver.Stub() {

                                @Override
                                public void onGetStatsCompleted(PackageStats pStats, boolean succeeded)
                                        throws RemoteException {
                                    publishProgress(++appCount);

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
                                        } catch (PackageManager.NameNotFoundException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    countDownLatch.countDown();
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
            if (mOnActionListener != null)
                mOnActionListener.onScanProgressUpdated(values[0], packages.size());
        }

        @Override
        protected void onPostExecute(List<AppsListItem> result) {
            if (mOnActionListener != null)
                mOnActionListener.onScanCompleted(result);

            mIsScanning = false;
        }
    }

    private class TaskClean extends AsyncTask<Long, Void, Long> {

        private CountDownLatch countDownLatch = new CountDownLatch(1);

        @Override
        protected void onPreExecute() {
            if (mOnActionListener != null)
                mOnActionListener.onCleanStarted();
        }

        @Override
        protected Long doInBackground(Long... params) {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

            try {
                mFreeStorageAndNotifyMethod.invoke(mPackageManager,
                        (2 * params[0]) + ((long) stat.getFreeBlocks() * (long) stat.getBlockSize()),
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

            return params[0];
        }

        @Override
        protected void onPostExecute(Long result) {
            if (mOnActionListener != null)
                mOnActionListener.onCleanCompleted(result);

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

    public void cleanCache(long cacheSize) {
        mIsCleaning = true;

        new TaskClean().execute(cacheSize);
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
}
