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
package com.android.launcher3.uioverrides.touchcontrollers;

import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_BUTTONS;
import static com.android.launcher3.LauncherState.QUICK_SWITCH;
import static com.android.launcher3.anim.AlphaUpdateListener.ALPHA_CUTOFF_THRESHOLD;
import static com.android.launcher3.anim.Interpolators.ACCEL_0_75;
import static com.android.launcher3.anim.Interpolators.DEACCEL_5;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_UNKNOWN_SWIPEDOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_UNKNOWN_SWIPEUP;
import static com.android.launcher3.logging.StatsLogManager.getLauncherAtomEvent;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_VERTICAL_PROGRESS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW;
import static com.android.launcher3.touch.BothAxesSwipeDetector.DIRECTION_RIGHT;
import static com.android.launcher3.touch.BothAxesSwipeDetector.DIRECTION_UP;
import static com.android.launcher3.uioverrides.states.QuickstepAtomicAnimationFactory.INDEX_PAUSE_TO_OVERVIEW_ANIM;
import static com.android.launcher3.util.DefaultDisplay.getSingleFrameMs;
import static com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC;
import static com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState.CANCEL;
import static com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState.HIDE;
import static com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState.PEEK;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.OverviewScrim;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.BaseSwipeDetector;
import com.android.launcher3.touch.BothAxesSwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.VibratorWrapper;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.util.ShelfPeekAnim;
import com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.views.LauncherRecentsView;

/**
 * Handles quick switching to a recent task from the home screen. To give as much flexibility to
 * the user as possible, also handles swipe up and hold to go to overview and swiping back home.
 */
