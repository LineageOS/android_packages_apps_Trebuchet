/*
 * Copyright (C) 2017 The Android Open Source Project
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


package com.android.launcher3.graphics;

import static com.android.launcher3.anim.Interpolators.EMPHASIZED;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_DOWNLOAD_APP_UX_V2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Property;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.window.RefreshRateTracker;

import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Extension of {@link FastBitmapDrawable} which shows a progress bar around the icon.
 */
public class PreloadIconDrawable extends FastBitmapDrawable {

    private static final Property<PreloadIconDrawable, Float> INTERNAL_STATE =
            new Property<PreloadIconDrawable, Float>(Float.TYPE, "internalStateProgress") {
                @Override
                public Float get(PreloadIconDrawable object) {
                    return object.mInternalStateProgress;
                }

                @Override
                public void set(PreloadIconDrawable object, Float value) {
                    object.setInternalProgress(value);
                }
            };

    private static final int DEFAULT_PATH_SIZE = 100;
    private static final int MAX_PAINT_ALPHA = 255;
    private static final int TRACK_ALPHA = (int) (0.27f * MAX_PAINT_ALPHA);
    private static final int DISABLED_ICON_ALPHA = (int) (0.6f * MAX_PAINT_ALPHA);

    private static final long DURATION_SCALE = 500;
    private static final long SCALE_AND_ALPHA_ANIM_DURATION = 500;

    // The smaller the number, the faster the animation would be.
    // Duration = COMPLETE_ANIM_FRACTION * DURATION_SCALE
    private static final float COMPLETE_ANIM_FRACTION = 1f;

    private static final float SMALL_SCALE = 0.7f;
    private static final float PROGRESS_STROKE_SCALE = ENABLE_DOWNLOAD_APP_UX_V2.get()
            ? 0.0655f
            : 0.075f;
    private static final float PROGRESS_BOUNDS_SCALE = 0.075f;

    private static final int PRELOAD_ACCENT_COLOR_INDEX = 0;
    private static final int PRELOAD_BACKGROUND_COLOR_INDEX = 1;

    private static final int ALPHA_DURATION_MILLIS = 3000;
    private static final int OVERLAY_ALPHA_RANGE = 191;
    private static final long WAVE_MOTION_DELAY_FACTOR_MILLIS = 100;
    private static final WeakHashMap<Integer, PorterDuffColorFilter> COLOR_FILTER_MAP =
            new WeakHashMap<>();
    public static final Function<Integer, PorterDuffColorFilter> FILTER_FACTORY =
            currArgb -> new PorterDuffColorFilter(currArgb, PorterDuff.Mode.SRC_ATOP);

    private final Matrix mTmpMatrix = new Matrix();
    private final PathMeasure mPathMeasure = new PathMeasure();

    private final ItemInfoWithIcon mItem;

    // Path in [0, 100] bounds.
    private final Path mShapePath;

    private final Path mScaledTrackPath;
    private final Path mScaledProgressPath;
    private final Paint mProgressPaint;

    private final int mIndicatorColor;
    private final int mSystemAccentColor;
    private final int mSystemBackgroundColor;
    private final boolean mIsDarkMode;

    private float mTrackLength;

    private boolean mRanFinishAnimation;
    private final int mRefreshRateMillis;

    // Progress of the internal state. [0, 1] indicates the fraction of completed progress,
    // [1, (1 + COMPLETE_ANIM_FRACTION)] indicates the progress of zoom animation.
    private float mInternalStateProgress;
    // This multiplier is used to animate scale when going from 0 to non-zero and expanding
    private final Runnable mInvalidateRunnable = this::invalidateSelf;
    private final AnimatedFloat mIconScaleMultiplier = new AnimatedFloat(mInvalidateRunnable);

    private ObjectAnimator mCurrentAnim;

    private boolean mIsStartable;

    public PreloadIconDrawable(ItemInfoWithIcon info, Context context) {
        this(
                info,
                IconPalette.getPreloadProgressColor(context, info.bitmap.color),
                getPreloadColors(context),
                Utilities.isDarkTheme(context),
                getRefreshRateMillis(context),
                GraphicsUtils.getShapePath(context, DEFAULT_PATH_SIZE));
    }

