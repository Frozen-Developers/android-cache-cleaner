package com.frozendevs.cache.cleaner.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.frozendevs.cache.cleaner.R;

public class SettingsActivity extends PreferenceActivity {

    private final static String ACTION_SETTINGS_ABOUT = "com.frozendevs.cache.cleaner.SETTINGS_ABOUT";

    private Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent().getAction();

        if (action != null && action.equals(ACTION_SETTINGS_ABOUT)) {
            addPreferencesFromResource(R.xml.settings_about);

            String versionName;
            try {
                versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                versionName = getString(R.string.version_number_unknown);
            }
            findPreference("version").setSummary(versionName);

            findPreference("licences").setOnPreferenceClickListener(
                    new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            WebView webView = new WebView(getApplicationContext());
                            webView.loadUrl("file:///android_asset/html/licenses.html");

                            AlertDialog dialog = new AlertDialog.Builder(SettingsActivity.this).create();
                            dialog.setTitle(R.string.open_source_licences);
                            dialog.setView(webView);
                            dialog.show();

                            return true;
                        }
                    });
        } else {
            addPreferencesFromResource(R.xml.settings);
        }

        mToolbar.setTitle(getTitle());
    }

    @Override
    public void setContentView(int layoutResID) {
        ViewGroup contentView = (ViewGroup) LayoutInflater.from(this).inflate(
                R.layout.settings_activity, (ViewGroup) getWindow().getDecorView().getParent(), false);

        mToolbar = (Toolbar) contentView.findViewById(R.id.toolbar);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ViewGroup contentWrapper = (ViewGroup) contentView.findViewById(R.id.content_wrapper);
        LayoutInflater.from(this).inflate(layoutResID, contentWrapper, true);

        getWindow().setContentView(contentView);
    }
}
