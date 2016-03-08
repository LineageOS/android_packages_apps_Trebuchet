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

package com.android.launcher3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.android.launcher3.util.Thunk;


/**
 * A base {@link RecyclerView}, which does the following:
 * <ul>
 *   <li> NOT intercept a touch unless the scrolling velocity is below a predefined threshold.
 *   <li> Enable fast scroller.
 * </ul>
 */
public abstract class BaseRecyclerView extends RecyclerView
        implements RecyclerView.OnItemTouchListener {

    private static final int SCROLL_DELTA_THRESHOLD_DP = 4;

    /** Keeps the last known scrolling delta/velocity along y-axis. */
    @Thunk int mDy = 0;
    private float mDeltaThreshold;

    /**
     * The current scroll state of the recycler view.  We use this in onUpdateScrollbar()
     * and scrollToPositionAtProgress() to determine the scroll position of the recycler view so
     * that we can calculate what the scroll bar looks like, and where to jump to from the fast
     * scroller.
     */
    public static class ScrollPositionState {
        // The index of the first visible row
        public int rowIndex;
        // The offset of the first visible row
        public int rowTopOffset;
        // The height of a given row (they are currently all the same height)
        public int rowHeight;
    }

    protected BaseRecyclerViewFastScrollBar mScrollbar;
    protected boolean mUseScrollbar = false;

    private int mDownX;
    private int mDownY;
    private int mLastY;
    protected Rect mBackgroundPadding = new Rect();

    public BaseRecyclerView(Context context) {
        this(context, null);
    }

    public BaseRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDeltaThreshold = getResources().getDisplayMetrics().density * SCROLL_DELTA_THRESHOLD_DP;

        ScrollListener listener = new ScrollListener();
        addOnScrollListener(listener);
    }

    private class ScrollListener extends OnScrollListener {
        public ScrollListener() {
            // Do nothing
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            mDy = dy;

            // TODO(winsonc): If we want to animate the section heads while scrolling, we can
            //                initiate that here if the recycler view scroll state is not
            //                RecyclerView.SCROLL_STATE_IDLE.

            if (mUseScrollbar) {
                onUpdateScrollbar(dy);
            }
        }
    }

    public void reset() {
        if (mUseScrollbar) {
            mScrollbar.reattachThumbToScroll();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        addOnItemTouchListener(this);
    }

    /**
     * We intercept the touch handling only to support fast scrolling when initiated from the
     * scroll bar.  Otherwise, we fall back to the default RecyclerView touch handling.
     */
    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent ev) {
        return handleTouchEvent(ev);
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent ev) {
        handleTouchEvent(ev);
    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    private boolean handleTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Keep track of the down positions
                mDownX = x;
                mDownY = mLastY = y;
                if (shouldStopScroll(ev)) {
                    stopScroll();
                }
                if (mScrollbar != null) {
                    mScrollbar.handleTouchEvent(ev, mDownX, mDownY, mLastY);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                mLastY = y;
                if (mScrollbar != null) {
                    mScrollbar.handleTouchEvent(ev, mDownX, mDownY, mLastY);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onFastScrollCompleted();
                if (mScrollbar != null) {
                    mScrollbar.handleTouchEvent(ev, mDownX, mDownY, mLastY);
                }
                break;
        }
        if (mUseScrollbar) {
            return mScrollbar.isDraggingThumb();
        }
        return false;
    }

    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // DO NOT REMOVE, NEEDED IMPLEMENTATION FOR M BUILDS
    }

    /**
     * Returns whether this {@link MotionEvent} should trigger the scroll to be stopped.
     */
    protected boolean shouldStopScroll(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if ((Math.abs(mDy) < mDeltaThreshold &&
                    getScrollState() != RecyclerView.SCROLL_STATE_IDLE)) {
                // now the touch events are being passed to the {@link WidgetCell} until the
                // touch sequence goes over the touch slop.
                return true;
            }
        }
        return false;
    }

    public void updateBackgroundPadding(Rect padding) {
        mBackgroundPadding.set(padding);
    }

    public Rect getBackgroundPadding() {
        return mBackgroundPadding;
    }

    /**
     * Returns the scroll bar width when the user is scrolling.
     */
    public int getMaxScrollbarWidth() {
        if (mUseScrollbar) {
            return mScrollbar.getThumbMaxWidth();
        }
        return 0;
    }

    /**
     * Returns the available scroll height:
     *   AvailableScrollHeight = Total height of the all items - last page height
     *
     * This assumes that all rows are the same height.
     */
    protected int getAvailableScrollHeight(int rowCount, int rowHeight) {
        int visibleHeight = getHeight() - mBackgroundPadding.top - mBackgroundPadding.bottom;
        int scrollHeight = getPaddingTop() + rowCount * rowHeight + getPaddingBottom();
        int availableScrollHeight = scrollHeight - visibleHeight;
        return availableScrollHeight;
    }

    /**
     * Returns the available scroll bar height:
     *   AvailableScrollBarHeight = Total height of the visible view - thumb height
     */
    protected int getAvailableScrollBarHeight() {
        if (mUseScrollbar) {
            int visibleHeight = getHeight() - mBackgroundPadding.top - mBackgroundPadding.bottom;
            int availableScrollBarHeight = visibleHeight - mScrollbar.getThumbHeight();
            return availableScrollBarHeight;
        }
        return 0;
    }

    /**
     * Returns the track color (ignoring alpha), can be overridden by each subclass.
     */
    public int getFastScrollerTrackColor(int defaultTrackColor) {
        return defaultTrackColor;
    }

    /**
     * Returns the inactive thumb color, can be overridden by each subclass.
     */
    public int getFastScrollerThumbInactiveColor(int defaultInactiveThumbColor) {
        return defaultInactiveThumbColor;
    }

    public void setUseScrollbar(boolean useScrollbar) {
        mUseScrollbar = useScrollbar;
        if (useScrollbar) {
            mScrollbar = new BaseRecyclerViewFastScrollBar(this, getResources());
        }  else {
            mScrollbar = null;
        }
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mUseScrollbar) {
            onUpdateScrollbar(0);
            mScrollbar.draw(canvas);
        }
    }

    /**
     * Updates the scrollbar thumb offset to match the visible scroll of the recycler view.  It does
     * this by mapping the available scroll area of the recycler view to the available space for the
     * scroll bar.
     *
     * @param scrollPosState the current scroll position
     * @param rowCount the number of rows, used to calculate the total scroll height (assumes that
     *                 all rows are the same height)
     */
    protected void synchronizeScrollBarThumbOffsetToViewScroll(ScrollPositionState scrollPosState,
            int rowCount) {
        if (!mUseScrollbar) {
            return;
        }
        // Only show the scrollbar if there is height to be scrolled
        int availableScrollBarHeight = getAvailableScrollBarHeight();
        int availableScrollHeight = getAvailableScrollHeight(rowCount, scrollPosState.rowHeight);
        if (availableScrollHeight <= 0) {
            mScrollbar.setThumbOffset(-1, -1);
            return;
        }

        // Calculate the current scroll position, the scrollY of the recycler view accounts for the
        // view padding, while the scrollBarY is drawn right up to the background padding (ignoring
        // padding)
        int scrollY = getCurrentScroll(scrollPosState);
        int scrollBarY = mBackgroundPadding.top +
                (int) (((float) scrollY / availableScrollHeight) * availableScrollBarHeight);

        // Calculate the position and size of the scroll bar
        int scrollBarX;
        if (Utilities.isRtl(getResources())) {
            scrollBarX = mBackgroundPadding.left;
        } else {
            scrollBarX = getWidth() - mBackgroundPadding.right - mScrollbar.getThumbWidth();
        }
        mScrollbar.setThumbOffset(scrollBarX, scrollBarY);
    }

    /**
     * @param scrollPosState current state of view scrolling.
     * @return the vertical scroll position
     */
    protected int getCurrentScroll(ScrollPositionState scrollPosState) {
        return getPaddingTop() + (scrollPosState.rowIndex * scrollPosState.rowHeight) -
                scrollPosState.rowTopOffset;
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     * <p>Override in each subclass of this base class.
     */
    public abstract String scrollToPositionAtProgress(float touchFraction);

    public abstract String scrollToSection(String sectionName);

    public abstract String[] getSectionNames();

    public void setFastScrollDragging(boolean dragging) {}

    public void setPreviousSectionFastScrollFocused() {}

    /**
     * Updates the bounds for the scrollbar.
     * <p>Override in each subclass of this base class.
     */
    public abstract void onUpdateScrollbar(int dy);

    /**
     * <p>Override in each subclass of this base class.
     */
    public void onFastScrollCompleted() {}

    /**
     * Returns information about the item that the recycler view is currently scrolled to.
     */
    protected abstract void getCurScrollState(ScrollPositionState stateOut);
}