package com.frozendevs.cache.cleaner.model.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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

    private class ViewHolder {
        ImageView image;
        TextView name, size;
        String packageName;
    }

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
        return true;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewParent) {
        final AppsListItem item = mFilteredItems.get(i);

        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.list_item, viewParent, false);

            ViewHolder viewHolder = new ViewHolder();

            viewHolder.image = (ImageView) convertView.findViewById(R.id.app_icon);
            viewHolder.name = (TextView) convertView.findViewById(R.id.app_name);
            viewHolder.size = (TextView) convertView.findViewById(R.id.app_size);

            convertView.setTag(viewHolder);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + ((ViewHolder) v.getTag()).packageName));

                    mContext.startActivity(intent);
                }
            });
        }

        ViewHolder viewHolder = (ViewHolder) convertView.getTag();

        viewHolder.image.setImageDrawable(item.getApplicationIcon());
        viewHolder.name.setText(item.getApplicationName());
        viewHolder.size.setText(Formatter.formatShortFileSize(mContext, item.getCacheSize()));
        viewHolder.packageName = item.getPackageName();

        return convertView;
    }

    public void setItems(List<AppsListItem> items) {
        mItems = new ArrayList<AppsListItem>(items);

        sort();
    }

    public void filterAppsByName(String filter) {
        List<AppsListItem> filteredItems = new ArrayList<AppsListItem>();

        Locale current = mContext.getResources().getConfiguration().locale;

        for (AppsListItem item : mItems) {
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

        for (AppsListItem app : mItems)
            size += app.getCacheSize();

        return size;
    }

    public void sort() {
        Collections.sort(mItems, new Comparator<AppsListItem>() {
            @Override
            public int compare(AppsListItem lhs, AppsListItem rhs) {
                switch (mSharedPreferences.getInt(mContext.getString(R.string.sort_by_key),
                        SORT_BY_CACHE_SIZE)) {
                    case SORT_BY_APP_NAME:
                        return lhs.getApplicationName().compareToIgnoreCase(rhs.getApplicationName());

                    case SORT_BY_CACHE_SIZE:
                        return (int) (rhs.getCacheSize() - lhs.getCacheSize());
                }

                return 0;
            }
        });

        mFilteredItems = new ArrayList<AppsListItem>(mItems);
    }
}
