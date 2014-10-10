package com.frozendevs.cache.cleaner.activity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.applications.LinearColorBar;
import com.frozendevs.cache.cleaner.R;
import com.frozendevs.cache.cleaner.model.adapter.AppsListAdapter;
import com.frozendevs.cache.cleaner.model.AppsListItem;
import com.frozendevs.cache.cleaner.helper.CacheManager;

import java.util.ArrayList;
import java.util.List;

public class CleanerActivity extends ActionBarActivity implements CacheManager.OnActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private LinearColorBar mColorBar;
    private TextView mUsedStorageText;
    private TextView mFreeStorageText;
    private long mLastUsedStorage, mLastFreeStorage;

    private AppsListAdapter mAppsListAdapter = null;
    private CacheManager mCacheManager = null;
    private TextView mEmptyView;
    private SharedPreferences mSharedPreferences;
    private SearchView mSearchView;
    private ProgressDialog mProgressDialog;
    private View mProgressBar;
    private TextView mProgressBarText;

    private boolean mAlreadyScanned = false;
    private boolean mAlreadyCleaned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cleaner_activity);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mColorBar = (LinearColorBar) findViewById(R.id.storage_color_bar);
        mUsedStorageText = (TextView) findViewById(R.id.usedStorageText);
        mFreeStorageText = (TextView) findViewById(R.id.freeStorageText);

        mEmptyView = (TextView) findViewById(android.R.id.empty);

        mAppsListAdapter = new AppsListAdapter(this);

        ListView listView = (ListView) findViewById(android.R.id.list);
        listView.setAdapter(mAppsListAdapter);
        listView.setOnItemClickListener(mAppsListAdapter);
        listView.setEmptyView(mEmptyView);

        mProgressBar = findViewById(R.id.progressBar);
        mProgressBarText = (TextView) findViewById(R.id.progressBarText);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.setTitle(R.string.cleaning_cache);
        mProgressDialog.setMessage(getString(R.string.cleaning_in_progress));
        mProgressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                return true;
            }
        });

        mCacheManager = new CacheManager(getPackageManager());
        mCacheManager.setOnActionListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);

        mSearchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                mSearchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                mAppsListAdapter.filterAppsByName(newText);

                return true;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchItem,
                new MenuItemCompat.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        mEmptyView.setText(R.string.no_such_app);

                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        mAppsListAdapter.clearFilter();

                        mEmptyView.setText(R.string.empty_cache);

                        return true;
                    }
                });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clean:
                if (!mCacheManager.isScanning() && !mCacheManager.isCleaning() &&
                        mCacheManager.getCacheSize() > 0) {
                    mAlreadyCleaned = false;

                    mCacheManager.cleanCache();
                }
                return true;

            case R.id.action_refresh:
                if (!mCacheManager.isScanning() && !mCacheManager.isCleaning()) {
                    mCacheManager.scanCache();
                }
                return true;

            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.action_sort_by_app_name:
                setSortBy(AppsListAdapter.SortBy.APP_NAME);
                return true;

            case R.id.action_sort_by_cache_size:
                setSortBy(AppsListAdapter.SortBy.CACHE_SIZE);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);

        super.onStart();
    }

    @Override
    protected void onStop() {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

        super.onStop();
    }

    @Override
    protected void onResume() {
        updateStorageUsage();

        if (mCacheManager.isScanning() && !isProgressBarVisible()) {
            showProgressBar(true);
        } else if (!mCacheManager.isScanning() && isProgressBarVisible()) {
            showProgressBar(false);
        }

        if (mCacheManager.isCleaning() && !mProgressDialog.isShowing()) {
            mProgressDialog.show();
        }

        if (!mCacheManager.isScanning() && !mAlreadyScanned) {
            mCacheManager.scanCache();
        }

        super.onResume();
    }

    @Override
    protected void onPause() {
        if (mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        super.onPause();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void updateStorageUsage() {
        long freeStorage, appStorage, totalStorage;

        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

        totalStorage = (long) stat.getBlockCount() * (long) stat.getBlockSize();
        freeStorage = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();

        appStorage = mCacheManager.getCacheSize();

        if (totalStorage > 0) {
            mColorBar.setRatios((totalStorage - freeStorage - appStorage) / (float) totalStorage,
                    appStorage / (float) totalStorage, freeStorage / (float) totalStorage);
            long usedStorage = totalStorage - freeStorage;
            if (mLastUsedStorage != usedStorage) {
                mLastUsedStorage = usedStorage;
                String sizeStr = Formatter.formatShortFileSize(this, usedStorage);
                mUsedStorageText.setText(getString(R.string.service_foreground_processes, sizeStr));
            }
            if (mLastFreeStorage != freeStorage) {
                mLastFreeStorage = freeStorage;
                String sizeStr = Formatter.formatShortFileSize(this, freeStorage);
                mFreeStorageText.setText(getString(R.string.service_background_processes, sizeStr));
            }
        } else {
            mColorBar.setRatios(0, 0, 0);
            if (mLastUsedStorage != -1) {
                mLastUsedStorage = -1;
                mUsedStorageText.setText("");
            }
            if (mLastFreeStorage != -1) {
                mLastFreeStorage = -1;
                mFreeStorageText.setText("");
            }
        }
    }

    private AppsListAdapter.SortBy getSortBy() {
        return AppsListAdapter.SortBy.valueOf(mSharedPreferences.getString(
                getString(R.string.sort_by_key), AppsListAdapter.SortBy.CACHE_SIZE.toString()));
    }

    private void setSortBy(AppsListAdapter.SortBy sortBy) {
        mSharedPreferences.edit().putString(getString(R.string.sort_by_key), sortBy.toString()).apply();

        mAppsListAdapter.filterAppsByName(mSearchView.getQuery().toString());
    }

    private boolean isProgressBarVisible() {
        return mProgressBar.getVisibility() == View.VISIBLE;
    }

    private void showProgressBar(boolean show) {
        if (show) {
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
            mProgressBar.setVisibility(View.GONE);
        }
    }

    private void setProgressBarProgress(int current, int max) {
        mProgressBarText.setText(getString(R.string.scanning) + " " + current + "/" + max);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.sort_by_key))) {
            mAppsListAdapter.sort(getSortBy());

            if (mSearchView.isShown()) {
                mAppsListAdapter.filterAppsByName(mSearchView.getQuery().toString());
            }
        }
    }

    @Override
    public void onScanStarted(int appsCount) {
        if (mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        setProgressBarProgress(0, appsCount);
        showProgressBar(true);
    }

    @Override
    public void onScanProgressUpdated(int current, int max) {
        setProgressBarProgress(current, max);
    }

    @Override
    public void onScanCompleted(List<AppsListItem> apps) {
        mAppsListAdapter.setItems(apps, getSortBy());

        updateStorageUsage();

        if (mSearchView != null && mSearchView.isShown()) {
            mAppsListAdapter.filterAppsByName(mSearchView.getQuery().toString());
        }

        showProgressBar(false);

        if (!mAlreadyScanned) {
            mAlreadyScanned = true;

            if (mSharedPreferences.getBoolean(getString(R.string.clean_on_app_startup_key), false)) {
                mAlreadyCleaned = true;

                mCacheManager.cleanCache();
            }
        }
    }

    @Override
    public void onCleanStarted() {
        if (isProgressBarVisible()) {
            showProgressBar(false);
        }

        if (!isFinishing()) {
            mProgressDialog.show();
        }
    }

    @Override
    public void onCleanCompleted(long cacheSize) {
        mAppsListAdapter.setItems(new ArrayList<AppsListItem>(), getSortBy());

        updateStorageUsage();

        if (mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        Toast.makeText(this, getString(R.string.cleaned) + " (" +
                Formatter.formatShortFileSize(this, cacheSize) + ")", Toast.LENGTH_LONG).show();

        if (!mAlreadyCleaned) {
            if (mSharedPreferences.getBoolean(getString(R.string.exit_after_clean_key), false)) {
                finish();
            }
        }
    }
}
