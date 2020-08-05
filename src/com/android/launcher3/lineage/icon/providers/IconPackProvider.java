/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.Utilities;
import com.android.launcher3.lineage.icon.IconPack;
import com.android.launcher3.lineage.icon.IconPackStore;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IconPackProvider {
    private static final String TAG = "IconPackProvider";

    private static Map<String, IconPack> iconPacks = new ArrayMap<>();

    public static final String ICON_MASK_TAG = "iconmask";
    public static final String ICON_BACK_TAG = "iconback";
    public static final String ICON_UPON_TAG = "iconupon";
    public static final String ICON_SCALE_TAG = "scale";

    private IconPackProvider() {
    }

    public static IconPack getIconPack(String packageName){
        return iconPacks.get(packageName);
    }

    public static IconPack loadAndGetIconPack(Context context) {
        final String packageName = new IconPackStore(context).getCurrent();
        if (IconPackStore.SYSTEM_ICON_PACK.equals(packageName)){
            return null;
        }

        if (!iconPacks.containsKey(packageName)){
            loadIconPack(context, packageName);
        }
        return getIconPack(packageName);
    }

    public static void loadIconPack(Context context, String packageName) {
        if (IconPackStore.SYSTEM_ICON_PACK.equals(packageName)){
            iconPacks.put("", null);
        }

        try {
            final XmlPullParser appFilter = getAppFilter(context, packageName);
            if (appFilter != null) {
                final IconPack pack = new IconPack(context, packageName);
                parseAppFilter(packageName, appFilter, pack);
                iconPacks.put(packageName, pack);
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid IconPack", e);
            return;
        }
    }

    private static void parseAppFilter(String packageName, XmlPullParser parser,
            IconPack pack) throws Exception {
        final Map<String, String> iconPackResources = new HashMap<>();
        final List<String> iconBackStrings = new ArrayList<>();

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }

            final String name = parser.getName();
            if (name.equals("item")) {
                String component = parser.getAttributeValue(null, "component");
                final String drawable = parser.getAttributeValue(null, "drawable");
                // Validate component/drawable exist
                if (TextUtils.isEmpty(component) || TextUtils.isEmpty(drawable)) {
                    continue;
                }
                // Validate format/length of component
                if (!component.startsWith("ComponentInfo{") || !component.endsWith("}") ||
                        component.length() < 16) {
                    continue;
                }
                // Sanitize stored value
                component = component.substring(14, component.length() - 1);
                if (!component.contains("/")) {
                    // Package icon reference
                    iconPackResources.put(component, drawable);
                } else {
                    final ComponentName componentName = ComponentName.unflattenFromString(
                            component);
                    if (componentName != null) {
                        iconPackResources.put(componentName.getPackageName(), drawable);
                        iconPackResources.put(component, drawable);
                    }
                }
                continue;
            }

            if (name.equalsIgnoreCase(ICON_BACK_TAG)) {
                final String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        iconBackStrings.add(parser.getAttributeValue(i));
                    }
                }
                continue;
            }

            if (name.equalsIgnoreCase(ICON_MASK_TAG) || name.equalsIgnoreCase(ICON_UPON_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    if (parser.getAttributeCount() > 0) {
                        icon = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), icon);
                continue;
            }

            if (name.equalsIgnoreCase(ICON_SCALE_TAG)) {
                String factor = parser.getAttributeValue(null, "factor");
                if (factor == null) {
                    if (parser.getAttributeCount() > 0) {
                        factor = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), factor);
                continue;
            }
        }

        pack.setIcons(iconPackResources, iconBackStrings);
    }

    private static XmlPullParser getAppFilter(Context context, String packageName) {
        try {
            final Resources res = context.getPackageManager()
                    .getResourcesForApplication(packageName);
            final int resourceId = res.getIdentifier("appfilter", "xml", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getXml(packageName, resourceId, null);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get AppFilter", e);
        }
        return null;
    }
}
