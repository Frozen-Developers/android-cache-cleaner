package com.frozendevs.cache.cleaner.model.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frozendevs.cache.cleaner.model.AppsListItem;
import com.frozendevs.cache.cleaner.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AppsListAdapter extends BaseAdapter {

    public static final int SORT_BY_APP_NAME = 0;
    public static final int SORT_BY_CACHE_SIZE = 1;

    private List<AppsListItem> items, filteredItems;
    private Context context;
    private SharedPreferences sharedPreferences;

    public AppsListAdapter(Context context, SharedPreferences sharedPreferences) {
        this.context = context;
        this.sharedPreferences = sharedPreferences;

        items = new ArrayList<AppsListItem>();
        filteredItems = new ArrayList<AppsListItem>(items);
    }

    @Override
    public int getCount() {
        return filteredItems.size();
    }

    @Override
    public Object getItem(int i) {
        return filteredItems.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final AppsListItem item = filteredItems.get(i);

        LinearLayout layout = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.list_item, null);
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + item.getPackageName()));
                }
                else {
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");

                    if(Build.VERSION.SDK_INT == Build.VERSION_CODES.FROYO)
                        intent.putExtra("pkg", item.getPackageName());
                    else
                        intent.putExtra("com.android.settings.ApplicationPkgName", item.getPackageName());
                }

                context.startActivity(intent);
            }
        });

        ImageView imageView = (ImageView)layout.findViewById(R.id.app_icon);
        imageView.setImageDrawable(item.getApplicationIcon());

        TextView nameView = (TextView)layout.findViewById(R.id.app_name);
        nameView.setText(item.getApplicationName());

        TextView sizeView = (TextView)layout.findViewById(R.id.app_size);
        sizeView.setText(Formatter.formatShortFileSize(context, item.getCacheSize()));

        return layout;
    }

    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return filteredItems.size() == 0;
    }

    public void setItems(List<AppsListItem> items) {
        this.items = new ArrayList<AppsListItem>(items);

        sort();
    }

    public void filterAppsByName(String filter) {
        List<AppsListItem> filteredItems = new ArrayList<AppsListItem>();

        Locale current = context.getResources().getConfiguration().locale;

        for(AppsListItem item : items)
            if(item.getApplicationName().toLowerCase(current).contains(filter.toLowerCase(current)))
                filteredItems.add(item);

        this.filteredItems = filteredItems;

        notifyDataSetChanged();
    }

    public void clearFilter() {
        filteredItems = new ArrayList<AppsListItem>(items);

        notifyDataSetChanged();
    }

    public long getTotalCacheSize() {
        long size = 0;

        for(AppsListItem app : items)
            size += app.getCacheSize();

        return size;
    }

    public void sort() {
        Collections.sort(items, new Comparator<AppsListItem>() {
            @Override
            public int compare(AppsListItem lhs, AppsListItem rhs) {
                switch (sharedPreferences.getInt(context.getString(R.string.sort_by_key), SORT_BY_CACHE_SIZE)) {
                    case SORT_BY_APP_NAME:
                        return lhs.getApplicationName().compareToIgnoreCase(rhs.getApplicationName());

                    case SORT_BY_CACHE_SIZE:
                        return (int)(rhs.getCacheSize() - lhs.getCacheSize());
                }

                return 0;
            }
        });

        filteredItems = new ArrayList<AppsListItem>(items);
    }
}
