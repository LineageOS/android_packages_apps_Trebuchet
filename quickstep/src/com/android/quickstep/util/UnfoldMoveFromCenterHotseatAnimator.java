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

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.systemui.unfold.updates.RotationChangeProvider;

/**
 * Animation that moves hotseat icons from center to the sides (final position)
 */
public class UnfoldMoveFromCenterHotseatAnimator extends BaseUnfoldMoveFromCenterAnimator {

    private final Launcher mLauncher;

    public UnfoldMoveFromCenterHotseatAnimator(Launcher launcher, WindowManager windowManager,
            RotationChangeProvider rotationChangeProvider) {
        super(windowManager, rotationChangeProvider);
        mLauncher = launcher;
    }

    @Override
    protected void onPrepareViewsForAnimation() {
        Hotseat hotseat = mLauncher.getHotseat();

        ViewGroup hotseatIcons = hotseat.getShortcutsAndWidgets();
        setClipChildren(hotseat, false);
        setClipToPadding(hotseat, false);

        for (int i = 0; i < hotseatIcons.getChildCount(); i++) {
            View child = hotseatIcons.getChildAt(i);
            registerViewForAnimation(child);
        }
    }

    @Override
    public void onTransitionFinished() {
        restoreClippings();
        super.onTransitionFinished();
    }
}
