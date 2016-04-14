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

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import com.android.launcher3.util.Thunk;

/**
 * The track and scrollbar that shows when you scroll the list.
 */
public class BaseRecyclerViewFastScrollBar {

    public interface FastScrollFocusable {
        int FAST_SCROLL_FOCUS_DIMMABLE = 1;
        int FAST_SCROLL_FOCUS_SCALABLE = 2;

        void setFastScrollFocused(boolean focused, boolean animated);
        void setFastScrollDimmed(boolean dimmed, boolean animated);
    }

    /**
     * Helper class to apply fast scroll focus functionality to any view.
     */
    public static class FastScrollFocusApplicator implements FastScrollFocusable {
        private static final int FAST_SCROLL_FOCUS_FADE_IN_DURATION = 175;
        private static final int FAST_SCROLL_FOCUS_FADE_OUT_DURATION = 125;
        private static final float FAST_SCROLL_FOCUS_MAX_SCALE = 1.15f;

        private final View mView;
        private final int mFastScrollMode;

        private ObjectAnimator mFastScrollFocusAnimator;
        private ObjectAnimator mFastScrollDimAnimator;
        private boolean mFastScrollFocused;
        private boolean mFastScrollDimmed;

        public static void createApplicator(final View v, int mode) {
            FastScrollFocusApplicator applicator = new FastScrollFocusApplicator(v, mode);
            v.setTag(R.id.fast_scroll_focus_applicator_tag, applicator);
        }

        public static void setFastScrollFocused(final View v, boolean focused, boolean animated) {
            FastScrollFocusable focusable = getFromView(v);
            if (focusable == null) return;

            focusable.setFastScrollFocused(focused, animated);
        }

        public static void setFastScrollDimmed(final View v, boolean dimmed, boolean animated) {
            FastScrollFocusable focusable = getFromView(v);
            if (focusable == null) return;

            focusable.setFastScrollDimmed(dimmed, animated);
        }

        private static FastScrollFocusable getFromView(final View v) {
            Object tag = v.getTag(R.id.fast_scroll_focus_applicator_tag);
            if (tag != null) {
                return (FastScrollFocusApplicator) tag;
            }
            return null;
        }

        private FastScrollFocusApplicator(final View v, final int mode) {
            mView = v;
            mFastScrollMode = mode & ~FAST_SCROLL_FOCUS_SCALABLE; // Globally disable scaling.
        }

        public void setFastScrollFocused(boolean focused, boolean animated) {
            if ((mFastScrollMode & FAST_SCROLL_FOCUS_SCALABLE) == 0) {
                return;
            }

            if (mFastScrollFocused != focused) {
                mFastScrollFocused = focused;

                if (animated) {
                    // Clean up the previous focus animator
                    if (mFastScrollFocusAnimator != null) {
                        mFastScrollFocusAnimator.cancel();
                    }

                    // Setup animator for bi-directional scaling.
                    float value = focused ? FAST_SCROLL_FOCUS_MAX_SCALE : 1f;
                    PropertyValuesHolder pvhScaleX =
                            PropertyValuesHolder.ofFloat(View.SCALE_X, value);
                    PropertyValuesHolder pvhScaleY =
                            PropertyValuesHolder.ofFloat(View.SCALE_Y, value);
                    mFastScrollFocusAnimator = ObjectAnimator.ofPropertyValuesHolder(mView,
                            pvhScaleX, pvhScaleY);

                    if (focused) {
                        mFastScrollFocusAnimator.setInterpolator(new DecelerateInterpolator());
                    } else {
                        mFastScrollFocusAnimator.setInterpolator(new AccelerateInterpolator());
                    }
                    mFastScrollFocusAnimator.setDuration(focused ?
                            FAST_SCROLL_FOCUS_FADE_IN_DURATION : FAST_SCROLL_FOCUS_FADE_OUT_DURATION);
                    mFastScrollFocusAnimator.start();
                }
            }

            // Let the view do any additional operations if it wants.
            if (mView instanceof FastScrollFocusable) {
                ((FastScrollFocusable) mView).setFastScrollFocused(focused, animated);
            }
        }

