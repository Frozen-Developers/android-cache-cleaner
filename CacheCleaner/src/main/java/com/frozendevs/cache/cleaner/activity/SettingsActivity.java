package com.frozendevs.cache.cleaner.activity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.frozendevs.cache.cleaner.fragment.AboutFragment;
import com.frozendevs.cache.cleaner.fragment.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {

    private final static String ACTION_SETTINGS_ABOUT = "com.frozendevs.cache.cleaner.SETTINGS_ABOUT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Fragment fragment;

        String action = getIntent().getAction();

        if (action != null && action.equals(ACTION_SETTINGS_ABOUT)) {
            fragment = new AboutFragment();
        } else {
            fragment = new SettingsFragment();
        }

        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
