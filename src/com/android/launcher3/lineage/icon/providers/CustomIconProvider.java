/*
 * Copyright (C) 2019 Paranoid Android
 * Copyright (C) 2020 Shift GmbH
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
package com.android.launcher3.lineage.icon.providers;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;

import androidx.annotation.Keep;

import com.android.launcher3.IconProvider;
import com.android.launcher3.lineage.icon.IconPack;

@Keep
public class CustomIconProvider extends IconProvider {

    private final Context context;

    public CustomIconProvider(Context context) {
        this.context = context;
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        final Drawable icon = super.getIcon(info, iconDpi, flattenDrawable);
        final IconPack iconPack = IconPackProvider.loadAndGetIconPack(context);
        if (iconPack == null) {
            return icon;
        }

        final Drawable iconMask = iconPack.getIcon(info, null, info.getLabel());
        return iconMask == null ? icon : iconMask;
    }
}
