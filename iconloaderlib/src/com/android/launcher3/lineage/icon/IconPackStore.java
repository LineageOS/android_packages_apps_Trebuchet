/*
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.launcher3.icons.R;

public final class IconPackStore {
    public static final String SYSTEM_ICON_PACK = "android";
    public static final String KEY_ICON_PACK = "pref_iconPackPackage";

    private Context context;
    private SharedPreferences prefs;

    public IconPackStore(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(
                "com.android.launcher3.prefs", Context.MODE_PRIVATE);
    }

    public String getCurrent() {
        return prefs.getString(KEY_ICON_PACK, getDefaultIconPack());
    }

    public void setCurrent(String pkgName) {
        prefs.edit()
            .putString(KEY_ICON_PACK, pkgName)
            .apply();
    }

    public boolean isUsingSystemIcons() {
        return SYSTEM_ICON_PACK.equals(getCurrent());
    }

    public String getCurrentLabel(String defaultLabel) {
        final String pkgName = getCurrent();
        if (SYSTEM_ICON_PACK.equals(pkgName)) {
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

    private String getDefaultIconPack() {
        return context.getString(R.string.icon_pack_default_pkg);
    }
}