public class NoButtonQuickSwitchTouchController implements TouchController,
        BothAxesSwipeDetector.Listener, MotionPauseDetector.OnMotionPauseListener {

    /** The minimum progress of the scale/translationY animation until drag end. */
    private static final float Y_ANIM_MIN_PROGRESS = 0.25f;
    private static final Interpolator FADE_OUT_INTERPOLATOR = DEACCEL_5;
    private static final Interpolator TRANSLATE_OUT_INTERPOLATOR = ACCEL_0_75;
    private static final Interpolator SCALE_DOWN_INTERPOLATOR = LINEAR;

    private final BaseQuickstepLauncher mLauncher;
    private final BothAxesSwipeDetector mSwipeDetector;
    private final ShelfPeekAnim mShelfPeekAnim;
    private final float mXRange;
    private final float mYRange;
    private final float mMaxYProgress;
    private final MotionPauseDetector mMotionPauseDetector;
    private final float mMotionPauseMinDisplacement;
    private final LauncherRecentsView mRecentsView;

    private boolean mNoIntercept;
    private LauncherState mStartState;

    private boolean mIsHomeScreenVisible = true;

    // As we drag, we control 3 animations: one to get non-overview components out of the way,
    // and the other two to set overview properties based on x and y progress.
    private AnimatorPlaybackController mNonOverviewAnim;
    private AnimatorPlaybackController mXOverviewAnim;
    private AnimatedFloat mYOverviewAnim;

    public NoButtonQuickSwitchTouchController(BaseQuickstepLauncher launcher) {
        mLauncher = launcher;
        mSwipeDetector = new BothAxesSwipeDetector(mLauncher, this);
        mShelfPeekAnim = mLauncher.getShelfPeekAnim();
        mRecentsView = mLauncher.getOverviewPanel();
        mXRange = mLauncher.getDeviceProfile().widthPx / 2f;
        mYRange = LayoutUtils.getShelfTrackingDistance(
            mLauncher, mLauncher.getDeviceProfile(), mRecentsView.getPagedOrientationHandler());
        mMaxYProgress = mLauncher.getDeviceProfile().heightPx / mYRange;
        mMotionPauseDetector = new MotionPauseDetector(mLauncher);
        mMotionPauseMinDisplacement = mLauncher.getResources().getDimension(
                R.dimen.motion_pause_detector_min_displacement_from_app);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mNoIntercept = !canInterceptTouch(ev);
            if (mNoIntercept) {
                return false;
            }

            // Only detect horizontal swipe for intercept, then we will allow swipe up as well.
            mSwipeDetector.setDetectableScrollConditions(DIRECTION_RIGHT,
                    false /* ignoreSlopWhenSettling */);
        }

        if (mNoIntercept) {
            return false;
        }

        onControllerTouchEvent(ev);
        return mSwipeDetector.isDraggingOrSettling();
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mSwipeDetector.onTouchEvent(ev);
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (!mLauncher.isInState(LauncherState.NORMAL)) {
            return false;
        }
        if ((ev.getEdgeFlags() & Utilities.EDGE_NAV_BAR) == 0) {
            return false;
        }
        int stateFlags = SystemUiProxy.INSTANCE.get(mLauncher).getLastSystemUiStateFlags();
        if ((stateFlags & SYSUI_STATE_OVERVIEW_DISABLED) != 0) {
            return false;
        }
        return true;
    }

    @Override
    public void onDragStart(boolean start) {
        mMotionPauseDetector.clear();
        if (start) {
            mStartState = mLauncher.getStateManager().getState();

            mMotionPauseDetector.setOnMotionPauseListener(this);

            // We have detected horizontal drag start, now allow swipe up as well.
            mSwipeDetector.setDetectableScrollConditions(DIRECTION_RIGHT | DIRECTION_UP,
                    false /* ignoreSlopWhenSettling */);

            setupAnimators();
        }
    }

    @Override
    public void onMotionPauseChanged(boolean isPaused) {
        VibratorWrapper.INSTANCE.get(mLauncher).vibrate(OVERVIEW_HAPTIC);

        if (FeatureFlags.ENABLE_OVERVIEW_ACTIONS.get()) {
            return;
        }

        ShelfAnimState shelfState = isPaused ? PEEK : HIDE;
        if (shelfState == PEEK) {
            // Some shelf elements (e.g. qsb) were hidden, but we need them visible when peeking.
            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            allAppsController.setAlphas(
                    NORMAL, new StateAnimationConfig(), NO_ANIM_PROPERTY_SETTER);

            if ((OVERVIEW.getVisibleElements(mLauncher) & HOTSEAT_ICONS) != 0) {
                // Hotseat was hidden, but we need it visible when peeking.
                mLauncher.getHotseat().setAlpha(1);
            }
        }
        mShelfPeekAnim.setShelfState(shelfState, ShelfPeekAnim.INTERPOLATOR,
                ShelfPeekAnim.DURATION);
    }

    private void setupAnimators() {
        // Animate the non-overview components (e.g. workspace, shelf) out of the way.
        StateAnimationConfig nonOverviewBuilder = new StateAnimationConfig();
        nonOverviewBuilder.setInterpolator(ANIM_WORKSPACE_FADE, FADE_OUT_INTERPOLATOR);
        nonOverviewBuilder.setInterpolator(ANIM_ALL_APPS_FADE, FADE_OUT_INTERPOLATOR);
        nonOverviewBuilder.setInterpolator(ANIM_WORKSPACE_TRANSLATE, TRANSLATE_OUT_INTERPOLATOR);
        nonOverviewBuilder.setInterpolator(ANIM_VERTICAL_PROGRESS, TRANSLATE_OUT_INTERPOLATOR);
        updateNonOverviewAnim(QUICK_SWITCH, nonOverviewBuilder);
        mNonOverviewAnim.dispatchOnStart();

        if (mRecentsView.getTaskViewCount() == 0) {
            mRecentsView.setOnEmptyMessageUpdatedListener(isEmpty -> {
                if (!isEmpty && mSwipeDetector.isDraggingState()) {
                    // We have loaded tasks, update the animators to start at the correct scale etc.
                    setupOverviewAnimators();
                }
            });
        }

        setupOverviewAnimators();
    }

    /** Create state animation to control non-overview components. */
    private void updateNonOverviewAnim(LauncherState toState, StateAnimationConfig config) {
        config.duration = (long) (Math.max(mXRange, mYRange) * 2);
        config.animFlags = config.animFlags | SKIP_OVERVIEW;
        mNonOverviewAnim = mLauncher.getStateManager()
                .createAnimationToNewWorkspace(toState, config)
                .setOnCancelRunnable(this::clearState);
    }

    private void setupOverviewAnimators() {
        final LauncherState fromState = QUICK_SWITCH;
        final LauncherState toState = OVERVIEW;

        // Set RecentView's initial properties.
        RECENTS_SCALE_PROPERTY.set(mRecentsView, fromState.getOverviewScaleAndOffset(mLauncher)[0]);
        ADJACENT_PAGE_OFFSET.set(mRecentsView, 1f);
        mRecentsView.setContentAlpha(1);
        mRecentsView.setFullscreenProgress(fromState.getOverviewFullscreenProgress());
        mLauncher.getActionsView().getVisibilityAlpha().setValue(
                (fromState.getVisibleElements(mLauncher) & OVERVIEW_BUTTONS) != 0 ? 1f : 0f);

        float[] scaleAndOffset = toState.getOverviewScaleAndOffset(mLauncher);
        // As we drag right, animate the following properties:
        //   - RecentsView translationX
        //   - OverviewScrim
        PendingAnimation xAnim = new PendingAnimation((long) (mXRange * 2));
        xAnim.setFloat(mRecentsView, ADJACENT_PAGE_OFFSET, scaleAndOffset[1], LINEAR);
        xAnim.setFloat(mLauncher.getDragLayer().getOverviewScrim(), OverviewScrim.SCRIM_PROGRESS,
                toState.getOverviewScrimAlpha(mLauncher), LINEAR);
        mXOverviewAnim = xAnim.createPlaybackController();
        mXOverviewAnim.dispatchOnStart();

        // As we drag up, animate the following properties:
        //   - RecentsView scale
        //   - RecentsView fullscreenProgress
        PendingAnimation yAnim = new PendingAnimation((long) (mYRange * 2));
        yAnim.setFloat(mRecentsView, RECENTS_SCALE_PROPERTY, scaleAndOffset[0],
                SCALE_DOWN_INTERPOLATOR);
        yAnim.setFloat(mRecentsView, FULLSCREEN_PROGRESS,
                toState.getOverviewFullscreenProgress(), SCALE_DOWN_INTERPOLATOR);
        AnimatorPlaybackController yNormalController = yAnim.createPlaybackController();
        AnimatorControllerWithResistance yAnimWithResistance = AnimatorControllerWithResistance
                .createForRecents(yNormalController, mLauncher,
                        mRecentsView.getPagedViewOrientedState(), mLauncher.getDeviceProfile(),
                        mRecentsView, RECENTS_SCALE_PROPERTY, mRecentsView,
                        TASK_SECONDARY_TRANSLATION);
        mYOverviewAnim = new AnimatedFloat(() -> {
            if (mYOverviewAnim != null) {
                yAnimWithResistance.setProgress(mYOverviewAnim.value, mMaxYProgress);
            }
        });
        yNormalController.dispatchOnStart();
    }

    @Override
    public boolean onDrag(PointF displacement, MotionEvent ev) {
        float xProgress = Math.max(0, displacement.x) / mXRange;
        float yProgress = Math.max(0, -displacement.y) / mYRange;
        yProgress = Utilities.mapRange(yProgress, Y_ANIM_MIN_PROGRESS, 1f);

        boolean wasHomeScreenVisible = mIsHomeScreenVisible;
        if (wasHomeScreenVisible && mNonOverviewAnim != null) {
            mNonOverviewAnim.setPlayFraction(xProgress);
        }
        mIsHomeScreenVisible = FADE_OUT_INTERPOLATOR.getInterpolation(xProgress)
                <= 1 - ALPHA_CUTOFF_THRESHOLD;

        if (wasHomeScreenVisible && !mIsHomeScreenVisible) {
            // Get the shelf all the way offscreen so it pops up when we decide to peek it.
            mShelfPeekAnim.setShelfState(HIDE, LINEAR, 0);
        }

        // Only allow motion pause if the home screen is invisible, since some
        // home screen elements will appear in the shelf on motion pause.
        mMotionPauseDetector.setDisallowPause(mIsHomeScreenVisible
                || -displacement.y < mMotionPauseMinDisplacement);
        mMotionPauseDetector.addPosition(ev);

        if (mIsHomeScreenVisible) {
            // Cancel the shelf anim so it doesn't clobber mNonOverviewAnim.
            mShelfPeekAnim.setShelfState(CANCEL, LINEAR, 0);
        }

        if (mXOverviewAnim != null) {
            mXOverviewAnim.setPlayFraction(xProgress);
        }
        if (mYOverviewAnim != null) {
            mYOverviewAnim.updateValue(yProgress);
        }
        return true;
    }

    @Override
    public void onDragEnd(PointF velocity) {
        boolean horizontalFling = mSwipeDetector.isFling(velocity.x);
        boolean verticalFling = mSwipeDetector.isFling(velocity.y);
        boolean noFling = !horizontalFling && !verticalFling;
        int logAction = noFling ? Touch.SWIPE : Touch.FLING;
        if (mMotionPauseDetector.isPaused() && noFling) {
            cancelAnimations();

            Animator overviewAnim = mLauncher.createAtomicAnimationFactory()
                    .createStateElementAnimation(INDEX_PAUSE_TO_OVERVIEW_ANIM);
            overviewAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onAnimationToStateCompleted(OVERVIEW, logAction);
                }
            });
            overviewAnim.start();
            return;
        }

        final LauncherState targetState;
        if (horizontalFling && verticalFling) {
            if (velocity.x < 0) {
                // Flinging left and up or down both go back home.
                targetState = NORMAL;
            } else {
                if (velocity.y > 0) {
                    // Flinging right and down goes to quick switch.
                    targetState = QUICK_SWITCH;
                } else {
                    // Flinging up and right could go either home or to quick switch.
                    // Determine the target based on the higher velocity.
                    targetState = Math.abs(velocity.x) > Math.abs(velocity.y)
                        ? QUICK_SWITCH : NORMAL;
                }
            }
        } else if (horizontalFling) {
            targetState = velocity.x > 0 ? QUICK_SWITCH : NORMAL;
        } else if (verticalFling) {
            targetState = velocity.y > 0 ? QUICK_SWITCH : NORMAL;
        } else {
            // If user isn't flinging, just snap to the closest state.
            boolean passedHorizontalThreshold = mXOverviewAnim.getInterpolatedProgress() > 0.5f;
            boolean passedVerticalThreshold = mYOverviewAnim.value > 1f;
            targetState = passedHorizontalThreshold && !passedVerticalThreshold
                    ? QUICK_SWITCH : NORMAL;
        }

        // Animate the various components to the target state.

        float xProgress = mXOverviewAnim.getProgressFraction();
        float startXProgress = Utilities.boundToRange(xProgress
                + velocity.x * getSingleFrameMs(mLauncher) / mXRange, 0f, 1f);
        final float endXProgress = targetState == NORMAL ? 0 : 1;
        long xDuration = BaseSwipeDetector.calculateDuration(velocity.x,
                Math.abs(endXProgress - startXProgress));
        ValueAnimator xOverviewAnim = mXOverviewAnim.getAnimationPlayer();
        xOverviewAnim.setFloatValues(startXProgress, endXProgress);
        xOverviewAnim.setDuration(xDuration)
                .setInterpolator(scrollInterpolatorForVelocity(velocity.x));
        mXOverviewAnim.dispatchOnStart();

        boolean flingUpToNormal = verticalFling && velocity.y < 0 && targetState == NORMAL;

        float yProgress = mYOverviewAnim.value;
        float startYProgress = Utilities.boundToRange(yProgress
                - velocity.y * getSingleFrameMs(mLauncher) / mYRange, 0f, mMaxYProgress);
        final float endYProgress;
        if (flingUpToNormal) {
            endYProgress = 1;
        } else if (targetState == NORMAL) {
            // Keep overview at its current scale/translationY as it slides off the screen.
            endYProgress = startYProgress;
        } else {
            endYProgress = 0;
        }
        float yDistanceToCover = Math.abs(endYProgress - startYProgress) * mYRange;
        long yDuration = (long) (yDistanceToCover / Math.max(1f, Math.abs(velocity.y)));
        ValueAnimator yOverviewAnim = mYOverviewAnim.animateToValue(startYProgress, endYProgress);
        yOverviewAnim.setDuration(yDuration);
        mYOverviewAnim.updateValue(startYProgress);

        ValueAnimator nonOverviewAnim = mNonOverviewAnim.getAnimationPlayer();
        if (flingUpToNormal && !mIsHomeScreenVisible) {
            // We are flinging to home while workspace is invisible, run the same staggered
            // animation as from an app.
            StateAnimationConfig config = new StateAnimationConfig();
            // Update mNonOverviewAnim to do nothing so it doesn't interfere.
            config.animFlags = 0;
            updateNonOverviewAnim(targetState, config);
            nonOverviewAnim = mNonOverviewAnim.getAnimationPlayer();

            new StaggeredWorkspaceAnim(mLauncher, velocity.y, false /* animateOverviewScrim */)
                    .start();
        } else {
            boolean canceled = targetState == NORMAL;
            if (canceled) {
                // Let the state manager know that the animation didn't go to the target state,
                // but don't clean up yet (we already clean up when the animation completes).
                mNonOverviewAnim.dispatchOnCancelWithoutCancelRunnable();
            }
            float startProgress = mNonOverviewAnim.getProgressFraction();
            float endProgress = canceled ? 0 : 1;
            nonOverviewAnim.setFloatValues(startProgress, endProgress);
            mNonOverviewAnim.dispatchOnStart();
        }

        nonOverviewAnim.setDuration(Math.max(xDuration, yDuration));
        mNonOverviewAnim.setEndAction(() -> onAnimationToStateCompleted(targetState, logAction));

        cancelAnimations();
        xOverviewAnim.start();
        yOverviewAnim.start();
        nonOverviewAnim.start();
    }

    private void onAnimationToStateCompleted(LauncherState targetState, int logAction) {
        mLauncher.getUserEventDispatcher().logStateChangeAction(logAction,
                getDirectionForLog(), mSwipeDetector.getDownX(), mSwipeDetector.getDownY(),
                LauncherLogProto.ContainerType.NAVBAR,
                mStartState.containerType,
                targetState.containerType,
                mLauncher.getWorkspace().getCurrentPage());
        mLauncher.getStatsLogManager().logger()
                .withSrcState(LAUNCHER_STATE_HOME)
                .withDstState(StatsLogManager.containerTypeToAtomState(targetState.containerType))
                .log(getLauncherAtomEvent(mStartState.containerType, targetState.containerType,
                        targetState.ordinal > mStartState.ordinal
                                ? LAUNCHER_UNKNOWN_SWIPEUP
                                : LAUNCHER_UNKNOWN_SWIPEDOWN));
        mLauncher.getStateManager().goToState(targetState, false, this::clearState);
    }

    private int getDirectionForLog() {
        return Utilities.isRtl(mLauncher.getResources()) ? Direction.LEFT : Direction.RIGHT;
    }

    private void cancelAnimations() {
        if (mNonOverviewAnim != null) {
            mNonOverviewAnim.getAnimationPlayer().cancel();
        }
        if (mXOverviewAnim != null) {
            mXOverviewAnim.getAnimationPlayer().cancel();
        }
        if (mYOverviewAnim != null) {
            mYOverviewAnim.cancelAnimation();
        }
        mShelfPeekAnim.setShelfState(ShelfAnimState.CANCEL, LINEAR, 0);
        mMotionPauseDetector.clear();
    }

    private void clearState() {
        cancelAnimations();
        mNonOverviewAnim = null;
        mXOverviewAnim = null;
        mYOverviewAnim = null;
        mIsHomeScreenVisible = true;
        mSwipeDetector.finishedScrolling();
        mRecentsView.setOnEmptyMessageUpdatedListener(null);
    }
}
