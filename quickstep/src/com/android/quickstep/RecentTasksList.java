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

package com.android.quickstep;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.util.SparseBooleanArray;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.util.GroupTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.recents.IRecentTasksListener;
import com.android.wm.shell.util.GroupedRecentTaskInfo;
import com.android.wm.shell.util.SplitBounds;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * Manages the recent task list from the system, caching it as necessary.
 */
@TargetApi(Build.VERSION_CODES.R)
public class RecentTasksList {

    private static final TaskLoadResult INVALID_RESULT = new TaskLoadResult(-1, false, 0);

    private final KeyguardManager mKeyguardManager;
    private final LooperExecutor mMainThreadExecutor;
    private final SystemUiProxy mSysUiProxy;

    // The list change id, increments as the task list changes in the system
    private int mChangeId;
    // Whether we are currently updating the tasks in the background (up to when the result is
    // posted back on the main thread)
    private boolean mLoadingTasksInBackground;

    private TaskLoadResult mResultsBg = INVALID_RESULT;
    private TaskLoadResult mResultsUi = INVALID_RESULT;

    private RecentsModel.RunningTasksListener mRunningTasksListener;
    // Tasks are stored in order of least recently launched to most recently launched.
    private ArrayList<ActivityManager.RunningTaskInfo> mRunningTasks;

