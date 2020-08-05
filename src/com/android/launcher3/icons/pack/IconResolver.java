/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.icons.pack;

import android.graphics.drawable.Drawable;

import com.android.launcher3.icons.clock.CustomClock;

public interface IconResolver {
    boolean isCalendar();

    boolean isClock();

    CustomClock.Metadata clockData();

    /**
     * Resolves an external icon for a given density.
     * @param iconDpi Positive integer. If it is non-positive the full scale drawable is returned.
     * @param fallback Method to load the drawable when resolving using the override fails.
     * @return Loaded drawable, or fallback drawable when resolving fails.
     */
    Drawable getIcon(int iconDpi, DefaultDrawableProvider fallback);

    interface DefaultDrawableProvider {
        Drawable get();
    }
}
