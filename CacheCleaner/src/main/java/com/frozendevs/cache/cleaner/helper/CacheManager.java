package com.frozendevs.cache.cleaner.helper;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
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

    public static interface OnActionListener {
        public void onScanStarted();
        public void onScanCompleted(List<AppsListItem> apps);

        public void onCleanStarted();
        public void onCleanCompleted(long cacheSize);
    }

    public CacheManager(PackageManager packageManager) {
        this.packageManager = packageManager;
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

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (onActionListener != null)
                    onActionListener.onScanStarted();

                final List<AppsListItem> apps = new ArrayList<AppsListItem>();
                final List<ApplicationInfo> packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

                for (final ApplicationInfo pkg : packages) {
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
            }
        });
    }

    public void cleanCache(final long cacheSize) {
        isCleaning = true;

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (onActionListener != null)
                    onActionListener.onCleanStarted();

                StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

                invokeMethod("freeStorageAndNotify",
                        (2 * cacheSize) + ((long) stat.getFreeBlocks() * (long) stat.getBlockSize()),
                        new IPackageDataObserver.Stub() {
                            @Override
                            public void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException {
                                isCleaning = false;
                                
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (onActionListener != null)
                                            onActionListener.onCleanCompleted(cacheSize);
                                    }
                                });
                            }
                        });
            }
        });
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
