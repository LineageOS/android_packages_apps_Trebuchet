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

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.UNSPECIFIED;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_VERTICAL_SWIPE_BEGIN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_VERTICAL_SWIPE_END;
import static com.android.launcher3.util.LogConfig.SEARCH_LOGGING;
import static com.android.launcher3.util.UiThreadHelper.hideKeyboardAsync;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.RecyclerViewFastScroller;

import java.util.ArrayList;
import java.util.List;

/**
 * A RecyclerView with custom fast scroll support for the all apps view.
 */
public class AllAppsRecyclerView extends BaseRecyclerView {
    private static final String TAG = "AllAppsContainerView";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_LATENCY = Utilities.isPropertyEnabled(SEARCH_LOGGING);

    private AlphabeticalAppsList mApps;
    private final int mNumAppsPerRow;

    // The specific view heights that we use to calculate scroll
    private final SparseIntArray mViewHeights = new SparseIntArray();
    private final SparseIntArray mCachedScrollPositions = new SparseIntArray();
    private final AllAppsFastScrollHelper mFastScrollHelper;


    private final AdapterDataObserver mObserver = new RecyclerView.AdapterDataObserver() {
        public void onChanged() {
            mCachedScrollPositions.clear();
        }
    };

    // The empty-search result background
    private AllAppsBackgroundDrawable mEmptySearchBackground;
    private int mEmptySearchBackgroundTopOffset;

    private ArrayList<View> mAutoSizedOverlays = new ArrayList<>();

