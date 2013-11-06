package com.frozendevs.cache.cleaner;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewConfiguration;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import com.frozendevs.cache.cleaner.adapter.AppsListAdapter;
import com.frozendevs.cache.cleaner.model.AppsListItem;
import com.frozendevs.cache.cleaner.model.CacheManager;
import com.frozendevs.cache.cleaner.view.LinearColorBar;

import java.lang.reflect.Field;
import java.util.ArrayList;

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
    private boolean updateChart = true;

    private static boolean alreadyScanned = false;
    private static boolean alreadyCleaned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.cleaner_activity);

        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");

            if(menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) <
                Configuration.SCREENLAYOUT_SIZE_LARGE)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        colorBar = (LinearColorBar)findViewById(R.id.storage_color_bar);
        usedStorageText = (TextView)findViewById(R.id.usedStorageText);
        freeStorageText = (TextView)findViewById(R.id.freeStorageText);

        emptyView = (TextView)findViewById(android.R.id.empty);

        listView = (ListView)findViewById(android.R.id.list);
        listView.setEmptyView(findViewById(android.R.id.empty));
        if(appsListAdapter == null)
            appsListAdapter = new AppsListAdapter(this);
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
                appsListAdapter.setItems(cacheManager.getAppsList());
                appsListAdapter.notifyDataSetChanged();

                if(!alreadyScanned) {
                    alreadyScanned = true;

                    if(sharedPreferences.getBoolean(getString(R.string.clean_on_startup_key), false)) {
                        alreadyCleaned = true;
                        cacheManager.cleanCache(appsListAdapter.getTotalCacheSize());
                    }
                }
            }
        });
        cacheManager.setOnCleanCompletedListener(new CacheManager.OnCleanCompletedListener() {
            @Override
            public void OnCleanCompleted() {
                appsListAdapter.setItems(new ArrayList<AppsListItem>());
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

        MenuItem searchItem = menu.getItem(2);
        final SearchView searchView = ((SearchView)searchItem.getActionView());
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                appsListAdapter.setItems(cacheManager.getAppsList());
                appsListAdapter.setItems(appsListAdapter.getItemsFilteredByAppName(newText));
                updateChart = false;
                appsListAdapter.notifyDataSetChanged();
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
                appsListAdapter.setItems(cacheManager.getAppsList());
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
                cacheManager.cleanCache(appsListAdapter.getTotalCacheSize());
                return true;

            case R.id.action_refresh:
                cacheManager.scanCache();
                return true;

            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        appsListAdapter = null;
        cacheManager = null;
        alreadyScanned = false;
        alreadyCleaned = false;
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

}