    public PreloadIconDrawable(
            ItemInfoWithIcon info,
            int indicatorColor,
            int[] preloadColors,
            boolean isDarkMode,
            int refreshRateMillis,
            Path shapePath) {
        super(info.bitmap);
        mItem = info;
        mShapePath = shapePath;
        mScaledTrackPath = new Path();
        mScaledProgressPath = new Path();

        mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        mIndicatorColor = indicatorColor;

        mSystemAccentColor = preloadColors[PRELOAD_ACCENT_COLOR_INDEX];
        mSystemBackgroundColor = preloadColors[PRELOAD_BACKGROUND_COLOR_INDEX];
        mIsDarkMode = isDarkMode;
        mRefreshRateMillis = refreshRateMillis;

        // If it's a pending app we will animate scale and alpha when it's no longer pending.
        mIconScaleMultiplier.updateValue(info.getProgressLevel() == 0 ? 0 : 1);

        setLevel(info.getProgressLevel());
        setIsStartable(info.isAppStartable());
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);


        float progressWidth = bounds.width() * (ENABLE_DOWNLOAD_APP_UX_V2.get()
                ? PROGRESS_BOUNDS_SCALE
                : PROGRESS_STROKE_SCALE);
        mTmpMatrix.setScale(
                (bounds.width() - 2 * progressWidth) / DEFAULT_PATH_SIZE,
                (bounds.height() - 2 * progressWidth) / DEFAULT_PATH_SIZE);
        mTmpMatrix.postTranslate(bounds.left + progressWidth, bounds.top + progressWidth);

        mShapePath.transform(mTmpMatrix, mScaledTrackPath);
        mProgressPaint.setStrokeWidth(PROGRESS_STROKE_SCALE * bounds.width());

        mPathMeasure.setPath(mScaledTrackPath, true);
        mTrackLength = mPathMeasure.getLength();

