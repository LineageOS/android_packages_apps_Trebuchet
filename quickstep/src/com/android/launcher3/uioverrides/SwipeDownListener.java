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

package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.NORMAL;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.TouchController;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class SwipeDownListener implements TouchController {
    private static final String PREF_STATUSBAR_EXPAND = "pref_expand_statusbar";

    private GestureDetector mGestureDetector;
    private Launcher mLauncher;

    public SwipeDownListener(Launcher launcher) {
        SharedPreferences prefs = Utilities.getPrefs(launcher.getApplicationContext());

        mLauncher = launcher;
        mGestureDetector = new GestureDetector(launcher,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vy) {
                if (prefs.getBoolean(PREF_STATUSBAR_EXPAND, true) && e1.getY() < e2.getY()) {
                    expandStatusBar(launcher);
                }
                return true;
            }
        });
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (mLauncher.isInState(NORMAL)) {
            mGestureDetector.onTouchEvent(ev);
        }
        return false;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return false;
    }

    private void expandStatusBar(Context context) {
        try {
            Object service = context.getSystemService("statusbar");
            Class<?> manager = Class.forName("android.app.StatusBarManager");
            Method expand = manager.getMethod("expandNotificationsPanel");
            expand.invoke(service);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                InvocationTargetException e) {
            Log.w("Reflection",
                    "Can't to invoke android.app.StatusBarManager$expandNotificationsPanel");
        }
    }
}