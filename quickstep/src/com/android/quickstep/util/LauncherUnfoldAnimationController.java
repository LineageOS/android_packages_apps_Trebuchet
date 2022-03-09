/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.Utilities.comp;

import android.annotation.Nullable;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import androidx.core.view.OneShotPreDrawListener;

import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener;
import com.android.systemui.unfold.util.NaturalRotationUnfoldProgressProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

/**
 * Controls animations that are happening during unfolding foldable devices
 */
public class LauncherUnfoldAnimationController {

    // Percentage of the width of the quick search bar that will be reduced
    // from the both sides of the bar when progress is 0
    private static final float MAX_WIDTH_INSET_FRACTION = 0.15f;

    private final Launcher mLauncher;

    @Nullable
    private HorizontalInsettableView mQsbInsettable;

    private final ScopedUnfoldTransitionProgressProvider mProgressProvider;
    private final NaturalRotationUnfoldProgressProvider mNaturalOrientationProgressProvider;

    public LauncherUnfoldAnimationController(
            Launcher launcher,
            WindowManager windowManager,
            UnfoldTransitionProgressProvider unfoldTransitionProgressProvider) {
        mLauncher = launcher;
        mProgressProvider = new ScopedUnfoldTransitionProgressProvider(
                unfoldTransitionProgressProvider);
        mNaturalOrientationProgressProvider = new NaturalRotationUnfoldProgressProvider(launcher,
                WindowManagerGlobal.getWindowManagerService(), mProgressProvider);
        mNaturalOrientationProgressProvider.init();

        // Animated in all orientations
        mProgressProvider.addCallback(new UnfoldMoveFromCenterWorkspaceAnimator(launcher,
                windowManager));

        // Animated only in natural orientation
        mNaturalOrientationProgressProvider
                .addCallback(new QsbAnimationListener());
        mNaturalOrientationProgressProvider
                .addCallback(new UnfoldMoveFromCenterHotseatAnimator(launcher, windowManager));
    }

    /**
     * Called when launcher is resumed
     */
    public void onResume() {
        Hotseat hotseat = mLauncher.getHotseat();
        if (hotseat != null && hotseat.getQsb() instanceof HorizontalInsettableView) {
            mQsbInsettable = (HorizontalInsettableView) hotseat.getQsb();
        }

        OneShotPreDrawListener.add(mLauncher.getWorkspace(),
                () -> mProgressProvider.setReadyToHandleTransition(true));
    }

    /**
     * Called when launcher activity is paused
     */
    public void onPause() {
        mProgressProvider.setReadyToHandleTransition(false);
        mQsbInsettable = null;
    }

    /**
     * Called when launcher activity is destroyed
     */
    public void onDestroy() {
        mProgressProvider.destroy();
        mNaturalOrientationProgressProvider.destroy();
    }

    private class QsbAnimationListener implements TransitionProgressListener {

        @Override
        public void onTransitionStarted() {
        }

        @Override
        public void onTransitionFinished() {
            if (mQsbInsettable != null) {
                mQsbInsettable.setHorizontalInsets(0);
            }
        }

        @Override
        public void onTransitionProgress(float progress) {
            if (mQsbInsettable != null) {
                float insetPercentage = comp(progress) * MAX_WIDTH_INSET_FRACTION;
                mQsbInsettable.setHorizontalInsets(insetPercentage);
            }
        }
    }
}
