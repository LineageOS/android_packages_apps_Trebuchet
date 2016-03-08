/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.net.Uri;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import com.android.launcher3.AppInfo;
import com.android.launcher3.BaseRecyclerViewFastScrollBar.FastScrollFocusApplicator;
import com.android.launcher3.BaseRecyclerViewFastScrollBar.FastScrollFocusable;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.RemoteFolderManager;
import com.android.launcher3.Utilities;
import com.android.launcher3.settings.SettingsProvider;
import com.android.launcher3.util.Thunk;

import java.util.HashMap;
import java.util.List;


/**
 * The grid view adapter of all the apps.
 */
public class AllAppsGridAdapter extends RecyclerView.Adapter<AllAppsGridAdapter.ViewHolder> {

    public static final String TAG = "AppsGridAdapter";
    private static final boolean DEBUG = false;

    // A section break in the grid
    public static final int SECTION_BREAK_VIEW_TYPE = 0;
    // A normal icon
    public static final int ICON_VIEW_TYPE = 1;
    // A prediction icon
    public static final int PREDICTION_ICON_VIEW_TYPE = 2;
    // The message shown when there are no filtered results
    public static final int EMPTY_SEARCH_VIEW_TYPE = 3;
    // A divider that separates the apps list and the search market button
    public static final int SEARCH_MARKET_DIVIDER_VIEW_TYPE = 4;
    // The message to continue to a market search when there are no filtered results
    public static final int SEARCH_MARKET_VIEW_TYPE = 5;
    // Section header for customized predicated apps.
    public static final int CUSTOM_PREDICTED_APPS_HEADER_VIEW_TYPE = 6;
    // Additional spacing between predicted apps and regular apps.
    public static final int CUSTOM_PREDICTED_APPS_FOOTER_VIEW_TYPE = 7;

    private boolean mIconsDimmed = false;

    private int mGridTheme;


    private AlphabeticalAppsList.SectionInfo mFocusedSection;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View mContent;

