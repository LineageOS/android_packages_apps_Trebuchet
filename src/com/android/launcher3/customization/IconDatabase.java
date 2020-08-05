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

package com.android.launcher3.customization;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;

public class IconDatabase {

    private static final String PREF_FILE_NAME = BuildConfig.APPLICATION_ID + ".ICON_DATABASE";
    public static final String KEY_ICON_PACK = "pref_icon_pack";
    public static final String VALUE_DEFAULT = "";

    public static String getGlobal(Context context) {
        return LauncherPrefs.getPrefs(context).getString(KEY_ICON_PACK, VALUE_DEFAULT);
    }

    public static String getGlobalLabel(Context context) {
        final String defaultLabel = context.getString(R.string.icon_pack_default_label);
        final String pkgName = getGlobal(context);
        if (VALUE_DEFAULT.equals(pkgName)) {
            return defaultLabel;
        }

        final PackageManager pm = context.getPackageManager();
        try {
            final ApplicationInfo ai = pm.getApplicationInfo(pkgName, 0);
            return (String) pm.getApplicationLabel(ai);
        } catch (PackageManager.NameNotFoundException e) {
            return defaultLabel;
        }
    }

    public static void setGlobal(Context context, String value) {
        LauncherPrefs.getPrefs(context).edit().putString(KEY_ICON_PACK, value).apply();
    }

    public static void resetGlobal(Context context) {
        LauncherPrefs.getPrefs(context).edit().remove(KEY_ICON_PACK).apply();
    }

    public static String getByComponent(Context context, ComponentKey key) {
        return getIconPackPrefs(context).getString(key.toString(), getGlobal(context));
    }

    public static void setForComponent(Context context, ComponentKey key, String value) {
        getIconPackPrefs(context).edit().putString(key.toString(), value).apply();
    }

    public static void resetForComponent(Context context, ComponentKey key) {
        getIconPackPrefs(context).edit().remove(key.toString()).apply();
    }

    private static SharedPreferences getIconPackPrefs(Context context) {
        return context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
    }
}
