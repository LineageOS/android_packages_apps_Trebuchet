/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.util.NavigationMode.NO_BUTTON;

import android.view.Surface;

import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.NavigationMode;

/**
 * Utility class to check nav bar position.
 */
public class NavBarPosition {

    private final NavigationMode mMode;
    private final int mDisplayRotation;

    public NavBarPosition(NavigationMode mode, Info info) {
        mMode = mode;
        mDisplayRotation = info.rotation;
    }

    public NavBarPosition(NavigationMode mode, int displayRotation) {
        mMode = mode;
        mDisplayRotation = displayRotation;
    }

    public boolean isRightEdge() {
        return mMode != NO_BUTTON && mDisplayRotation == Surface.ROTATION_90;
    }

    public boolean isLeftEdge() {
        return mMode != NO_BUTTON && mDisplayRotation == Surface.ROTATION_270;
    }

    public float getRotation() {
        return isLeftEdge() ? 90 : (isRightEdge() ? -90 : 0);
    }
}
