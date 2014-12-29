package com.frozendevs.cache.cleaner.model.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.frozendevs.cache.cleaner.R;
import com.frozendevs.cache.cleaner.model.AppsListItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AppsListAdapter extends BaseAdapter implements AdapterView.OnItemClickListener {

    public static enum SortBy {
        APP_NAME,
        CACHE_SIZE
    }

    private List<AppsListItem> mItems, mFilteredItems;
    private Context mContext;
    private SortBy mLastSortBy;

    private class ViewHolder {
        ImageView image;
        TextView name, size;
        String packageName;
    }

    public AppsListAdapter(Context context) {
        mContext = context;

        mItems = new ArrayList<>();
        mFilteredItems = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return mFilteredItems.size();
    }

    @Override
    public AppsListItem getItem(int i) {
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
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.list_item, viewParent, false);
        }

        ViewHolder viewHolder = (ViewHolder) convertView.getTag();

        if (viewHolder == null) {
            viewHolder = new ViewHolder();

            viewHolder.image = (ImageView) convertView.findViewById(R.id.app_icon);
            viewHolder.name = (TextView) convertView.findViewById(R.id.app_name);
            viewHolder.size = (TextView) convertView.findViewById(R.id.app_size);

            convertView.setTag(viewHolder);
        }

        AppsListItem item = getItem(i);

        viewHolder.image.setImageDrawable(item.getApplicationIcon());
        viewHolder.name.setText(item.getApplicationName());
        viewHolder.size.setText(Formatter.formatShortFileSize(mContext, item.getCacheSize()));
        viewHolder.packageName = item.getPackageName();

        return convertView;
    }

    public void setItems(List<AppsListItem> items, SortBy sortBy, String filter) {
        mItems = items;

        mLastSortBy = null;

        if (mItems.size() > 0) {
            sortAndFilter(sortBy, filter);
        } else {
            mFilteredItems = new ArrayList<>(mItems);

            notifyDataSetChanged();
        }
    }

    public void sortAndFilter(final SortBy sortBy, String filter) {
        if (sortBy != mLastSortBy) {
            mLastSortBy = sortBy;

            ArrayList<AppsListItem> items = new ArrayList<>(mItems);

            Collections.sort(items, new Comparator<AppsListItem>() {
                @Override
                public int compare(AppsListItem lhs, AppsListItem rhs) {
                    switch (sortBy) {
                        case APP_NAME:
                            return lhs.getApplicationName().compareToIgnoreCase(
                                    rhs.getApplicationName());

                        case CACHE_SIZE:
                            return (int) (rhs.getCacheSize() - lhs.getCacheSize());
                    }

                    return 0;
                }
            });

            mItems = items;
        }

        if (!filter.equals("")) {
            List<AppsListItem> filteredItems = new ArrayList<>();

            Locale current = mContext.getResources().getConfiguration().locale;

            for (AppsListItem item : mItems) {
                if (item.getApplicationName().toLowerCase(current).contains(
                        filter.toLowerCase(current))) {
                    filteredItems.add(item);
                }
            }

            mFilteredItems = filteredItems;
        } else {
            mFilteredItems = new ArrayList<>(mItems);
        }

        notifyDataSetChanged();
    }

    public void clearFilter() {
        mFilteredItems = new ArrayList<>(mItems);

        notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ViewHolder viewHolder = (ViewHolder) view.getTag();

        if (viewHolder != null && viewHolder.packageName != null) {
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + viewHolder.packageName));

            mContext.startActivity(intent);
        }
    }
}