    public RecentTasksList(LooperExecutor mainThreadExecutor, KeyguardManager keyguardManager,
            SystemUiProxy sysUiProxy) {
        mMainThreadExecutor = mainThreadExecutor;
        mKeyguardManager = keyguardManager;
        mChangeId = 1;
        mSysUiProxy = sysUiProxy;
        sysUiProxy.registerRecentTasksListener(new IRecentTasksListener.Stub() {
            @Override
            public void onRecentTasksChanged() throws RemoteException {
                mMainThreadExecutor.execute(RecentTasksList.this::onRecentTasksChanged);
            }

            @Override
            public void onRunningTaskAppeared(ActivityManager.RunningTaskInfo taskInfo) {
                mMainThreadExecutor.execute(() -> {
                    RecentTasksList.this.onRunningTaskAppeared(taskInfo);
                });
            }

            @Override
            public void onRunningTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
                mMainThreadExecutor.execute(() -> {
                    RecentTasksList.this.onRunningTaskVanished(taskInfo);
                });
            }
        });
        // We may receive onRunningTaskAppeared events later for tasks which have already been
        // included in the list returned by mSysUiProxy.getRunningTasks(), or may receive
        // onRunningTaskVanished for tasks not included in the returned list. These cases will be
        // addressed when the tasks are added to/removed from mRunningTasks.
        initRunningTasks(mSysUiProxy.getRunningTasks(Integer.MAX_VALUE));
    }

    @VisibleForTesting
    public boolean isLoadingTasksInBackground() {
        return mLoadingTasksInBackground;
    }

    /**
     * Fetches the task keys skipping any local cache.
     */
    public void getTaskKeys(int numTasks, Consumer<ArrayList<GroupTask>> callback) {
        // Kick off task loading in the background
        UI_HELPER_EXECUTOR.execute(() -> {
            ArrayList<GroupTask> tasks = loadTasksInBackground(numTasks, -1,
                    true /* loadKeysOnly */);
            mMainThreadExecutor.execute(() -> callback.accept(tasks));
        });
    }

    /**
     * Asynchronously fetches the list of recent tasks, reusing cached list if available.
     *
     * @param loadKeysOnly Whether to load other associated task data, or just the key
     * @param callback The callback to receive the list of recent tasks
     * @return The change id of the current task list
     */
    public synchronized int getTasks(boolean loadKeysOnly,
            Consumer<ArrayList<GroupTask>> callback) {
        final int requestLoadId = mChangeId;
        if (mResultsUi.isValidForRequest(requestLoadId, loadKeysOnly)) {
            // The list is up to date, send the callback on the next frame,
            // so that requestID can be returned first.
            if (callback != null) {
                // Copy synchronously as the changeId might change by next frame
                ArrayList<GroupTask> result = copyOf(mResultsUi);
                mMainThreadExecutor.post(() -> {
                    callback.accept(result);
                });
            }

            return requestLoadId;
        }

        // Kick off task loading in the background
        mLoadingTasksInBackground = true;
        UI_HELPER_EXECUTOR.execute(() -> {
            if (!mResultsBg.isValidForRequest(requestLoadId, loadKeysOnly)) {
                mResultsBg = loadTasksInBackground(Integer.MAX_VALUE, requestLoadId, loadKeysOnly);
            }
            TaskLoadResult loadResult = mResultsBg;
            mMainThreadExecutor.execute(() -> {
                mLoadingTasksInBackground = false;
                mResultsUi = loadResult;
                if (callback != null) {
                    ArrayList<GroupTask> result = copyOf(mResultsUi);
                    callback.accept(result);
                }
            });
        });

        return requestLoadId;
    }

    /**
     * @return Whether the provided {@param changeId} is the latest recent tasks list id.
     */
    public synchronized boolean isTaskListValid(int changeId) {
        return mChangeId == changeId;
    }

    public void onRecentTasksChanged() {
        invalidateLoadedTasks();
    }

    private synchronized void invalidateLoadedTasks() {
        UI_HELPER_EXECUTOR.execute(() -> mResultsBg = INVALID_RESULT);
        mResultsUi = INVALID_RESULT;
        mChangeId++;
    }

     /**
     * Registers a listener for running tasks
     */
    public void registerRunningTasksListener(RecentsModel.RunningTasksListener listener) {
        mRunningTasksListener = listener;
    }

    /**
     * Removes the previously registered running tasks listener
     */
    public void unregisterRunningTasksListener() {
        mRunningTasksListener = null;
    }

    private void initRunningTasks(ArrayList<ActivityManager.RunningTaskInfo> runningTasks) {
        // Tasks are retrieved in order of most recently launched/used to least recently launched.
        mRunningTasks = new ArrayList<>(runningTasks);
        Collections.reverse(mRunningTasks);
    }

    /**
     * Gets the set of running tasks.
     */
    public ArrayList<ActivityManager.RunningTaskInfo> getRunningTasks() {
        return mRunningTasks;
    }

    private void onRunningTaskAppeared(ActivityManager.RunningTaskInfo taskInfo) {
        // Make sure this task is not already in the list
        for (ActivityManager.RunningTaskInfo existingTask : mRunningTasks) {
            if (taskInfo.taskId == existingTask.taskId) {
                return;
            }
        }
        mRunningTasks.add(taskInfo);
        if (mRunningTasksListener != null) {
            mRunningTasksListener.onRunningTasksChanged();
        }
    }

    private void onRunningTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        // Find the task from the list of running tasks, if it exists
        for (ActivityManager.RunningTaskInfo existingTask : mRunningTasks) {
            if (existingTask.taskId != taskInfo.taskId) continue;

            mRunningTasks.remove(existingTask);
            if (mRunningTasksListener != null) {
                mRunningTasksListener.onRunningTasksChanged();
            }
            return;
        }
    }

    /**
     * Loads and creates a list of all the recent tasks.
     */
    @VisibleForTesting
    TaskLoadResult loadTasksInBackground(int numTasks, int requestId, boolean loadKeysOnly) {
        int currentUserId = Process.myUserHandle().getIdentifier();
        ArrayList<GroupedRecentTaskInfo> rawTasks =
                mSysUiProxy.getRecentTasks(numTasks, currentUserId);
        // The raw tasks are given in most-recent to least-recent order, we need to reverse it
        Collections.reverse(rawTasks);

        SparseBooleanArray tmpLockedUsers = new SparseBooleanArray() {
            @Override
            public boolean get(int key) {
                if (indexOfKey(key) < 0) {
                    // Fill the cached locked state as we fetch
                    put(key, mKeyguardManager.isDeviceLocked(key));
                }
                return super.get(key);
            }
        };

        TaskLoadResult allTasks = new TaskLoadResult(requestId, loadKeysOnly, rawTasks.size());
        for (GroupedRecentTaskInfo rawTask : rawTasks) {
            if (rawTask.getType() == GroupedRecentTaskInfo.TYPE_FREEFORM) {
                GroupTask desktopTask = createDesktopTask(rawTask);
                allTasks.add(desktopTask);
                continue;
            }
            ActivityManager.RecentTaskInfo taskInfo1 = rawTask.getTaskInfo1();
            ActivityManager.RecentTaskInfo taskInfo2 = rawTask.getTaskInfo2();
            Task.TaskKey task1Key = new Task.TaskKey(taskInfo1);
            Task task1 = loadKeysOnly
                    ? new Task(task1Key)
                    : Task.from(task1Key, taskInfo1,
                            tmpLockedUsers.get(task1Key.userId) /* isLocked */);
            task1.setLastSnapshotData(taskInfo1);
            Task task2 = null;
            if (taskInfo2 != null) {
                Task.TaskKey task2Key = new Task.TaskKey(taskInfo2);
                task2 = loadKeysOnly
                        ? new Task(task2Key)
                        : Task.from(task2Key, taskInfo2,
                                tmpLockedUsers.get(task2Key.userId) /* isLocked */);
                task2.setLastSnapshotData(taskInfo2);
            }
            final SplitConfigurationOptions.SplitBounds launcherSplitBounds =
                    convertSplitBounds(rawTask.getSplitBounds());
            allTasks.add(new GroupTask(task1, task2, launcherSplitBounds));
        }

        return allTasks;
    }

    private GroupTask createDesktopTask(GroupedRecentTaskInfo taskInfo) {
        // TODO(b/244348395): create a subclass of GroupTask for desktop tile
        // We need a single task information as the primary task. Use the first task
        Task.TaskKey key = new Task.TaskKey(taskInfo.getTaskInfo1());
        Task task = new Task(key);
        task.desktopTile = true;
        task.topActivity = key.sourceComponent;
        return new GroupTask(task, null, null);
    }

    private SplitConfigurationOptions.SplitBounds convertSplitBounds(
            SplitBounds shellSplitBounds) {
        return shellSplitBounds == null ?
                null :
                new SplitConfigurationOptions.SplitBounds(
                        shellSplitBounds.leftTopBounds, shellSplitBounds.rightBottomBounds,
                        shellSplitBounds.leftTopTaskId, shellSplitBounds.rightBottomTaskId);
    }

    private ArrayList<GroupTask> copyOf(ArrayList<GroupTask> tasks) {
        ArrayList<GroupTask> newTasks = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            newTasks.add(new GroupTask(tasks.get(i)));
        }
        return newTasks;
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "RecentTasksList:");
        writer.println(prefix + "  mChangeId=" + mChangeId);
        writer.println(prefix + "  mResultsUi=[id=" + mResultsUi.mRequestId + ", tasks=");
        for (GroupTask task : mResultsUi) {
            writer.println(prefix + "    t1=" + task.task1.key.id
                    + " t2=" + (task.hasMultipleTasks() ? task.task2.key.id : "-1"));
        }
        writer.println(prefix + "  ]");
        int currentUserId = Process.myUserHandle().getIdentifier();
        ArrayList<GroupedRecentTaskInfo> rawTasks =
                mSysUiProxy.getRecentTasks(Integer.MAX_VALUE, currentUserId);
        writer.println(prefix + "  rawTasks=[");
        for (GroupedRecentTaskInfo task : rawTasks) {
            writer.println(prefix + "    t1=" + task.getTaskInfo1().taskId
                    + " t2=" + (task.getTaskInfo2() != null ? task.getTaskInfo2().taskId : "-1"));
        }
        writer.println(prefix + "  ]");
    }

    private static class TaskLoadResult extends ArrayList<GroupTask> {

        final int mRequestId;

        // If the result was loaded with keysOnly  = true
        final boolean mKeysOnly;

        TaskLoadResult(int requestId, boolean keysOnly, int size) {
            super(size);
            mRequestId = requestId;
            mKeysOnly = keysOnly;
        }

        boolean isValidForRequest(int requestId, boolean loadKeysOnly) {
            return mRequestId == requestId && (!mKeysOnly || loadKeysOnly);
        }
    }
}