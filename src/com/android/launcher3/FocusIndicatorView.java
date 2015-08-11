/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.view.ViewParent;

public class FocusIndicatorView extends View implements View.OnFocusChangeListener {

    // It can be any number >0. The view is resized using scaleX and scaleY.
    static final int DEFAULT_LAYOUT_SIZE = 100;
    private static final float MIN_VISIBLE_ALPHA = 0.2f;

    private static final int[] sTempPos = new int[2];
    private static final int[] sTempShift = new int[2];

    private final int[] mIndicatorPos = new int[2];
    private final int[] mTargetViewPos = new int[2];

    private View mLastFocusedView;
    private boolean mInitiated;

    private Pair<View, Boolean> mPendingCall;

    public FocusIndicatorView(Context context) {
        this(context, null);
    }

    public FocusIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlpha(0);
        setBackgroundColor(getResources().getColor(R.color.focused_background));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Redraw if it is already showing. This avoids a bug where the height changes by a small
        // amount on connecting/disconnecting a bluetooth keyboard.
        if (mLastFocusedView != null) {
            mPendingCall = Pair.create(mLastFocusedView, Boolean.TRUE);
            invalidate();
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        mPendingCall = null;
        if (!mInitiated && (getWidth() == 0)) {
            // View not yet laid out. Wait until the view is ready to be drawn, so that be can
            // get the location on screen.
            mPendingCall = Pair.create(v, hasFocus);
            invalidate();
            return;
        }

        if (!mInitiated) {
            getLocationRelativeToParentPagedView(this, mIndicatorPos);
            mInitiated = true;
        }

        if (hasFocus) {
            setViewAnimation(getAlpha() > MIN_VISIBLE_ALPHA, v);
            mLastFocusedView = v;
        } else {
            if (mLastFocusedView == v) {
                mLastFocusedView = null;
                // force the translation to the target position
                setViewAnimation(false, v);
                animate().alpha(0);
            }
        }
    }

    private void setViewAnimation(boolean animate, View v) {
        int indicatorWidth = getWidth();
        int indicatorHeight = getHeight();

        float scaleX = v.getScaleX() * v.getWidth() / indicatorWidth;
        float scaleY = v.getScaleY() * v.getHeight() / indicatorHeight;

        getLocationRelativeToParentPagedView(v, mTargetViewPos);
        float x = mTargetViewPos[0] - mIndicatorPos[0] - (1 - scaleX) * indicatorWidth / 2;
        float y = mTargetViewPos[1] - mIndicatorPos[1] - (1 - scaleY) * indicatorHeight / 2;

        if (animate) {
            animate()
            .translationX(x)
            .translationY(y)
            .scaleX(scaleX)
            .scaleY(scaleY)
            .alpha(1)
            .setDuration(100);
        } else {
            setTranslationX(x);
            setTranslationY(y);
            setScaleX(scaleX);
            setScaleY(scaleY);
            animate().alpha(1);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mPendingCall != null) {
            onFocusChange(mPendingCall.first, mPendingCall.second);
        }
    }

    /**
     * Gets the location of a view relative in the window, off-setting any shift due to
     * page view scroll
     */
    private static void getLocationRelativeToParentPagedView(View v, int[] pos) {
        getPagedViewScrollShift(v, sTempShift);
        v.getLocationInWindow(sTempPos);
        pos[0] = sTempPos[0] + sTempShift[0];
        pos[1] = sTempPos[1] + sTempShift[1];
    }

    private static void getPagedViewScrollShift(View child, int[] shift) {
        ViewParent parent = child.getParent();
        if (parent instanceof PagedView) {
            View parentView = (View) parent;
            child.getLocationInWindow(sTempPos);
            shift[0] = parentView.getPaddingLeft() - sTempPos[0];
            shift[1] = -(int) child.getTranslationY();
        } else if (parent instanceof View) {
            getPagedViewScrollShift((View) parent, shift);
        } else {
            shift[0] = shift[1] = 0;
        }
    }
}
