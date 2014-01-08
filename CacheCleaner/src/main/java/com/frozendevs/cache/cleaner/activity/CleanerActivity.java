package com.frozendevs.cache.cleaner.activity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
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

public class CleanerActivity extends ActionBarActivity implements SharedPreferences.OnSharedPreferenceChangeListener, CacheManager.OnActionListener {

    private LinearColorBar colorBar;
    private TextView usedStorageText;
    private TextView freeStorageText;
    private long lastUsedStorage, lastFreeStorage;
    private static AppsListAdapter appsListAdapter = null;
    private static CacheManager cacheManager = null;
    private ListView listView;
    private TextView emptyView;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor sharedPreferencesEditor;
    private boolean updateChart = true;
    private SearchView searchView;
    private ProgressDialog progressDialog;

    private static boolean alreadyScanned = false;
    private static boolean alreadyCleaned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cleaner_activity);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferencesEditor = sharedPreferences.edit();

        colorBar = (LinearColorBar)findViewById(R.id.storage_color_bar);
        usedStorageText = (TextView)findViewById(R.id.usedStorageText);
        freeStorageText = (TextView)findViewById(R.id.freeStorageText);

        emptyView = (TextView)findViewById(android.R.id.empty);

        listView = (ListView)findViewById(android.R.id.list);
        listView.setEmptyView(emptyView);
        if(appsListAdapter == null)
            appsListAdapter = new AppsListAdapter(this, sharedPreferences);
        listView.setAdapter(appsListAdapter);
        appsListAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                if(updateChart)
                    updateStorageUsage();
                updateChart = true;

                listView.invalidateViews();
                emptyView.invalidate();
            }
        });

        updateStorageUsage();

        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setTitle(R.string.cleaning_cache);
        progressDialog.setMessage(getString(R.string.cleaning_in_progress));
        progressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                return true;
            }
        });

        if(cacheManager == null)
            cacheManager = new CacheManager(getPackageManager());
        cacheManager.setOnActionListener(this);

        if(!alreadyScanned) {
            cacheManager.scanCache();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = ((SearchView)MenuItemCompat.getActionView(searchItem));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                updateChart = false;
                appsListAdapter.filterAppsByName(newText);
                return true;
            }
        });
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                emptyView.setText(R.string.no_such_app);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                appsListAdapter.clearFilter();
                emptyView.setText(R.string.empty_cache);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clean:
                if(!cacheManager.isScanning() && !cacheManager.isCleaning() &&
                        appsListAdapter.getTotalCacheSize() > 0) {
                    alreadyCleaned = false;
                    cacheManager.cleanCache(appsListAdapter.getTotalCacheSize());
                }
                return true;

            case R.id.action_refresh:
                if(!cacheManager.isScanning() && !cacheManager.isCleaning())
                    cacheManager.scanCache();
                return true;

            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.action_sort_by_app_name:
                setSortBy(AppsListAdapter.SORT_BY_APP_NAME);
                return true;

            case R.id.action_sort_by_cache_size:
                setSortBy(AppsListAdapter.SORT_BY_CACHE_SIZE);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        cacheManager = null;
        if(appsListAdapter != null)
            appsListAdapter.clearFilter();

        super.onDestroy();
    }

    @Override
    public void finish() {
        appsListAdapter = null;
        alreadyScanned = false;
        alreadyCleaned = false;

        super.finish();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(sharedPreferences != null)
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);

        if(cacheManager != null) {
            if(cacheManager.isScanning() && !isProgressBarShowing())
                showProgressBar(true);
            else if(!cacheManager.isScanning() && isProgressBarShowing())
                showProgressBar(false);

            if(cacheManager.isCleaning() && !progressDialog.isShowing())
                progressDialog.show();
        }
    }

    @Override
    protected void onStop() {
        if(progressDialog.isShowing())
            progressDialog.dismiss();
        if(sharedPreferences != null)
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

        super.onStop();
    }

    private void updateStorageUsage() {
        long freeStorage, appStorage, totalStorage;

        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

        totalStorage = (long)stat.getBlockCount() * (long)stat.getBlockSize();
        freeStorage = (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();

        appStorage = appsListAdapter.getTotalCacheSize();

        if (totalStorage > 0) {
            colorBar.setRatios((totalStorage - freeStorage - appStorage) / (float)totalStorage,
                    appStorage / (float)totalStorage, freeStorage / (float) totalStorage);
            long usedStorage = totalStorage - freeStorage;
            if (lastUsedStorage != usedStorage) {
                lastUsedStorage = usedStorage;
                String sizeStr = Formatter.formatShortFileSize(this, usedStorage);
                usedStorageText.setText(getString(R.string.service_foreground_processes, sizeStr));
            }
            if (lastFreeStorage != freeStorage) {
                lastFreeStorage = freeStorage;
                String sizeStr = Formatter.formatShortFileSize(this, freeStorage);
                freeStorageText.setText(getString(R.string.service_background_processes, sizeStr));
            }
        } else {
            colorBar.setRatios(0, 0, 0);
            if (lastUsedStorage != -1) {
                lastUsedStorage = -1;
                usedStorageText.setText("");
            }
            if (lastFreeStorage != -1) {
                lastFreeStorage = -1;
                freeStorageText.setText("");
            }
        }
    }

    private void setSortBy(int sortBy) {
        sharedPreferencesEditor.putInt(getString(R.string.sort_by_key), sortBy);
        sharedPreferencesEditor.commit();
        appsListAdapter.filterAppsByName(searchView.getQuery().toString());
    }

    private boolean isProgressBarShowing() {
        View progressBar = findViewById(R.id.progressBar);

        if(progressBar != null)
            return progressBar.getVisibility() == View.VISIBLE;

        return false;
    }

    private void showProgressBar(boolean show) {
        View progressBar = findViewById(R.id.progressBar);

        if(progressBar != null) {
            if(show) {
                progressBar.setVisibility(View.VISIBLE);
            }
            else {
                progressBar.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                progressBar.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(getString(R.string.sort_by_key))) {
            if(appsListAdapter != null) {
                appsListAdapter.sort();
                if(searchView != null) {
                    if(searchView.isShown())
                        appsListAdapter.filterAppsByName(searchView.getQuery().toString());
                }
                appsListAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onScanStarted() {
        if(progressDialog != null)
            progressDialog.dismiss();
        
        showProgressBar(true);
    }

    @Override
    public void onScanCompleted(List<AppsListItem> apps) {
        if(appsListAdapter != null) {
            appsListAdapter.setItems(apps);
            if(searchView != null) {
                if(searchView.isShown()) {
                    appsListAdapter.filterAppsByName(searchView.getQuery().toString());
                    updateChart = false;
                }
            }
            appsListAdapter.notifyDataSetChanged();

            showProgressBar(false);

            if(!alreadyScanned) {
                alreadyScanned = true;

                if(sharedPreferences.getBoolean(getString(R.string.clean_on_app_startup_key), false)) {
                    alreadyCleaned = true;
                    cacheManager.cleanCache(appsListAdapter.getTotalCacheSize());
                }
            }
        }
    }

    @Override
    public void onCleanStarted() {
        showProgressBar(false);

        if(progressDialog != null)
            progressDialog.show();
    }

    @Override
    public void onCleanCompleted(long cacheSize) {
        if(appsListAdapter != null) {
            appsListAdapter.setItems(new ArrayList<AppsListItem>());
            appsListAdapter.notifyDataSetChanged();
        }

        if(progressDialog != null)
            progressDialog.dismiss();

        Toast.makeText(this, getString(R.string.cleaned) + " (" +
                Formatter.formatShortFileSize(this, cacheSize) + ")", Toast.LENGTH_LONG).show();

        if(!alreadyCleaned) {
            if(sharedPreferences.getBoolean(getString(R.string.exit_after_clean_key), false)) {
                finish();
            }
        }
    }
}
