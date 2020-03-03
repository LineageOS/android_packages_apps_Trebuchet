/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.views;

import static com.android.launcher3.LauncherState.ALL_APPS_HEADER_EXTRA;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.QUICK_SWITCH;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Path.Op;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.animation.Interpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepAppTransitionManagerImpl;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ScrimView;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.SysUINavigationMode.NavigationModeChangeListener;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.ShelfPeekAnim;

/**
 * Scrim used for all-apps and shelf in Overview
 * In transposed layout, it behaves as a simple color scrim.
 * In portrait layout, it draws a rounded rect such that
 *    From normal state to overview state, the shelf just fades in and does not move
 *    From overview state to all-apps state the shelf moves up and fades in to cover the screen
 */
public class ShelfScrimView extends ScrimView implements NavigationModeChangeListener {

    // If the progress is more than this, shelf follows the finger, otherwise it moves faster to
    // cover the whole screen
    private static final float SCRIM_CATCHUP_THRESHOLD = 0.2f;

    // Temporarily needed until android.R.attr.bottomDialogCornerRadius becomes public
    private static final float BOTTOM_CORNER_RADIUS_RATIO = 2f;

    // In transposed layout, we simply draw a flat color.
    private boolean mDrawingFlatColor;

    // For shelf mode
    private final int mEndAlpha;
    private final float mRadius;
    private final int mMaxScrimAlpha;
    private final Paint mPaint;

    // Mid point where the alpha changes
    private int mMidAlpha;
    private float mMidProgress;

    // The progress at which the drag handle starts moving up with the shelf.
    private float mDragHandleProgress;

    private Interpolator mBeforeMidProgressColorInterpolator = ACCEL;
    private Interpolator mAfterMidProgressColorInterpolator = ACCEL;

    private float mShiftRange;

    private final float mShelfOffset;
    private float mTopOffset;
    private float mShelfTop;
    private float mShelfTopAtThreshold;

    private int mShelfColor;
    private int mRemainingScreenColor;

    private final Path mTempPath = new Path();
    private final Path mRemainingScreenPath = new Path();
    private boolean mRemainingScreenPathValid = false;

    private Mode mSysUINavigationMode;

    public ShelfScrimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMaxScrimAlpha = Math.round(OVERVIEW.getOverviewScrimAlpha(mLauncher) * 255);

