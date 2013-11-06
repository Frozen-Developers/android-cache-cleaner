package com.frozendevs.cache.cleaner.model;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.os.Environment;
import android.os.RemoteException;
import android.os.StatFs;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.widget.Toast;

import com.frozendevs.cache.cleaner.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CacheManager {

    private PackageManager packageManager;
    private Activity activity;
    private List<ApplicationInfo> packages;
    private List<AppsListItem> apps;
    private OnScanCompletedListener onScanCompletedListener = null;
    private OnCleanCompletedListener onCleanCompletedListener = null;
    private ProgressDialog progressDialog;
    private int currentPackage;
    private boolean scanningStopped = false;

    public static abstract class OnScanCompletedListener {
        public abstract void onScanCompleted();
    }

    public static abstract class OnCleanCompletedListener {
        public abstract void OnCleanCompleted();
    }

    public CacheManager(Activity activity) {
        this.activity = activity;
        packageManager = activity.getPackageManager();

        progressDialog = new ProgressDialog(activity);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCanceledOnTouchOutside(false);
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
        scanningStopped = false;

        packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        apps = new ArrayList<AppsListItem>();

        progressDialog.setTitle(R.string.scanning_cache);
        progressDialog.setMessage(activity.getString(R.string.scanning) + " 0/" + packages.size());
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                stopScanning();
            }
        });
        progressDialog.show();

        currentPackage = 0;

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (final ApplicationInfo pkg : packages) {
                    invokePackageManagersMethod("getPackageSizeInfo", pkg.packageName, new IPackageStatsObserver.Stub() {

                        @Override
                        public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) throws RemoteException {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressDialog.setMessage(activity.getString(R.string.scanning) +
                                            " " + currentPackage++ + "/" + packages.size());
                                }
                            });

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
                                if (onScanCompletedListener != null && !scanningStopped) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            onScanCompletedListener.onScanCompleted();
                                        }
                                    });
                                }

                                if (!scanningStopped) {
                                    progressDialog.dismiss();
                                }
                            }
                        }
                    });
                }
            }
        }).start();
    }

    public void stopScanning() {
        scanningStopped = true;

        if (progressDialog.isShowing())
            progressDialog.dismiss();
    }

    public List<AppsListItem> getAppsList() {
        return new ArrayList<AppsListItem>(apps);
    }

    public void setOnScanCompletedListener(OnScanCompletedListener listener) {
        if (listener == null) throw new IllegalArgumentException();

        onScanCompletedListener = listener;
    }

    public void setOnCleanCompletedListener(OnCleanCompletedListener listener) {
        if (listener == null) throw new IllegalArgumentException();

        onCleanCompletedListener = listener;
    }

    public void cleanCache(final long cacheSize) {
        if(cacheSize == 0)
            return;

        apps = new ArrayList<AppsListItem>();

        progressDialog.setTitle(R.string.cleaning_cache);
        progressDialog.setMessage(activity.getString(R.string.cleaning_in_progress));
        progressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                return true;
            }
        });
        progressDialog.show();

        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

        invokePackageManagersMethod("freeStorageAndNotify", (cacheSize + ((long) stat.getAvailableBlocks() *
                (long) stat.getBlockSize())) * 2, new IPackageDataObserver.Stub() {
            @Override
            public void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (onCleanCompletedListener != null)
                            onCleanCompletedListener.OnCleanCompleted();

                        progressDialog.dismiss();

                        Toast.makeText(activity, activity.getString(R.string.cleaned) + " (" +
                                Formatter.formatShortFileSize(activity, cacheSize) + ")",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
