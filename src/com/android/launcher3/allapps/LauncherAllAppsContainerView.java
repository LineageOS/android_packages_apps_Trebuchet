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
import android.util.AttributeSet;
import android.view.WindowInsets;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;

/**
 * AllAppsContainerView with launcher specific callbacks
 */
public class LauncherAllAppsContainerView extends ActivityAllAppsContainerView<Launcher> {

    public LauncherAllAppsContainerView(Context context) {
        this(context, null);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int computeNavBarScrimHeight(WindowInsets insets) {
        if (Utilities.ATLEAST_Q) {
            return insets.getTappableElementInsets().bottom;
        } else {
            return insets.getStableInsetBottom();
        }
    }

    @Override
    public boolean isInAllApps() {
        return mActivityContext.getStateManager().isInStableState(LauncherState.ALL_APPS);
    }
}
