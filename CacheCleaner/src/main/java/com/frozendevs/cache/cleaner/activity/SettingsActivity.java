package com.frozendevs.cache.cleaner.activity;

import android.app.AlertDialog;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;

import com.frozendevs.cache.cleaner.R;

public class SettingsActivity extends PreferenceActivity {

    private final static String ACTION_SETTINGS_ABOUT = "com.frozendevs.cache.cleaner.activity.SETTINGS_ABOUT";

    private Toolbar mActionBar;

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

        mActionBar.setTitle(getTitle());
    }

    @Override
    public void setContentView(int layoutResID) {
        ViewGroup contentView = (ViewGroup) LayoutInflater.from(this).inflate(layoutResID,
                new LinearLayout(this), false);

        TypedArray typedArray = getTheme().obtainStyledAttributes(R.style.Theme_Application,
                new int[]{R.attr.colorPrimary, R.attr.homeAsUpIndicator});

        mActionBar = new Toolbar(this);
        mActionBar.setBackgroundResource(typedArray.getResourceId(0,
                R.color.primary_material_dark));
        mActionBar.setNavigationIcon(typedArray.getResourceId(1,
                R.drawable.abc_ic_ab_back_mtrl_am_alpha));
        mActionBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        typedArray.recycle();

        contentView.addView(mActionBar, 0);

        getWindow().setContentView(contentView);
    }
}
