package com.frozendevs.cache.cleaner.helper;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.StatFs;

import com.frozendevs.cache.cleaner.model.AppsListItem;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CacheManager {

    private PackageManager packageManager;
    private OnActionListener onActionListener = null;
    private static boolean isScanning = false;
    private static boolean isCleaning = false;
    private TaskScan taskScan;
    private TaskClean taskClean;

    public static interface OnActionListener {
        public void onScanStarted();
        public void onScanCompleted(List<AppsListItem> apps);

        public void onCleanStarted();
        public void onCleanCompleted(long cacheSize);
    }

    private class TaskScan extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute () {
            if (onActionListener != null)
                onActionListener.onScanStarted();
        }

        @Override
        protected Void doInBackground(Void... params) {
            final List<AppsListItem> apps = new ArrayList<AppsListItem>();
            final List<ApplicationInfo> packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo pkg : packages) {
                invokeMethod("getPackageSizeInfo", pkg.packageName, new IPackageStatsObserver.Stub() {

                    @Override
                    public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) throws RemoteException {
                        if (succeeded) {
                            try {
                                if (pStats.cacheSize > 0)
                                    apps.add(new AppsListItem(pStats.packageName, packageManager.getApplicationLabel(
                                            packageManager.getApplicationInfo(pStats.packageName, PackageManager.GET_META_DATA)).toString(),
                                            packageManager.getApplicationIcon(pStats.packageName), pStats.cacheSize));
                            } catch (PackageManager.NameNotFoundException e) {
                                e.printStackTrace();
                            }
                        }

                        if (pStats.packageName.equals(packages.get(packages.size() - 1).packageName)) {
                            taskScan = new TaskScan();

                            isScanning = false;

                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    if (onActionListener != null)
                                        onActionListener.onScanCompleted(apps);
                                }
                            });
                        }
                    }
                });
            }

            return null;
        }
    }

    private class TaskClean extends AsyncTask<Long, Void, Void> {

        @Override
        protected void onPreExecute () {
            if (onActionListener != null)
                onActionListener.onCleanStarted();
        }

        @Override
        protected Void doInBackground(final Long... params) {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

            invokeMethod("freeStorageAndNotify",
                    (2 * params[0]) + ((long) stat.getFreeBlocks() * (long) stat.getBlockSize()),
                    new IPackageDataObserver.Stub() {
                        @Override
                        public void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException {
                            taskClean = new TaskClean();

                            isCleaning = false;

                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    if (onActionListener != null)
                                        onActionListener.onCleanCompleted(params[0]);
                                }
                            });
                        }
                    });

            return null;
        }
    }

    public CacheManager(PackageManager packageManager) {
        this.packageManager = packageManager;

        taskScan = new TaskScan();
        taskClean = new TaskClean();
    }

    private Method getMethod(String methodName) {
        for(Method method : packageManager.getClass().getMethods()) {
            if(method.getName().equals(methodName))
                return method;
        }

        return null;
    }

    private void invokeMethod(String method, Object... args) {
        try {
            getMethod(method).invoke(packageManager, args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void scanCache() {
        isScanning = true;

        taskScan.execute();
    }

    public void cleanCache(long cacheSize) {
        isCleaning = true;

        taskClean.execute(cacheSize);
    }

    public void setOnActionListener(OnActionListener listener) {
        onActionListener = listener;
    }

    public boolean isScanning() {
        return isScanning;
    }

    public boolean isCleaning() {
        return isCleaning;
    }
}