    public AllAppsRecyclerView(Context context) {
        this(context, null);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AllAppsRecyclerView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);
        Resources res = getResources();
        mEmptySearchBackgroundTopOffset = res.getDimensionPixelSize(
                R.dimen.all_apps_empty_search_bg_top_offset);
        mNumAppsPerRow = LauncherAppState.getIDP(context).numColumns;
        mFastScrollHelper = new AllAppsFastScrollHelper(this);
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList apps) {
        mApps = apps;
    }

    public AlphabeticalAppsList getApps() {
        return mApps;
    }

    private void updatePoolSize() {
        DeviceProfile grid = BaseDraggingActivity.fromContext(getContext()).getDeviceProfile();
        RecyclerView.RecycledViewPool pool = getRecycledViewPool();
        int approxRows = (int) Math.ceil(grid.availableHeightPx / grid.allAppsIconSizePx);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_EMPTY_SEARCH, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_ALL_APPS_DIVIDER, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_SEARCH_MARKET, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.VIEW_TYPE_ICON, approxRows
                * (mNumAppsPerRow + 1));

        mViewHeights.clear();
        mViewHeights.put(AllAppsGridAdapter.VIEW_TYPE_ICON, grid.allAppsCellHeightPx);
    }


    @Override
    public void onDraw(Canvas c) {
        // Draw the background
        if (mEmptySearchBackground != null && mEmptySearchBackground.getAlpha() > 0) {
            mEmptySearchBackground.draw(c);
        }
        if (DEBUG) {
            Log.d(TAG, "onDraw at = " + System.currentTimeMillis());
        }
        if (DEBUG_LATENCY) {
            Log.d(SEARCH_LOGGING,
                    "-- Recycle view onDraw, time stamp = " + System.currentTimeMillis());
        }
        super.onDraw(c);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mEmptySearchBackground || super.verifyDrawable(who);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateEmptySearchBackgroundBounds();
        updatePoolSize();
        for (int i = 0; i < mAutoSizedOverlays.size(); i++) {
            View overlay = mAutoSizedOverlays.get(i);
            overlay.measure(makeMeasureSpec(w, EXACTLY), makeMeasureSpec(w, EXACTLY));
            overlay.layout(0, 0, w, h);
        }
    }

    /**
     * Adds an overlay that automatically rescales with the recyclerview.
     */
    public void addAutoSizedOverlay(View overlay) {
        mAutoSizedOverlays.add(overlay);
        getOverlay().add(overlay);
        onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
    }

    /**
     * Clears auto scaling overlay views added by #addAutoSizedOverlay
     */
    public void clearAutoSizedOverlays() {
        for (View v : mAutoSizedOverlays) {
            getOverlay().remove(v);
        }
        mAutoSizedOverlays.clear();
    }

    public void onSearchResultsChanged() {
        // Always scroll the view to the top so the user can see the changed results
        scrollToTop();

        if (mApps.hasNoFilteredResults() && !FeatureFlags.ENABLE_DEVICE_SEARCH.get()) {
            if (mEmptySearchBackground == null) {
                mEmptySearchBackground = new AllAppsBackgroundDrawable(getContext());
                mEmptySearchBackground.setAlpha(0);
                mEmptySearchBackground.setCallback(this);
                updateEmptySearchBackgroundBounds();
            }
            mEmptySearchBackground.animateBgAlpha(1f, 150);
        } else if (mEmptySearchBackground != null) {
            // For the time being, we just immediately hide the background to ensure that it does
            // not overlap with the results
            mEmptySearchBackground.setBgAlpha(0f);
        }
    }

    @Override
    public void onScrollStateChanged(int state) {
        super.onScrollStateChanged(state);

        StatsLogManager mgr = BaseDraggingActivity.fromContext(getContext()).getStatsLogManager();
        switch (state) {
            case SCROLL_STATE_DRAGGING:
                requestFocus();
                mgr.logger().sendToInteractionJankMonitor(
                        LAUNCHER_ALLAPPS_VERTICAL_SWIPE_BEGIN, this);
                break;
            case SCROLL_STATE_IDLE:
                mgr.logger().sendToInteractionJankMonitor(
                        LAUNCHER_ALLAPPS_VERTICAL_SWIPE_END, this);
                break;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        boolean result = super.onInterceptTouchEvent(e);
        if (!result && e.getAction() == MotionEvent.ACTION_DOWN
                && mEmptySearchBackground != null && mEmptySearchBackground.getAlpha() > 0) {
            mEmptySearchBackground.setHotspot(e.getX(), e.getY());
        }
        hideKeyboardAsync(ActivityContext.lookupContext(getContext()),
                getApplicationWindowToken());
        return result;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public String scrollToPositionAtProgress(float touchFraction) {
        int rowCount = mApps.getNumAppRows();
        if (rowCount == 0) {
            return "";
        }

        // Find the fastscroll section that maps to this touch fraction
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        AlphabeticalAppsList.FastScrollSectionInfo lastInfo = fastScrollSections.get(0);
        for (int i = 1; i < fastScrollSections.size(); i++) {
            AlphabeticalAppsList.FastScrollSectionInfo info = fastScrollSections.get(i);
            if (info.touchFraction > touchFraction) {
                break;
            }
            lastInfo = info;
        }

        mFastScrollHelper.smoothScrollToSection(lastInfo);
        return lastInfo.sectionName;
    }

    @Override
    public void onFastScrollCompleted() {
        super.onFastScrollCompleted();
        mFastScrollHelper.onFastScrollCompleted();
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if (getAdapter() != null) {
            getAdapter().unregisterAdapterDataObserver(mObserver);
        }
        super.setAdapter(adapter);
        if (adapter != null) {
            adapter.registerAdapterDataObserver(mObserver);
        }
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        // No bottom fading edge.
        return 0;
    }

    @Override
    protected boolean isPaddingOffsetRequired() {
        return true;
    }

    @Override
    protected int getTopPaddingOffset() {
        return -getPaddingTop();
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar(int dy) {
        if (mApps == null) {
            return;
        }
        List<AllAppsGridAdapter.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items or we haven't been measured
        if (items.isEmpty() || mNumAppsPerRow == 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // Skip early if, there no child laid out in the container.
        int scrollY = getCurrentScrollY();
        if (scrollY < 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        // Only show the scrollbar if there is height to be scrolled
        int availableScrollBarHeight = getAvailableScrollBarHeight();
        int availableScrollHeight = getAvailableScrollHeight();
        if (availableScrollHeight <= 0) {
            mScrollbar.setThumbOffsetY(-1);
            return;
        }

        if (mScrollbar.isThumbDetached()) {
            if (!mScrollbar.isDraggingThumb()) {
                // Calculate the current scroll position, the scrollY of the recycler view accounts
                // for the view padding, while the scrollBarY is drawn right up to the background
                // padding (ignoring padding)
                int scrollBarY = (int)
                        (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

                int thumbScrollY = mScrollbar.getThumbOffsetY();
                int diffScrollY = scrollBarY - thumbScrollY;
                if (diffScrollY * dy > 0f) {
                    // User is scrolling in the same direction the thumb needs to catch up to the
                    // current scroll position.  We do this by mapping the difference in movement
                    // from the original scroll bar position to the difference in movement necessary
                    // in the detached thumb position to ensure that both speed towards the same
                    // position at either end of the list.
                    if (dy < 0) {
                        int offset = (int) ((dy * thumbScrollY) / (float) scrollBarY);
                        thumbScrollY += Math.max(offset, diffScrollY);
                    } else {
                        int offset = (int) ((dy * (availableScrollBarHeight - thumbScrollY)) /
                                (float) (availableScrollBarHeight - scrollBarY));
                        thumbScrollY += Math.min(offset, diffScrollY);
                    }
                    thumbScrollY = Math.max(0, Math.min(availableScrollBarHeight, thumbScrollY));
                    mScrollbar.setThumbOffsetY(thumbScrollY);
                    if (scrollBarY == thumbScrollY) {
                        mScrollbar.reattachThumbToScroll();
                    }
                } else {
                    // User is scrolling in an opposite direction to the direction that the thumb
                    // needs to catch up to the scroll position.  Do nothing except for updating
                    // the scroll bar x to match the thumb width.
                    mScrollbar.setThumbOffsetY(thumbScrollY);
                }
            }
        } else {
            synchronizeScrollBarThumbOffsetToViewScroll(scrollY, availableScrollHeight);
        }
    }

    @Override
    public boolean supportsFastScrolling() {
        // Only allow fast scrolling when the user is not searching, since the results are not
        // grouped in a meaningful order
        return !mApps.hasFilter();
    }

    @Override
    public int getCurrentScrollY() {
        // Return early if there are no items or we haven't been measured
        List<AllAppsGridAdapter.AdapterItem> items = mApps.getAdapterItems();
        if (items.isEmpty() || mNumAppsPerRow == 0 || getChildCount() == 0) {
            return -1;
        }

        // Calculate the y and offset for the item
        View child = getChildAt(0);
        int position = getChildPosition(child);
        if (position == NO_POSITION) {
            return -1;
        }
        return getPaddingTop() +
                getCurrentScrollY(position, getLayoutManager().getDecoratedTop(child));
    }

    public int getCurrentScrollY(int position, int offset) {
        List<AllAppsGridAdapter.AdapterItem> items = mApps.getAdapterItems();
        AllAppsGridAdapter.AdapterItem posItem = position < items.size()
                ? items.get(position) : null;
        int y = mCachedScrollPositions.get(position, -1);
        if (y < 0) {
            y = 0;
            for (int i = 0; i < position; i++) {
                AllAppsGridAdapter.AdapterItem item = items.get(i);
                if (AllAppsGridAdapter.isIconViewType(item.viewType)) {
                    // Break once we reach the desired row
                    if (posItem != null && posItem.viewType == item.viewType &&
                            posItem.rowIndex == item.rowIndex) {
                        break;
                    }
                    // Otherwise, only account for the first icon in the row since they are the same
                    // size within a row
                    if (item.rowAppIndex == 0) {
                        y += mViewHeights.get(item.viewType, 0);
                    }
                } else {
                    // Rest of the views span the full width
                    int elHeight = mViewHeights.get(item.viewType);
                    if (elHeight == 0) {
                        ViewHolder holder = findViewHolderForAdapterPosition(i);
                        if (holder == null) {
                            holder = getAdapter().createViewHolder(this, item.viewType);
                            getAdapter().onBindViewHolder(holder, i);
                            holder.itemView.measure(UNSPECIFIED, UNSPECIFIED);
                            elHeight = holder.itemView.getMeasuredHeight();

                            getRecycledViewPool().putRecycledView(holder);
                        } else {
                            elHeight = holder.itemView.getMeasuredHeight();
                        }
                    }
                    y += elHeight;
                }
            }
            mCachedScrollPositions.put(position, y);
        }
        return y - offset;
    }

    /**
     * Returns the available scroll height:
     * AvailableScrollHeight = Total height of the all items - last page height
     */
    @Override
    protected int getAvailableScrollHeight() {
        return getPaddingTop() + getCurrentScrollY(getAdapter().getItemCount(), 0)
                - getHeight() + getPaddingBottom();
    }

    public int getScrollBarTop() {
        return getResources().getDimensionPixelOffset(R.dimen.all_apps_header_top_padding);
    }

    public RecyclerViewFastScroller getScrollbar() {
        return mScrollbar;
    }

    /**
     * Updates the bounds of the empty search background.
     */
    private void updateEmptySearchBackgroundBounds() {
        if (mEmptySearchBackground == null) {
            return;
        }

        // Center the empty search background on this new view bounds
        int x = (getMeasuredWidth() - mEmptySearchBackground.getIntrinsicWidth()) / 2;
        int y = mEmptySearchBackgroundTopOffset;
        mEmptySearchBackground.setBounds(x, y,
                x + mEmptySearchBackground.getIntrinsicWidth(),
                y + mEmptySearchBackground.getIntrinsicHeight());
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /**
     * Returns distance between left and right app icons
     */
    public int getTabWidth() {
        DeviceProfile grid = BaseDraggingActivity.fromContext(getContext()).getDeviceProfile();
        int totalWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int iconPadding = totalWidth / grid.numShownAllAppsColumns - grid.allAppsIconSizePx;
        return totalWidth - iconPadding - grid.allAppsIconDrawablePaddingPx;
    }
}
