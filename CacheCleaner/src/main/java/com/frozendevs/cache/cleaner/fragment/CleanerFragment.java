package com.frozendevs.cache.cleaner.fragment;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.text.BidiFormatter;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.settings.applications.LinearColorBar;
import com.frozendevs.cache.cleaner.R;
import com.frozendevs.cache.cleaner.activity.SettingsActivity;
import com.frozendevs.cache.cleaner.model.AppsListItem;
import com.frozendevs.cache.cleaner.model.CleanerService;
import com.frozendevs.cache.cleaner.model.adapter.AppsListAdapter;

import java.util.ArrayList;
import java.util.List;

public class CleanerFragment extends Fragment implements CleanerService.OnActionListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private LinearColorBar mColorBar;
    private TextView mSystemSizeText;
    private TextView mCacheSizeText;
    private TextView mFreeSizeText;
    private View mHeaderView;

    private CleanerService mCleanerService;
    private AppsListAdapter mAppsListAdapter;
    private TextView mEmptyView;
    private SharedPreferences mSharedPreferences;
    private SearchView mSearchView;
    private ProgressDialog mProgressDialog;
    private View mProgressBar;
    private TextView mProgressBarText;

    private boolean mAlreadyScanned = false;
    private boolean mAlreadyCleaned = false;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mCleanerService = ((CleanerService.CleanerServiceBinder) service).getService();
            mCleanerService.setOnActionListener(CleanerFragment.this);

            updateStorageUsage();

            if (!mCleanerService.isScanning() && !mAlreadyScanned) {
                mCleanerService.scanCache();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mCleanerService.setOnActionListener(null);
            mCleanerService = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        setRetainInstance(true);

        mAppsListAdapter = new AppsListAdapter(getActivity());

        getActivity().getApplication().bindService(new Intent(getActivity(), CleanerService.class),
                mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.cleaner_fragment, container, false);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        mEmptyView = (TextView) rootView.findViewById(android.R.id.empty);

        View headerLayout = inflater.inflate(R.layout.apps_list_header, null);
        mHeaderView = headerLayout.findViewById(R.id.apps_list_header);

        ListView listView = (ListView) rootView.findViewById(android.R.id.list);
        listView.addHeaderView(headerLayout, null, false);
        listView.setEmptyView(mEmptyView);
        listView.setAdapter(mAppsListAdapter);
        listView.setOnItemClickListener(mAppsListAdapter);

        mColorBar = (LinearColorBar) rootView.findViewById(R.id.color_bar);
        mColorBar.setColors(getResources().getColor(R.color.apps_list_system_memory),
                getResources().getColor(R.color.apps_list_cache_memory),
                getResources().getColor(R.color.apps_list_free_memory));
        mSystemSizeText = (TextView) rootView.findViewById(R.id.systemSize);
        mCacheSizeText = (TextView) rootView.findViewById(R.id.cacheSize);
        mFreeSizeText = (TextView) rootView.findViewById(R.id.freeSize);

        mProgressBar = rootView.findViewById(R.id.progressBar);
        mProgressBarText = (TextView) rootView.findViewById(R.id.progressBarText);

        mProgressDialog = new ProgressDialog(getActivity());
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

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main_menu, menu);

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
                        mHeaderView.setVisibility(View.GONE);

                        mEmptyView.setText(R.string.no_such_app);

                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        mHeaderView.setVisibility(View.VISIBLE);

                        mAppsListAdapter.clearFilter();

                        mEmptyView.setText(R.string.empty_cache);

                        return true;
                    }
                });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clean:
                if (mCleanerService != null && !mCleanerService.isScanning() &&
                        !mCleanerService.isCleaning() && mCleanerService.getCacheSize() > 0) {
                    mAlreadyCleaned = false;

                    mCleanerService.cleanCache();
                }
                return true;

            case R.id.action_refresh:
                if (mCleanerService != null && !mCleanerService.isScanning() &&
                        !mCleanerService.isCleaning()) {
                    mCleanerService.scanCache();
                }
                return true;

            case R.id.action_settings:
                startActivity(new Intent(getActivity(), SettingsActivity.class));
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
    public void onStart() {
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);

        super.onStart();
    }

    @Override
    public void onStop() {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);

        super.onStop();
    }

    @Override
    public void onResume() {
        updateStorageUsage();

        if (mCleanerService != null) {
            if (mCleanerService.isScanning() && !isProgressBarVisible()) {
                showProgressBar(true);
            } else if (!mCleanerService.isScanning() && isProgressBarVisible()) {
                showProgressBar(false);
            }

            if (mCleanerService.isCleaning() && !mProgressDialog.isShowing()) {
                mProgressDialog.show();
            }
        }

        super.onResume();
    }

    @Override
    public void onPause() {
        if (mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {
        getActivity().getApplication().unbindService(mServiceConnection);

        super.onDestroy();
    }

    private void updateStorageUsage() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());

        long totalMemory = (long) stat.getBlockCount() * (long) stat.getBlockSize();
        long medMemory = mCleanerService != null ? mCleanerService.getCacheSize() : 0;
        long lowMemory = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
        long highMemory = totalMemory - medMemory - lowMemory;

        BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        String sizeStr = bidiFormatter.unicodeWrap(
                Formatter.formatShortFileSize(getActivity(), lowMemory));
        mFreeSizeText.setText(getResources().getString(
                R.string.apps_list_header_memory, sizeStr));
        sizeStr = bidiFormatter.unicodeWrap(
                Formatter.formatShortFileSize(getActivity(), medMemory));
        mCacheSizeText.setText(getResources().getString(
                R.string.apps_list_header_memory, sizeStr));
        sizeStr = bidiFormatter.unicodeWrap(
                Formatter.formatShortFileSize(getActivity(), highMemory));
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
            mProgressBar.startAnimation(AnimationUtils.loadAnimation(
                    getActivity(), android.R.anim.fade_out));
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
        if (isAdded()) {
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            mProgressBarText.setText(R.string.scanning);
            showProgressBar(true);
        }
    }

    @Override
    public void onScanProgressUpdated(int current, int max) {
        if (isAdded()) {
            mProgressBarText.setText(getString(R.string.scanning_m_of_n, current, max));
        }
    }

    @Override
    public void onScanCompleted(List<AppsListItem> apps) {
        if (isAdded()) {
            mAppsListAdapter.setItems(apps, getSortBy());

            updateStorageUsage();

            if (mSearchView != null && mSearchView.isShown()) {
                mAppsListAdapter.filterAppsByName(mSearchView.getQuery().toString());
            }

            showProgressBar(false);

            if (!mAlreadyScanned) {
                mAlreadyScanned = true;

                if (mCleanerService != null && mSharedPreferences.getBoolean(
                        getString(R.string.clean_on_app_startup_key), false)) {
                    mAlreadyCleaned = true;

                    mCleanerService.cleanCache();
                }
            }
        }
    }

    @Override
    public void onCleanStarted() {
        if (isAdded()) {
            if (isProgressBarVisible()) {
                showProgressBar(false);
            }

            if (!getActivity().isFinishing()) {
                mProgressDialog.show();
            }
        }
    }

    @Override
    public void onCleanCompleted(long cacheSize) {
        if (isAdded()) {
            mAppsListAdapter.setItems(new ArrayList<AppsListItem>(), getSortBy());

            updateStorageUsage();

            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            Toast.makeText(getActivity(), getString(R.string.cleaned, Formatter.formatShortFileSize(
                    getActivity(), cacheSize)), Toast.LENGTH_LONG).show();

            if (!mAlreadyCleaned) {
                if (mSharedPreferences.getBoolean(getString(R.string.exit_after_clean_key), false)) {
                    getActivity().finish();
                }
            }
        }
    }
}
