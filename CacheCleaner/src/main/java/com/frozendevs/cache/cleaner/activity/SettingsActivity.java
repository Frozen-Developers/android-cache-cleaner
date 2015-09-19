package com.frozendevs.cache.cleaner.activity;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.frozendevs.cache.cleaner.fragment.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {
    public final static String ARGUMENT_ACTION = "action";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle bundle = new Bundle();
        bundle.putString(ARGUMENT_ACTION, getIntent().getAction());

        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, Fragment.instantiate(this,
                        SettingsFragment.class.getName(), bundle))
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
