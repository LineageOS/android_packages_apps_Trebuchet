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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;

/**
 * AllAppsContainerView with launcher specific callbacks
 */
public class LauncherAllAppsContainerView extends AllAppsContainerView {

    private final Launcher mLauncher;

    public LauncherAllAppsContainerView(Context context) {
        this(context, null);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // The AllAppsContainerView houses the QSB and is hence visible from the Workspace
        // Overview states. We shouldn't intercept for the scrubber in these cases.
        if (!mLauncher.isInState(LauncherState.ALL_APPS)) {
            mTouchHandler = null;
            return false;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mLauncher.isInState(LauncherState.ALL_APPS)) {
            return false;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void setInsets(Rect insets) {
        super.setInsets(insets);
        int allAppsStartingPositionY = mLauncher.getDeviceProfile().availableHeightPx
                - mLauncher.getDeviceProfile().allAppsOpenVerticalTranslate;
        mLauncher.getAllAppsController().setScrollRangeDelta(allAppsStartingPositionY);
    }

    @Override
    public void onActivePageChanged(int currentActivePage) {
        super.onActivePageChanged(currentActivePage);
    }
}
