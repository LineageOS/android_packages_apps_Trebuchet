/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.compat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionCallback;
import android.content.pm.ShortcutInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.PackageUserKey;

import java.util.List;

public abstract class LauncherAppsCompat {

    public interface OnAppsChangedCallbackCompat {
        default void onPackageRemoved(String packageName, UserHandle user) { }
        default void onPackageAdded(String packageName, UserHandle user) { }
        default void onPackageChanged(String packageName, UserHandle user) { }
        default void onPackagesAvailable(String[] packageNames, UserHandle user,
                boolean replacing) { }
        default void onPackagesUnavailable(String[] packageNames, UserHandle user,
                boolean replacing) { }
        default void onPackagesSuspended(String[] packageNames, UserHandle user) { }
        default void onPackagesUnsuspended(String[] packageNames, UserHandle user) { }
        default void onShortcutsChanged(String packageName, List<ShortcutInfo> shortcuts,
                UserHandle user) { }
    }

    protected LauncherAppsCompat() {
    }

    private static LauncherAppsCompat sInstance;
    private static final Object sInstanceLock = new Object();

    public static LauncherAppsCompat getInstance(Context context) {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                if (Utilities.ATLEAST_Q) {
                    sInstance = new LauncherAppsCompatVQ(context.getApplicationContext());
                } else if (Utilities.ATLEAST_OREO) {
                    sInstance = new LauncherAppsCompatVO(context.getApplicationContext());
                } else {
                    sInstance = new LauncherAppsCompatVL(context.getApplicationContext());
                }
            }
            return sInstance;
        }
    }

    public abstract List<LauncherActivityInfo> getActivityList(String packageName,
            UserHandle user);
    public abstract LauncherActivityInfo resolveActivity(Intent intent,
            UserHandle user);
    public abstract void startActivityForProfile(ComponentName component, UserHandle user,
            Rect sourceBounds, Bundle opts);
    public abstract ApplicationInfo getApplicationInfo(
            String packageName, int flags, UserHandle user);
    public abstract void showAppDetailsForProfile(ComponentName component, UserHandle user,
            Rect sourceBounds, Bundle opts);
    public abstract void addOnAppsChangedCallback(OnAppsChangedCallbackCompat listener);
    public abstract void removeOnAppsChangedCallback(OnAppsChangedCallbackCompat listener);
    public abstract boolean isPackageEnabledForProfile(String packageName, UserHandle user);
    public abstract boolean isActivityEnabledForProfile(ComponentName component,
            UserHandle user);
    public abstract List<ShortcutConfigActivityInfo> getCustomShortcutActivityList(
            @Nullable PackageUserKey packageUser);

    public abstract List<PackageInstaller.SessionInfo> getAllPackageInstallerSessions();

    public abstract void registerSessionCallback(LooperExecutor executor,
                                                 SessionCallback sessionCallback);
    public abstract void unregisterSessionCallback(SessionCallback sessionCallback);
}
