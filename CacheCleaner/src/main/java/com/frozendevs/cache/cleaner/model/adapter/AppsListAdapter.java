package com.frozendevs.cache.cleaner.model.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
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

    private class ViewHolder {
        ImageView image;
        TextView name, size;
        String packageName;
    }

    public AppsListAdapter(Context context) {
        mContext = context;

        mItems = new ArrayList<AppsListItem>();
        mFilteredItems = new ArrayList<AppsListItem>();
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

    public void setItems(List<AppsListItem> items, SortBy sortBy) {
        mItems = items;

        sort(sortBy);
    }

    public void filterAppsByName(String filter) {
        new AsyncTask<String, Void, List<AppsListItem>>() {

            @Override
            protected List<AppsListItem> doInBackground(String... params) {
                List<AppsListItem> filteredItems = new ArrayList<AppsListItem>();

                Locale current = mContext.getResources().getConfiguration().locale;

                for (AppsListItem item : mItems) {
                    if (item.getApplicationName().toLowerCase(current).contains(
                            params[0].toLowerCase(current))) {
                        filteredItems.add(item);
                    }
                }

                return filteredItems;
            }

            @Override
            protected void onPostExecute(List<AppsListItem> result) {
                mFilteredItems = result;

                notifyDataSetChanged();
            }

        }.execute(filter);
    }

    public void clearFilter() {
        mFilteredItems = mItems;

        notifyDataSetChanged();
    }

    public void sort(SortBy sortBy) {
        new AsyncTask<SortBy, Void, ArrayList<AppsListItem>>() {

            @Override
            protected ArrayList<AppsListItem> doInBackground(final SortBy... params) {
                ArrayList<AppsListItem> items = new ArrayList<AppsListItem>(mItems);

                Collections.sort(items, new Comparator<AppsListItem>() {
                    @Override
                    public int compare(AppsListItem lhs, AppsListItem rhs) {
                        switch (params[0]) {
                            case APP_NAME:
                                return lhs.getApplicationName().compareToIgnoreCase(
                                        rhs.getApplicationName());

                            case CACHE_SIZE:
                                return (int) (rhs.getCacheSize() - lhs.getCacheSize());
                        }

                        return 0;
                    }
                });

                return items;
            }

            @Override
            protected void onPostExecute(ArrayList<AppsListItem> result) {
                mFilteredItems = result;

                notifyDataSetChanged();
            }

        }.execute(sortBy);
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
