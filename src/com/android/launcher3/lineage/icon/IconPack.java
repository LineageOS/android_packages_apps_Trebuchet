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
package com.android.launcher3.lineage.icon;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.android.launcher3.lineage.icon.providers.IconPackProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IconPack {
    /*
     * Useful Links:
     * https://github.com/teslacoil/Example_NovaTheme
     * http://stackoverflow.com/questions/7205415/getting-resources-of-another-application
     * http://stackoverflow.com/questions/3890012/how-to-access-string-resource-from-another-application
    */

    private final Context context;
    private final String packageName;

    private Map<String, String> iconPackResources;
    private List<String> iconBackStrings;
    private List<Drawable> iconBackList;
    private Drawable iconUpon;
    private Drawable iconMask;
    private Resources loadedIconPackResource;
    private float iconScale;

    public IconPack(Context context, String packageName){
        this.context = context;
        this.packageName = packageName;
    }

    public void setIcons(Map<String, String> iconPackResources, List<String> iconBackStrings) {
        this.iconPackResources = iconPackResources;
        this.iconBackStrings = iconBackStrings;
        iconBackList = new ArrayList<Drawable>();
        try {
            loadedIconPackResource = context.getPackageManager()
                    .getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            // must never happen cause it's checked already in the provider
            return;
        }

        iconMask = getDrawableForName(IconPackProvider.ICON_MASK_TAG);
        iconUpon = getDrawableForName(IconPackProvider.ICON_UPON_TAG);
        for (int i = 0; i < iconBackStrings.size(); i++) {
            final String backIconString = iconBackStrings.get(i);
            final Drawable backIcon = getDrawableWithName(backIconString);
            if (backIcon != null) {
                iconBackList.add(backIcon);
            }
        }

        final String scale = iconPackResources.get(IconPackProvider.ICON_SCALE_TAG);
        if (scale != null) {
            try {
                iconScale = Float.valueOf(scale);
            } catch (NumberFormatException e) {
            }
        }
    }

    public Drawable getIcon(LauncherActivityInfo info, Drawable appIcon, CharSequence appLabel) {
        return getIcon(info.getComponentName(), appIcon, appLabel);
    }

    public Drawable getIcon(ActivityInfo info, Drawable appIcon, CharSequence appLabel) {
        return getIcon(new ComponentName(info.packageName, info.name), appIcon, appLabel);
    }

    public Drawable getIcon(ComponentName name, Drawable appIcon, CharSequence appLabel) {
        return getDrawable(name.flattenToString(), appIcon, appLabel);
    }

    public Drawable getIcon(String packageName, Drawable appIcon, CharSequence appLabel) {
        return getDrawable(packageName, appIcon, appLabel);
    }

    private Drawable getDrawable(String name, Drawable appIcon, CharSequence appLabel) {
        Drawable d = getDrawableForName(name);
        if (d == null && appIcon != null) {
            d = compose(name, appIcon, appLabel);
        }
        return d;
    }

    private Drawable getIconBackFor(CharSequence tag) {
        if (iconBackList == null || iconBackList.size() == 0) {
            return null;
        }

        if (iconBackList.size() == 1) {
            return iconBackList.get(0);
        }

        try {
            final Drawable back = iconBackList.get(
                    (tag.hashCode() & 0x7fffffff) % iconBackList.size());
            return back;
        } catch (ArrayIndexOutOfBoundsException e) {
            return iconBackList.get(0);
        }
    }

    private int getResourceIdForDrawable(String resource) {
        return loadedIconPackResource.getIdentifier(resource, "drawable", packageName);
    }

    private Drawable getDrawableForName(String name) {
        final String item = iconPackResources.get(name);
        if (TextUtils.isEmpty(item)) {
            return null;
        }

        final int id = getResourceIdForDrawable(item);
        return id == 0 ? null : loadedIconPackResource.getDrawable(id);
    }

    private Drawable getDrawableWithName(String name) {
        final int id = getResourceIdForDrawable(name);
        return id == 0 ? null : loadedIconPackResource.getDrawable(id);
    }

    private BitmapDrawable getBitmapDrawable(Drawable image) {
        if (image instanceof BitmapDrawable) {
            return (BitmapDrawable) image;
        }

        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));
        final Bitmap bmResult = Bitmap.createBitmap(image.getIntrinsicWidth(),
                image.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmResult);
        image.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        image.draw(canvas);
        return new BitmapDrawable(loadedIconPackResource, bmResult);
    }

    private Drawable compose(String name, Drawable appIcon, CharSequence appLabel) {
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));
        final BitmapDrawable appIconBitmap = getBitmapDrawable(appIcon);
        final int width = appIconBitmap.getBitmap().getWidth();
        final int height = appIconBitmap.getBitmap().getHeight();
        float scale = iconScale;
        final Drawable iconBack = getIconBackFor(appLabel);
        if (iconBack == null && iconMask == null && iconUpon == null){
            scale = 1.0f;
        }

        final Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        final int scaledWidth = (int) (width * scale);
        final int scaledHeight = (int) (height * scale);
        if (scaledWidth != width || scaledHeight != height) {
            final Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                    appIconBitmap.getBitmap(), scaledWidth, scaledHeight, true);
            canvas.drawBitmap(scaledBitmap, (width - scaledWidth) / 2,
                    (height - scaledHeight) / 2, null);
        } else {
            canvas.drawBitmap(appIconBitmap.getBitmap(), 0, 0, null);
        }

        if (iconMask != null) {
            iconMask.setBounds(0, 0, width, height);
            BitmapDrawable  b = getBitmapDrawable(iconMask);
            b.getPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            b.draw(canvas);
        }
        if (iconBack != null) {
            iconBack.setBounds(0, 0, width, height);
            BitmapDrawable  b = getBitmapDrawable(iconBack);
            b.getPaint().setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
            b.draw(canvas);
        }
        if (iconUpon != null) {
            iconUpon.setBounds(0, 0, width, height);
            iconUpon.draw(canvas);
        }

        return new BitmapDrawable(loadedIconPackResource, bitmap);
    }
}