        setInternalProgress(mInternalStateProgress);
    }

    @Override
    public void drawInternal(Canvas canvas, Rect bounds) {
        if (mRanFinishAnimation) {
            super.drawInternal(canvas, bounds);
            return;
        }

        if (!ENABLE_DOWNLOAD_APP_UX_V2.get() && mInternalStateProgress > 0) {
            // Draw background.
            mProgressPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mProgressPaint.setColor(mSystemBackgroundColor);
            canvas.drawPath(mScaledTrackPath, mProgressPaint);
        }

        if (!ENABLE_DOWNLOAD_APP_UX_V2.get() || mInternalStateProgress > 0) {
            // Draw track and progress.
            mProgressPaint.setStyle(Paint.Style.STROKE);
            mProgressPaint.setColor(mSystemAccentColor);
            mProgressPaint.setAlpha(TRACK_ALPHA);
            canvas.drawPath(mScaledTrackPath, mProgressPaint);
            mProgressPaint.setAlpha(MAX_PAINT_ALPHA);
            canvas.drawPath(mScaledProgressPath, mProgressPaint);
        }

        int saveCount = canvas.save();
        float scale = ENABLE_DOWNLOAD_APP_UX_V2.get()
                ? 1 - mIconScaleMultiplier.value * (1 - SMALL_SCALE)
                : SMALL_SCALE;
        canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY());

        ColorFilter filter = getOverlayFilter();
        mPaint.setColorFilter(filter);
        super.drawInternal(canvas, bounds);
        canvas.restoreToCount(saveCount);

        if (ENABLE_DOWNLOAD_APP_UX_V2.get() && filter != null) {
            reschedule();
        }
    }

    @Override
    protected void updateFilter() {
        if (!ENABLE_DOWNLOAD_APP_UX_V2.get()) {
            setAlpha(mIsDisabled ? DISABLED_ICON_ALPHA : MAX_PAINT_ALPHA);
        }
    }

    /**
     * Updates the install progress based on the level
     */
    @Override
    protected boolean onLevelChange(int level) {
        // Run the animation if we have already been bound.
        updateInternalState(level * 0.01f, false, null);
        return true;
    }

    /**
     * Runs the finish animation if it is has not been run after last call to
     * {@link #onLevelChange}
     */
    public void maybePerformFinishedAnimation(
            PreloadIconDrawable oldIcon, Runnable onFinishCallback) {

        if (oldIcon.mInternalStateProgress >= 1) {
            mInternalStateProgress = oldIcon.mInternalStateProgress;
        }

        // If the drawable was recently initialized, skip the progress animation.
        if (mInternalStateProgress == 0) {
            mInternalStateProgress = 1;
        }
        updateInternalState(1 + COMPLETE_ANIM_FRACTION, true, onFinishCallback);
    }

    public boolean hasNotCompleted() {
        return !mRanFinishAnimation;
    }

    /** Sets whether this icon should display the startable app UI. */
    public void setIsStartable(boolean isStartable) {
        if (mIsStartable != isStartable) {
            mIsStartable = isStartable;
            setIsDisabled(!isStartable);
        }
    }

    private void updateInternalState(
            float finalProgress, boolean isFinish, Runnable onFinishCallback) {
        if (mCurrentAnim != null) {
            mCurrentAnim.cancel();
            mCurrentAnim = null;
        }

        boolean animateProgress =
                finalProgress >= mInternalStateProgress && getBounds().width() > 0;
        if (!animateProgress || mRanFinishAnimation) {
            setInternalProgress(finalProgress);
            if (isFinish && onFinishCallback != null) {
                onFinishCallback.run();
            }
        } else {
            mCurrentAnim = ObjectAnimator.ofFloat(this, INTERNAL_STATE, finalProgress);
            mCurrentAnim.setDuration(
                    (long) ((finalProgress - mInternalStateProgress) * DURATION_SCALE));
            mCurrentAnim.setInterpolator(LINEAR);
            if (isFinish) {
                if (onFinishCallback != null) {
                    mCurrentAnim.addListener(AnimatorListeners.forEndCallback(onFinishCallback));
                }
                mCurrentAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mRanFinishAnimation = true;
                    }
                });
            }
            mCurrentAnim.start();
        }
    }

    /**
     * Sets the internal progress and updates the UI accordingly
     *   for progress <= 0:
     *     - icon with pending motion
     *     - progress track is not visible
     *     - progress bar is not visible
     *   for progress < 1:
     *     - icon without pending motion
     *     - progress track is visible
     *     - progress bar is visible. Progress bar is drawn as a fraction of
     *       {@link #mScaledTrackPath}.
     *       @see PathMeasure#getSegment(float, float, Path, boolean)
     *   for progress > 1:
     *     - scale the icon back to full size
     */
    private void setInternalProgress(float progress) {
        // Animate scale and alpha from pending to downloading state.
        if (ENABLE_DOWNLOAD_APP_UX_V2.get() && progress > 0 && mInternalStateProgress == 0) {
            // Progress is changing for the first time, animate the icon scale
            Animator iconScaleAnimator = mIconScaleMultiplier.animateToValue(1);
            iconScaleAnimator.setDuration(SCALE_AND_ALPHA_ANIM_DURATION);
            iconScaleAnimator.setInterpolator(EMPHASIZED);
            iconScaleAnimator.start();
        }

        mInternalStateProgress = progress;
        if (progress <= 0) {
            if (!ENABLE_DOWNLOAD_APP_UX_V2.get()) {
                mScaledTrackPath.reset();
            }
            mIconScaleMultiplier.updateValue(0);
        } else {
            mPathMeasure.getSegment(
                    0, Math.min(progress, 1) * mTrackLength, mScaledProgressPath, true);
            if (progress > 1 && ENABLE_DOWNLOAD_APP_UX_V2.get()) {
                // map the scale back to original value
                mIconScaleMultiplier.updateValue(Utilities.mapBoundToRange(
                        progress - 1, 0, COMPLETE_ANIM_FRACTION, 1, 0, EMPHASIZED));
            }
        }
        invalidateSelf();
    }

    private static int[] getPreloadColors(Context context) {
        int[] preloadColors = new int[2];

        preloadColors[PRELOAD_ACCENT_COLOR_INDEX] = Themes.getAttrColor(context,
                R.attr.preloadIconAccentColor);
        preloadColors[PRELOAD_BACKGROUND_COLOR_INDEX] = Themes.getAttrColor(context,
                R.attr.preloadIconBackgroundColor);

        return preloadColors;
    }

    private static int getRefreshRateMillis(Context context) {
        return RefreshRateTracker.getSingleFrameMs(context);
    }

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public static PreloadIconDrawable newPendingIcon(Context context, ItemInfoWithIcon info) {
        return new PreloadIconDrawable(info, context);
    }

    @Override
    public FastBitmapConstantState newConstantState() {
        return new PreloadIconConstantState(
                mBitmap,
                mIconColor,
                mItem,
                mIndicatorColor,
                new int[] {mSystemAccentColor, mSystemBackgroundColor},
                mIsDarkMode,
                mRefreshRateMillis,
                mShapePath);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        if (!visible) {
            unscheduleSelf(mInvalidateRunnable);
        }
        return super.setVisible(visible, restart);
    }

    private void reschedule() {
        unscheduleSelf(mInvalidateRunnable);
        if (!isVisible()) {
            return;
        }
        final long upTime = SystemClock.uptimeMillis();
        scheduleSelf(mInvalidateRunnable,
                upTime - ((upTime % mRefreshRateMillis)) + mRefreshRateMillis);
    }

    /**
     * Returns a color filter to be used as an overlay on the pending icon with cascading motion
     * based on its position.
     */
    private ColorFilter getOverlayFilter() {
        if (!ENABLE_DOWNLOAD_APP_UX_V2.get() || mInternalStateProgress > 0) {
            // If the download has started, we do no need to animate
            return null;
        }
        long waveMotionDelay = (mItem.cellX * WAVE_MOTION_DELAY_FACTOR_MILLIS)
                + (mItem.cellY * WAVE_MOTION_DELAY_FACTOR_MILLIS);
        long time = SystemClock.uptimeMillis();
        int alpha = (int) Utilities.mapBoundToRange(
                (int) ((time + waveMotionDelay) % ALPHA_DURATION_MILLIS),
                0,
                ALPHA_DURATION_MILLIS,
                0,
                OVERLAY_ALPHA_RANGE * 2,
                LINEAR);
        if (alpha > OVERLAY_ALPHA_RANGE) {
            alpha = (OVERLAY_ALPHA_RANGE - (alpha % OVERLAY_ALPHA_RANGE));
        }
        int overlayColor = mIsDarkMode ? 0 : 255;
        int currArgb = Color.argb(alpha, overlayColor, overlayColor, overlayColor);
        return COLOR_FILTER_MAP.computeIfAbsent(currArgb, FILTER_FACTORY);
    }

    protected static class PreloadIconConstantState extends FastBitmapConstantState {

        protected final ItemInfoWithIcon mInfo;
        protected final int mIndicatorColor;
        protected final int[] mPreloadColors;
        protected final boolean mIsDarkMode;
        protected final int mLevel;
        protected final int mRefreshRateMillis;
        private final Path mShapePath;

        public PreloadIconConstantState(
                Bitmap bitmap,
                int iconColor,
                ItemInfoWithIcon info,
                int indicatorColor,
                int[] preloadColors,
                boolean isDarkMode,
                int refreshRateMillis,
                Path shapePath) {
            super(bitmap, iconColor);
            mInfo = info;
            mIndicatorColor = indicatorColor;
            mPreloadColors = preloadColors;
            mIsDarkMode = isDarkMode;
            mLevel = info.getProgressLevel();
            mRefreshRateMillis = refreshRateMillis;
            mShapePath = shapePath;
        }

        @Override
        public PreloadIconDrawable createDrawable() {
            return new PreloadIconDrawable(
                    mInfo,
                    mIndicatorColor,
                    mPreloadColors,
                    mIsDarkMode,
                    mRefreshRateMillis,
                    mShapePath);
        }
    }
}
