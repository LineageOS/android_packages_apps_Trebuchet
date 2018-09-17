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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import com.android.launcher3.IconCache;
import com.android.launcher3.IconProvider;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AdaptiveIconDrawableCompat;
import com.android.launcher3.compat.FixedScaleDrawableCompat;
import com.android.launcher3.graphics.IconNormalizer;
import com.android.launcher3.graphics.IconShapeOverride;
import com.android.launcher3.util.DrawableHack;
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

    public Drawable getRoundIconBackport(String packageName, int iconDpi) {
        int resId = 0;
        Drawable legacyIcon = null;
        PackageManager mPackageManager = mContext.getPackageManager();
        Resources resourcesForApplication = null;
        try {resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);}
        catch (PackageManager.NameNotFoundException e) {return null;}
        AssetManager assets = resourcesForApplication.getAssets();

        try (XmlResourceParser parseXml = assets.openXmlResourceParser("AndroidManifest.xml")) {
            String attribute = null;
            int eventType;
            while ((eventType = parseXml.nextToken()) != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parseXml.getName().equals("application"))
                    for (int i = 0; i < parseXml.getAttributeCount(); i++) {
                        attribute = parseXml.getAttributeName(i);
                        if (resId == 0 && attribute.equals("icon"))
                            resId = Integer.parseInt(parseXml.getAttributeValue(i).substring(1));
                        else if (attribute.equals("roundIcon")) {
                            resId = Integer.parseInt(parseXml.getAttributeValue(i).substring(1));
                            break;
                        }
                    }
                if (resId!=0 && attribute.equals("roundIcon")) break;
            }
        } catch (Exception ex) {
            android.util.Log.w("getLegacyIcon", ex);
        }
        if (resId!=0) try {
            resourcesForApplication = ResourceHack.setResSdk(resourcesForApplication, 26);
            try {
                legacyIcon = resourcesForApplication.getDrawableForDensity(resId, iconDpi);
            } catch (Resources.NotFoundException e) {
                Object drawableInflater = DrawableHack.getDrawableInflater(resourcesForApplication);
                XmlPullParser parser = resourcesForApplication.getXml(resId);
                legacyIcon = DrawableHack.inflateFromXml(drawableInflater, parser);
            }
        } catch (Exception e) {}
        if (resId!=0 && legacyIcon==null) try{resourcesForApplication=ResourceHack.setResSdk(resourcesForApplication, android.os.Build.VERSION.SDK_INT);} catch (Exception e){}
        return legacyIcon;
    }

    public Drawable wrapToAdaptiveIconBackport(Drawable drawable) {
        if (Utilities.ATLEAST_OREO || !(Utilities.isAdaptiveIconForced(mContext))) {
            return drawable;
        }

        float scale;
        boolean[] outShape = new boolean[1];
        AdaptiveIconDrawableCompat iconWrapper = new AdaptiveIconDrawableCompat(new ColorDrawable(mContext.getResources().getColor(R.color.legacy_icon_background)), new FixedScaleDrawableCompat(), Utilities.ATLEAST_MARSHMALLOW);
        try {
            if (!(drawable instanceof AdaptiveIconDrawableCompat) && (!Utilities.ATLEAST_OREO || !(drawable instanceof AdaptiveIconDrawable))) {
                scale = IconNormalizer.getInstance(mContext).getScale(drawable, null, iconWrapper.getIconMask(), outShape);
                FixedScaleDrawableCompat fsd = ((FixedScaleDrawableCompat) iconWrapper.getForeground());
                fsd.setDrawable(drawable);
                fsd.setScale(scale);
                return (Drawable) iconWrapper;
            }
        } catch (Exception e) {
            return drawable;
        }
        return drawable;
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi, boolean flattenDrawable) {
        // if we are not using any icon pack, load application icon directly
        Drawable portedIcon = null;
        if (Utilities.ATLEAST_OREO && IconShapeOverride.isSupported(mContext) && Utilities.isAdaptiveIconDisabled(mContext))
            portedIcon = getLegacyIcon(info.getComponentName().getPackageName(), iconDpi);
        if (((Utilities.ATLEAST_OREO && !IconShapeOverride.isSupported(mContext)) || (!Utilities.ATLEAST_OREO)) && !Utilities.isAdaptiveIconDisabled(mContext))
            portedIcon = getRoundIconBackport(info.getComponentName().getPackageName(), iconDpi);

        if (Utilities.ATLEAST_OREO && !Utilities.isUsingIconPack(mContext)) {
            if (portedIcon!=null) return portedIcon;
            return mContext.getPackageManager().getApplicationIcon(info.getApplicationInfo());
        }
        else if (!Utilities.ATLEAST_OREO && !Utilities.isUsingIconPack(mContext) && portedIcon!=null) return wrapToAdaptiveIconBackport(portedIcon);

        final Bitmap bm = mHandler.getThemedDrawableIconForPackage(info.getComponentName());
        if (bm == null) {
            return wrapToAdaptiveIconBackport((portedIcon!=null) ? portedIcon : info.getIcon(iconDpi));
        }

        return wrapToAdaptiveIconBackport(new BitmapDrawable(mContext.getResources(), bm));
    }
}
