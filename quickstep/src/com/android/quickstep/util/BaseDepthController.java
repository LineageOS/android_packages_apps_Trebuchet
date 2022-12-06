/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.quickstep.util;

import android.app.WallpaperManager;
import android.os.IBinder;
import android.util.FloatProperty;
import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.systemui.shared.system.BlurUtils;

/**
 * Utility class for applying depth effect
 */
public class BaseDepthController {

    private static final FloatProperty<BaseDepthController> DEPTH =
            new FloatProperty<BaseDepthController>("depth") {
                @Override
                public void setValue(BaseDepthController depthController, float depth) {
                    depthController.setDepth(depth);
                }

                @Override
                public Float get(BaseDepthController depthController) {
                    return depthController.mDepth;
                }
            };

    private static final MultiPropertyFactory<BaseDepthController> DEPTH_PROPERTY_FACTORY =
            new MultiPropertyFactory<>("depthProperty", DEPTH, Float::max);

    private static final int DEPTH_INDEX_STATE_TRANSITION = 1;
    private static final int DEPTH_INDEX_WIDGET = 2;

    /** Property to set the depth for state transition. */
    public static final FloatProperty<BaseDepthController> STATE_DEPTH =
            DEPTH_PROPERTY_FACTORY.get(DEPTH_INDEX_STATE_TRANSITION);
    /** Property to set the depth for widget picker. */
    public static final FloatProperty<BaseDepthController> WIDGET_DEPTH =
            DEPTH_PROPERTY_FACTORY.get(DEPTH_INDEX_WIDGET);

    protected final Launcher mLauncher;

    /**
     * Blur radius when completely zoomed out, in pixels.
     */
    protected final int mMaxBlurRadius;
    protected final WallpaperManager mWallpaperManager;
    protected boolean mCrossWindowBlursEnabled;

    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    protected float mDepth;

    protected SurfaceControl mSurface;

    // Hints that there is potentially content behind Launcher and that we shouldn't optimize by
    // marking the launcher surface as opaque.  Only used in certain Launcher states.
    private boolean mHasContentBehindLauncher;
    /**
     * Last blur value, in pixels, that was applied.
     * For debugging purposes.
     */
    protected int mCurrentBlur;
    /**
     * If we requested early wake-up offsets to SurfaceFlinger.
     */
    protected boolean mInEarlyWakeUp;

    public BaseDepthController(Launcher activity) {
        mLauncher = activity;
        mMaxBlurRadius = activity.getResources().getInteger(R.integer.max_depth_blur_radius);
        mWallpaperManager = activity.getSystemService(WallpaperManager.class);
    }

    protected void setCrossWindowBlursEnabled(boolean isEnabled) {
        mCrossWindowBlursEnabled = isEnabled;
        applyDepthAndBlur();
    }

    public void setHasContentBehindLauncher(boolean hasContentBehindLauncher) {
        mHasContentBehindLauncher = hasContentBehindLauncher;
    }

    protected void applyDepthAndBlur() {
        float depth = mDepth;
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null) {
            mWallpaperManager.setWallpaperZoomOut(windowToken, depth);
        }

        if (!BlurUtils.supportsBlursOnWindows()) {
            return;
        }
        if (mSurface == null || !mSurface.isValid()) {
            return;
        }
        boolean hasOpaqueBg = mLauncher.getScrimView().isFullyOpaque();
        boolean isSurfaceOpaque = !mHasContentBehindLauncher && hasOpaqueBg;

        mCurrentBlur = !mCrossWindowBlursEnabled || hasOpaqueBg
                ? 0 : (int) (depth * mMaxBlurRadius);
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()
                .setBackgroundBlurRadius(mSurface, mCurrentBlur)
                .setOpaque(mSurface, isSurfaceOpaque);

        // Set early wake-up flags when we know we're executing an expensive operation, this way
        // SurfaceFlinger will adjust its internal offsets to avoid jank.
        boolean wantsEarlyWakeUp = depth > 0 && depth < 1;
        if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
            transaction.setEarlyWakeupStart();
            mInEarlyWakeUp = true;
        } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
            transaction.setEarlyWakeupEnd();
            mInEarlyWakeUp = false;
        }

        AttachedSurfaceControl rootSurfaceControl =
                mLauncher.getRootView().getRootSurfaceControl();
        if (rootSurfaceControl != null) {
            rootSurfaceControl.applyTransactionOnDraw(transaction);
        }
    }

    protected void setDepth(float depth) {
        depth = Utilities.boundToRange(depth, 0, 1);
        // Round out the depth to dedupe frequent, non-perceptable updates
        int depthI = (int) (depth * 256);
        float depthF = depthI / 256f;
        if (Float.compare(mDepth, depthF) == 0) {
            return;
        }
        mDepth = depthF;
        applyDepthAndBlur();
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     */
    protected void setSurface(SurfaceControl surface) {
        if (mSurface != surface) {
            mSurface = surface;
            applyDepthAndBlur();
        }
    }
}
