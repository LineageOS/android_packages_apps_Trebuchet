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

package com.android.launcher3.tapl;

import android.graphics.Point;
import android.graphics.Rect;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.TestProtocol;

import java.util.regex.Pattern;

/**
 * App icon, whether in all apps or in workspace/
 */
public final class AppIcon extends Launchable implements FolderDragTarget {

    private static final Pattern LONG_CLICK_EVENT = Pattern.compile("onAllAppsItemLongClick");

    AppIcon(LauncherInstrumentation launcher, UiObject2 icon) {
        super(launcher, icon);
    }

    static BySelector getAppIconSelector(String appName, LauncherInstrumentation launcher) {
        return By.clazz(TextView.class).text(appName).pkg(launcher.getLauncherPackageName());
    }

    /**
     * Long-clicks the icon to open its menu.
     */
    public AppIconMenu openMenu() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            return new AppIconMenu(mLauncher, mLauncher.clickAndGet(
                    mObject, "popup_container", LONG_CLICK_EVENT));
        }
    }

    /**
     * Long-clicks the icon to open its menu, and looks at the deep shortcuts container only.
     */
    public AppIconMenu openDeepShortcutMenu() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            return new AppIconMenu(mLauncher, mLauncher.clickAndGet(
                    mObject, "deep_shortcuts_container", LONG_CLICK_EVENT));
        }
    }

    /**
     * Drag the AppIcon to the given position of other icon. The drag must result in a folder.
     *
     * @param target the destination icon.
     */
    @NonNull
    public FolderIcon dragToIcon(FolderDragTarget target) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer("want to drag icon")) {
            final Rect dropBounds = target.getDropLocationBounds();
            Workspace.dragIconToWorkspace(
                    mLauncher, this,
                    () -> {
                        final Rect bounds = target.getDropLocationBounds();
                        return new Point(bounds.centerX(), bounds.centerY());
                    },
                    getLongPressIndicator());
            FolderIcon result = target.getTargetFolder(dropBounds);
            mLauncher.assertTrue("Can't find the target folder.", result != null);
            return result;
        }
    }

    @Override
    protected void addExpectedEventsForLongClick() {
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LONG_CLICK_EVENT);
    }

    @Override
    protected String getLongPressIndicator() {
        return "popup_container";
    }

    @Override
    protected void expectActivityStartEvents() {
        mLauncher.expectEvent(TestProtocol.SEQUENCE_MAIN, LauncherInstrumentation.EVENT_START);
    }

    @Override
    protected String launchableType() {
        return "app icon";
    }

    @Override
    public Rect getDropLocationBounds() {
        return mLauncher.getVisibleBounds(mObject);
    }

    @Override
    public FolderIcon getTargetFolder(Rect bounds) {
        for (FolderIcon folderIcon : mLauncher.getWorkspace().getFolderIcons()) {
            final Rect folderIconBounds = folderIcon.getDropLocationBounds();
            if (bounds.contains(folderIconBounds.centerX(), folderIconBounds.centerY())) {
                return folderIcon;
            }
        }
        return null;
    }
}
