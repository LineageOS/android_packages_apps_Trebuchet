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

import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;


/**
 * Stores the list of all applications for the all apps view.
 */
class AllAppsList {
    public static final int DEFAULT_APPLICATIONS_NUMBER = 42;
    
    /** The list off all apps. */
    public ArrayList<AppInfo> data =
            new ArrayList<AppInfo>(DEFAULT_APPLICATIONS_NUMBER);
    /** The list of apps that have been added since the last notify() call. */
    public ArrayList<AppInfo> added =
            new ArrayList<AppInfo>(DEFAULT_APPLICATIONS_NUMBER);
    /** The list of apps that have been removed since the last notify() call. */
    public ArrayList<AppInfo> removed = new ArrayList<AppInfo>();
    /** The list of apps that have been modified since the last notify() call. */
    public ArrayList<AppInfo> modified = new ArrayList<AppInfo>();

    private IconCache mIconCache;

    private AppFilter mAppFilter;

    /**
     * Boring constructor.
     */
    public AllAppsList(IconCache iconCache, AppFilter appFilter) {
        mIconCache = iconCache;
        mAppFilter = appFilter;
    }

    /**
     * Add the supplied ApplicationInfo objects to the list, and enqueue it into the
     * list to broadcast when notify() is called.
     *
     * If the app is already in the list, doesn't add it.
     */
    public void add(AppInfo info) {
        if (mAppFilter != null && !mAppFilter.shouldShowApp(info.componentName)) {
            return;
        }
        if (findActivity(data, info.componentName)) {
            return;
        }
        data.add(info);
        added.add(info);
    }

    public void clear() {
        data.clear();
        // TODO: do we clear these too?
        added.clear();
        removed.clear();
        modified.clear();
    }

    public int size() {
        return data.size();
    }

    public AppInfo get(int index) {
        return data.get(index);
    }

    /**
     * Add the icons for the supplied apk called packageName.
     */
    public void addPackage(Context context, String packageName) {
        final List<ResolveInfo> matches = findActivitiesForPackage(context, packageName);

        if (matches.size() > 0) {
            for (ResolveInfo info : matches) {
                add(new AppInfo(context.getPackageManager(), info, mIconCache, null));
            }
        }
    }

    /**
     * Remove the apps for the given apk identified by packageName.
     */
    public void removePackage(String packageName) {
        final List<AppInfo> data = this.data;
        for (int i = data.size() - 1; i >= 0; i--) {
            AppInfo info = data.get(i);
            final ComponentName component = info.intent.getComponent();
            if (packageName.equals(component.getPackageName())) {
                removed.add(info);
                data.remove(i);
            }
        }
        // This is more aggressive than it needs to be.
        mIconCache.flush();
    }

    /**
     * Add and remove icons for this package which has been updated.
     */
    public void updatePackage(Context context, String packageName) {
        final List<ResolveInfo> matches = findActivitiesForPackage(context, packageName);
        if (matches.size() > 0) {
            // Find disabled/removed activities and remove them from data and add them
            // to the removed list.
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                final ComponentName component = applicationInfo.intent.getComponent();
                if (packageName.equals(component.getPackageName())) {
                    if (!findActivity(matches, component)) {
                        removed.add(applicationInfo);
                        mIconCache.remove(component);
                        data.remove(i);
                    }
                }
            }

            // Find enabled activities and add them to the adapter
            // Also updates existing activities with new labels/icons
            int count = matches.size();
            for (int i = 0; i < count; i++) {
                final ResolveInfo info = matches.get(i);
                AppInfo applicationInfo = findApplicationInfoLocked(
                        info.activityInfo.applicationInfo.packageName,
                        info.activityInfo.name);
                if (applicationInfo == null) {
                    add(new AppInfo(context.getPackageManager(), info, mIconCache, null));
                } else {
                    mIconCache.remove(applicationInfo.componentName);
                    mIconCache.getTitleAndIcon(applicationInfo, info, null);
                    modified.add(applicationInfo);
                }
            }
        } else {
            // Remove all data for this package.
            for (int i = data.size() - 1; i >= 0; i--) {
                final AppInfo applicationInfo = data.get(i);
                final ComponentName component = applicationInfo.intent.getComponent();
                if (packageName.equals(component.getPackageName())) {
                    removed.add(applicationInfo);
                    mIconCache.remove(component);
                    data.remove(i);
                }
            }
        }
    }

    /**
     * Query the package manager for MAIN/LAUNCHER activities in the supplied package.
     */
    static List<ResolveInfo> findActivitiesForPackage(Context context, String packageName) {
        final PackageManager packageManager = context.getPackageManager();

        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);

        final List<ResolveInfo> apps = packageManager.queryIntentActivities(mainIntent, 0);
        return apps != null ? apps : new ArrayList<ResolveInfo>();
    }

    /**
     * Returns whether <em>apps</em> contains <em>component</em>.
     */
    private static boolean findActivity(List<ResolveInfo> apps, ComponentName component) {
        final String className = component.getClassName();
        for (ResolveInfo info : apps) {
            final ActivityInfo activityInfo = info.activityInfo;
            if (activityInfo.name.equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether <em>apps</em> contains <em>component</em>.
     */
    private static boolean findActivity(ArrayList<AppInfo> apps, ComponentName component) {
        final int N = apps.size();
        for (int i=0; i<N; i++) {
            final AppInfo info = apps.get(i);
            if (info.componentName.equals(component)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find an ApplicationInfo object for the given packageName and className.
     */
    private AppInfo findApplicationInfoLocked(String packageName, String className) {
        for (AppInfo info: data) {
            final ComponentName component = info.intent.getComponent();
            if (packageName.equals(component.getPackageName())
                    && className.equals(component.getClassName())) {
                return info;
            }
        }
        return null;
    }
}
