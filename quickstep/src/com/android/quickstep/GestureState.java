/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;

import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;

import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.tracing.GestureStateProto;
import com.android.launcher3.tracing.SwipeHandlerProto;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the state for an active system gesture, listens for events from the system and Launcher,
 * and fires events when the states change.
 */
@TargetApi(Build.VERSION_CODES.R)
public class GestureState implements RecentsAnimationCallbacks.RecentsAnimationListener {

    /**
     * Defines the end targets of a gesture and the associated state.
     */
    public enum GestureEndTarget {
        HOME(true, LAUNCHER_STATE_HOME, false, GestureStateProto.GestureEndTarget.HOME),

        RECENTS(true, LAUNCHER_STATE_OVERVIEW, true, GestureStateProto.GestureEndTarget.RECENTS),

        NEW_TASK(false, LAUNCHER_STATE_BACKGROUND, true,
                GestureStateProto.GestureEndTarget.NEW_TASK),

        LAST_TASK(false, LAUNCHER_STATE_BACKGROUND, true,
                GestureStateProto.GestureEndTarget.LAST_TASK);

        GestureEndTarget(boolean isLauncher, int containerType, boolean recentsAttachedToAppWindow,
                GestureStateProto.GestureEndTarget protoEndTarget) {
            this.isLauncher = isLauncher;
            this.containerType = containerType;
            this.recentsAttachedToAppWindow = recentsAttachedToAppWindow;
            this.protoEndTarget = protoEndTarget;
        }

        /** Whether the target is in the launcher activity. Implicitly, if the end target is going
         to Launcher, then we can not interrupt the animation to start another gesture. */
        public final boolean isLauncher;
        /** Used to log where the user ended up after the gesture ends */
        public final int containerType;
        /** Whether RecentsView should be attached to the window as we animate to this target */
        public final boolean recentsAttachedToAppWindow;
        /** The GestureStateProto enum value, used for winscope tracing. See launcher_trace.proto */
        public final GestureStateProto.GestureEndTarget protoEndTarget;
    }

    private static final String TAG = "GestureState";

    private static final ArrayList<String> STATE_NAMES = new ArrayList<>();
    public static final GestureState DEFAULT_STATE = new GestureState();

    private static int FLAG_COUNT = 0;
    private static int getFlagForIndex(String name) {
        if (DEBUG_STATES) {
            STATE_NAMES.add(name);
        }
        int index = 1 << FLAG_COUNT;
        FLAG_COUNT++;
        return index;
    }

    // Called when the end target as been set
    public static final int STATE_END_TARGET_SET =
            getFlagForIndex("STATE_END_TARGET_SET");

    // Called when the end target animation has finished
    public static final int STATE_END_TARGET_ANIMATION_FINISHED =
            getFlagForIndex("STATE_END_TARGET_ANIMATION_FINISHED");

    // Called when the recents animation has been requested to start
    public static final int STATE_RECENTS_ANIMATION_INITIALIZED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_INITIALIZED");

    // Called when the recents animation is started and the TaskAnimationManager has been updated
    // with the controller and targets
    public static final int STATE_RECENTS_ANIMATION_STARTED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_STARTED");

    // Called when the recents animation is canceled
    public static final int STATE_RECENTS_ANIMATION_CANCELED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_CANCELED");

    // Called when the recents animation finishes
    public static final int STATE_RECENTS_ANIMATION_FINISHED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_FINISHED");

    // Always called when the recents animation ends (regardless of cancel or finish)
    public static final int STATE_RECENTS_ANIMATION_ENDED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_ENDED");

    // Called when RecentsView stops scrolling and settles on a TaskView.
    public static final int STATE_RECENTS_SCROLLING_FINISHED =
            getFlagForIndex("STATE_RECENTS_SCROLLING_FINISHED");

    // Needed to interact with the current activity
    private final Intent mHomeIntent;
    private final Intent mOverviewIntent;
    private final BaseActivityInterface mActivityInterface;
    private final MultiStateCallback mStateCallback;
    private final int mGestureId;

    private ActivityManager.RunningTaskInfo mRunningTask;
    private ActivityManager.RunningTaskInfo[] mRunningTasks;
    private GestureEndTarget mEndTarget;
    private RemoteAnimationTargetCompat mLastAppearedTaskTarget;
    private Set<Integer> mPreviouslyAppearedTaskIds = new HashSet<>();
    private int mLastStartedTaskId = -1;
    private RecentsAnimationController mRecentsAnimationController;
    private HashMap<Integer, ThumbnailData> mRecentsAnimationCanceledSnapshots;

    /** The time when the swipe up gesture is triggered. */
    private long mSwipeUpStartTimeMs;

    private boolean mHandlingAtomicEvent;

    public GestureState(OverviewComponentObserver componentObserver, int gestureId) {
        mHomeIntent = componentObserver.getHomeIntent();
        mOverviewIntent = componentObserver.getOverviewIntent();
        mActivityInterface = componentObserver.getActivityInterface();
        mStateCallback = new MultiStateCallback(STATE_NAMES.toArray(new String[0]));
        mGestureId = gestureId;
    }

