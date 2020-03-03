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
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Performs operations related to deep shortcuts, such as querying for them, pinning them, etc.
 */
public class DeepShortcutManager {
    private static final String TAG = "DeepShortcutManager";

    private static final int FLAG_GET_ALL = ShortcutQuery.FLAG_MATCH_DYNAMIC
            | ShortcutQuery.FLAG_MATCH_MANIFEST | ShortcutQuery.FLAG_MATCH_PINNED;

    private static DeepShortcutManager sInstance;

    public static DeepShortcutManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new DeepShortcutManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private final LauncherApps mLauncherApps;

    private DeepShortcutManager(Context context) {
        mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
    }

    /**
     * Queries for the shortcuts with the package name and provided ids.
     *
     * This method is intended to get the full details for shortcuts when they are added or updated,
     * because we only get "key" fields in onShortcutsChanged().
     */
    public QueryResult queryForFullDetails(String packageName,
            List<String> shortcutIds, UserHandle user) {
        return query(FLAG_GET_ALL, packageName, null, shortcutIds, user);
    }

    /**
     * Gets all the manifest and dynamic shortcuts associated with the given package and user,
     * to be displayed in the shortcuts container on long press.
     */
    public QueryResult queryForShortcutsContainer(@Nullable ComponentName activity,
            UserHandle user) {
        if (activity == null) return QueryResult.FAILURE;
        return query(ShortcutQuery.FLAG_MATCH_MANIFEST | ShortcutQuery.FLAG_MATCH_DYNAMIC,
                activity.getPackageName(), activity, null, user);
    }

    /**
     * Removes the given shortcut from the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void unpinShortcut(final ShortcutKey key) {
        String packageName = key.componentName.getPackageName();
        String id = key.getId();
        UserHandle user = key.user;
        List<String> pinnedIds = extractIds(queryForPinnedShortcuts(packageName, user));
        pinnedIds.remove(id);
        try {
            mLauncherApps.pinShortcuts(packageName, pinnedIds, user);
        } catch (SecurityException|IllegalStateException e) {
            Log.w(TAG, "Failed to unpin shortcut", e);
        }
    }

    /**
     * Adds the given shortcut to the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    public void pinShortcut(final ShortcutKey key) {
        String packageName = key.componentName.getPackageName();
        String id = key.getId();
        UserHandle user = key.user;
        List<String> pinnedIds = extractIds(queryForPinnedShortcuts(packageName, user));
        pinnedIds.add(id);
        try {
            mLauncherApps.pinShortcuts(packageName, pinnedIds, user);
        } catch (SecurityException|IllegalStateException e) {
            Log.w(TAG, "Failed to pin shortcut", e);
        }
    }

    public void startShortcut(String packageName, String id, Rect sourceBounds,
          Bundle startActivityOptions, UserHandle user) {
        try {
            mLauncherApps.startShortcut(packageName, id, sourceBounds,
                    startActivityOptions, user);
        } catch (SecurityException|IllegalStateException e) {
            Log.e(TAG, "Failed to start shortcut", e);
        }
    }

    public Drawable getShortcutIconDrawable(ShortcutInfo shortcutInfo, int density) {
        try {
            return mLauncherApps.getShortcutIconDrawable(shortcutInfo, density);
        } catch (SecurityException|IllegalStateException e) {
            Log.e(TAG, "Failed to get shortcut icon", e);
            return null;
        }
    }

    /**
     * Returns the id's of pinned shortcuts associated with the given package and user.
     *
     * If packageName is null, returns all pinned shortcuts regardless of package.
     */
    public QueryResult queryForPinnedShortcuts(String packageName, UserHandle user) {
        return queryForPinnedShortcuts(packageName, null, user);
    }

    public QueryResult queryForPinnedShortcuts(String packageName, List<String> shortcutIds,
            UserHandle user) {
        return query(ShortcutQuery.FLAG_MATCH_PINNED, packageName, null, shortcutIds, user);
    }

    public QueryResult queryForAllShortcuts(UserHandle user) {
        return query(FLAG_GET_ALL, null, null, null, user);
    }

    private static List<String> extractIds(List<ShortcutInfo> shortcuts) {
        List<String> shortcutIds = new ArrayList<>(shortcuts.size());
        for (ShortcutInfo shortcut : shortcuts) {
            shortcutIds.add(shortcut.getId());
        }
        return shortcutIds;
    }

    /**
     * Query the system server for all the shortcuts matching the given parameters.
     * If packageName == null, we query for all shortcuts with the passed flags, regardless of app.
     *
     * TODO: Use the cache to optimize this so we don't make an RPC every time.
     */
    private QueryResult query(int flags, String packageName, ComponentName activity,
            List<String> shortcutIds, UserHandle user) {
        ShortcutQuery q = new ShortcutQuery();
        q.setQueryFlags(flags);
        if (packageName != null) {
            q.setPackage(packageName);
            q.setActivity(activity);
            q.setShortcutIds(shortcutIds);
        }
        try {
            return new QueryResult(mLauncherApps.getShortcuts(q, user));
        } catch (SecurityException|IllegalStateException e) {
            Log.e(TAG, "Failed to query for shortcuts", e);
            return QueryResult.FAILURE;
        }
    }

    public boolean hasHostPermission() {
        try {
            return mLauncherApps.hasShortcutHostPermission();
        } catch (SecurityException|IllegalStateException e) {
            Log.e(TAG, "Failed to make shortcut manager call", e);
        }
        return false;
    }

    public static class QueryResult extends ArrayList<ShortcutInfo> {

        static QueryResult FAILURE = new QueryResult();

        private final boolean mWasSuccess;

        QueryResult(List<ShortcutInfo> result) {
            super(result == null ? Collections.emptyList() : result);
            mWasSuccess = true;
        }

        QueryResult() {
            mWasSuccess = false;
        }


        public boolean wasSuccess() {
            return mWasSuccess;
        }
    }
}
