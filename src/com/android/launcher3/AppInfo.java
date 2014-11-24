/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.util.Log;

import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.compat.UserHandleCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Represents an app in AllAppsView.
 */
public class AppInfo extends ItemInfo {
    private static final String TAG = "Launcher3.AppInfo";

    /**
     * The intent used to start the application.
     */
    Intent intent;

    /**
     * A bitmap version of the application icon.
     */
    Bitmap iconBitmap;

    /**
     * The time at which the app was first installed.
     */
    long firstInstallTime;

    ComponentName componentName;

    int count;

    int unreadNum = 0;

    static final int DOWNLOADED_FLAG = 1;
    static final int UPDATED_SYSTEM_APP_FLAG = 2;

    int flags = 0;

    AppInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    public Intent getIntent() {
        return intent;
    }

    protected Intent getRestoredIntent() {
        return null;
    }

    /**
     * Must not hold the Context.
     */
    public AppInfo(Context context, LauncherActivityInfoCompat info, UserHandleCompat user,
            IconCache iconCache, HashMap<Object, CharSequence> labelCache) {
        this.componentName = info.getComponentName();
        this.container = ItemInfo.NO_ID;

        flags = initFlags(info);
        firstInstallTime = info.getFirstInstallTime();
        iconCache.getTitleAndIcon(this, info, labelCache);
        intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(info.getComponentName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        long serialNumber = UserManagerCompat.getInstance(context).getSerialNumberForUser(user);
        intent.putExtra(EXTRA_PROFILE, serialNumber);
        this.user = user;
    }

    private static int initFlags(LauncherActivityInfoCompat info) {
        int appFlags = info.getApplicationInfo().flags;
        int flags = 0;
        if ((appFlags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
            flags |= DOWNLOADED_FLAG;

            if ((appFlags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                flags |= UPDATED_SYSTEM_APP_FLAG;
            }
        }
        return flags;
    }

    public static int initFlags(PackageInfo info) {
        int appFlags = info.applicationInfo.flags;
        int flags = 0;
        if ((appFlags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
            flags |= DOWNLOADED_FLAG;

            if ((appFlags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                flags |= UPDATED_SYSTEM_APP_FLAG;
            }
        }
        return flags;
    }

    public AppInfo(AppInfo info) {
        super(info);
        componentName = info.componentName;
        title = info.title.toString();
        intent = new Intent(info.intent);
        flags = info.flags;
        firstInstallTime = info.firstInstallTime;
        iconBitmap = info.iconBitmap;
    }

    @Override
    public String toString() {
        return "ApplicationInfo(title=" + title.toString() + " id=" + this.id
                + " type=" + this.itemType + " container=" + this.container
                + " screen=" + screenId + " cellX=" + cellX + " cellY=" + cellY
                + " spanX=" + spanX + " spanY=" + spanY + " dropPos=" + Arrays.toString(dropPos)
                + " user=" + user + ")";
    }

    public static void dumpApplicationInfoList(String tag, String label, ArrayList<AppInfo> list) {
        Log.d(tag, label + " size=" + list.size());
        for (AppInfo info: list) {
            Log.d(tag, "   title=\"" + info.title + "\" iconBitmap="
                    + info.iconBitmap + " firstInstallTime="
                    + info.firstInstallTime);
        }
    }

    public ShortcutInfo makeShortcut() {
        return new ShortcutInfo(this);
    }
}
