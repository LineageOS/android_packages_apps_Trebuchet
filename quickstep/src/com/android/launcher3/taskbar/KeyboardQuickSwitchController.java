/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.util.GroupTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Handles initialization of the {@link KeyboardQuickSwitchViewController}.
 */
public final class KeyboardQuickSwitchController implements
        TaskbarControllers.LoggableTaskbarController {

    static final int MAX_TASKS = 6;

    @NonNull private final ControllerCallbacks mControllerCallbacks = new ControllerCallbacks();

    // Initialized on init
    @Nullable private RecentsModel mModel;

    // Used to keep track of the last requested task list id, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private int mTaskListChangeId = -1;
    // Only empty before the recent tasks list has been loaded the first time
    @NonNull private List<GroupTask> mTasks = new ArrayList<>();
    private int mNumHiddenTasks = 0;

    // Initialized in init
    private TaskbarControllers mControllers;

    @Nullable private KeyboardQuickSwitchViewController mQuickSwitchViewController;

    /** Initialize the controller. */
    public void init(@NonNull TaskbarControllers controllers) {
        mControllers = controllers;
        mModel = RecentsModel.INSTANCE.get(controllers.taskbarActivityContext);
    }

    void onConfigurationChanged(@ActivityInfo.Config int configChanges) {
        if (mQuickSwitchViewController == null) {
            return;
        }
        if ((configChanges & (ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN)) != 0) {
            mQuickSwitchViewController.closeQuickSwitchView(true);
            return;
        }
        int currentFocusedIndex = mQuickSwitchViewController.getCurrentFocusedIndex();
        onDestroy();
        if (currentFocusedIndex != -1) {
            mControllers.taskbarActivityContext.getMainThreadHandler().post(
                    () -> openQuickSwitchView(currentFocusedIndex));
        }
    }

    void openQuickSwitchView() {
        openQuickSwitchView(-1);
    }

    private void openQuickSwitchView(int currentFocusedIndex) {
        if (mQuickSwitchViewController != null) {
            return;
        }
        TaskbarOverlayContext overlayContext =
                mControllers.taskbarOverlayController.requestWindow();
        KeyboardQuickSwitchView keyboardQuickSwitchView =
                (KeyboardQuickSwitchView) overlayContext.getLayoutInflater()
                        .inflate(
                                R.layout.keyboard_quick_switch_view,
                                overlayContext.getDragLayer(),
                                /* attachToRoot= */ false);
        mQuickSwitchViewController = new KeyboardQuickSwitchViewController(
                mControllers, overlayContext, keyboardQuickSwitchView, mControllerCallbacks);

        if (mModel.isTaskListValid(mTaskListChangeId)) {
            mQuickSwitchViewController.openQuickSwitchView(
                    mTasks, mNumHiddenTasks, /* updateTasks= */ false, currentFocusedIndex);
            return;
        }
        mTaskListChangeId = mModel.getTasks((tasks) -> {
            // Only store MAX_TASK tasks, from most to least recent
            Collections.reverse(tasks);
            mTasks = tasks.stream().limit(MAX_TASKS).collect(Collectors.toList());
            mNumHiddenTasks = Math.max(0, tasks.size() - MAX_TASKS);
            mQuickSwitchViewController.openQuickSwitchView(
                    mTasks, mNumHiddenTasks, /* updateTasks= */ true, currentFocusedIndex);
        });
    }

    void closeQuickSwitchView() {
        if (mQuickSwitchViewController == null) {
            return;
        }
        mQuickSwitchViewController.closeQuickSwitchView(true);
    }

    /**
     * See {@link TaskbarUIController#launchFocusedTask()}
     */
    int launchFocusedTask() {
        // Return -1 so that the RecentsView is not incorrectly opened when the user closes the
        // quick switch view by tapping the screen.
        return mQuickSwitchViewController == null
                ? -1 : mQuickSwitchViewController.launchFocusedTask();
    }

    void onDestroy() {
        if (mQuickSwitchViewController != null) {
            mQuickSwitchViewController.onDestroy();
        }
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "KeyboardQuickSwitchController:");

        pw.println(prefix + "\tisOpen=" + (mQuickSwitchViewController != null));
        pw.println(prefix + "\tmNumHiddenTasks=" + mNumHiddenTasks);
        pw.println(prefix + "\tmTaskListChangeId=" + mTaskListChangeId);
        pw.println(prefix + "\tmTasks=[");
        for (GroupTask task : mTasks) {
            Task task1 = task.task1;
            Task task2 = task.task2;
            ComponentName cn1 = task1.getTopComponent();
            ComponentName cn2 = task2 != null ? task2.getTopComponent() : null;
            pw.println(prefix + "\t\tt1: (id=" + task1.key.id
                    + "; package=" + (cn1 != null ? cn1.getPackageName() + ")" : "no package)")
                    + " t2: (id=" + (task2 != null ? task2.key.id : "-1")
                    + "; package=" + (cn2 != null ? cn2.getPackageName() + ")"
                    : "no package)"));
        }
        pw.println(prefix + "\t]");

        if (mQuickSwitchViewController != null) {
            mQuickSwitchViewController.dumpLogs(prefix + '\t', pw);
        }
    }

    class ControllerCallbacks {

        int getTaskCount() {
            return mNumHiddenTasks == 0 ? mTasks.size() : MAX_TASKS + 1;
        }

        @Nullable
        GroupTask getTaskAt(int index) {
            return index < 0 || index >= mTasks.size() ? null : mTasks.get(index);
        }

        void updateThumbnailInBackground(Task task, Consumer<ThumbnailData> callback) {
            mModel.getThumbnailCache().updateThumbnailInBackground(task, callback);
        }

        void updateTitleInBackground(Task task, Consumer<Task> callback) {
            mModel.getIconCache().updateIconInBackground(task, callback);
        }

        void onCloseComplete() {
            mQuickSwitchViewController = null;
        }
    }
}
