/*
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2017 Paranoid Android
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
import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.android.launcher3.IconCache;
import com.android.launcher3.IconProvider;
import com.android.launcher3.Utilities;

public class CustomIconsProvider extends IconProvider {
    private Context mContext;
    private IconsHandler mHandler;

    public CustomIconsProvider(Context context) {
        super();
        mContext = context;
        mHandler = IconCache.getIconsHandler(context);
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        // if we are not using any icon pack, load application icon directly
        if (Utilities.ATLEAST_OREO && !Utilities.isUsingIconPack(mContext)) {
            return mContext.getPackageManager().getApplicationIcon(info.getApplicationInfo());
        }

        Bitmap bm = mHandler.getDrawableIconForPackage(info.getComponentName());
        if (bm == null) {
            return info.getIcon(iconDpi);
        }

        return new BitmapDrawable(mContext.getResources(), bm);
    }
}