        public void setFastScrollDimmed(boolean dimmed, boolean animated) {
            if ((mFastScrollMode & FAST_SCROLL_FOCUS_DIMMABLE) == 0) {
                return;
            }
            // Clean up the previous dim animator
            if (mFastScrollDimAnimator != null) {
                mFastScrollDimAnimator.cancel();
            }

            if (!animated) {
                mFastScrollDimmed = dimmed;
                mView.setAlpha(dimmed ? 0.4f : 1f);
            } else  if (mFastScrollDimmed != dimmed) {
                mFastScrollDimmed = dimmed;

                mFastScrollDimAnimator = ObjectAnimator.ofFloat(mView, View.ALPHA, dimmed ? 0.4f : 1f);
                mFastScrollDimAnimator.setDuration(dimmed ?
                        FAST_SCROLL_FOCUS_FADE_IN_DURATION : FAST_SCROLL_FOCUS_FADE_OUT_DURATION);
                mFastScrollDimAnimator.start();
            }

            // Let the view do any additional operations if it wants.
            if (mView instanceof FastScrollFocusable) {
                ((FastScrollFocusable) mView).setFastScrollDimmed(dimmed, animated);
            }
        }
    }

    private final static int MAX_TRACK_ALPHA = 30;
    private final static int SCROLL_BAR_VIS_DURATION = 150;

    @Thunk BaseRecyclerView mRv;
    private BaseRecyclerViewFastScrollPopup mPopup;

    private AnimatorSet mScrollbarAnimator;

    private int mThumbInactiveColor;
    private int mThumbActiveColor;
    @Thunk Point mThumbOffset = new Point(-1, -1);
    @Thunk Paint mThumbPaint;
    private int mThumbMinWidth;
    private int mThumbMaxWidth;
    @Thunk int mThumbWidth;
    @Thunk int mThumbHeight;
    private int mThumbCurvature;
    private Path mThumbPath = new Path();
    private Paint mTrackPaint;
    private int mTrackWidth;
    private float mLastTouchY;
    // The inset is the buffer around which a point will still register as a click on the scrollbar
    private int mTouchInset;
    private boolean mIsDragging;
    private boolean mIsThumbDetached;
    private boolean mCanThumbDetach;
    private boolean mIgnoreDragGesture;

    // This is the offset from the top of the scrollbar when the user first starts touching.  To
    // prevent jumping, this offset is applied as the user scrolls.
    private int mTouchOffset;

    private Rect mInvalidateRect = new Rect();
    private Rect mTmpRect = new Rect();

