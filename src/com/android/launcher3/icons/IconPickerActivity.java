/*
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2017 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.icons;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import com.android.launcher3.IconCache;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;

public class IconPickerActivity extends Activity {
    private static final String TAG = "IconPickerActivity";
    public static final String EXTRA_PACKAGE = "app_package";
    public static final String EXTRA_LABEL = "app_label";
    public static final String EXTRA_ICON_PACK = "icon_pack_package";

    private GridLayoutManager mGridLayout;
    private ProgressBar mProgressBar;
    private RecyclerView mIconsGrid;

    private static ItemInfo sItemInfo;
    private IconCache mIconCache;
    private IconsHandler mIconsHandler;

    private List<String> mAllicons;
    private List<String> mMatchingIcons;
    private GridAdapter mAdapter;

    private String mCurrentPackageLabel;
    private String mCurrentPackageName;
    private String mIconPackPackageName;
    private int mIconSize;
    private boolean mCanSearch;

    public static void setItemInfo(ItemInfo info) {
        sItemInfo = info;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.all_icons_view);

        mCurrentPackageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        mCurrentPackageLabel = getIntent().getStringExtra(EXTRA_LABEL);
        mIconPackPackageName = getIntent().getStringExtra(EXTRA_ICON_PACK);

        mIconCache = LauncherAppState.getInstance(this).getIconCache();
        mIconsHandler = IconCache.getIconsHandler(this);

        int itemSpacing = getResources().getDimensionPixelSize(R.dimen.grid_item_spacing);
        mIconsGrid = (RecyclerView) findViewById(R.id.icons_grid);
        mIconsGrid.setHasFixedSize(true);
        mIconsGrid.addItemDecoration(new GridItemSpacer(itemSpacing));
        mGridLayout = new GridLayoutManager(this, 4);
        mIconsGrid.setLayoutManager(mGridLayout);
        mIconsGrid.setAlpha(0.0f);

        mProgressBar = (ProgressBar) findViewById(R.id.icons_grid_progress);
        mIconSize = getResources().getDimensionPixelSize(R.dimen.icon_pack_icon_size);

        LinearLayout headerBar = (LinearLayout) findViewById(R.id.icons_grid_name_header);
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(mIconPackPackageName,
                    PackageManager.GET_META_DATA);

            TextView headerTitle = (TextView) findViewById(R.id.icons_grid_name_header_title);
            ImageView headerIcon = (ImageView) findViewById(R.id.icons_grid_name_header_icon);

            headerTitle.setText(pm.getApplicationLabel(info));
            headerIcon.setImageDrawable(pm.getApplicationIcon(info));
        } catch (PackageManager.NameNotFoundException e) {
            headerBar.setVisibility(View.GONE);
            Log.e(TAG, e.getMessage());
        }

        new Thread(() -> {
            mCanSearch = false;
            final Activity activity = IconPickerActivity.this;
            mAllicons = mIconsHandler.getAllDrawables(mIconPackPackageName);
            mMatchingIcons = mIconsHandler.getMatchingDrawables(mCurrentPackageName);
            activity.runOnUiThread(() -> {
                mAdapter = new GridAdapter(mAllicons, mMatchingIcons);
                mIconsGrid.setAdapter(mAdapter);
                mProgressBar.setVisibility(View.GONE);
                mIconsGrid.animate().alpha(1.0f);
                mCanSearch = true;
            });
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.icon_picker, menu);

        MenuItem search = menu.findItem(R.id.icon_picker_search);
        SearchView searchView = (SearchView) search.getActionView();
        searchView.setOnQueryTextListener(getSearchListener());
        return true;
    }

    private SearchView.OnQueryTextListener getSearchListener() {
        return new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (!mCanSearch) {
                    return false;
                }
                IconsSearchUtils.filter(s, mMatchingIcons, mAllicons, mAdapter);
                return true;
            }
        };
    }

    class GridAdapter extends RecyclerView.Adapter<GridAdapter.ViewHolder> implements Filterable {

        private static final int TYPE_MATCHING_HEADER = 0;
        private static final int TYPE_MATCHING_ICONS = 1;
        private static final int TYPE_ALL_HEADER = 2;
        private static final int TYPE_ALL_ICONS = 3;

        private boolean mNoMatchingDrawables;

        private List<String> mAllDrawables = new ArrayList<>();
        private List<String> mMatchingDrawables = new ArrayList<>();
        private final SpanSizeLookup mSpanSizeLookup = new SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_MATCHING_HEADER ||
                        getItemViewType(position) == TYPE_ALL_HEADER ? 4 : 1;
            }
        };

        private GridAdapter(List<String> allDrawables, List<String> matchingDrawables) {
            mAllDrawables.add(null);
            mAllDrawables.addAll(allDrawables);
            mMatchingDrawables.add(null);
            mMatchingDrawables.addAll(matchingDrawables);
            mGridLayout.setSpanSizeLookup(mSpanSizeLookup);
            mNoMatchingDrawables = matchingDrawables.isEmpty();
            if (mNoMatchingDrawables) {
                mMatchingDrawables.clear();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (!mNoMatchingDrawables && position < mMatchingDrawables.size() &&
                    mMatchingDrawables.get(position) == null) {
                return TYPE_MATCHING_HEADER;
            }

            if (!mNoMatchingDrawables && position > TYPE_MATCHING_HEADER &&
                    position < mMatchingDrawables.size()) {
                return TYPE_MATCHING_ICONS;
            }

            if (position == mMatchingDrawables.size()) {
                return TYPE_ALL_HEADER;
            }
            return TYPE_ALL_ICONS;
        }

        @Override
        public int getItemCount() {
            return mAllDrawables.size() + 1;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Activity activity = IconPickerActivity.this;
            if (viewType == TYPE_MATCHING_HEADER) {
                TextView text = (TextView) activity.getLayoutInflater().inflate(
                        R.layout.all_icons_view_header, null);
                text.setText(R.string.icon_pack_suggestions);
                return new ViewHolder(text);
            }
            if (viewType == TYPE_ALL_HEADER) {
                TextView text = (TextView) activity.getLayoutInflater().inflate(
                        R.layout.all_icons_view_header, null);
                text.setText(R.string.icon_pack_all_icons);
                return new ViewHolder(text);
            }

            ImageView view = new ImageView(activity);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, mIconSize);
            view.setLayoutParams(params);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (holder.getItemViewType() != TYPE_MATCHING_HEADER
                    && holder.getItemViewType() != TYPE_ALL_HEADER) {
                boolean drawablesMatching = holder.getItemViewType() == TYPE_MATCHING_ICONS;
                final List<String> drawables = new ArrayList<>(drawablesMatching ?
                        mMatchingDrawables : mAllDrawables);
                if (holder.getAdapterPosition() >= drawables.size()) {
                    return;
                }
                holder.itemView.setOnClickListener(v -> {
                    Drawable icon = mIconsHandler.loadDrawable(mIconPackPackageName,
                            drawables.get(holder.getAdapterPosition()), true);
                    if (icon != null) {
                        mIconCache.addCustomInfoToDataBase(icon, sItemInfo, mCurrentPackageLabel);
                    }
                    IconPickerActivity.this.finish();
                });
                Drawable icon = null;
                String drawable = drawables.get(position);
                try {
                    icon = mIconsHandler.loadDrawable(mIconPackPackageName, drawable, true);
                } catch (OutOfMemoryError e) {
                    Log.e(TAG, e.getMessage());
                }
                if (icon != null) {
                    ((ImageView) holder.itemView).setImageDrawable(icon);
                }
            }
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence charSequence) {
                    return new FilterResults();
                }

                @Override
                protected void publishResults(CharSequence charSequence, FilterResults results) {
                }
            };
        }


        void filterList(List<String> filteredDrawables, List<String> filteredMatches) {
            mAllDrawables = filteredDrawables;
            mMatchingDrawables = filteredMatches;
            notifyDataSetChanged();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private ViewHolder(View v) {
                super(v);
            }
        }
    }

    private class GridItemSpacer extends RecyclerView.ItemDecoration {
        private int spacing;

        private GridItemSpacer(int spacing) {
            this.spacing = spacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            outRect.top = spacing;
        }
    }
}