        mEndAlpha = Color.alpha(mEndScrim);
        mRadius = BOTTOM_CORNER_RADIUS_RATIO * Themes.getDialogCornerRadius(context);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mShelfOffset = context.getResources().getDimension(R.dimen.shelf_surface_offset);
        // Just assume the easiest UI for now, until we have the proper layout information.
        mDrawingFlatColor = true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRemainingScreenPathValid = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        onNavigationModeChanged(SysUINavigationMode.INSTANCE.get(getContext())
                .addModeChangeListener(this));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        SysUINavigationMode.INSTANCE.get(getContext()).removeModeChangeListener(this);
    }

    @Override
    public void onNavigationModeChanged(Mode newMode) {
        mSysUINavigationMode = newMode;
        // Note that these interpolators are inverted because progress goes 1 to 0.
        if (mSysUINavigationMode == Mode.NO_BUTTON) {
            // Show the shelf more quickly before reaching overview progress.
            mBeforeMidProgressColorInterpolator = ACCEL_2;
            mAfterMidProgressColorInterpolator = ACCEL;
        } else {
            mBeforeMidProgressColorInterpolator = ACCEL;
            mAfterMidProgressColorInterpolator = Interpolators.clampToProgress(ACCEL, 0.5f, 1f);
        }
    }

    @Override
    public void reInitUi() {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        mDrawingFlatColor = dp.isVerticalBarLayout();

        if (!mDrawingFlatColor) {
            mRemainingScreenPathValid = false;
            mShiftRange = mLauncher.getAllAppsController().getShiftRange();

            if ((OVERVIEW.getVisibleElements(mLauncher) & ALL_APPS_HEADER_EXTRA) == 0) {
                mMidProgress = 1;
                mDragHandleProgress = 1;
                mMidAlpha = 0;
            } else {
                Context context = getContext();
                mMidAlpha = Themes.getAttrInteger(context, R.attr.allAppsInterimScrimAlpha);
                mMidProgress =  OVERVIEW.getVerticalProgress(mLauncher);
                Rect hotseatPadding = dp.getHotseatLayoutPadding();
                int hotseatSize = dp.hotseatBarSizePx + dp.getInsets().bottom
                        + hotseatPadding.bottom + hotseatPadding.top;
                float dragHandleTop =
                        Math.min(hotseatSize, LayoutUtils.getDefaultSwipeHeight(context, dp));
                mDragHandleProgress =  1 - (dragHandleTop / mShiftRange);
            }
            mTopOffset = dp.getInsets().top - mShelfOffset;
            mShelfTopAtThreshold = mShiftRange * SCRIM_CATCHUP_THRESHOLD + mTopOffset;
        }
        updateColors();
        updateDragHandleAlpha();
        invalidate();
    }

    @Override
    public void updateColors() {
        super.updateColors();
        if (mDrawingFlatColor) {
            mDragHandleOffset = 0;
            return;
        }

        mDragHandleOffset = mShelfOffset - mDragHandleSize;
        if (mProgress >= SCRIM_CATCHUP_THRESHOLD) {
            mShelfTop = mShiftRange * mProgress + mTopOffset;
        } else {
            mShelfTop = Utilities.mapRange(mProgress / SCRIM_CATCHUP_THRESHOLD, -mRadius,
                    mShelfTopAtThreshold);
        }

        if (mProgress >= 1) {
            mRemainingScreenColor = 0;
            mShelfColor = 0;
            ShelfPeekAnim shelfPeekAnim = ((QuickstepAppTransitionManagerImpl)
                    mLauncher.getAppTransitionManager()).getShelfPeekAnim();
            LauncherState state = mLauncher.getStateManager().getState();
            if (mSysUINavigationMode == Mode.NO_BUTTON
                    && (state == BACKGROUND_APP || state == QUICK_SWITCH)
                    && shelfPeekAnim.isPeeking()) {
                // Show the shelf background when peeking during swipe up.
                mShelfColor = setColorAlphaBound(mEndScrim, mMidAlpha);
            }
        } else if (mProgress >= mMidProgress) {
            mRemainingScreenColor = 0;

            int alpha = Math.round(Utilities.mapToRange(
                    mProgress, mMidProgress, 1, mMidAlpha, 0, mBeforeMidProgressColorInterpolator));
            mShelfColor = setColorAlphaBound(mEndScrim, alpha);
        } else {
            // Note that these ranges and interpolators are inverted because progress goes 1 to 0.
            int alpha = Math.round(
                    Utilities.mapToRange(mProgress, (float) 0, mMidProgress, (float) mEndAlpha,
                            (float) mMidAlpha, mAfterMidProgressColorInterpolator));
            mShelfColor = setColorAlphaBound(mEndScrim, alpha);

            int remainingScrimAlpha = Math.round(
                    Utilities.mapToRange(mProgress, (float) 0, mMidProgress, mMaxScrimAlpha,
                            (float) 0, LINEAR));
            mRemainingScreenColor = setColorAlphaBound(mScrimColor, remainingScrimAlpha);
        }

        if (mProgress < mDragHandleProgress) {
            mDragHandleOffset += mShiftRange * (mDragHandleProgress - mProgress);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);
        drawDragHandle(canvas);
    }

    private void drawBackground(Canvas canvas) {
        if (mDrawingFlatColor) {
            if (mCurrentFlatColor != 0) {
                canvas.drawColor(mCurrentFlatColor);
            }
            return;
        }

        if (Color.alpha(mShelfColor) == 0) {
            return;
        } else if (mProgress <= 0) {
            canvas.drawColor(mShelfColor);
            return;
        }

        int height = getHeight();
        int width = getWidth();
        // Draw the scrim over the remaining screen if needed.
        if (mRemainingScreenColor != 0) {
            if (!mRemainingScreenPathValid) {
                mTempPath.reset();
                // Using a arbitrary '+10' in the bottom to avoid any left-overs at the
                // corners due to rounding issues.
                mTempPath.addRoundRect(0, height - mRadius, width, height + mRadius + 10,
                        mRadius, mRadius, Direction.CW);
                mRemainingScreenPath.reset();
                mRemainingScreenPath.addRect(0, 0, width, height, Direction.CW);
                mRemainingScreenPath.op(mTempPath, Op.DIFFERENCE);
            }

            float offset = height - mRadius - mShelfTop;
            canvas.translate(0, -offset);
            mPaint.setColor(mRemainingScreenColor);
            canvas.drawPath(mRemainingScreenPath, mPaint);
            canvas.translate(0, offset);
        }

        mPaint.setColor(mShelfColor);
        canvas.drawRoundRect(0, mShelfTop, width, height + mRadius, mRadius, mRadius, mPaint);
    }
}
