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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import java.util.Calendar;

import com.android.launcher3.icons.clock.CustomClock;

public class IconResolverExternal implements IconResolver {
    private final PackageManager mPm;
    private final ApplicationInfo mPackInfo;
    private final int mDrawableId;
    private final String mCalendarPrefix;
    private final IconPack.Clock mClockData;

    IconResolverExternal(PackageManager pm, ApplicationInfo packInfo, int drawableId,
                         String calendarPrefix, IconPack.Clock clockData) {
        mPm = pm;
        mPackInfo = packInfo;
        mDrawableId = drawableId;
        mCalendarPrefix = calendarPrefix;
        mClockData = clockData;
    }

    public boolean isCalendar() {
        return mCalendarPrefix != null;
    }

    public boolean isClock() {
        return mClockData != null;
    }

    public CustomClock.Metadata clockData() {
        return new CustomClock.Metadata(
                mClockData.hourLayerIndex,
                mClockData.minuteLayerIndex,
                mClockData.secondLayerIndex,
                mClockData.defaultHour,
                mClockData.defaultMinute,
                mClockData.defaultSecond
        );
    }

    public Drawable getIcon(int iconDpi, DefaultDrawableProvider fallback) {
        try {
            Resources res = mPm.getResourcesForApplication(mPackInfo);

            // First try loading the calendar.
            if (isCalendar()) {
                String calendarId = mCalendarPrefix + Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                int drawableId = res.getIdentifier(calendarId, "drawable", mPackInfo.packageName);
                if (drawableId != 0) {
                    Drawable drawable;
                    if (iconDpi > 0) {
                        // Try loading with the right density
                        drawable = res.getDrawableForDensity(drawableId, iconDpi, null);
                        if (drawable != null) {
                            return drawable;
                        }
                    }

                    drawable = mPm.getDrawable(mPackInfo.packageName, drawableId, null);
                    if (drawable != null) {
                        return drawable;
                    }
                }
            }

            if (iconDpi > 0) {
                // Fall back to mipmap loading with correct density.
                Drawable drawable = res.getDrawableForDensity(mDrawableId, iconDpi, null);
                if (drawable != null) {
                    return drawable;
                }
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
        }

        // Finally, try directly returning the drawable.
        return mPm.getDrawable(mPackInfo.packageName, mDrawableId, mPackInfo);
    }
}
