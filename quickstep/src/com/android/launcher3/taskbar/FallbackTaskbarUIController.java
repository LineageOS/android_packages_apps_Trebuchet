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
package com.android.launcher3.taskbar;

import static com.android.launcher3.Utilities.isRunningInTestHarness;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_APP;
import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_IN_STASHED_LAUNCHER_STATE;

import android.animation.Animator;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.StateManager;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.views.RecentsView;

/**
 * A data source which integrates with the fallback RecentsActivity instance (for 3P launchers).
 */
public class FallbackTaskbarUIController extends TaskbarUIController {

    private final RecentsActivity mRecentsActivity;

    private final StateManager.StateListener<RecentsState> mStateListener =
            new StateManager.StateListener<RecentsState>() {
                @Override
                public void onStateTransitionStart(RecentsState toState) {
                    animateToRecentsState(toState);

                    // Handle tapping on live tile.
                    getRecentsView().setTaskLaunchListener(toState == RecentsState.DEFAULT
                            ? (() -> animateToRecentsState(RecentsState.BACKGROUND_APP)) : null);
                }

                @Override
                public void onStateTransitionComplete(RecentsState finalState) {
                    boolean finalStateDefault = finalState == RecentsState.DEFAULT;
                    // TODO(b/268120202) Taskbar shows up on 3P home, currently we don't go to
                    //  overview from 3P home. Either implement that or it'll change w/ contextual?
                    boolean disallowLongClick = finalState == RecentsState.OVERVIEW_SPLIT_SELECT;
                    Utilities.setOverviewDragState(mControllers,
                            finalStateDefault /*disallowGlobalDrag*/, disallowLongClick,
                            finalStateDefault /*allowInitialSplitSelection*/);
                }
            };

    public FallbackTaskbarUIController(RecentsActivity recentsActivity) {
        mRecentsActivity = recentsActivity;
    }

    @Override
    protected void init(TaskbarControllers taskbarControllers) {
        super.init(taskbarControllers);

        mRecentsActivity.setTaskbarUIController(this);
        mRecentsActivity.getStateManager().addStateListener(mStateListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecentsActivity.setTaskbarUIController(null);
        mRecentsActivity.getStateManager().removeStateListener(mStateListener);
    }

    /**
     * Creates an animation to animate the taskbar for the given state (but does not start it).
     * Currently this animation just force stashes the taskbar in Overview.
     */
    public Animator createAnimToRecentsState(RecentsState toState, long duration) {
        // Force stash the taskbar in overview modal state or when going home. We do not force
        // stash on home when running in a test as 3p launchers rely on taskbar instead of hotseat.
        boolean isGoingHome = toState == RecentsState.HOME && !isRunningInTestHarness();
        boolean useStashedLauncherState = toState.hasOverviewActions() || isGoingHome;
        boolean stashedLauncherState = useStashedLauncherState && (
                (FeatureFlags.ENABLE_GRID_ONLY_OVERVIEW.get() && toState == RecentsState.MODAL_TASK)
                        || isGoingHome);
        TaskbarStashController stashController = mControllers.taskbarStashController;
        // Set both FLAG_IN_STASHED_LAUNCHER_STATE and FLAG_IN_APP to ensure the state is respected.
        // For all other states, just use the current stashed-in-app setting (e.g. if long clicked).
        stashController.updateStateForFlag(FLAG_IN_STASHED_LAUNCHER_STATE, stashedLauncherState);
        stashController.updateStateForFlag(FLAG_IN_APP, !useStashedLauncherState);
        return stashController.createApplyStateAnimator(duration);
    }

    private void animateToRecentsState(RecentsState toState) {
        Animator anim = createAnimToRecentsState(toState,
                mControllers.taskbarStashController.getStashDuration());
        if (anim != null) {
            anim.start();
        }
    }

    @Override
    public RecentsView getRecentsView() {
        return mRecentsActivity.getOverviewPanel();
    }
}
