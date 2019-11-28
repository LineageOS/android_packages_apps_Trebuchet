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

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;

import java.util.Collections;
import java.util.List;

/**
 * Common overview pane for both Launcher and fallback recents
 */
public class BaseOverview extends LauncherInstrumentation.VisibleContainer {
    private static final int FLING_SPEED = LauncherInstrumentation.isAvd() ? 500 : 1500;
    private static final int FLINGS_FOR_DISMISS_LIMIT = 40;

    BaseOverview(LauncherInstrumentation launcher) {
        super(launcher);
        verifyActiveContainer();
    }

    @Override
    protected LauncherInstrumentation.ContainerType getContainerType() {
        return LauncherInstrumentation.ContainerType.BASE_OVERVIEW;
    }

    /**
     * Flings forward (left) and waits the fling's end.
     */
    public void flingForward() {
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling forward in overview")) {
            LauncherInstrumentation.log("Overview.flingForward before fling");
            final UiObject2 overview = verifyActiveContainer();
            mLauncher.scroll(overview, Direction.LEFT, 1,
                    new Rect(mLauncher.getEdgeSensitivityWidth(), 0, 0, 0), 20);
            verifyActiveContainer();
        }
    }

    /**
     * Dismissed all tasks by scrolling to Clear-all button and pressing it.
     */
    public Workspace dismissAllTasks() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "dismissing all tasks")) {
            final BySelector clearAllSelector = mLauncher.getLauncherObjectSelector("clear_all");
            for (int i = 0;
                    i < FLINGS_FOR_DISMISS_LIMIT
                            && !verifyActiveContainer().hasObject(clearAllSelector);
                    ++i) {
                flingForward();
            }

            mLauncher.getObjectInContainer(verifyActiveContainer(), clearAllSelector).click();
            try (LauncherInstrumentation.Closable c1 = mLauncher.addContextLayer(
                    "dismissed all tasks")) {
                return new Workspace(mLauncher);
            }
        }
    }

    /**
     * Flings backward (right) and waits the fling's end.
     */
    public void flingBackward() {
        try (LauncherInstrumentation.Closable c =
                     mLauncher.addContextLayer("want to fling backward in overview")) {
            LauncherInstrumentation.log("Overview.flingBackward before fling");
            final UiObject2 overview = verifyActiveContainer();
            mLauncher.scroll(overview, Direction.RIGHT, 1,
                    new Rect(0, 0, mLauncher.getEdgeSensitivityWidth(), 0), 20);
            verifyActiveContainer();
        }
    }

    /**
     * Gets the current task in the carousel, or fails if the carousel is empty.
     *
     * @return the task in the middle of the visible tasks list.
     */
    @NonNull
    public OverviewTask getCurrentTask() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to get current task")) {
            verifyActiveContainer();
            final List<UiObject2> taskViews = mLauncher.getDevice().findObjects(
                    mLauncher.getLauncherObjectSelector("snapshot"));
            mLauncher.assertNotEquals("Unable to find a task", 0, taskViews.size());

            // taskViews contains up to 3 task views: the 'main' (having the widest visible
            // part) one in the center, and parts of its right and left siblings. Find the
            // main task view by its width.
            final UiObject2 widestTask = Collections.max(taskViews,
                    (t1, t2) -> Integer.compare(t1.getVisibleBounds().width(),
                            t2.getVisibleBounds().width()));

            return new OverviewTask(mLauncher, widestTask, this);
        }
    }
}