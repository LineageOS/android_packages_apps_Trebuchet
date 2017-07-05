package com.android.launcher3;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.Map;

public class IconPackProvider {
    private static Map<String, IconPack> iconPacks = new ArrayMap<>();

    public static IconPack getIconPack(String packageName){
        return iconPacks.get(packageName);
    }

    public static IconPack loadAndGetIconPack(Context context){
        SharedPreferences prefs = Utilities.getPrefs(context);
        String packageName = prefs.getString("pref_iconPackPackage", "");
        if("".equals(packageName)){
            return null;
        }
        if(!iconPacks.containsKey(packageName)){
            loadIconPack(context, packageName);
        }
        return getIconPack(packageName);
    }

    public static void loadIconPack(Context context, String packageName) {
        if("".equals(packageName)){
            iconPacks.put("", null);
        }
        Map<String, String> appFilter;
        try {
            appFilter = parseAppFilter(getAppFilter(context, packageName));
        } catch (Exception e) {
            Toast.makeText(context, "Invalid IconPack", Toast.LENGTH_SHORT).show();
            iconPacks.put(packageName, null);
            return;
        }
        iconPacks.put(packageName, new IconPack(appFilter, context, packageName));
    }

    private static Map<String, String> parseAppFilter(XmlPullParser parser) throws Exception {
        Map<String, String> entries = new ArrayMap<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("item")) {
                String comp = parser.getAttributeValue(null, "component");
                String drawable = parser.getAttributeValue(null, "drawable");
                if (drawable != null && comp != null) {
                    entries.put(comp, drawable);
                }
            }
        }
        return entries;
    }

    private static XmlPullParser getAppFilter(Context context, String packageName) {
        Resources res;
        try {
            res = context.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier("appfilter", "xml", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getXml(packageName, resourceId, null);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(context, "Failed to get AppFilter", Toast.LENGTH_SHORT).show();
        }
        return null;
    }
}