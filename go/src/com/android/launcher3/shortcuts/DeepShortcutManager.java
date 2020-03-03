/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.notification.NotificationKeyData;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs operations related to deep shortcuts, such as querying for them, pinning them, etc.
 */
public class DeepShortcutManager {

    private static final DeepShortcutManager sInstance = new DeepShortcutManager();

    public static DeepShortcutManager getInstance(Context context) {
        return sInstance;
    }

    private final QueryResult mFailure = new QueryResult();

    private DeepShortcutManager() { }

    /**
     * Queries for the shortcuts with the package name and provided ids.
     *
     * This method is intended to get the full details for shortcuts when they are added or updated,
     * because we only get "key" fields in onShortcutsChanged().
     */
    public QueryResult queryForFullDetails(String packageName,
            List<String> shortcutIds, UserHandle user) {
        return mFailure;
    }

    /**
     * Gets all the manifest and dynamic shortcuts associated with the given package and user,
     * to be displayed in the shortcuts container on long press.
     */
    public QueryResult queryForShortcutsContainer(ComponentName activity,
            UserHandle user) {
        return mFailure;
    }

    /**
     * Removes the given shortcut from the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void unpinShortcut(final ShortcutKey key) {
    }

    /**
     * Adds the given shortcut to the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void pinShortcut(final ShortcutKey key) {
    }

    public void startShortcut(String packageName, String id, Rect sourceBounds,
            Bundle startActivityOptions, UserHandle user) {
    }

    public Drawable getShortcutIconDrawable(ShortcutInfo shortcutInfo, int density) {
        return null;
    }

    /**
     * Returns the id's of pinned shortcuts associated with the given package and user.
     *
     * If packageName is null, returns all pinned shortcuts regardless of package.
     */
    public QueryResult queryForPinnedShortcuts(String packageName, UserHandle user) {
        return mFailure;
    }

    public QueryResult queryForPinnedShortcuts(String packageName,
            List<String> shortcutIds, UserHandle user) {
        return mFailure;
    }

    public QueryResult queryForAllShortcuts(UserHandle user) {
        return mFailure;
    }

    public boolean hasHostPermission() {
        return false;
    }


    public static class QueryResult extends ArrayList<ShortcutInfo> {

        public boolean wasSuccess() {
            return true;
        }
    }
}
