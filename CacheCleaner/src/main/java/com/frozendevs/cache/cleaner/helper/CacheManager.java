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
    private static List<AppsListItem> apps;
    private OnActionListener onActionListener = null;
    private static boolean isScanning = false;
    private static boolean isCleaning = false;

    public static interface OnActionListener {
        public void onScanStarted();
        public void onScanCompleted();

        public void onCleanStarted();
        public void onCleanCompleted(long cacheSize);
    }

    public CacheManager(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    private Method getPackageManagersMethod(String methodName) {
        for(Method method : packageManager.getClass().getMethods()) {
            if(method.getName().equals(methodName))
                return method;
        }

        return null;
    }

    private void invokePackageManagersMethod(String method, Object... args) {
        try {
            getPackageManagersMethod(method).invoke(packageManager, args);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void scanCache() {
        if(!isScanning) {
            isScanning = true;

            if(onActionListener != null)
                onActionListener.onScanStarted();

            apps = new ArrayList<AppsListItem>();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    final List<ApplicationInfo> packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

                    for (final ApplicationInfo pkg : packages) {
                        invokePackageManagersMethod("getPackageSizeInfo", pkg.packageName, new IPackageStatsObserver.Stub() {

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
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (onActionListener != null)
                                                onActionListener.onScanCompleted();

                                            isScanning = false;
                                        }
                                    });
                                }
                            }
                        });
                    }
                }
            }).start();
        }
    }

    public List<AppsListItem> getAppsList() {
        return new ArrayList<AppsListItem>(apps);
    }

    public void setOnActionListener(OnActionListener listener) {
        onActionListener = listener;
    }

    public void cleanCache(final long cacheSize) {
        if(cacheSize > 0 && !isCleaning) {
            isCleaning = true;

            if(onActionListener != null)
                onActionListener.onCleanStarted();

            apps = new ArrayList<AppsListItem>();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

                    invokePackageManagersMethod("freeStorageAndNotify",
                            (2 * cacheSize) + ((long) stat.getFreeBlocks() * (long) stat.getBlockSize()),
                            new IPackageDataObserver.Stub() {
                        @Override
                        public void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    if (onActionListener != null)
                                        onActionListener.onCleanCompleted(cacheSize);

                                    isCleaning = false;
                                }
                            });
                        }
                    });
                }
            }).start();
        }
    }

    public boolean isScanning() {
        return isScanning;
    }

    public boolean isCleaning() {
        return isCleaning;
    }
}
