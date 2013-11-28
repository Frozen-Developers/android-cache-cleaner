package com.frozendevs.cache.cleaner;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import com.frozendevs.cache.cleaner.adapter.AppsListAdapter;
import com.frozendevs.cache.cleaner.model.AppsListItem;
import com.frozendevs.cache.cleaner.model.CacheManager;
import com.frozendevs.cache.cleaner.view.LinearColorBar;

import java.util.ArrayList;
import java.util.List;

public class CleanerActivity extends Activity {

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
    private static List<AppsListItem> appsList = new ArrayList<AppsListItem>();
    private SearchView searchView;

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

        if(cacheManager == null)
            cacheManager = new CacheManager(this);
        cacheManager.setOnScanCompletedListener(new CacheManager.OnScanCompletedListener() {
            @Override
            public void onScanCompleted() {
                appsListAdapter.setItems(appsList = cacheManager.getAppsList());
                if(searchView != null) {
                    if(searchView.isShown()) {
                        appsListAdapter.filterAppsByName(searchView.getQuery().toString());
                        updateChart = false;
                    }
                }
                appsListAdapter.notifyDataSetChanged();

                if(!alreadyScanned) {
                    alreadyScanned = true;

                    if(sharedPreferences.getBoolean(getString(R.string.clean_on_startup_key), false)) {
                        alreadyCleaned = true;
                        cacheManager.cleanCache(getTotalCacheSize());
                    }
                }
            }
        });
        cacheManager.setOnCleanCompletedListener(new CacheManager.OnCleanCompletedListener() {
            @Override
            public void OnCleanCompleted() {
                appsListAdapter.setItems(appsList = new ArrayList<AppsListItem>());
                appsListAdapter.notifyDataSetChanged();

                if(!alreadyCleaned) {
                    if(sharedPreferences.getBoolean(getString(R.string.exit_after_clean_key), false)) {
                        finish();
                    }
                }
            }
        });

        if(!alreadyScanned) {
            cacheManager.scanCache();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = ((SearchView)searchItem.getActionView());
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                updateChart = false;
                reloadAdapterItems(newText);
                return true;
            }
        });
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                emptyView.setText(R.string.no_such_app);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                appsListAdapter.setItems(appsList);
                appsListAdapter.notifyDataSetChanged();
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
                alreadyCleaned = false;
                cacheManager.cleanCache(getTotalCacheSize());
                return true;

            case R.id.action_refresh:
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

        super.onDestroy();
    }

    @Override
    public void finish() {
        appsListAdapter = null;
        appsList = new ArrayList<AppsListItem>();
        alreadyScanned = false;
        alreadyCleaned = false;

        super.finish();
    }

    @Override
    protected void onStart() {
        super.onStart();

        cacheManager.onStart();
    }

    @Override
    protected void onStop() {
        cacheManager.onStop();

        super.onStop();
    }

    private void updateStorageUsage() {
        long freeStorage, appStorage, totalStorage;

        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

        totalStorage = (long)stat.getBlockCount() * (long)stat.getBlockSize();
        freeStorage = (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();

        appStorage = getTotalCacheSize();

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

    private long getTotalCacheSize() {
        long size = 0;

        for(AppsListItem app : appsList)
            size += app.getCacheSize();

        return size;
    }

    private void reloadAdapterItems(String filter) {
        appsListAdapter.setItems(appsList);
        appsListAdapter.filterAppsByName(filter);
        appsListAdapter.notifyDataSetChanged();
    }

    private void setSortBy(int sortBy) {
        sharedPreferencesEditor.putInt(getString(R.string.sort_by_key), sortBy);
        sharedPreferencesEditor.commit();
        reloadAdapterItems(searchView.getQuery().toString());
    }
}
