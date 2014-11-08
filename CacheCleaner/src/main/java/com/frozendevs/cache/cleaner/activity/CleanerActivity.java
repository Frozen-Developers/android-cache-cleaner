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
import android.support.v4.text.BidiFormatter;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.applications.LinearColorBar;
import com.frozendevs.cache.cleaner.R;
import com.frozendevs.cache.cleaner.helper.CacheManager;
import com.frozendevs.cache.cleaner.model.AppsListItem;
import com.frozendevs.cache.cleaner.model.adapter.AppsListAdapter;

import java.util.ArrayList;
import java.util.List;

public class CleanerActivity extends ActionBarActivity implements CacheManager.OnActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private LinearColorBar mColorBar;
    private TextView mSystemSizeText;
    private TextView mCacheSizeText;
    private TextView mFreeSizeText;
    private View mHeaderView;

    private AppsListAdapter mAppsListAdapter = null;
    private CacheManager mCacheManager = null;
    private TextView mEmptyView;
    private SharedPreferences mSharedPreferences;
    private SearchView mSearchView;
    private ProgressDialog mProgressDialog;
    private View mProgressBar;
    private TextView mProgressBarText;
    private ListView mListView;

    private boolean mAlreadyScanned = false;
    private boolean mAlreadyCleaned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cleaner_activity);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mEmptyView = (TextView) findViewById(android.R.id.empty);

        mAppsListAdapter = new AppsListAdapter(this);

        mHeaderView = LayoutInflater.from(this).inflate(
                R.layout.apps_list_header, null);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setAdapter(mAppsListAdapter);
        mListView.setOnItemClickListener(mAppsListAdapter);
        mListView.setEmptyView(mEmptyView);
        mListView.addHeaderView(mHeaderView, null, false);

        mColorBar = (LinearColorBar) findViewById(R.id.color_bar);
        mColorBar.setColors(getResources().getColor(R.color.apps_list_system_memory),
                getResources().getColor(R.color.apps_list_cache_memory),
                getResources().getColor(R.color.apps_list_free_memory));
        mSystemSizeText = (TextView) findViewById(R.id.systemSize);
        mCacheSizeText = (TextView) findViewById(R.id.cacheSize);
        mFreeSizeText = (TextView) findViewById(R.id.freeSize);

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
                        mListView.removeHeaderView(mHeaderView);

                        mEmptyView.setText(R.string.no_such_app);

                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        mListView.addHeaderView(mHeaderView, null, false);

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
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

        long totalMemory = (long) stat.getBlockCount() * (long) stat.getBlockSize();
        long medMemory = mCacheManager.getCacheSize();
        long lowMemory = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
        long highMemory = totalMemory - medMemory - lowMemory;

        BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        String sizeStr = bidiFormatter.unicodeWrap(
                Formatter.formatShortFileSize(this, lowMemory));
        mFreeSizeText.setText(getResources().getString(
                R.string.apps_list_header_memory, sizeStr));
        sizeStr = bidiFormatter.unicodeWrap(
                Formatter.formatShortFileSize(this, medMemory));
        mCacheSizeText.setText(getResources().getString(
                R.string.apps_list_header_memory, sizeStr));
        sizeStr = bidiFormatter.unicodeWrap(
                Formatter.formatShortFileSize(this, highMemory));
        mSystemSizeText.setText(getResources().getString(
                R.string.apps_list_header_memory, sizeStr));
        mColorBar.setRatios((float) highMemory / (float) totalMemory,
                (float) medMemory / (float) totalMemory,
                (float) lowMemory / (float) totalMemory);
    }

    private AppsListAdapter.SortBy getSortBy() {
        try {
            return AppsListAdapter.SortBy.valueOf(mSharedPreferences.getString(
                    getString(R.string.sort_by_key), AppsListAdapter.SortBy.CACHE_SIZE.toString()));
        } catch (ClassCastException e) {
            return AppsListAdapter.SortBy.CACHE_SIZE;
        }
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
    public void onScanStarted() {
        if (mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        mProgressBarText.setText(R.string.scanning);
        showProgressBar(true);
    }

    @Override
    public void onScanProgressUpdated(int current, int max) {
        mProgressBarText.setText(getString(R.string.scanning_m_of_n, current, max));
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

        Toast.makeText(this, getString(R.string.cleaned,
                Formatter.formatShortFileSize(this, cacheSize)), Toast.LENGTH_LONG).show();

        if (!mAlreadyCleaned) {
            if (mSharedPreferences.getBoolean(getString(R.string.exit_after_clean_key), false)) {
                finish();
            }
        }
    }
}