    public GestureState(GestureState other) {
        mHomeIntent = other.mHomeIntent;
        mOverviewIntent = other.mOverviewIntent;
        mActivityInterface = other.mActivityInterface;
        mStateCallback = other.mStateCallback;
        mGestureId = other.mGestureId;
        mRunningTask = other.mRunningTask;
        mEndTarget = other.mEndTarget;
        mLastAppearedTaskTarget = other.mLastAppearedTaskTarget;
        mPreviouslyAppearedTaskIds = other.mPreviouslyAppearedTaskIds;
        mLastStartedTaskId = other.mLastStartedTaskId;
    }

    public GestureState() {
        // Do nothing, only used for initializing the gesture state prior to user unlock
        mHomeIntent = new Intent();
        mOverviewIntent = new Intent();
        mActivityInterface = null;
        mStateCallback = new MultiStateCallback(STATE_NAMES.toArray(new String[0]));
        mGestureId = -1;
    }

    /**
     * @return whether the gesture state has the provided {@param stateMask} flags set.
     */
    public boolean hasState(int stateMask) {
        return mStateCallback.hasStates(stateMask);
    }

    /**
     * Sets the given {@param stateFlag}s.
     */
    public void setState(int stateFlag) {
        mStateCallback.setState(stateFlag);
    }

    /**
     * Adds a callback for when the states matching the given {@param stateMask} is set.
     */
    public void runOnceAtState(int stateMask, Runnable callback) {
        mStateCallback.runOnceAtState(stateMask, callback);
    }

    /**
     * @return the intent for the Home component.
     */
    public Intent getHomeIntent() {
        return mHomeIntent;
    }

    /**
     * @return the intent for the Overview component.
     */
    public Intent getOverviewIntent() {
        return mOverviewIntent;
    }

    /**
     * @return the interface to the activity handing the UI updates for this gesture.
     */
    public <S extends BaseState<S>,
            T extends StatefulActivity<S>> BaseActivityInterface<S, T> getActivityInterface() {
        return mActivityInterface;
    }

    /**
     * @return the id for this particular gesture.
     */
    public int getGestureId() {
        return mGestureId;
    }

    /**
     * @return the running task for this gesture.
     */
    public ActivityManager.RunningTaskInfo getRunningTask() {
        return mRunningTask;
    }

    /**
     * This will array will contain the task returned by {@link #getRunningTask()}
     * @return the running tasks for this gesture.
     */
    public ActivityManager.RunningTaskInfo[] getRunningTasks() {
        return mRunningTasks;
    }

    /**
     * @return the running task id for this gesture.
     */
    public int getRunningTaskId() {
        return mRunningTask != null ? mRunningTask.taskId : -1;
    }

    /**
     * Updates the running task for the gesture to be the given {@param runningTask}.
     */
    public void updateRunningTask(ActivityManager.RunningTaskInfo runningTask) {
        mRunningTask = runningTask;
    }

    /**
     * TODO(b/210903248) refactor to consolidate w/ method above
     * Updates the running task for the gesture to be the given {@param runningTask}.
     */
    public void updateRunningTasks(ActivityManager.RunningTaskInfo[] runningTasks) {
        mRunningTasks = runningTasks;
        updateRunningTask(runningTasks[0]);
    }

    /**
     * Updates the last task that appeared during this gesture.
     */
    public void updateLastAppearedTaskTarget(RemoteAnimationTargetCompat lastAppearedTaskTarget) {
        mLastAppearedTaskTarget = lastAppearedTaskTarget;
        if (lastAppearedTaskTarget != null) {
            mPreviouslyAppearedTaskIds.add(lastAppearedTaskTarget.taskId);
        }
    }

    /**
     * @return The id of the task that appeared during this gesture.
     */
    public int getLastAppearedTaskId() {
        return mLastAppearedTaskTarget != null ? mLastAppearedTaskTarget.taskId : -1;
    }

    public void updatePreviouslyAppearedTaskIds(Set<Integer> previouslyAppearedTaskIds) {
        mPreviouslyAppearedTaskIds = previouslyAppearedTaskIds;
    }

    public Set<Integer> getPreviouslyAppearedTaskIds() {
        return mPreviouslyAppearedTaskIds;
    }

    /**
     * Updates the last task that we started via startActivityFromRecents() during this gesture.
     */
    public void updateLastStartedTaskId(int lastStartedTaskId) {
        mLastStartedTaskId = lastStartedTaskId;
    }

    /**
     * @return The id of the task that was most recently started during this gesture, or -1 if
     * no task has been started yet (i.e. we haven't settled on a new task).
     */
    public int getLastStartedTaskId() {
        return mLastStartedTaskId;
    }

    /**
     * @return the end target for this gesture (if known).
     */
    public GestureEndTarget getEndTarget() {
        return mEndTarget;
    }

