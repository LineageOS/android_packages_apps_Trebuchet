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

import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.util.ComponentKey;

import com.android.launcher3.icons.calendar.DateChangeReceiver;
import com.android.launcher3.icons.calendar.DynamicCalendar;
import com.android.launcher3.icons.clock.CustomClock;
import com.android.launcher3.icons.clock.DynamicClock;
import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.IconResolver;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;

public class ThirdPartyDrawableFactory extends DrawableFactory {
    private final IconPackManager mManager;
    private final DynamicClock mDynamicClockDrawer;
    private final CustomClock mCustomClockDrawer;
    private final DateChangeReceiver mCalendars;

    public ThirdPartyDrawableFactory(Context context) {
        mManager = IconPackManager.get(context);
        mDynamicClockDrawer = new DynamicClock(context);
        mCustomClockDrawer = new CustomClock(context);
        mCalendars = new DateChangeReceiver(context);
    }

    @Override
    public FastBitmapDrawable newIcon(Context context, ItemInfoWithIcon info) {
        if (info != null && info.getTargetComponent() != null
                && info.itemType == ITEM_TYPE_APPLICATION) {
            ComponentKey key = new ComponentKey(info.getTargetComponent(), info.user);

            IconResolver resolver = mManager.resolve(key);
            mCalendars.setIsDynamic(key, (resolver != null && resolver.isCalendar())
                || info.getTargetComponent().getPackageName().equals(DynamicCalendar.CALENDAR));

            if (resolver != null) {
                if (resolver.isClock()) {
                    Drawable drawable = resolver.getIcon(0, () -> null);
                    if (drawable != null) {
                        FastBitmapDrawable fb = mCustomClockDrawer.drawIcon(
                                info, drawable, resolver.clockData());
                        fb.setIsDisabled(info.isDisabled());
                        return fb;
                    }
                }
            } else if (info.getTargetComponent().equals(DynamicClock.DESK_CLOCK)) {
                return mDynamicClockDrawer.drawIcon(info);
            }
        }

        return super.newIcon(context, info);
    }
}
