/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.SharedPreferences;
import com.android.launcher3.stats.LauncherStats;

public class WallpaperChangedReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent data) {
        LauncherAppState.setApplicationContext(context.getApplicationContext());
        LauncherAppState appState = LauncherAppState.getInstance();
        appState.onWallpaperChanged();
        SharedPreferences prefs = context.getSharedPreferences(LauncherAppState
                        .getSharedPreferencesKey(), Context.MODE_PRIVATE);
        boolean fromSelf = prefs.getBoolean(Launcher.LONGPRESS_CHANGE, false);
        if (fromSelf) {
            prefs.edit().putBoolean(Launcher.LONGPRESS_CHANGE, false).apply();
            LauncherApplication.getLauncherStats().sendWallpaperChangedEvent(
                    LauncherStats.ORIGIN_TREB_LONGPRESS);
        } else {
            LauncherApplication.getLauncherStats().sendWallpaperChangedEvent(
                    LauncherStats.ORIGIN_CHOOSER);
        }

    }
}