    public BaseRecyclerViewFastScrollBar(BaseRecyclerView rv, Resources res) {
        mRv = rv;
        mPopup = new BaseRecyclerViewFastScrollPopup(rv, res);
        mTrackPaint = new Paint();
        mTrackPaint.setColor(rv.getFastScrollerTrackColor(Color.BLACK));
        mTrackPaint.setAlpha(MAX_TRACK_ALPHA);
        mThumbInactiveColor = rv.getFastScrollerThumbInactiveColor(
                res.getColor(R.color.container_fastscroll_thumb_inactive_color));
        mThumbActiveColor = res.getColor(R.color.container_fastscroll_thumb_active_color);
        mThumbPaint = new Paint();
        mThumbPaint.setAntiAlias(true);
        mThumbPaint.setColor(mThumbInactiveColor);
        mThumbPaint.setStyle(Paint.Style.FILL);
        mThumbWidth = mThumbMinWidth = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_min_width);
        mThumbMaxWidth = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_max_width);
        mThumbHeight = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_height);
        mThumbCurvature = mThumbMaxWidth - mThumbMinWidth;
        mTouchInset = res.getDimensionPixelSize(R.dimen.container_fastscroll_thumb_touch_inset);
    }

    public void setDetachThumbOnFastScroll() {
        mCanThumbDetach = true;
    }

    public void reattachThumbToScroll() {
        mIsThumbDetached = false;
    }

    public void setThumbOffset(int x, int y) {
        if (mThumbOffset.x == x && mThumbOffset.y == y) {
            return;
        }
        mInvalidateRect.set(mThumbOffset.x - mThumbCurvature, mThumbOffset.y,
                mThumbOffset.x + mThumbWidth, mThumbOffset.y + mThumbHeight);
        mThumbOffset.set(x, y);
        updateThumbPath();
        mInvalidateRect.union(mThumbOffset.x - mThumbCurvature, mThumbOffset.y,
                mThumbOffset.x + mThumbWidth, mThumbOffset.y + mThumbHeight);
        mRv.invalidate(mInvalidateRect);
    }

    public Point getThumbOffset() {
        return mThumbOffset;
    }

    // Setter/getter for the thumb bar width for animations
    public void setThumbWidth(int width) {
        mInvalidateRect.set(mThumbOffset.x - mThumbCurvature, mThumbOffset.y,
                mThumbOffset.x + mThumbWidth, mThumbOffset.y + mThumbHeight);
        mThumbWidth = width;
        updateThumbPath();
        mInvalidateRect.union(mThumbOffset.x - mThumbCurvature, mThumbOffset.y,
                mThumbOffset.x + mThumbWidth, mThumbOffset.y + mThumbHeight);
        mRv.invalidate(mInvalidateRect);
    }

    public int getThumbWidth() {
        return mThumbWidth;
    }

    // Setter/getter for the track bar width for animations
    public void setTrackWidth(int width) {
        mInvalidateRect.set(mThumbOffset.x - mThumbCurvature, 0, mThumbOffset.x + mThumbWidth,
                mRv.getHeight());
        mTrackWidth = width;
        updateThumbPath();
        mInvalidateRect.union(mThumbOffset.x - mThumbCurvature, 0, mThumbOffset.x + mThumbWidth,
                mRv.getHeight());
        mRv.invalidate(mInvalidateRect);
    }

    public int getTrackWidth() {
        return mTrackWidth;
    }

    public int getThumbHeight() {
        return mThumbHeight;
    }

    public int getThumbMaxWidth() {
        return mThumbMaxWidth;
    }

    public float getLastTouchY() {
        return mLastTouchY;
    }

    public boolean isDraggingThumb() {
        return mIsDragging;
    }

    public boolean isThumbDetached() {
        return mIsThumbDetached;
    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    public void handleTouchEvent(MotionEvent ev, int downX, int downY, int lastY) {
        ViewConfiguration config = ViewConfiguration.get(mRv.getContext());

        int action = ev.getAction();
        int y = (int) ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (isNearThumb(downX, downY)) {
                    mTouchOffset = downY - mThumbOffset.y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // Check if we should start scrolling, but ignore this fastscroll gesture if we have
                // exceeded some fixed movement
                mIgnoreDragGesture |= Math.abs(y - downY) > config.getScaledPagingTouchSlop();
                if (!mIsDragging && !mIgnoreDragGesture && isNearThumb(downX, lastY) &&
                        Math.abs(y - downY) > config.getScaledTouchSlop()) {
                    mRv.getParent().requestDisallowInterceptTouchEvent(true);
                    mIsDragging = true;
                    mRv.setFastScrollDragging(mIsDragging);
                    if (mCanThumbDetach) {
                        mIsThumbDetached = true;
                    }
                    mTouchOffset += (lastY - downY);
                    mPopup.animateVisibility(true);
                    animateScrollbar(true);
                }
                if (mIsDragging) {
                    // Update the fastscroller section name at this touch position
                    int top = mRv.getBackgroundPadding().top;
                    int bottom = mRv.getHeight() - mRv.getBackgroundPadding().bottom - mThumbHeight;
                    float boundedY = (float) Math.max(top, Math.min(bottom, y - mTouchOffset));
                    String sectionName = mRv.scrollToPositionAtProgress((boundedY - top) /
                            (bottom - top));
                    mPopup.setSectionName(sectionName);
                    mPopup.animateVisibility(!sectionName.isEmpty());
                    mRv.invalidate(mPopup.updateFastScrollerBounds(mRv, lastY));
                    mLastTouchY = boundedY;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchOffset = 0;
                mLastTouchY = 0;
                mIgnoreDragGesture = false;
                if (mIsDragging) {
                    mIsDragging = false;
                    mRv.setFastScrollDragging(mIsDragging);
                    mPopup.animateVisibility(false);
                    animateScrollbar(false);
                }
                break;
        }
    }

    public void draw(Canvas canvas) {
        if (mThumbOffset.x < 0 || mThumbOffset.y < 0) {
            return;
        }

        // Draw the scroll bar track and thumb
        if (mTrackPaint.getAlpha() > 0) {
            canvas.drawRect(mThumbOffset.x, 0, mThumbOffset.x + mThumbWidth, mRv.getHeight(), mTrackPaint);
        }
        canvas.drawPath(mThumbPath, mThumbPaint);

        // Draw the popup
        mPopup.draw(canvas);
    }

    /**
     * Animates the width and color of the scrollbar.
     */
    private void animateScrollbar(boolean isScrolling) {
        if (mScrollbarAnimator != null) {
            mScrollbarAnimator.cancel();
        }

        mScrollbarAnimator = new AnimatorSet();
        ObjectAnimator trackWidthAnim = ObjectAnimator.ofInt(this, "trackWidth",
                isScrolling ? mThumbMaxWidth : mThumbMinWidth);
        ObjectAnimator thumbWidthAnim = ObjectAnimator.ofInt(this, "thumbWidth",
                isScrolling ? mThumbMaxWidth : mThumbMinWidth);
        mScrollbarAnimator.playTogether(trackWidthAnim, thumbWidthAnim);
        if (mThumbActiveColor != mThumbInactiveColor) {
            ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(),
                    mThumbPaint.getColor(), isScrolling ? mThumbActiveColor : mThumbInactiveColor);
            colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    mThumbPaint.setColor((Integer) animator.getAnimatedValue());
                    mRv.invalidate(mThumbOffset.x, mThumbOffset.y, mThumbOffset.x + mThumbWidth,
                            mThumbOffset.y + mThumbHeight);
                }
            });
            mScrollbarAnimator.play(colorAnimation);
        }
        mScrollbarAnimator.setDuration(SCROLL_BAR_VIS_DURATION);
        mScrollbarAnimator.start();
    }

    /**
     * Updates the path for the thumb drawable.
     */
    private void updateThumbPath() {
        mThumbCurvature = mThumbMaxWidth - mThumbWidth;
        mThumbPath.reset();
        mThumbPath.moveTo(mThumbOffset.x + mThumbWidth, mThumbOffset.y);                    // tr
        mThumbPath.lineTo(mThumbOffset.x + mThumbWidth, mThumbOffset.y + mThumbHeight);     // br
        mThumbPath.lineTo(mThumbOffset.x, mThumbOffset.y + mThumbHeight);                   // bl
        mThumbPath.cubicTo(mThumbOffset.x, mThumbOffset.y + mThumbHeight,
                mThumbOffset.x - mThumbCurvature, mThumbOffset.y + mThumbHeight / 2,
                mThumbOffset.x, mThumbOffset.y);                                            // bl2tl
        mThumbPath.close();
    }

    /**
     * Returns whether the specified points are near the scroll bar bounds.
     */
    private boolean isNearThumb(int x, int y) {
        mTmpRect.set(mThumbOffset.x, mThumbOffset.y, mThumbOffset.x + mThumbWidth,
                mThumbOffset.y + mThumbHeight);
        mTmpRect.inset(mTouchInset, mTouchInset);
        return mTmpRect.contains(x, y);
    }
}
