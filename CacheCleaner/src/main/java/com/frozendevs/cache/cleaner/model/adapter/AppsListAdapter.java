package com.frozendevs.cache.cleaner.model.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.text.BidiFormatter;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.settings.applications.LinearColorBar;
import com.frozendevs.cache.cleaner.R;
import com.frozendevs.cache.cleaner.model.AppsListItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class AppsListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public enum SortBy {
        APP_NAME,
        CACHE_SIZE
    }

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_ITEM = 1;

    private List<AppsListItem> mItems = new ArrayList<>();
    private List<AppsListItem> mFilteredItems = new ArrayList<>();
    private SortBy mLastSortBy;
    private boolean mShowHeaderView = true;

    private long mTotalMemory, mLowMemory, mMedMemory, mHighMemory;

    public static class ItemViewHolder extends RecyclerView.ViewHolder implements
            View.OnClickListener {
        private ImageView mIcon;
        private TextView mName, mSize;
        private String mPackageName;

        public ItemViewHolder(View itemView) {
            super(itemView);

            mIcon = (ImageView) itemView.findViewById(R.id.app_icon);
            mName = (TextView) itemView.findViewById(R.id.app_name);
            mSize = (TextView) itemView.findViewById(R.id.app_size);

            itemView.setOnClickListener(this);
        }

        public void setIcon(Drawable drawable) {
            mIcon.setImageDrawable(drawable);
        }

        public void setName(String name) {
            mName.setText(name);
        }

        public void setPackageName(String packageName) {
            mPackageName = packageName;
        }

        public void setSize(long size) {
            mSize.setText(Formatter.formatShortFileSize(mSize.getContext(), size));
        }

        @Override
        public void onClick(View view) {
            if (mPackageName != null) {
                Intent intent = new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + mPackageName));

                view.getContext().startActivity(intent);
            }
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private LinearColorBar mColorBar;
        private TextView mSystemSizeText;
        private TextView mCacheSizeText;
        private TextView mFreeSizeText;

        public HeaderViewHolder(View itemView) {
            super(itemView);

            mColorBar = (LinearColorBar) itemView.findViewById(R.id.color_bar);
            mColorBar.setColors(itemView.getResources().getColor(R.color.apps_list_system_memory),
                    itemView.getResources().getColor(R.color.apps_list_cache_memory),
                    itemView.getResources().getColor(R.color.apps_list_free_memory));
            mSystemSizeText = (TextView) itemView.findViewById(R.id.systemSize);
            mCacheSizeText = (TextView) itemView.findViewById(R.id.cacheSize);
            mFreeSizeText = (TextView) itemView.findViewById(R.id.freeSize);
        }

        public void updateStorageUsage(long totalMemory, long lowMemory, long medMemory,
                                       long highMemory) {
            Context context = mColorBar.getContext();

            BidiFormatter bidiFormatter = BidiFormatter.getInstance();

            String sizeStr = bidiFormatter.unicodeWrap(
                    Formatter.formatShortFileSize(context, lowMemory));
            mFreeSizeText.setText(context.getString(R.string.apps_list_header_memory, sizeStr));

            sizeStr = bidiFormatter.unicodeWrap(
                    Formatter.formatShortFileSize(context, medMemory));
            mCacheSizeText.setText(context.getString(R.string.apps_list_header_memory, sizeStr));

            sizeStr = bidiFormatter.unicodeWrap(
                    Formatter.formatShortFileSize(context, highMemory));
            mSystemSizeText.setText(context.getString(R.string.apps_list_header_memory, sizeStr));

            mColorBar.setRatios((float) highMemory / (float) totalMemory,
                    (float) medMemory / (float) totalMemory,
                    (float) lowMemory / (float) totalMemory);
        }
    }

    public AppsListAdapter() {
        setHasStableIds(true);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                return new HeaderViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(
                        R.layout.apps_list_header, viewGroup, false));

            case VIEW_TYPE_ITEM:
                return new ItemViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(
                        R.layout.list_item, viewGroup, false));
        }

        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        if (viewHolder instanceof ItemViewHolder) {
            AppsListItem item = mFilteredItems.get(position);

            ((ItemViewHolder) viewHolder).setIcon(item.getApplicationIcon());
            ((ItemViewHolder) viewHolder).setName(item.getApplicationName());
            ((ItemViewHolder) viewHolder).setPackageName(item.getPackageName());
            ((ItemViewHolder) viewHolder).setSize(item.getCacheSize());
        } else if (viewHolder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) viewHolder).updateStorageUsage(mTotalMemory, mLowMemory,
                    mMedMemory, mHighMemory);
        }
    }

    @Override
    public long getItemId(int i) {
        AppsListItem item = mFilteredItems.get(i);

        return item != null ? item.hashCode() : 0;
    }

    @Override
    public int getItemCount() {
        return mFilteredItems.size();
    }

    private void insertHeaderView(List<AppsListItem> items) {
        if (mShowHeaderView && items.size() > 0) {
            items.add(0, null);

            notifyItemInserted(0);
        }
    }

    public void setItems(Context context, List<AppsListItem> items, SortBy sortBy, String filter) {
        mItems = items;

        mLastSortBy = null;

        if (mItems.size() > 0) {
            sortAndFilter(context, sortBy, filter);
        } else {
            mFilteredItems = new ArrayList<>(mItems);

            insertHeaderView(mFilteredItems);

            notifyDataSetChanged();
        }
    }

    public void sortAndFilter(Context context, final SortBy sortBy, String filter) {
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

        if (filter != null && !filter.equals("")) {
            List<AppsListItem> filteredItems = new ArrayList<>();

            Locale current;

            try {
                current = context.getResources().getConfiguration().locale;
            } catch (NullPointerException e) {
                current = Locale.getDefault();
            }

            for (AppsListItem item : mItems) {
                if (item.getApplicationName().toLowerCase(current).contains(
                        filter.toLowerCase(current))) {
                    filteredItems.add(item);
                }
            }

            mFilteredItems = filteredItems;

            insertHeaderView(mFilteredItems);
        } else {
            mFilteredItems = new ArrayList<>(mItems);

            insertHeaderView(mFilteredItems);
        }

        notifyDataSetChanged();
    }

    public void clearFilter() {
        mFilteredItems = new ArrayList<>(mItems);

        insertHeaderView(mFilteredItems);

        notifyDataSetChanged();
    }

    public void updateStorageUsage(long totalMemory, long lowMemory, long medMemory,
                                   long highMemory) {
        mTotalMemory = totalMemory;
        mLowMemory = lowMemory;
        mMedMemory = medMemory;
        mHighMemory = highMemory;

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mFilteredItems.get(position) == null ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    public void setShowHeaderView(boolean show) {
        boolean oldShow = mShowHeaderView;

        mShowHeaderView = show;

        if (show && !oldShow) {
            insertHeaderView(mFilteredItems);
        } else if (!show && oldShow && mFilteredItems.size() > 0) {
            mFilteredItems.remove(0);

            notifyItemRemoved(0);
        }
    }

    public void trashItems() {
        mItems = new ArrayList<>();
        mFilteredItems = new ArrayList<>();

        notifyDataSetChanged();
    }
}
