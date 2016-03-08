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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.BaseRecyclerView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.BaseRecyclerViewFastScrollBar.FastScrollFocusApplicator;
import com.android.launcher3.R;
import com.android.launcher3.Stats;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.List;

/**
 * A RecyclerView with custom fast scroll support for the all apps view.
 */
public class AllAppsRecyclerView extends BaseRecyclerView
        implements Stats.LaunchSourceProvider {

    private static final int FAST_SCROLL_MODE_JUMP_TO_FIRST_ICON = 0;
    private static final int FAST_SCROLL_MODE_FREE_SCROLL = 1;

    private static final int FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_ROW = 0;
    private static final int FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_SECTIONS = 1;

    private AlphabeticalAppsList mApps;
    private int mNumAppsPerRow;
    private int mSectionStrategy = AllAppsContainerView.SECTION_STRATEGY_RAGGED;

    @Thunk ArrayList<View> mLastFastScrollFocusedViews = new ArrayList();
    @Thunk int mPrevFastScrollFocusedPosition;
    @Thunk AlphabeticalAppsList.SectionInfo mPrevFastScrollFocusedSection;
    @Thunk int mFastScrollFrameIndex;
    @Thunk final int[] mFastScrollFrames = new int[10];

    private final int mFastScrollMode = FAST_SCROLL_MODE_JUMP_TO_FIRST_ICON;
    private final int mScrollBarMode = FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_ROW;

    private ScrollPositionState mScrollPosState = new ScrollPositionState();
    private boolean mFastScrollDragging;

    private AllAppsBackgroundDrawable mEmptySearchBackground;
    private int mEmptySearchBackgroundTopOffset;

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
        if (mUseScrollbar) {
            mScrollbar.setDetachThumbOnFastScroll();
        }
        mEmptySearchBackgroundTopOffset = res.getDimensionPixelSize(
                R.dimen.all_apps_empty_search_bg_top_offset);

        addOnScrollListener(new FocusScrollListener());
    }

    private class FocusScrollListener extends OnScrollListener {
        public FocusScrollListener() { }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            switch (newState) {
                case SCROLL_STATE_IDLE:
                    // Don't change anything if we've stopped touching the scroll bar.
                    if (mFastScrollDragging) {
                        // Animation completed, set the fast scroll state on the target views
                        setSectionFastScrollFocused(mPrevFastScrollFocusedPosition);
                        setSectionFastScrollDimmed(mPrevFastScrollFocusedPosition, false, true);
                    }
            }
        }
    }

    /**
     * Sets the list of apps in this view, used to determine the fastscroll position.
     */
    public void setApps(AlphabeticalAppsList apps) {
        mApps = apps;
    }

    /**
     * Sets the number of apps per row in this recycler view.
     */
    public void setNumAppsPerRow(DeviceProfile grid, int numAppsPerRow) {
        mNumAppsPerRow = numAppsPerRow;

        RecyclerView.RecycledViewPool pool = getRecycledViewPool();
        int approxRows = (int) Math.ceil(grid.availableHeightPx / grid.allAppsIconSizePx);
        pool.setMaxRecycledViews(AllAppsGridAdapter.EMPTY_SEARCH_VIEW_TYPE, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.SEARCH_MARKET_DIVIDER_VIEW_TYPE, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.SEARCH_MARKET_VIEW_TYPE, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.ICON_VIEW_TYPE, approxRows * mNumAppsPerRow);
        pool.setMaxRecycledViews(AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE, mNumAppsPerRow);
        pool.setMaxRecycledViews(AllAppsGridAdapter.SECTION_BREAK_VIEW_TYPE, approxRows);
        pool.setMaxRecycledViews(AllAppsGridAdapter.CUSTOM_PREDICTED_APPS_HEADER_VIEW_TYPE, 1);
        pool.setMaxRecycledViews(AllAppsGridAdapter.CUSTOM_PREDICTED_APPS_FOOTER_VIEW_TYPE, 1);
    }

    public void setSectionStrategy(int sectionStrategy) {
        mSectionStrategy = sectionStrategy;
    }

    /**
     * Scrolls this recycler view to the top.
     */
    public void scrollToTop() {
        if (mUseScrollbar) {
            // Ensure we reattach the scrollbar if it was previously detached while fast-scrolling
            if (mScrollbar.isThumbDetached()) {
                mScrollbar.reattachThumbToScroll();
            }
        }
        scrollToPosition(0);
    }

    /**
     * We need to override the draw to ensure that we don't draw the overscroll effect beyond the
     * background bounds.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.clipRect(mBackgroundPadding.left, mBackgroundPadding.top,
                getWidth() - mBackgroundPadding.right,
                getHeight() - mBackgroundPadding.bottom);
        super.dispatchDraw(canvas);
    }

    @Override
    public void onDraw(Canvas c) {
        // Draw the background
        if (mEmptySearchBackground != null && mEmptySearchBackground.getAlpha() > 0) {
            c.clipRect(mBackgroundPadding.left, mBackgroundPadding.top,
                    getWidth() - mBackgroundPadding.right,
                    getHeight() - mBackgroundPadding.bottom);

            mEmptySearchBackground.draw(c);
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
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Bind event handlers
        addOnItemTouchListener(this);
    }

    @Override
    public void fillInLaunchSourceData(Bundle sourceData) {
        sourceData.putString(Stats.SOURCE_EXTRA_CONTAINER, Stats.CONTAINER_ALL_APPS);
        if (mApps.hasFilter()) {
            sourceData.putString(Stats.SOURCE_EXTRA_SUB_CONTAINER,
                    Stats.SUB_CONTAINER_ALL_APPS_SEARCH);
        } else {
            sourceData.putString(Stats.SOURCE_EXTRA_SUB_CONTAINER,
                    Stats.SUB_CONTAINER_ALL_APPS_A_Z);
        }
    }

    public void onSearchResultsChanged() {
        // Always scroll the view to the top so the user can see the changed results
        scrollToTop();

        if (mApps.hasNoFilteredResults()) {
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

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    @Override
    public String scrollToPositionAtProgress(float touchFraction) {
        int rowCount = mApps.getNumAppRows();
        if (rowCount == 0) {
            return "";
        }

        // Stop the scroller if it is scrolling
        stopScroll();

        // Find the fastscroll section that maps to this touch fraction
        List<AlphabeticalAppsList.FastScrollSectionInfo> fastScrollSections =
                mApps.getFastScrollerSections();
        AlphabeticalAppsList.FastScrollSectionInfo lastInfo = fastScrollSections.get(0);
        if (mScrollBarMode == FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_ROW) {
            for (int i = 1; i < fastScrollSections.size(); i++) {
                AlphabeticalAppsList.FastScrollSectionInfo info = fastScrollSections.get(i);
                if (info.touchFraction > touchFraction) {
                    break;
                }
                lastInfo = info;
            }
        } else if (mScrollBarMode == FAST_SCROLL_BAR_MODE_DISTRIBUTE_BY_SECTIONS){
            lastInfo = fastScrollSections.get((int) (touchFraction * (fastScrollSections.size() - 1)));
        } else {
            throw new RuntimeException("Unexpected scroll bar mode");
        }

        // Reset the last focused section
        if (mPrevFastScrollFocusedSection != lastInfo.sectionInfo) {
            setSectionFastScrollDimmed(mPrevFastScrollFocusedPosition, true, true);
            clearSectionFocusedItems();
        }

        mPrevFastScrollFocusedPosition = lastInfo.fastScrollToItem.position;
        mPrevFastScrollFocusedSection = lastInfo.sectionInfo;

        getCurScrollState(mScrollPosState);
        if (mFastScrollMode == FAST_SCROLL_MODE_JUMP_TO_FIRST_ICON) {
            smoothSnapToPosition(mPrevFastScrollFocusedPosition, mScrollPosState);

            setSectionFastScrollDimmed(mPrevFastScrollFocusedPosition, false, true);
            setSectionFastScrollFocused(mPrevFastScrollFocusedPosition);
        } else if (mFastScrollMode == FAST_SCROLL_MODE_FREE_SCROLL) {
            // Map the touch position back to the scroll of the recycler view
            int availableScrollHeight = getAvailableScrollHeight(rowCount, mScrollPosState.rowHeight);
            LinearLayoutManager layoutManager = (LinearLayoutManager) getLayoutManager();
            layoutManager.scrollToPositionWithOffset(0, (int) -(availableScrollHeight * touchFraction));

            setSectionFastScrollFocused(mPrevFastScrollFocusedPosition);
        } else {
            throw new RuntimeException("Unexpected fast scroll mode");
        }
        return lastInfo.sectionName;
    }

    @Override
    public void onFastScrollCompleted() {
        super.onFastScrollCompleted();

        // Reset and clean up the last focused views
        clearSectionFocusedItems();
        mPrevFastScrollFocusedPosition = -1;
        mPrevFastScrollFocusedSection = null;
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    @Override
    public void onUpdateScrollbar(int dy) {
        if (!mUseScrollbar) {
            return;
        }
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();

        // Skip early if there are no items or we haven't been measured
        if (items.isEmpty() || mNumAppsPerRow == 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        // Find the index and height of the first visible row (all rows have the same height)
        int rowCount = mApps.getNumAppRows();
        getCurScrollState(mScrollPosState);
        if (mScrollPosState.rowIndex < 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        // Only show the scrollbar if there is height to be scrolled
        int availableScrollBarHeight = getAvailableScrollBarHeight();
        int availableScrollHeight = getAvailableScrollHeight(mApps.getNumAppRows(),
                mScrollPosState.rowHeight);
        if (availableScrollHeight <= 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        // Calculate the current scroll position, the scrollY of the recycler view accounts for the
        // view padding, while the scrollBarY is drawn right up to the background padding (ignoring
        // padding)
        int scrollY = getCurrentScroll(mScrollPosState);
        int scrollBarY = mBackgroundPadding.top +
                (int) (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

        if (mScrollbar.isThumbDetached()) {
            int scrollBarX;
            if (Utilities.isRtl(getResources())) {
                scrollBarX = mBackgroundPadding.left;
            } else {
                scrollBarX = getWidth() - mBackgroundPadding.right - mScrollbar.getThumbWidth();
            }

            if (mScrollbar.isDraggingThumb()) {
                // If the thumb is detached, then just update the thumb to the current
                // touch position
                mScrollbar.setThumbOffset(scrollBarX, (int) mScrollbar.getLastTouchY());
            } else {
                int thumbScrollY = mScrollbar.getThumbOffset().y;
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
                    mScrollbar.setThumbOffset(scrollBarX, thumbScrollY);
                    if (scrollBarY == thumbScrollY) {
                        mScrollbar.reattachThumbToScroll();
                    }
                } else {
                    // User is scrolling in an opposite direction to the direction that the thumb
                    // needs to catch up to the scroll position.  Do nothing except for updating
                    // the scroll bar x to match the thumb width.
                    mScrollbar.setThumbOffset(scrollBarX, thumbScrollY);
                }
            }
        } else {
            synchronizeScrollBarThumbOffsetToViewScroll(mScrollPosState, rowCount);
        }
    }

    @Override
    public String scrollToSection(String sectionName) {
        List<AlphabeticalAppsList.FastScrollSectionInfo> scrollSectionInfos =
                mApps.getFastScrollerSections();
        if (scrollSectionInfos != null) {
            for (int i = 0; i < scrollSectionInfos.size(); i++) {
                AlphabeticalAppsList.FastScrollSectionInfo info = scrollSectionInfos.get(i);
                if (info.sectionName.equals(sectionName))  {
                    scrollToPositionAtProgress(info.touchFraction);
                    return info.sectionName;
                }
            }
        }
        return null;
    }

    @Override
    public String[] getSectionNames() {
        List<AlphabeticalAppsList.FastScrollSectionInfo> scrollSectionInfos =
                mApps.getFastScrollerSections();
        if (scrollSectionInfos != null) {
            String[] sectionNames = new String[scrollSectionInfos.size()];
            for (int i = 0; i < scrollSectionInfos.size(); i++) {
                AlphabeticalAppsList.FastScrollSectionInfo info = scrollSectionInfos.get(i);
                sectionNames[i] = info.sectionName;
            }

        return sectionNames;
        }
        return new String[0];
    }

    private void setSectionFastScrollFocused(int position) {
        if (mPrevFastScrollFocusedSection != null) {
            ((AllAppsGridAdapter)getAdapter()).setFocusedSection(mPrevFastScrollFocusedSection);
            int size = mPrevFastScrollFocusedSection.numApps +
                    mPrevFastScrollFocusedSection.numOtherViews;
            for (int i = 0; i < size; i++) {
                int sectionPosition = position+i;
                final ViewHolder vh = findViewHolderForAdapterPosition(sectionPosition);
                if (vh != null) {
                    FastScrollFocusApplicator.setFastScrollFocused(vh.itemView, true, true);
                    mLastFastScrollFocusedViews.add(vh.itemView);
                }
            }
        }
    }

    @Override
    public void setPreviousSectionFastScrollFocused() {
        setSectionFastScrollFocused(mPrevFastScrollFocusedPosition);
    }

    private void setSectionFastScrollDimmed(int position, boolean dimmed, boolean animate) {
        if (mPrevFastScrollFocusedSection != null) {
            int size = mPrevFastScrollFocusedSection.numApps +
                    mPrevFastScrollFocusedSection.numOtherViews;
            for (int i = 0; i < size; i++) {
                int sectionPosition = position+i;
                final ViewHolder vh = findViewHolderForAdapterPosition(sectionPosition);
                if (vh != null) {
                    FastScrollFocusApplicator.setFastScrollDimmed(vh.itemView, dimmed, animate);
                }
            }
        }
    }

    private void clearSectionFocusedItems() {
        final int N = mLastFastScrollFocusedViews.size();
        for (int i = 0; i < N; i++) {
            View view = mLastFastScrollFocusedViews.get(i);
            FastScrollFocusApplicator.setFastScrollFocused(view, false, true);
        }
        mLastFastScrollFocusedViews.clear();
    }

    @Override
    public void setFastScrollDragging(boolean dragging) {
        ((AllAppsGridAdapter) getAdapter()).setIconsDimmed(dragging);
        mFastScrollDragging = dragging;
    }

    /**
     * This runnable runs a single frame of the smooth scroll animation and posts the next frame
     * if necessary.
     */
    @Thunk Runnable mSmoothSnapNextFrameRunnable = new Runnable() {
        @Override
        public void run() {
            if (mFastScrollFrameIndex < mFastScrollFrames.length) {
                setSectionFastScrollDimmed(mPrevFastScrollFocusedPosition, false, true);
                scrollBy(0, mFastScrollFrames[mFastScrollFrameIndex]);
                mFastScrollFrameIndex++;
                postOnAnimation(mSmoothSnapNextFrameRunnable);
            } else {
                setSectionFastScrollDimmed(mPrevFastScrollFocusedPosition, false, false);
                setSectionFastScrollFocused(mPrevFastScrollFocusedPosition);
            }
        }
    };

    /**
     * Smoothly snaps to a given position.  We do this manually by calculating the keyframes
     * ourselves and animating the scroll on the recycler view.
     */
    private void smoothSnapToPosition(final int position, ScrollPositionState scrollPosState) {
        removeCallbacks(mSmoothSnapNextFrameRunnable);

        // Calculate the full animation from the current scroll position to the final scroll
        // position, and then run the animation for the duration.
        int curScrollY = getCurrentScroll(scrollPosState);
        int newScrollY = getScrollAtPosition(position, scrollPosState.rowHeight);
        int numFrames = mFastScrollFrames.length;
        for (int i = 0; i < numFrames; i++) {
            // TODO(winsonc): We can interpolate this as well.
            mFastScrollFrames[i] = (newScrollY - curScrollY) / numFrames;
        }
        mFastScrollFrameIndex = 0;
        postOnAnimation(mSmoothSnapNextFrameRunnable);
    }

    /**
     * Returns the current scroll state of the apps rows.
     */
    protected void getCurScrollState(ScrollPositionState stateOut) {
        stateOut.rowIndex = -1;
        stateOut.rowTopOffset = -1;
        stateOut.rowHeight = -1;

        // Return early if there are no items or we haven't been measured
        List<AlphabeticalAppsList.AdapterItem> items = mApps.getAdapterItems();
        if (items.isEmpty() || mNumAppsPerRow == 0) {
            return;
        }

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            int position = getChildPosition(child);
            if (position != NO_POSITION) {
                AlphabeticalAppsList.AdapterItem item = items.get(position);
                if (item.viewType == AllAppsGridAdapter.ICON_VIEW_TYPE ||
                        item.viewType == AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE) {
                    stateOut.rowIndex = item.rowIndex;
                    stateOut.rowTopOffset = getLayoutManager().getDecoratedTop(child) -
                            getAdditionalScrollHeight(stateOut.rowIndex);
                    stateOut.rowHeight = child.getHeight();
                    break;
                }
            }
        }
    }

    @Override
    protected int getAvailableScrollHeight(int rowCount, int rowHeight) {
        return super.getAvailableScrollHeight(rowCount, rowHeight) +
                getAdditionalScrollHeight(mApps.getAdapterItems().size());
    }

    private int getAdditionalScrollHeight(int rowIndex) {
        return ((AllAppsGridAdapter) getAdapter()).getCustomPredictedAppsOffset(rowIndex);
    }

    /**
     * Returns the scrollY for the given position in the adapter.
     */
    private int getScrollAtPosition(int position, int rowHeight) {
        AlphabeticalAppsList.AdapterItem item = mApps.getAdapterItems().get(position);
        if (item.viewType == AllAppsGridAdapter.ICON_VIEW_TYPE ||
                item.viewType == AllAppsGridAdapter.PREDICTION_ICON_VIEW_TYPE) {
            int offset = item.rowIndex > 0 ? getPaddingTop() : 0;
            offset += ((AllAppsGridAdapter) getAdapter()).
                    getCustomPredictedAppsOffset(item.rowIndex);
            return offset + item.rowIndex * rowHeight;
        } else {
            return 0;
        }
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
}
