/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.tapl;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.UiObject2;

public class Folder {

    protected static final String FOLDER_CONTENT_RES_ID = "folder_content";

    private final UiObject2 mContainer;
    private final LauncherInstrumentation mLauncher;

    Folder(LauncherInstrumentation launcher) {
        this.mLauncher = launcher;
        this.mContainer = launcher.waitForLauncherObject(FOLDER_CONTENT_RES_ID);
    }

    /**
     * Find an app icon with given name or raise assertion error.
     */
    @NonNull
    public AppIcon getAppIcon(String appName) {
        try (LauncherInstrumentation.Closable ignored = mLauncher.addContextLayer(
                "Want to get app icon in folder")) {
            return new AppIcon(mLauncher,
                    mLauncher.waitForObjectInContainer(
                            mContainer,
                            AppIcon.getAppIconSelector(appName, mLauncher)));
        }
    }

    private void touchOutsideFolder() {
        Rect containerBounds = mLauncher.getVisibleBounds(this.mContainer);
        final long downTime = SystemClock.uptimeMillis();
        Point containerLeftTopCorner = new Point(containerBounds.left - 1, containerBounds.top - 1);
        mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN,
                containerLeftTopCorner, LauncherInstrumentation.GestureScope.INSIDE);
        mLauncher.sendPointer(downTime, downTime, MotionEvent.ACTION_UP,
                containerLeftTopCorner, LauncherInstrumentation.GestureScope.INSIDE);
    }

    /**
     * CLose opened folder if possible. It throws assertion error if the folder is already closed.
     */
    public Workspace close() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "Want to close opened folder")) {
            mLauncher.waitForLauncherObject(FOLDER_CONTENT_RES_ID);
            touchOutsideFolder();
            mLauncher.waitUntilLauncherObjectGone(FOLDER_CONTENT_RES_ID);
            return mLauncher.getWorkspace();
        }
    }
}
