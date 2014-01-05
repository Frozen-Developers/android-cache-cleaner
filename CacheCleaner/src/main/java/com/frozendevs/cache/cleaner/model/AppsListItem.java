package com.frozendevs.cache.cleaner.model;

import android.graphics.drawable.Drawable;

public class AppsListItem {

    private long cacheSize;
    private String packageName, applicationName;
    private Drawable icon;

    public AppsListItem(String packageName, String applicationName, Drawable icon, long cacheSize) {
        this.cacheSize = cacheSize;
        this.packageName = packageName;
        this.applicationName = applicationName;
        this.icon = icon;
    }

    public Drawable getApplicationIcon() {
        return icon;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public long getCacheSize() {
        return cacheSize;
    }

    public String getPackageName() {
        return packageName;
    }
}
