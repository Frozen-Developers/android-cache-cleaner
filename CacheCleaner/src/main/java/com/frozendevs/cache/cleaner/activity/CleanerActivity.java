package com.frozendevs.cache.cleaner.activity;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.frozendevs.cache.cleaner.R;

import java.util.List;

public class CleanerActivity extends AppCompatActivity {
    public final static int USAGE_ACCESS_SETTINGS = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cleaner_activity);
    }


    public static boolean checkUsageStatsEnabled(Context context) {
        // Usage Stats is theoretically available on API v19+, but official/reliable support starts with API v21.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }

        final AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        if (appOpsManager == null) {
            return false;
        }

        final int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
        if (mode != AppOpsManager.MODE_ALLOWED) {
            return false;
        }

        // Verify that access is possible. Some devices "lie" and return MODE_ALLOWED even when it's not.
        final long now = System.currentTimeMillis();
        final UsageStatsManager mUsageStatsManager;
        List<UsageStats> stats = null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (mUsageStatsManager != null) {
                stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now);
            }
        }

        return (stats != null && !stats.isEmpty());
    }
}
