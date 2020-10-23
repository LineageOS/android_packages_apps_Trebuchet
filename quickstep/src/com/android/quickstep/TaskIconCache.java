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
package com.android.quickstep;

import static com.android.launcher3.FastBitmapDrawable.newIcon;
import static com.android.launcher3.uioverrides.QuickstepLauncher.GO_LOW_RAM_RECENTS_ENABLED;

import android.app.ActivityManager.TaskDescription;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.WorkerThread;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.util.CancellableTask;
import com.android.quickstep.util.TaskKeyLruCache;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.PackageManagerWrapper;
import com.android.systemui.shared.system.TaskDescriptionCompat;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Manages the caching of task icons and related data.
 */
public class TaskIconCache {

    private final Executor mBgExecutor;
    private final AccessibilityManager mAccessibilityManager;

    private final Context mContext;
    private final TaskKeyLruCache<TaskCacheEntry> mIconCache;
    private final SparseArray<BitmapInfo> mDefaultIcons = new SparseArray<>();
    private final IconProvider mIconProvider;

    public TaskIconCache(Context context, Executor bgExecutor) {
        mContext = context;
        mBgExecutor = bgExecutor;
        mAccessibilityManager = context.getSystemService(AccessibilityManager.class);

        Resources res = context.getResources();
        int cacheSize = res.getInteger(R.integer.recentsIconCacheSize);
        mIconCache = new TaskKeyLruCache<>(cacheSize);
        mIconProvider = new IconProvider(context);
    }

    /**
     * Asynchronously fetches the icon and other task data.
     *
     * @param task The task to fetch the data for
     * @param callback The callback to receive the task after its data has been populated.
     * @return A cancelable handle to the request
     */
    public CancellableTask updateIconInBackground(Task task, Consumer<Task> callback) {
        Preconditions.assertUIThread();
        if (task.icon != null) {
            // Nothing to load, the icon is already loaded
            callback.accept(task);
            return null;
        }
        CancellableTask<TaskCacheEntry> request = new CancellableTask<TaskCacheEntry>() {
            @Override
            public TaskCacheEntry getResultOnBg() {
                return getCacheEntry(task);
            }

            @Override
            public void handleResult(TaskCacheEntry result) {
                task.icon = result.icon;
                task.titleDescription = result.contentDescription;
                callback.accept(task);
            }
        };
        mBgExecutor.execute(request);
        return request;
    }

    public void clear() {
        mIconCache.evictAll();
    }

    void onTaskRemoved(TaskKey taskKey) {
        mIconCache.remove(taskKey);
    }

    void invalidateCacheEntries(String pkg, UserHandle handle) {
        mBgExecutor.execute(() -> mIconCache.removeAll(key ->
                pkg.equals(key.getPackageName()) && handle.getIdentifier() == key.userId));
    }

    @WorkerThread
    private TaskCacheEntry getCacheEntry(Task task) {
        TaskCacheEntry entry = mIconCache.getAndInvalidateIfModified(task.key);
        if (entry != null) {
            return entry;
        }

        TaskDescription desc = task.taskDescription;
        TaskKey key = task.key;
        ActivityInfo activityInfo = null;

        // Create new cache entry
        entry = new TaskCacheEntry();

        // Load icon
        // TODO: Load icon resource (b/143363444)
        Bitmap icon = TaskDescriptionCompat.getIcon(desc, key.userId);
        if (icon != null) {
            entry.icon = new FastBitmapDrawable(getBitmapInfo(
                    new BitmapDrawable(mContext.getResources(), icon),
                    key.userId,
                    desc.getPrimaryColor(),
                    false /* isInstantApp */));
        } else {
            activityInfo = PackageManagerWrapper.getInstance().getActivityInfo(
                    key.getComponent(), key.userId);
            if (activityInfo != null) {
                BitmapInfo bitmapInfo = getBitmapInfo(
                        mIconProvider.getIcon(activityInfo, UserHandle.of(key.userId)),
                        key.userId,
                        desc.getPrimaryColor(),
                        activityInfo.applicationInfo.isInstantApp());
                entry.icon = newIcon(mContext, bitmapInfo);
            } else {
                entry.icon = getDefaultIcon(key.userId);
            }
        }

        // Loading content descriptions if accessibility or low RAM recents is enabled.
        if (GO_LOW_RAM_RECENTS_ENABLED || mAccessibilityManager.isEnabled()) {
            // Skip loading the content description if the activity no longer exists
            if (activityInfo == null) {
                activityInfo = PackageManagerWrapper.getInstance().getActivityInfo(
                        key.getComponent(), key.userId);
            }
            if (activityInfo != null) {
                entry.contentDescription = getBadgedContentDescription(
                        activityInfo, task.key.userId, task.taskDescription);
            }
        }

        mIconCache.put(task.key, entry);
        return entry;
    }

    private String getBadgedContentDescription(ActivityInfo info, int userId, TaskDescription td) {
        PackageManager pm = mContext.getPackageManager();
        String taskLabel = td == null ? null : Utilities.trim(td.getLabel());
        if (TextUtils.isEmpty(taskLabel)) {
            taskLabel = Utilities.trim(info.loadLabel(pm));
        }

        String applicationLabel = Utilities.trim(info.applicationInfo.loadLabel(pm));
        String badgedApplicationLabel = userId != UserHandle.myUserId()
                ? pm.getUserBadgedLabel(applicationLabel, UserHandle.of(userId)).toString()
                : applicationLabel;
        return applicationLabel.equals(taskLabel)
                ? badgedApplicationLabel : badgedApplicationLabel + " " + taskLabel;
    }

    @WorkerThread
    private Drawable getDefaultIcon(int userId) {
        synchronized (mDefaultIcons) {
            BitmapInfo info = mDefaultIcons.get(userId);
            if (info == null) {
                try (LauncherIcons la = LauncherIcons.obtain(mContext)) {
                    info = la.makeDefaultIcon(UserHandle.of(userId));
                }
                mDefaultIcons.put(userId, info);
            }
            return new FastBitmapDrawable(info);
        }
    }

    @WorkerThread
    private BitmapInfo getBitmapInfo(Drawable drawable, int userId,
            int primaryColor, boolean isInstantApp) {
        try (LauncherIcons la = LauncherIcons.obtain(mContext)) {
            la.disableColorExtraction();
            la.setWrapperBackgroundColor(primaryColor);

            // User version code O, so that the icon is always wrapped in an adaptive icon container
            return la.createBadgedIconBitmap(drawable, UserHandle.of(userId),
                    Build.VERSION_CODES.O, isInstantApp);
        }
    }

    private static class TaskCacheEntry {
        public Drawable icon;
        public String contentDescription = "";
    }
}
