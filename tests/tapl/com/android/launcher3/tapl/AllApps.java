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

import static com.android.launcher3.tapl.LauncherInstrumentation.DEFAULT_POLL_INTERVAL;
import static com.android.launcher3.tapl.LauncherInstrumentation.WAIT_TIME_MS;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiObject2;

import com.android.launcher3.testing.shared.TestProtocol;

import java.util.stream.Collectors;

/**
 * Operations on AllApps opened from Home. Also a parent for All Apps opened from Overview.
 */
public abstract class AllApps extends LauncherInstrumentation.VisibleContainer {
    // Defer updates flag used to defer all apps updates by a test's request.
    private static final int DEFER_UPDATES_TEST = 1 << 1;

    private static final int MAX_SCROLL_ATTEMPTS = 40;

    private final int mHeight;
    private final int mIconHeight;

    AllApps(LauncherInstrumentation launcher) {
        super(launcher);
        final UiObject2 allAppsContainer = verifyActiveContainer();
        mHeight = mLauncher.getVisibleBounds(allAppsContainer).height();
        final UiObject2 appListRecycler = getAppListRecycler(allAppsContainer);
        // Wait for the recycler to populate.
        mLauncher.waitForObjectInContainer(appListRecycler, By.clazz(TextView.class));
        verifyNotFrozen("All apps freeze flags upon opening all apps");
        mIconHeight = mLauncher.getTestInfo(TestProtocol.REQUEST_ICON_HEIGHT)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    private boolean hasClickableIcon(UiObject2 allAppsContainer, UiObject2 appListRecycler,
            BySelector appIconSelector, int displayBottom) {
        final UiObject2 icon;
        try {
            icon = appListRecycler.findObject(appIconSelector);
        } catch (StaleObjectException e) {
            mLauncher.fail("All apps recycler disappeared from screen");
            return false;
        }
        if (icon == null) {
            LauncherInstrumentation.log("hasClickableIcon: icon not visible");
            return false;
        }
        final Rect iconBounds = mLauncher.getVisibleBounds(icon);
        LauncherInstrumentation.log("hasClickableIcon: icon bounds: " + iconBounds);
        if (iconBounds.height() < mIconHeight / 2) {
            LauncherInstrumentation.log("hasClickableIcon: icon has insufficient height");
            return false;
        }
        if (hasSearchBox() && iconCenterInSearchBox(allAppsContainer, icon)) {
            LauncherInstrumentation.log("hasClickableIcon: icon center is under search box");
            return false;
        }
        if (iconCenterInRecyclerTopPadding(appListRecycler, icon)) {
            LauncherInstrumentation.log(
                    "hasClickableIcon: icon center is under the app list recycler's top padding.");
            return false;
        }
        if (iconBounds.bottom > displayBottom) {
            LauncherInstrumentation.log("hasClickableIcon: icon bottom below bottom offset");
            return false;
        }
        LauncherInstrumentation.log("hasClickableIcon: icon is clickable");
        return true;
    }

    private boolean iconCenterInSearchBox(UiObject2 allAppsContainer, UiObject2 icon) {
        final Point iconCenter = icon.getVisibleCenter();
        return mLauncher.getVisibleBounds(getSearchBox(allAppsContainer)).contains(
                iconCenter.x, iconCenter.y);
    }

    private boolean iconCenterInRecyclerTopPadding(UiObject2 appListRecycler, UiObject2 icon) {
        final Point iconCenter = icon.getVisibleCenter();

        return iconCenter.y <= mLauncher.getVisibleBounds(appListRecycler).top
                + getAppsListRecyclerTopPadding();
    }

    /**
     * Finds an icon. If the icon doesn't exist, return null.
     * Scrolls the app list when needed to make sure the icon is visible.
     *
     * @param appName name of the app.
     * @return The app if found, and null if not found.
     */
    @Nullable
    public AppIcon tryGetAppIcon(String appName) {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                     "getting app icon " + appName + " on all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            final UiObject2 appListRecycler = getAppListRecycler(allAppsContainer);

            int deviceHeight = mLauncher.getRealDisplaySize().y;
            int bottomGestureStartOnScreen = mLauncher.getBottomGestureStartOnScreen();
            final BySelector appIconSelector = AppIcon.getAppIconSelector(appName, mLauncher);
            if (!hasClickableIcon(allAppsContainer, appListRecycler, appIconSelector,
                    bottomGestureStartOnScreen)) {
                scrollBackToBeginning();
                int attempts = 0;
                int scroll = getAllAppsScroll();
                try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("scrolled")) {
                    while (!hasClickableIcon(allAppsContainer, appListRecycler, appIconSelector,
                            bottomGestureStartOnScreen)) {
                        mLauncher.scrollToLastVisibleRow(
                                allAppsContainer,
                                mLauncher.getObjectsInContainer(allAppsContainer, "icon")
                                        .stream()
                                        .filter(icon ->
                                                mLauncher.getVisibleBounds(icon).top
                                                        < bottomGestureStartOnScreen)
                                        .collect(Collectors.toList()),
                                mLauncher.getVisibleBounds(appListRecycler).top
                                        + getAppsListRecyclerTopPadding()
                                        - mLauncher.getVisibleBounds(allAppsContainer).top);
                        verifyActiveContainer();
                        final int newScroll = getAllAppsScroll();
                        mLauncher.assertTrue(
                                "Scrolled in a wrong direction in AllApps: from " + scroll + " to "
                                        + newScroll, newScroll >= scroll);
                        if (newScroll == scroll) break;

                        mLauncher.assertTrue(
                                "Exceeded max scroll attempts: " + MAX_SCROLL_ATTEMPTS,
                                ++attempts <= MAX_SCROLL_ATTEMPTS);
                        scroll = newScroll;
                    }
                }
                verifyActiveContainer();
            }
            // Ignore bottom offset selection here as there might not be any scroll more scroll
            // region available.
            if (hasClickableIcon(
                    allAppsContainer, appListRecycler, appIconSelector, deviceHeight)) {

                final UiObject2 appIcon = mLauncher.waitForObjectInContainer(appListRecycler,
                        appIconSelector);
                return createAppIcon(appIcon);
            } else {
                return null;
            }
        }
    }

    /**
     * Finds an icon. Fails if the icon doesn't exist. Scrolls the app list when needed to make
     * sure the icon is visible.
     *
     * @param appName name of the app.
     * @return The app.
     */
    @NonNull
    public AppIcon getAppIcon(String appName) {
        AppIcon appIcon = tryGetAppIcon(appName);
        mLauncher.assertNotNull("Unable to scroll to a clickable icon: " + appName, appIcon);
        return appIcon;
    }

    @NonNull
    protected abstract AppIcon createAppIcon(UiObject2 icon);

    protected abstract boolean hasSearchBox();

    protected abstract int getAppsListRecyclerTopPadding();

    private void scrollBackToBeginning() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to scroll back in all apps")) {
            LauncherInstrumentation.log("Scrolling to the beginning");
            final UiObject2 allAppsContainer = verifyActiveContainer();
            final UiObject2 appListRecycler = getAppListRecycler(allAppsContainer);

            int attempts = 0;
            final Rect margins = new Rect(
                    /* left= */ 0,
                    mLauncher.getVisibleBounds(appListRecycler).top
                            + getAppsListRecyclerTopPadding() + 1,
                    /* right= */ 0,
                    /* bottom= */ 5);

            for (int scroll = getAllAppsScroll();
                    scroll != 0;
                    scroll = getAllAppsScroll()) {
                mLauncher.assertTrue("Negative scroll position", scroll > 0);

                mLauncher.assertTrue(
                        "Exceeded max scroll attempts: " + MAX_SCROLL_ATTEMPTS,
                        ++attempts <= MAX_SCROLL_ATTEMPTS);

                mLauncher.scroll(
                        allAppsContainer,
                        Direction.UP,
                        margins,
                        /* steps= */ 12,
                        /* slowDown= */ false);
            }

            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer("scrolled up")) {
                verifyActiveContainer();
            }
        }
    }

    private int getAllAppsScroll() {
        return mLauncher.getTestInfo(
                TestProtocol.REQUEST_APPS_LIST_SCROLL_Y)
                .getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }

    private UiObject2 getAppListRecycler(UiObject2 allAppsContainer) {
        return mLauncher.waitForObjectInContainer(allAppsContainer, "apps_list_view");
    }

    protected UiObject2 getSearchBox(UiObject2 allAppsContainer) {
        return mLauncher.waitForObjectInContainer(allAppsContainer, "search_container_all_apps");
    }

    /**
     * Flings forward (down) and waits the fling's end.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling forward in all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            // Start the gesture in the center to avoid starting at elements near the top.
            mLauncher.scroll(
                    allAppsContainer,
                    Direction.DOWN,
                    new Rect(0, 0, 0, mHeight / 2),
                    /* steps= */ 10,
                    /* slowDown= */ false);
            verifyActiveContainer();
        }
    }

    /**
     * Flings backward (up) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable e = mLauncher.eventsCheck();
             LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling backward in all apps")) {
            final UiObject2 allAppsContainer = verifyActiveContainer();
            // Start the gesture in the center, for symmetry with forward.
            mLauncher.scroll(
                    allAppsContainer,
                    Direction.UP,
                    new Rect(0, mHeight / 2, 0, 0),
                    /* steps= */ 10,
                    /*slowDown= */ false);
            verifyActiveContainer();
        }
    }

    /**
     * Freezes updating app list upon app install/uninstall/update.
     */
    public void freeze() {
        mLauncher.getTestInfo(TestProtocol.REQUEST_FREEZE_APP_LIST);
    }

    /**
     * Resumes updating app list upon app install/uninstall/update.
     */
    public void unfreeze() {
        mLauncher.getTestInfo(TestProtocol.REQUEST_UNFREEZE_APP_LIST);
    }

    private void verifyNotFrozen(String message) {
        mLauncher.assertEquals(message, 0, getFreezeFlags() & DEFER_UPDATES_TEST);
        mLauncher.assertTrue(message, mLauncher.waitAndGet(() -> getFreezeFlags() == 0,
                WAIT_TIME_MS, DEFAULT_POLL_INTERVAL));
    }

    private int getFreezeFlags() {
        final Bundle testInfo = mLauncher.getTestInfo(TestProtocol.REQUEST_APP_LIST_FREEZE_FLAGS);
        return testInfo == null ? 0 : testInfo.getInt(TestProtocol.TEST_INFO_RESPONSE_FIELD);
    }
}