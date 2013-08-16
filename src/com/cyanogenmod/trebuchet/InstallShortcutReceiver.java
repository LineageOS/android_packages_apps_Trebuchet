/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2013 The Linux Foundation. All rights reserved.
 *
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only.
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

package com.cyanogenmod.trebuchet;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class InstallShortcutReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_SHORTCUT =
            "com.android.launcher.action.INSTALL_SHORTCUT";
    public static final String NEW_APPS_PAGE_KEY = "apps.new.page";
    public static final String NEW_APPS_LIST_KEY = "apps.new.list";

    public static final int NEW_SHORTCUT_BOUNCE_DURATION = 450;
    public static final int NEW_SHORTCUT_STAGGER_DELAY = 75;

    private static final int INSTALL_SHORTCUT_SUCCESSFUL = 0;
    private static final int INSTALL_SHORTCUT_IS_DUPLICATE = -1;
    private static final int INSTALL_SHORTCUT_NO_SPACE = -2;

    private static final String ACTION_INSTALL_SHORTCUT_SUCCESSFUL =
            "com.android.launcher.action.INSTALL_SHORTCUT_SUCCESSFUL";
    private static final String EXTRA_RESPONSE_PACKAGENAME = "response_packagename";
    private static final String EXTRA_SHORTCUT_PACKAGENAME = "shortcut_packagename";

    // A mime-type representing shortcut data
    public static final String SHORTCUT_MIMETYPE =
            "com.cyanogenmod.trebuchet/shortcut";

    private static Object sLock = new Object();

    // The set of shortcuts that are pending install
    private static ArrayList<PendingInstallShortcutInfo> mInstallQueue =
            new ArrayList<PendingInstallShortcutInfo>();

    private static void addToStringSet(SharedPreferences sharedPrefs,
            SharedPreferences.Editor editor, String key, String value) {
        Set<String> strings = sharedPrefs.getStringSet(key, null);
        if (strings == null) {
            strings = new HashSet<String>(0);
        } else {
            strings = new HashSet<String>(strings);
        }
        strings.add(value);
        editor.putStringSet(key, strings);
    }
    // Determines whether to defer installing shortcuts immediately until
    // processAllPendingInstalls() is called.
    private static boolean mUseInstallQueue = false;

    private static class PendingInstallShortcutInfo {
        Intent data;
        Intent launchIntent;
        String name;

        public PendingInstallShortcutInfo(Intent rawData, String shortcutName,
                Intent shortcutIntent) {
            data = rawData;
            name = shortcutName;
            launchIntent = shortcutIntent;
        }
    }

    public void onReceive(Context context, Intent data) {
        if (!ACTION_INSTALL_SHORTCUT.equals(data.getAction())) {
            return;
        }

        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        if (intent == null) {
            return;
        }
        // This name is only used for comparisons and notifications, so fall back to activity name
        // if not supplied
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        if (name == null) {
            try {
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = pm.getActivityInfo(intent.getComponent(), 0);
                name = info.loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException nnfe) {
                return;
            }
        }
        // Queue the item up for adding if launcher has not loaded properly yet
        boolean launcherNotLoaded = LauncherModel.getWorkspaceCellCountX() <= 0 ||
                LauncherModel.getWorkspaceCellCountY() <= 0;

        PendingInstallShortcutInfo info = new PendingInstallShortcutInfo(data, name, intent);
        if (mUseInstallQueue || launcherNotLoaded) {
            mInstallQueue.add(info);
        } else {
            processInstallShortcut(context, info);
        }
    }

    static void enableInstallQueue() {
        mUseInstallQueue = true;
    }
    static void disableAndFlushInstallQueue(Context context) {
        mUseInstallQueue = false;
        flushInstallQueue(context);
    }
    static void flushInstallQueue(Context context) {
        Iterator<PendingInstallShortcutInfo> iter = mInstallQueue.iterator();
        while (iter.hasNext()) {
            processInstallShortcut(context, iter.next());
            iter.remove();
        }
    }

    private static void processInstallShortcut(Context context,
            PendingInstallShortcutInfo pendingInfo) {
        String spKey = LauncherApplication.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);

        final Intent data = pendingInfo.data;
        final Intent intent = pendingInfo.launchIntent;
        final String name = pendingInfo.name;

        // Lock on the app so that we don't try and get the items while apps are being added
        LauncherApplication app = (LauncherApplication) context.getApplicationContext();
        final int[] result = {INSTALL_SHORTCUT_SUCCESSFUL};
        boolean found = false;
        synchronized (app) {
            // Flush the LauncherModel worker thread, so that if we just did another
            // processInstallShortcut, we give it time for its shortcut to get added to the
            // database (getItemsInLocalCoordinates reads the database)
            app.getModel().flushWorkerThread();
            final ArrayList<ItemInfo> items = LauncherModel.getItemsInLocalCoordinates(context);
            final boolean exists = LauncherModel.shortcutExists(context, intent);

            // Try adding to the workspace screens incrementally, starting at the default or center
            // screen and alternating between +1, -1, +2, -2, etc. (using ~ ceil(i/2f)*(-1)^(i-1))
            final int screenCount = PreferencesProvider.Interface.Homescreen.getNumberHomescreens();
            final int screenDefault = PreferencesProvider.Interface.Homescreen.getDefaultHomescreen(screenCount / 2);
            final int screen = (screenDefault >= screenCount) ? screenCount / 2 : screenDefault;

            for (int i = 0; i <= (2 * screenCount) + 1 && !found; ++i) {
                int si = screen + (int) ((i / 2f) + 0.5f) * ((i % 2 == 1) ? 1 : -1);
                if (0 <= si && si < screenCount) {
                    found = installShortcut(context, data, items, intent, si, exists, sp,
                            result);
                }
            }
        }

        // We only report error messages (duplicate shortcut or out of space) as the add-animation
        // will provide feedback otherwise
        if (!found) {
            if (result[0] == INSTALL_SHORTCUT_NO_SPACE) {
                Toast.makeText(context, context.getString(R.string.completely_out_of_space),
                        Toast.LENGTH_SHORT).show();
            } else if (result[0] == INSTALL_SHORTCUT_IS_DUPLICATE) {
                Toast.makeText(context, context.getString(R.string.shortcut_duplicate, name),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            // When the shortcut put successful, broadcast an intent with package name
            // So the application can use it to show a toast.
            String packageName = intent.getStringExtra(EXTRA_SHORTCUT_PACKAGENAME);
            Intent responseIntent = new Intent(ACTION_INSTALL_SHORTCUT_SUCCESSFUL);
            responseIntent.putExtra(EXTRA_RESPONSE_PACKAGENAME, packageName);
            context.sendBroadcast(responseIntent);
        }
    }

    private static boolean installShortcut(Context context, Intent data, ArrayList<ItemInfo> items,
            final Intent intent, final int screen, boolean shortcutExists,
            final SharedPreferences sharedPrefs, int[] result) {
        int[] tmpCoordinates = new int[2];
        if (findEmptyCell(items, tmpCoordinates, screen)) {
            if (intent != null) {
                if (intent.getAction() == null) {
                    intent.setAction(Intent.ACTION_VIEW);
                } else if (intent.getAction().equals(Intent.ACTION_MAIN) &&
                        intent.getCategories() != null &&
                        intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                }

                // By default, we allow for duplicate entries (located in
                // different places)
                boolean duplicate = data.getBooleanExtra(Launcher.EXTRA_SHORTCUT_DUPLICATE, true);
                if (duplicate || !shortcutExists) {
                    new Thread("setNewAppsThread") {
                        public void run() {
                            synchronized (sLock) {
                                // If the new app is going to fall into the same page as before,
                                // then just continue adding to the current page
                                final int newAppsScreen = sharedPrefs.getInt(
                                        NEW_APPS_PAGE_KEY, screen);
                                SharedPreferences.Editor editor = sharedPrefs.edit();
                                if (newAppsScreen == screen) {
                                    addToStringSet(sharedPrefs,
                                        editor, NEW_APPS_LIST_KEY, intent.toUri(0));
                                }
                                editor.putInt(NEW_APPS_PAGE_KEY, screen);
                                editor.commit();
                            }
                        }
                    }.start();

                    // Update the Launcher db
                    LauncherApplication app = (LauncherApplication) context.getApplicationContext();
                    ShortcutInfo info = app.getModel().addShortcut(context, data,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP, screen,
                            tmpCoordinates[0], tmpCoordinates[1], true);
                    if (info == null) {
                        return false;
                    }
                } else {
                    result[0] = INSTALL_SHORTCUT_IS_DUPLICATE;
                }

                return true;
            }
        } else {
            result[0] = INSTALL_SHORTCUT_NO_SPACE;
        }

        return false;
    }

    private static boolean findEmptyCell(ArrayList<ItemInfo> items, int[] xy,
            int screen) {
        final int xCount = LauncherModel.getWorkspaceCellCountX();
        final int yCount = LauncherModel.getWorkspaceCellCountY();
        boolean[][] occupied = new boolean[xCount][yCount];

        int cellX, cellY, spanX, spanY;
        for (ItemInfo item : items) {
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (item.screen == screen) {
                    cellX = item.cellX;
                    cellY = item.cellY;
                    spanX = item.spanX;
                    spanY = item.spanY;
                    for (int x = cellX; 0 <= x && x < cellX + spanX && x < xCount; x++) {
                        for (int y = cellY; 0 <= y && y < cellY + spanY && y < yCount; y++) {
                            occupied[x][y] = true;
                        }
                    }
                }
            }
        }

        return CellLayout.findVacantCell(xy, 1, 1, xCount, yCount, occupied);
    }
}