    /**
     * Sets the end target of this gesture and immediately notifies the state changes.
     */
    public void setEndTarget(GestureEndTarget target) {
        setEndTarget(target, true /* isAtomic */);
    }

    /**
     * Sets the end target of this gesture, but if {@param isAtomic} is {@code false}, then the
     * caller must explicitly set {@link #STATE_END_TARGET_ANIMATION_FINISHED} themselves.
     */
    public void setEndTarget(GestureEndTarget target, boolean isAtomic) {
        mEndTarget = target;
        mStateCallback.setState(STATE_END_TARGET_SET);
        ActiveGestureLog.INSTANCE.addLog("setEndTarget " + mEndTarget);
        if (isAtomic) {
            mStateCallback.setState(STATE_END_TARGET_ANIMATION_FINISHED);
        }
    }

    /**
     * Indicates if the gesture is handling an atomic event like a click and not a
     * user controlled gesture.
     */
    public void setHandlingAtomicEvent(boolean handlingAtomicEvent) {
        mHandlingAtomicEvent = true;
    }

    /**
     * Returns true if the gesture is handling an atomic event like a click and not a
     * user controlled gesture.
     */
    public boolean isHandlingAtomicEvent() {
        return mHandlingAtomicEvent;
    }

    /**
     * @return whether the current gesture is still running a recents animation to a state in the
     *         Launcher or Recents activity.
     */
    public boolean isRunningAnimationToLauncher() {
        return isRecentsAnimationRunning() && mEndTarget != null && mEndTarget.isLauncher;
    }

    /**
     * @return whether the recents animation is started but not yet ended
     */
    public boolean isRecentsAnimationRunning() {
        return mStateCallback.hasStates(STATE_RECENTS_ANIMATION_STARTED)
                && !mStateCallback.hasStates(STATE_RECENTS_ANIMATION_ENDED);
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        mRecentsAnimationController = controller;
        mStateCallback.setState(STATE_RECENTS_ANIMATION_STARTED);
    }

    @Override
    public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
        mRecentsAnimationCanceledSnapshots = thumbnailDatas;
        mStateCallback.setState(STATE_RECENTS_ANIMATION_CANCELED);
        mStateCallback.setState(STATE_RECENTS_ANIMATION_ENDED);
        if (mRecentsAnimationCanceledSnapshots != null) {
            // Clean up the screenshot to finalize the recents animation cancel
            if (mRecentsAnimationController != null) {
                mRecentsAnimationController.cleanupScreenshot();
            }
            mRecentsAnimationCanceledSnapshots = null;
        }
    }

    @Override
    public void onRecentsAnimationFinished(RecentsAnimationController controller) {
        mStateCallback.setState(STATE_RECENTS_ANIMATION_FINISHED);
        mStateCallback.setState(STATE_RECENTS_ANIMATION_ENDED);
    }

    /**
     * Returns and clears the canceled animation thumbnail data. This call only returns a value
     * while STATE_RECENTS_ANIMATION_CANCELED state is being set, and the caller is responsible for
     * calling {@link RecentsAnimationController#cleanupScreenshot()}.
     */
    @Nullable
    HashMap<Integer, ThumbnailData> consumeRecentsAnimationCanceledSnapshot() {
        if (mRecentsAnimationCanceledSnapshots != null) {
            HashMap<Integer, ThumbnailData> data =
                    new HashMap<Integer, ThumbnailData>(mRecentsAnimationCanceledSnapshots);
            mRecentsAnimationCanceledSnapshots = null;
            return data;
        }
        return null;
    }

    void setSwipeUpStartTimeMs(long uptimeMs) {
        mSwipeUpStartTimeMs = uptimeMs;
    }

    long getSwipeUpStartTimeMs() {
        return mSwipeUpStartTimeMs;
    }

    public void dump(PrintWriter pw) {
        pw.println("GestureState:");
        pw.println("  gestureID=" + mGestureId);
        pw.println("  runningTask=" + mRunningTask);
        pw.println("  endTarget=" + mEndTarget);
        pw.println("  lastAppearedTaskTargetId=" + getLastAppearedTaskId());
        pw.println("  lastStartedTaskId=" + mLastStartedTaskId);
        pw.println("  isRecentsAnimationRunning=" + isRecentsAnimationRunning());
    }

    /**
     * Used for winscope tracing, see launcher_trace.proto
     * @see com.android.systemui.shared.tracing.ProtoTraceable#writeToProto
     * @param swipeHandlerProto The parent of this proto message.
     */
    public void writeToProto(SwipeHandlerProto.Builder swipeHandlerProto) {
        GestureStateProto.Builder gestureStateProto = GestureStateProto.newBuilder();
        gestureStateProto.setEndTarget(mEndTarget == null
                ? GestureStateProto.GestureEndTarget.UNSET
                : mEndTarget.protoEndTarget);
        swipeHandlerProto.setGestureState(gestureStateProto);
    }
}
