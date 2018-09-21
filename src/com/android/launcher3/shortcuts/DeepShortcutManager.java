/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.shortcuts.backport.DeepShortcutManagerBackport;
import java.util.List;

/**
 * Performs operations related to deep shortcuts, such as querying for them, pinning them, etc.
 */
public abstract class DeepShortcutManager {
    private static final String TAG = "DeepShortcutManager";

    private static final int FLAG_GET_ALL = ShortcutQuery.FLAG_MATCH_DYNAMIC
            | ShortcutQuery.FLAG_MATCH_MANIFEST | ShortcutQuery.FLAG_MATCH_PINNED;

    private static DeepShortcutManager sInstance;
    private static final Object sInstanceLock = new Object();

    public static DeepShortcutManager getInstance(Context context) {
        DeepShortcutManager deepShortcutManager;
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.isShortcutBackportEnabled())
                    sInstance = new DeepShortcutManagerNative(context.getApplicationContext());
                else
                    sInstance = new DeepShortcutManagerBackport(context.getApplicationContext());
            }
            deepShortcutManager = sInstance;
        }
        return deepShortcutManager;
    }

    public static boolean supportsShortcuts(ItemInfo info) {
        return info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                && !info.isDisabled();
    }

    public abstract boolean wasLastCallSuccess();

    public abstract void onShortcutsChanged(List<ShortcutInfoCompat> shortcuts);

    public abstract List<ShortcutInfoCompat> queryForFullDetails(String packageName,
                                                                 List<String> shortcutIds, UserHandle user);

    public abstract List<ShortcutInfoCompat> queryForShortcutsContainer(ComponentName activity,
                                                                        List<String> ids, UserHandle user);

    public abstract void unpinShortcut(ShortcutKey key);

    public abstract void pinShortcut(ShortcutKey key);

    public abstract void startShortcut(String packageName, String id, Rect sourceBounds,
                                       Bundle startActivityOptions, UserHandle user);

    public abstract Drawable getShortcutIconDrawable(ShortcutInfoCompat shortcutInfo, int density);

    /**
     * Returns the id's of pinned shortcuts associated with the given package and user.
     *
     * If packageName is null, returns all pinned shortcuts regardless of package.
     */
    public final List<ShortcutInfoCompat> queryForPinnedShortcuts(String packageName, UserHandle user) {
        return query(2, packageName, null, null, user);
    }

    public final List<ShortcutInfoCompat> queryForAllShortcuts(UserHandle user) {
        return query(11, null, null, null, user);
    }

    protected abstract List<String> extractIds(List<ShortcutInfoCompat> shortcuts);

    protected abstract List<ShortcutInfoCompat> query(int flags, String packageName,
                                                      ComponentName activity, List<String> shortcutIds, UserHandle user);

    public abstract boolean hasHostPermission();
}
