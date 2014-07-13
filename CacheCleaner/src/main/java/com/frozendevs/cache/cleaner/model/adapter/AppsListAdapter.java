package com.frozendevs.cache.cleaner.model.adapter;

import android.annotation.TargetApi;
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

    private List<AppsListItem> mItems, mFilteredItems;
    private Context mContext;
    private SharedPreferences mSharedPreferences;

    public AppsListAdapter(Context context, SharedPreferences sharedPreferences) {
        mContext = context;
        mSharedPreferences = sharedPreferences;

        mItems = new ArrayList<AppsListItem>();
        mFilteredItems = new ArrayList<AppsListItem>(mItems);
    }

    @Override
    public int getCount() {
        return mFilteredItems.size();
    }

    @Override
    public Object getItem(int i) {
        return mFilteredItems.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final AppsListItem item = mFilteredItems.get(i);

        if(view == null) view = LayoutInflater.from(mContext).inflate(R.layout.list_item, null);

        view.setOnClickListener(new View.OnClickListener() {
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

                mContext.startActivity(intent);
            }
        });

        ImageView imageView = (ImageView)view.findViewById(R.id.app_icon);
        imageView.setImageDrawable(item.getApplicationIcon());

        TextView nameView = (TextView)view.findViewById(R.id.app_name);
        nameView.setText(item.getApplicationName());

        TextView sizeView = (TextView)view.findViewById(R.id.app_size);
        sizeView.setText(Formatter.formatShortFileSize(mContext, item.getCacheSize()));

        return view;
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
        return mFilteredItems.size() == 0;
    }

    public void setItems(List<AppsListItem> items) {
        mItems = new ArrayList<AppsListItem>(items);

        sort();
    }

    public void filterAppsByName(String filter) {
        List<AppsListItem> filteredItems = new ArrayList<AppsListItem>();

        Locale current = mContext.getResources().getConfiguration().locale;

        for(AppsListItem item : mItems) {
            if (item.getApplicationName().toLowerCase(current).contains(filter.toLowerCase(current)))
                filteredItems.add(item);
        }

        mFilteredItems = filteredItems;

        notifyDataSetChanged();
    }

    public void clearFilter() {
        mFilteredItems = new ArrayList<AppsListItem>(mItems);

        notifyDataSetChanged();
    }

    public long getTotalCacheSize() {
        long size = 0;

        for(AppsListItem app : mItems)
            size += app.getCacheSize();

        return size;
    }

    public void sort() {
        Collections.sort(mItems, new Comparator<AppsListItem>() {
            @Override
            public int compare(AppsListItem lhs, AppsListItem rhs) {
                switch (mSharedPreferences.getInt(mContext.getString(R.string.sort_by_key), SORT_BY_CACHE_SIZE)) {
                    case SORT_BY_APP_NAME:
                        return lhs.getApplicationName().compareToIgnoreCase(rhs.getApplicationName());

                    case SORT_BY_CACHE_SIZE:
                        return (int)(rhs.getCacheSize() - lhs.getCacheSize());
                }

                return 0;
            }
        });

        mFilteredItems = new ArrayList<AppsListItem>(mItems);
    }
}
