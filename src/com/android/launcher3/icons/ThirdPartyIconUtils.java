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

package com.android.launcher3.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.launcher3.util.ComponentKey;

import com.android.launcher3.icons.calendar.DynamicCalendar;
import com.android.launcher3.icons.clock.CustomClock;
import com.android.launcher3.icons.clock.DynamicClock;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.IconResolver;

class ThirdPartyIconUtils {
    static Drawable getByKey(Context context, ComponentKey key, int iconDpi,
                             IconResolver.DefaultDrawableProvider fallback) {
        IconResolver resolver = IconPackManager.get(context).resolve(key);
        Drawable icon = resolver == null
                ? null
                : resolver.getIcon(iconDpi, fallback);

        // Icon pack clocks go first.
        if (icon != null && resolver.isClock()) {
            return CustomClock.getClock(context, icon, resolver.clockData());
        }

        // Google Clock goes second, but only if the icon pack does not override it.
        if (icon == null && key.componentName.equals(DynamicClock.DESK_CLOCK)) {
            String packageName = key.componentName.getPackageName();
            IconProvider.ThemeData td = IconProvider.INSTANCE.get(context)
                    .getThemeDataForPackage(packageName);
            return ClockDrawableWrapper.forPackage(context, packageName, iconDpi, td);
        }

        // Google Calendar is checked last. Only applied if the icon pack does not override it.
        if (icon == null && key.componentName.getPackageName().equals(DynamicCalendar.CALENDAR)) {
            return DynamicCalendar.load(context, key.componentName, iconDpi);
        }

        return icon;
    }
}