        public ViewHolder(View v) {
            super(v);
            mContent = v;
        }
    }

    /**
     * A subclass of GridLayoutManager that overrides accessibility values during app search.
     */
    public class AppsGridLayoutManager extends GridLayoutManager {

        public AppsGridLayoutManager(Context context) {
            super(context, 1, GridLayoutManager.VERTICAL, false);
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);

            // Ensure that we only report the number apps for accessibility not including other
            // adapter views
            final AccessibilityRecordCompat record = AccessibilityEventCompat
                    .asRecord(event);
            record.setItemCount(mApps.getNumFilteredApps());
        }

        @Override
        public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
                RecyclerView.State state) {
            if (mApps.hasNoFilteredResults()) {
                // Disregard the no-search-results text as a list item for accessibility
                return 0;
            } else {
                return super.getRowCountForAccessibility(recycler, state);
            }
        }
    }

    /**
     * Helper class to size the grid items.
     */
    public class GridSpanSizer extends GridLayoutManager.SpanSizeLookup {

        public GridSpanSizer() {
            super();
            setSpanIndexCacheEnabled(true);
        }

        @Override
        public int getSpanSize(int position) {
            switch (mApps.getAdapterItems().get(position).viewType) {
                case AllAppsGridAdapter.ICON_VIEW_TYPE:
                case AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE:
                    return 1;
                default:
                    // Section breaks span the full width
                    return mAppsPerRow;
            }
        }
    }

    /**
     * Helper class to draw the section headers
     */
    public class GridItemDecoration extends RecyclerView.ItemDecoration {

        private static final boolean DEBUG_SECTION_MARGIN = false;
        private static final boolean FADE_OUT_SECTIONS = true;

        private HashMap<String, PointF> mCachedSectionBounds = new HashMap<>();
        private Rect mTmpBounds = new Rect();

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (mApps.hasFilter() || mAppsPerRow == 0) {
                return;
            }

            if (DEBUG_SECTION_MARGIN) {
                Paint p = new Paint();
                p.setColor(0x33ff0000);
                c.drawRect(mBackgroundPadding.left, 0, mBackgroundPadding.left + mSectionNamesMargin,
                        parent.getMeasuredHeight(), p);
            }

            List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
            boolean hasDrawnPredictedAppsDivider = false;
            boolean showSectionNames = mSectionNamesMargin > 0;
            int childCount = parent.getChildCount();
            int lastSectionTop = 0;
            int lastSectionHeight = 0;
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                ViewHolder holder = (ViewHolder) parent.getChildViewHolder(child);
                if (!isValidHolderAndChild(holder, child, items)) {
                    continue;
                }

                if (shouldDrawItemDivider(holder, items) && !hasDrawnPredictedAppsDivider) {
                    // Draw the divider under the predicted apps
                    int top = child.getTop() + child.getHeight() + mPredictionBarDividerOffset;
                    c.drawLine(mBackgroundPadding.left, top,
                            parent.getWidth() - mBackgroundPadding.right, top,
                            mPredictedAppsDividerPaint);
                    hasDrawnPredictedAppsDivider = true;

                    // Only customized predicted apps will draw a section name.
                    if (!mApps.mCustomPredictedAppsEnabled) continue;
                }

                if (showSectionNames && shouldDrawItemSection(holder, items)) {
                    // Draw the section name for the first visible item
                    int viewTopOffset = (2 * child.getPaddingTop());
                    int pos = holder.getPosition();
                    AlphabeticalAppsList.AdapterItem item = items.get(pos);
                    AlphabeticalAppsList.SectionInfo sectionInfo = item.sectionInfo;
                    String lastSectionName = item.sectionName;

                    // Find the section name bounds
                    PointF sectionBounds = getAndCacheSectionBounds(lastSectionName);

                    // Calculate where to draw the section
                    int sectionBaseline = (int) (viewTopOffset + sectionBounds.y);
                    int x = mIsRtl ?
                            parent.getWidth() - mBackgroundPadding.left - mSectionNamesMargin :
                                    mBackgroundPadding.left;
                    x += (int) ((mSectionNamesMargin - sectionBounds.x) / 2f);

                    int y;
                    boolean fixedToRow = false;
                    if (item.viewType == PREDICTION_ICON_VIEW_TYPE) {
                        y = child.getTop() - (int) mSectionTextPaint.getTextSize() / 2;
                    } else {
                        y = child.getTop() + sectionBaseline;

                        // Determine whether this is the last row with apps in that section, if
                        // so, then fix the section to the row allowing it to scroll past the
                        // baseline, otherwise, bound it to the baseline so it's in the viewport
                        int appIndexInSection = items.get(pos).sectionAppIndex;
                        int nextRowPos = Math.min(items.size() - 1,
                                pos + mAppsPerRow - (appIndexInSection % mAppsPerRow));
                        AlphabeticalAppsList.AdapterItem nextRowItem = items.get(nextRowPos);
                        fixedToRow = !lastSectionName.equals(nextRowItem.sectionName);
                        if (!fixedToRow) {
                            y = Math.max(sectionBaseline, y);
                        }

                        // In addition, if it overlaps with the last section that was drawn, then
                        // offset it so that it does not overlap
                        if (lastSectionHeight > 0 && y <= (lastSectionTop + lastSectionHeight)) {
                            y += lastSectionTop - y + lastSectionHeight;
                        }
                    }

                    // Draw the section header
                    if (FADE_OUT_SECTIONS) {
                        int alpha = 255;
                        if (fixedToRow) {
                            alpha = Math.min(255,
                                    (int) (255 * (Math.max(0, y) / (float) sectionBaseline)));
                        }
                        mSectionTextPaint.setAlpha(alpha);
                    }
                    c.drawText(lastSectionName, x, y, mSectionTextPaint);

                    lastSectionTop = y;
                    lastSectionHeight = (int) (sectionBounds.y + mSectionHeaderOffset);
                    i += (sectionInfo.numApps - item.sectionAppIndex);
                }
            }
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            // Do nothing
        }

        /**
         * Given a section name, return the bounds of the given section name.
         */
        private PointF getAndCacheSectionBounds(String sectionName) {
            PointF bounds = mCachedSectionBounds.get(sectionName);
            if (bounds == null) {
                mSectionTextPaint.getTextBounds(sectionName, 0, sectionName.length(), mTmpBounds);
                bounds = new PointF(mSectionTextPaint.measureText(sectionName), mTmpBounds.height());
                mCachedSectionBounds.put(sectionName, bounds);
            }
            return bounds;
        }

        /**
         * Returns whether we consider this a valid view holder for us to draw a divider or section for.
         */
        private boolean isValidHolderAndChild(ViewHolder holder, View child,
                List<AlphabeticalAppsList.AdapterItem> items) {
            // Ensure item is not already removed
            GridLayoutManager.LayoutParams lp = (GridLayoutManager.LayoutParams)
                    child.getLayoutParams();
            if (lp.isItemRemoved()) {
                return false;
            }
            // Ensure we have a valid holder
            if (holder == null) {
                return false;
            }
            // Ensure we have a holder position
            int pos = holder.getPosition();
            if (pos < 0 || pos >= items.size()) {
                return false;
            }
            return true;
        }

        /**
         * Returns whether to draw the divider for a given child.
         */
        private boolean shouldDrawItemDivider(ViewHolder holder,
                List<AlphabeticalAppsList.AdapterItem> items) {
            int pos = holder.getPosition();
            return items.get(pos).viewType == AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE;
        }

        /**
         * Returns whether to draw the section for the given child.
         */
        private boolean shouldDrawItemSection(ViewHolder holder,
                List<AlphabeticalAppsList.AdapterItem> items) {
            int pos = holder.getPosition();
            AlphabeticalAppsList.AdapterItem item = items.get(pos);

            // Ensure it's an icon
            if (item.viewType != ICON_VIEW_TYPE && item.viewType != PREDICTION_ICON_VIEW_TYPE) {
                return false;
            }

            return true;
        }
    }

    private final RemoteFolderManager mRemoteFolderManager;

    private Launcher mLauncher;
    private LayoutInflater mLayoutInflater;
    @Thunk AlphabeticalAppsList mApps;
    private GridLayoutManager mGridLayoutMgr;
    private GridSpanSizer mGridSizer;
    private GridItemDecoration mItemDecoration;
    private View.OnTouchListener mTouchListener;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;
    @Thunk final Rect mBackgroundPadding = new Rect();
    @Thunk int mPredictionBarDividerOffset;
    @Thunk int mAppsPerRow;
    @Thunk boolean mIsRtl;

    // The text to show when there are no search results and no market search handler.
    private String mEmptySearchMessage;
    // The name of the market app which handles searches, to be used in the format str
    // below when updating the search-market view.  Only needs to be loaded once.
    private String mMarketAppName;
    // The text to show when there is a market app which can handle a specific query, updated
    // each time the search query changes.
    private String mMarketSearchMessage;
    // The intent to send off to the market app, updated each time the search query changes.
    private Intent mMarketSearchIntent;
    // The last query that the user entered into the search field
    private String mLastSearchQuery;

    // Section drawing
    @Thunk int mSectionNamesMargin;
    @Thunk int mSectionHeaderOffset;
    @Thunk int mSectionStrategy;
    @Thunk Paint mSectionTextPaint;
    @Thunk Paint mPredictedAppsDividerPaint;

    private int mAllAppsTextColor;

    private int mCustomPredictedAppsHeaderHeight;
    private int mCustomPredictedAppsFooterHeight;

    public AllAppsGridAdapter(Launcher launcher, AlphabeticalAppsList apps,
            View.OnTouchListener touchListener, View.OnClickListener iconClickListener,
            View.OnLongClickListener iconLongClickListener) {
        Resources res = launcher.getResources();
        mLauncher = launcher;
        mApps = apps;
        mEmptySearchMessage = res.getString(R.string.all_apps_loading_message);
        mGridSizer = new GridSpanSizer();
        mGridLayoutMgr = new AppsGridLayoutManager(launcher);
        mGridLayoutMgr.setSpanSizeLookup(mGridSizer);
        mItemDecoration = new GridItemDecoration();
        mLayoutInflater = LayoutInflater.from(launcher);
        mTouchListener = touchListener;
        mIconClickListener = iconClickListener;
        mIconLongClickListener = iconLongClickListener;
        mSectionNamesMargin = mSectionStrategy ==
                AllAppsContainerView.SECTION_STRATEGY_GRID ?
                res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin) :
                res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin_with_sections);

        mAllAppsTextColor = mGridTheme == AllAppsContainerView.GRID_THEME_DARK ?
                res.getColor(R.color.quantum_panel_text_color_dark) :
                res.getColor(R.color.quantum_panel_text_color);

        mSectionHeaderOffset = res.getDimensionPixelSize(R.dimen.all_apps_grid_section_y_offset);

        mSectionTextPaint = new Paint();
        mSectionTextPaint.setTextSize(res.getDimensionPixelSize(
                R.dimen.all_apps_grid_section_text_size));
        int sectionTextColorId = mGridTheme == AllAppsContainerView.GRID_THEME_DARK ?
                R.color.all_apps_grid_section_text_color_dark :
                R.color.all_apps_grid_section_text_color;
        mSectionTextPaint.setColor(res.getColor(sectionTextColorId));
        mSectionTextPaint.setAntiAlias(true);

        mPredictedAppsDividerPaint = new Paint();
        mPredictedAppsDividerPaint.setStrokeWidth(Utilities.pxFromDp(1f, res.getDisplayMetrics()));
        mPredictedAppsDividerPaint.setColor(0x1E000000);
        mPredictedAppsDividerPaint.setAntiAlias(true);
        mPredictionBarDividerOffset =
                res.getDimensionPixelSize(R.dimen.all_apps_prediction_bar_divider_offset);

        // Resolve the market app handling additional searches
        PackageManager pm = launcher.getPackageManager();
        ResolveInfo marketInfo = pm.resolveActivity(createMarketSearchIntent(""),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (marketInfo != null) {
            mMarketAppName = marketInfo.loadLabel(pm).toString();
        }

        mRemoteFolderManager = launcher.getRemoteFolderManager();
    }

    /**
     * Sets the number of apps per row.
     */
    public void setNumAppsPerRow(int appsPerRow) {
        mAppsPerRow = appsPerRow;
        mGridLayoutMgr.setSpanCount(appsPerRow);
    }

    /**
     * Sets whether we are in RTL mode.
     */
    public void setRtl(boolean rtl) {
        mIsRtl = rtl;
    }

    public void setSectionStrategy(int sectionStrategy) {
        Resources res = mLauncher.getResources();
        mSectionStrategy = sectionStrategy;
        mSectionNamesMargin = mSectionStrategy ==
                AllAppsContainerView.SECTION_STRATEGY_GRID ?
                res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin) :
                res.getDimensionPixelSize(R.dimen.all_apps_grid_view_start_margin_with_sections);
    }

    /**
     * Sets the last search query that was made, used to show when there are no results and to also
     * seed the intent for searching the market.
     */
    public void setLastSearchQuery(String query) {
        Resources res = mLauncher.getResources();
        String formatStr = res.getString(R.string.all_apps_no_search_results);
        mLastSearchQuery = query;
        mEmptySearchMessage = String.format(formatStr, query);
        if (mMarketAppName != null) {
            mMarketSearchMessage = String.format(res.getString(R.string.all_apps_search_market_message),
                    mMarketAppName);
            mMarketSearchIntent = createMarketSearchIntent(query);
        }
    }

    /**
     * Notifies the adapter of the background padding so that it can draw things correctly in the
     * item decorator.
     */
    public void updateBackgroundPadding(Rect padding) {
        mBackgroundPadding.set(padding);
    }

    /**
     * Returns the grid layout manager.
     */
    public GridLayoutManager getLayoutManager() {
        return mGridLayoutMgr;
    }

    /**
     * Returns the item decoration for the recycler view.
     */
    public RecyclerView.ItemDecoration getItemDecoration() {
        // We don't draw any headers when we are uncomfortably dense
        return mItemDecoration;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        boolean hideIconLabels = SettingsProvider.getBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_DRAWER_HIDE_ICON_LABELS,
                R.bool.preferences_interface_drawer_hide_icon_labels_default);
        switch (viewType) {
            case SECTION_BREAK_VIEW_TYPE:
                return new ViewHolder(new View(parent.getContext()));
            case ICON_VIEW_TYPE: {
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.all_apps_icon, parent, false);
                if (hideIconLabels) {
                    icon.setTextVisibility(!hideIconLabels);
                }
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setLongPressTimeout(ViewConfiguration.get(parent.getContext())
                        .getLongPressTimeout());
                icon.setFocusable(true);
                FastScrollFocusApplicator.createApplicator(icon,
                        FastScrollFocusable.FAST_SCROLL_FOCUS_DIMMABLE |
                                FastScrollFocusable.FAST_SCROLL_FOCUS_SCALABLE);

                return new ViewHolder(icon);
            }
            case PREDICTION_ICON_VIEW_TYPE: {
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        R.layout.all_apps_prediction_bar_icon, parent, false);
                if (hideIconLabels) {
                    icon.setTextVisibility(!hideIconLabels);
                }
                icon.setOnTouchListener(mTouchListener);
                icon.setOnClickListener(mIconClickListener);
                icon.setOnLongClickListener(mIconLongClickListener);
                icon.setLongPressTimeout(ViewConfiguration.get(parent.getContext())
                        .getLongPressTimeout());
                icon.setFocusable(true);
                FastScrollFocusApplicator.createApplicator(icon,
                        FastScrollFocusable.FAST_SCROLL_FOCUS_DIMMABLE |
                                FastScrollFocusable.FAST_SCROLL_FOCUS_SCALABLE);

                ViewHolder holder = new ViewHolder(icon);
                mRemoteFolderManager.onCreateViewHolder(holder, viewType);

                return holder;
            }
            case EMPTY_SEARCH_VIEW_TYPE:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));
            case SEARCH_MARKET_DIVIDER_VIEW_TYPE:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_search_market_divider,
                        parent, false));
            case SEARCH_MARKET_VIEW_TYPE:
                View searchMarketView = mLayoutInflater.inflate(R.layout.all_apps_search_market,
                        parent, false);
                searchMarketView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mLauncher.startSearchFromAllApps(v, mMarketSearchIntent, mLastSearchQuery);
                    }
                });
                return new ViewHolder(searchMarketView);
            case CUSTOM_PREDICTED_APPS_HEADER_VIEW_TYPE: {
                View v = mLayoutInflater.inflate(
                        R.layout.custom_predicted_apps_header, parent, false);
                FastScrollFocusApplicator.createApplicator(v,
                        FastScrollFocusable.FAST_SCROLL_FOCUS_DIMMABLE);
                ViewHolder holder = new ViewHolder(v);
                mRemoteFolderManager.onCreateViewHolder(holder, viewType);
                return holder;
            }
            case CUSTOM_PREDICTED_APPS_FOOTER_VIEW_TYPE: {
                View v = mLayoutInflater.inflate(R.layout.custom_predicted_apps_footer,
                        parent, false);
                ViewHolder holder = new ViewHolder(v);
                mRemoteFolderManager.onCreateViewHolder(holder, viewType);
                return holder;
            }
            default:
                throw new RuntimeException("Unexpected view type");
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        boolean hideIconLabels = SettingsProvider.getBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_DRAWER_HIDE_ICON_LABELS,
                R.bool.preferences_interface_drawer_hide_icon_labels_default);
        FastScrollFocusApplicator.setFastScrollDimmed(holder.mContent, false, false);
        FastScrollFocusApplicator.setFastScrollFocused(holder.mContent, false, false);
        switch (holder.getItemViewType()) {
            case ICON_VIEW_TYPE: {
                AppInfo info = mApps.getAdapterItems().get(position).appInfo;
                BubbleTextView icon = (BubbleTextView) holder.mContent;
                icon.setTextColor(mAllAppsTextColor);
                if (hideIconLabels) {
                    icon.setTextVisibility(!hideIconLabels);
                }
                icon.applyFromApplicationInfo(info);
                FastScrollFocusApplicator.setFastScrollDimmed(icon, shouldDimPosition(position),
                        !mIconsDimmed);
                FastScrollFocusApplicator.setFastScrollFocused(icon, false, !mIconsDimmed);
                break;
            }
            case PREDICTION_ICON_VIEW_TYPE: {
                AppInfo info = mApps.getAdapterItems().get(position).appInfo;
                BubbleTextView icon = (BubbleTextView) holder.mContent;
                icon.setTextColor(mAllAppsTextColor);
                if (hideIconLabels) {
                    icon.setTextVisibility(!hideIconLabels);
                }
                icon.applyFromApplicationInfo(info);
                FastScrollFocusApplicator.setFastScrollDimmed(icon, shouldDimPosition(position),
                        !mIconsDimmed);
                FastScrollFocusApplicator.setFastScrollFocused(icon, false, !mIconsDimmed);

                mRemoteFolderManager.onBindViewHolder(holder, info);
                break;
            }
            case EMPTY_SEARCH_VIEW_TYPE:
                TextView emptyViewText = (TextView) holder.mContent;
                emptyViewText.setText(mEmptySearchMessage);
                emptyViewText.setGravity(mApps.hasNoFilteredResults() ? Gravity.CENTER :
                        Gravity.START | Gravity.CENTER_VERTICAL);
                break;
            case SEARCH_MARKET_VIEW_TYPE:
                TextView searchView = (TextView) holder.mContent;
                if (mMarketSearchIntent != null) {
                    searchView.setVisibility(View.VISIBLE);
                    searchView.setContentDescription(mMarketSearchMessage);
                    searchView.setGravity(mApps.hasNoFilteredResults() ? Gravity.CENTER :
                            Gravity.START | Gravity.CENTER_VERTICAL);
                    searchView.setText(mMarketSearchMessage);
                } else {
                    searchView.setVisibility(View.GONE);
                }
                break;
            case CUSTOM_PREDICTED_APPS_HEADER_VIEW_TYPE: {
                TextView title = (TextView) holder.mContent.findViewById(R.id.title);
                title.setTextColor(mAllAppsTextColor);
                FastScrollFocusApplicator.setFastScrollDimmed(holder.mContent,
                        shouldDimPosition(position), !mIconsDimmed);
                FastScrollFocusApplicator.setFastScrollFocused(holder.mContent, false, !mIconsDimmed);

                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) holder.mContent.getLayoutParams();
                mCustomPredictedAppsHeaderHeight = holder.mContent.getHeight() +
                        lp.topMargin + lp.bottomMargin;
                break;
            }
            case CUSTOM_PREDICTED_APPS_FOOTER_VIEW_TYPE:
                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) holder.mContent.getLayoutParams();
                mCustomPredictedAppsFooterHeight = holder.mContent.getHeight() +
                        lp.topMargin + lp.bottomMargin;
        }
    }

    private boolean shouldDimPosition(int position) {
        if (mFocusedSection != null && mIconsDimmed) {
            if (position >= mFocusedSection.firstAppItem.position &&
                    position < mFocusedSection.firstAppItem.position +
                            mFocusedSection.numApps) {
                return false;
            }
        }
        return mIconsDimmed;
    }

    public int getCustomPredictedAppsOffset(int rowIndex) {
        int offset = mCustomPredictedAppsHeaderHeight;
        if (rowIndex > 0) offset += mCustomPredictedAppsFooterHeight;
        return offset;
    }

    @Override
    public int getItemCount() {
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }

    public void setIconsDimmed(boolean iconsDimmed) {
        if (mIconsDimmed != iconsDimmed) {
            mIconsDimmed = iconsDimmed;
            notifyDataSetChanged();
        }
    }

    public void setFocusedSection(
            AlphabeticalAppsList.SectionInfo focusedSection) {
        mFocusedSection = focusedSection;
    }

    public void setGridTheme(int gridTheme) {
        mGridTheme = gridTheme;
        int sectionTextColorId = mGridTheme == AllAppsContainerView.GRID_THEME_DARK ?
                R.color.all_apps_grid_section_text_color_dark :
                R.color.all_apps_grid_section_text_color;
        mSectionTextPaint.setColor(mLauncher.getColor(sectionTextColorId));

        mAllAppsTextColor = mGridTheme == AllAppsContainerView.GRID_THEME_DARK ?
                mLauncher.getColor(R.color.quantum_panel_text_color_dark) :
                mLauncher.getColor(R.color.quantum_panel_text_color);

        int mPredictedAppsDividerColorId = mGridTheme == AllAppsContainerView.GRID_THEME_DARK ?
                R.color.drawer_divider_dark : R.color.drawer_divider_light;
        mPredictedAppsDividerPaint.setColor(mLauncher.getColor(mPredictedAppsDividerColorId));
    }

    /**
     * Creates a new market search intent.
     */
    private Intent createMarketSearchIntent(String query) {
        Uri marketSearchUri = Uri.parse("market://search")
                .buildUpon()
                .appendQueryParameter("q", query)
                .build();
        Intent marketSearchIntent = new Intent(Intent.ACTION_VIEW);
        marketSearchIntent.setData(marketSearchUri);
        return marketSearchIntent;
    }
}
