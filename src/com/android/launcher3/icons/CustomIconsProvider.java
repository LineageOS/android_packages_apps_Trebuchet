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
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.android.launcher3.IconCache;
import com.android.launcher3.IconProvider;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.IconShapeOverride;
import com.android.launcher3.util.ResourceHack;

import org.xmlpull.v1.XmlPullParser;

public class CustomIconsProvider extends IconProvider {
    private Context mContext;
    private IconsHandler mHandler;

    public CustomIconsProvider(Context context) {
        super();
        mContext = context;
        mHandler = IconCache.getIconsHandler(context);
    }

    public Drawable getLegacyIcon(String packageName, int iconDpi) {
        try {
            PackageManager mPackageManager = mContext.getPackageManager();
            Resources resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);
            AssetManager assets = resourcesForApplication.getAssets();
            XmlResourceParser parseXml = assets.openXmlResourceParser("AndroidManifest.xml");
            Drawable legacyIcon = null;
            resourcesForApplication = ResourceHack.setResSdk(resourcesForApplication, 25);
            int eventType;
            while ((eventType = parseXml.nextToken()) != XmlPullParser.END_DOCUMENT)
                if (eventType == XmlPullParser.START_TAG && parseXml.getName().equals("application"))
                    for (int i = 0; i < parseXml.getAttributeCount(); i++)
                        if (parseXml.getAttributeName(i).equals("icon"))
                            legacyIcon = resourcesForApplication.getDrawableForDensity(Integer.parseInt(parseXml.getAttributeValue(i).substring(1)), iconDpi);
            parseXml.close();
            return legacyIcon;
        } catch (Exception ex) {
            android.util.Log.w("getLegacyIcon", ex);
        }
        return null;
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        // if we are not using any icon pack, load application icon directly
        Drawable legacyIcon = null;
        if (Utilities.ATLEAST_OREO && IconShapeOverride.isSupported(mContext) && Utilities.isAdaptiveIconDisabled(mContext))
            legacyIcon = getLegacyIcon(info.getComponentName().getPackageName(), iconDpi);

        if (Utilities.ATLEAST_OREO && !Utilities.isUsingIconPack(mContext)) {
            if (legacyIcon!=null) return legacyIcon;
            return mContext.getPackageManager().getApplicationIcon(info.getApplicationInfo());
        }

        final Bitmap bm = mHandler.getThemedDrawableIconForPackage(info.getComponentName());
        if (bm == null) {
            return (legacyIcon!=null) ? legacyIcon : info.getIcon(iconDpi);
        }

        return new BitmapDrawable(mContext.getResources(), bm);
    }
}
