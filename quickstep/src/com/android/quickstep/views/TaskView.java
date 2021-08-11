/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.quickstep.views;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.END;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.widget.Toast.LENGTH_SHORT;

import static com.android.launcher3.AbstractFloatingView.TYPE_TASK_MENU;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.Utilities.comp;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.android.launcher3.util.ViewPool.Reusable;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskIconCache;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.util.CancellableTask;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.TaskCornerRadius;
import com.android.quickstep.views.TaskThumbnailView.PreviewPositionHelper;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.QuickStepContract;

import java.lang.annotation.Retention;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A task in the Recents view.
 */
public class TaskView extends FrameLayout implements Reusable {

    private static final String TAG = TaskView.class.getSimpleName();

    public static final int FLAG_UPDATE_ICON = 1;
    public static final int FLAG_UPDATE_THUMBNAIL = FLAG_UPDATE_ICON << 1;

    public static final int FLAG_UPDATE_ALL = FLAG_UPDATE_ICON | FLAG_UPDATE_THUMBNAIL;

    /**
     * Used in conjunction with {@link #onTaskListVisibilityChanged(boolean, int)}, providing more
     * granularity on which components of this task require an update
     */
    @Retention(SOURCE)
    @IntDef({FLAG_UPDATE_ALL, FLAG_UPDATE_ICON, FLAG_UPDATE_THUMBNAIL})
    public @interface TaskDataChanges {}

    /**
     * Should the layout account for space for a proactive action (or chip) to be added under
     * the task.
     */
    public static final boolean SHOW_PROACTIVE_ACTIONS = false;

    /** The maximum amount that a task view can be scrimmed, dimmed or tinted. */
    public static final float MAX_PAGE_SCRIM_ALPHA = 0.4f;

    /**
     * Should the TaskView display clip off the status and navigation bars in recents. When this
     * is false the overview shows the whole screen scaled down instead.
     */
    public static final boolean CLIP_STATUS_AND_NAV_BARS = false;

    /**
     * Should the TaskView scale down to fit whole thumbnail in fullscreen.
     */
    public static final boolean FULL_THUMBNAIL = false;

    private static final float EDGE_SCALE_DOWN_FACTOR_CAROUSEL = 0.03f;
    private static final float EDGE_SCALE_DOWN_FACTOR_GRID = 0.00f;

    public static final long SCALE_ICON_DURATION = 120;
    private static final long DIM_ANIM_DURATION = 700;

    private static final Interpolator GRID_INTERPOLATOR = ACCEL_DEACCEL;

    /**
     * This technically can be a vanilla {@link TouchDelegate} class, however that class requires
     * setting the touch bounds at construction, so we'd repeatedly be created many instances
     * unnecessarily as scrolling occurs, whereas {@link TransformingTouchDelegate} allows touch
     * delegated bounds only to be updated.
     */
    private TransformingTouchDelegate mIconTouchDelegate;
    private TransformingTouchDelegate mChipTouchDelegate;

    private static final List<Rect> SYSTEM_GESTURE_EXCLUSION_RECT =
            Collections.singletonList(new Rect());

    public static final FloatProperty<TaskView> FOCUS_TRANSITION =
            new FloatProperty<TaskView>("focusTransition") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setIconAndDimTransitionProgress(v, false /* invert */);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mFocusTransitionProgress;
                }
            };

    private static final FloatProperty<TaskView> SPLIT_SELECT_TRANSLATION_X =
            new FloatProperty<TaskView>("splitSelectTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setSplitSelectTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mSplitSelectTranslationX;
                }
            };

    private static final FloatProperty<TaskView> SPLIT_SELECT_TRANSLATION_Y =
            new FloatProperty<TaskView>("splitSelectTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setSplitSelectTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mSplitSelectTranslationY;
                }
            };

    private static final FloatProperty<TaskView> DISMISS_TRANSLATION_X =
            new FloatProperty<TaskView>("dismissTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setDismissTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mDismissTranslationX;
                }
            };

    private static final FloatProperty<TaskView> DISMISS_TRANSLATION_Y =
            new FloatProperty<TaskView>("dismissTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setDismissTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mDismissTranslationY;
                }
            };

    private static final FloatProperty<TaskView> TASK_OFFSET_TRANSLATION_X =
            new FloatProperty<TaskView>("taskOffsetTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskOffsetTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskOffsetTranslationX;
                }
            };

    private static final FloatProperty<TaskView> TASK_OFFSET_TRANSLATION_Y =
            new FloatProperty<TaskView>("taskOffsetTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskOffsetTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskOffsetTranslationY;
                }
            };

    private static final FloatProperty<TaskView> TASK_RESISTANCE_TRANSLATION_X =
            new FloatProperty<TaskView>("taskResistanceTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskResistanceTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskResistanceTranslationX;
                }
            };

    private static final FloatProperty<TaskView> TASK_RESISTANCE_TRANSLATION_Y =
            new FloatProperty<TaskView>("taskResistanceTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setTaskResistanceTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mTaskResistanceTranslationY;
                }
            };

    private static final FloatProperty<TaskView> NON_GRID_TRANSLATION_X =
            new FloatProperty<TaskView>("nonGridTranslationX") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setNonGridTranslationX(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mNonGridTranslationX;
                }
            };

    private static final FloatProperty<TaskView> NON_GRID_TRANSLATION_Y =
            new FloatProperty<TaskView>("nonGridTranslationY") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setNonGridTranslationY(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mNonGridTranslationY;
                }
            };

    public static final FloatProperty<TaskView> SNAPSHOT_SCALE =
            new FloatProperty<TaskView>("snapshotScale") {
                @Override
                public void setValue(TaskView taskView, float v) {
                    taskView.setSnapshotScale(v);
                }

                @Override
                public Float get(TaskView taskView) {
                    return taskView.mSnapshotView.getScaleX();
                }
            };

    private final TaskOutlineProvider mOutlineProvider;

    private Task mTask;
    private TaskThumbnailView mSnapshotView;
    private IconView mIconView;
    private final DigitalWellBeingToast mDigitalWellBeingToast;
    private float mFullscreenProgress;
    private float mGridProgress;
    private float mNonGridScale = 1;
    private float mDismissScale = 1;
    private final FullscreenDrawParams mCurrentFullscreenParams;
    private final StatefulActivity mActivity;

    // Various causes of changing primary translation, which we aggregate to setTranslationX/Y().
    private float mDismissTranslationX;
    private float mDismissTranslationY;
    private float mTaskOffsetTranslationX;
    private float mTaskOffsetTranslationY;
    private float mTaskResistanceTranslationX;
    private float mTaskResistanceTranslationY;
    // The following translation variables should only be used in the same orientation as Launcher.
    private float mBoxTranslationY;
    // The following grid translations scales with mGridProgress.
    private float mGridTranslationX;
    private float mGridTranslationY;
    // Applied as a complement to gridTranslation, for adjusting the carousel overview and quick
    // switch.
    private float mNonGridTranslationX;
    private float mNonGridTranslationY;
    // Used when in SplitScreenSelectState
    private float mSplitSelectTranslationY;
    private float mSplitSelectTranslationX;

    private ObjectAnimator mIconAndDimAnimator;
    private float mIconScaleAnimStartProgress = 0;
    private float mFocusTransitionProgress = 1;
    private float mModalness = 0;
    private float mStableAlpha = 1;

    private int mTaskViewId = -1;
    private final int[] mTaskIdContainer = new int[]{-1, -1};

    private boolean mShowScreenshot;

    // The current background requests to load the task thumbnail and icon
    private CancellableTask mThumbnailLoadRequest;
    private CancellableTask mIconLoadRequest;

    private boolean mEndQuickswitchCuj;

    private View mContextualChipWrapper;
    private final float[] mIconCenterCoords = new float[2];
    private final float[] mChipCenterCoords = new float[2];

    private boolean mIsClickableAsLiveTile = true;

    public TaskView(Context context) {
        this(context, null);
    }

    public TaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivity = StatefulActivity.fromContext(context);
        setOnClickListener(this::onClick);

        mCurrentFullscreenParams = new FullscreenDrawParams(context);
        mDigitalWellBeingToast = new DigitalWellBeingToast(mActivity, this);

        mOutlineProvider = new TaskOutlineProvider(getContext(), mCurrentFullscreenParams,
                mActivity.getDeviceProfile().overviewTaskThumbnailTopMarginPx);
        setOutlineProvider(mOutlineProvider);
    }

    public void setTaskViewId(int id) {
        this.mTaskViewId = id;
    }

    public int getTaskViewId() {
        return mTaskViewId;
    }

    /**
     * Builds proto for logging
     */
    public WorkspaceItemInfo getItemInfo() {
        final Task task = getTask();
        ComponentKey componentKey = TaskUtils.getLaunchComponentKeyForTask(task.key);
        WorkspaceItemInfo stubInfo = new WorkspaceItemInfo();
        stubInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_TASK;
        stubInfo.container = LauncherSettings.Favorites.CONTAINER_TASKSWITCHER;
        stubInfo.user = componentKey.user;
        stubInfo.intent = new Intent().setComponent(componentKey.componentName);
        stubInfo.title = task.title;
        stubInfo.screenId = getRecentsView().indexOfChild(this);
        return stubInfo;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView = findViewById(R.id.snapshot);
        mIconView = findViewById(R.id.icon);
        mIconTouchDelegate = new TransformingTouchDelegate(mIconView);
    }

    /**
     * Whether the taskview should take the touch event from parent. Events passed to children
     * that might require special handling.
     */
    public boolean offerTouchToChildren(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            computeAndSetIconTouchDelegate();
            computeAndSetChipTouchDelegate();
        }
        if (mIconTouchDelegate != null && mIconTouchDelegate.onTouchEvent(event)) {
            return true;
        }
        if (mChipTouchDelegate != null && mChipTouchDelegate.onTouchEvent(event)) {
            return true;
        }
        return false;
    }

    private void computeAndSetIconTouchDelegate() {
        float iconHalfSize = mIconView.getWidth() / 2f;
        mIconCenterCoords[0] = mIconCenterCoords[1] = iconHalfSize;
        getDescendantCoordRelativeToAncestor(mIconView, mActivity.getDragLayer(), mIconCenterCoords,
                false);
        mIconTouchDelegate.setBounds(
                (int) (mIconCenterCoords[0] - iconHalfSize),
                (int) (mIconCenterCoords[1] - iconHalfSize),
                (int) (mIconCenterCoords[0] + iconHalfSize),
                (int) (mIconCenterCoords[1] + iconHalfSize));
    }

    private void computeAndSetChipTouchDelegate() {
        if (mContextualChipWrapper != null) {
            float chipHalfWidth = mContextualChipWrapper.getWidth() / 2f;
            float chipHalfHeight = mContextualChipWrapper.getHeight() / 2f;
            mChipCenterCoords[0] = chipHalfWidth;
            mChipCenterCoords[1] = chipHalfHeight;
            getDescendantCoordRelativeToAncestor(mContextualChipWrapper, mActivity.getDragLayer(),
                    mChipCenterCoords,
                    false);
            mChipTouchDelegate.setBounds(
                    (int) (mChipCenterCoords[0] - chipHalfWidth),
                    (int) (mChipCenterCoords[1] - chipHalfHeight),
                    (int) (mChipCenterCoords[0] + chipHalfWidth),
                    (int) (mChipCenterCoords[1] + chipHalfHeight));
        }
    }

    /**
     * The modalness of this view is how it should be displayed when it is shown on its own in the
     * modal state of overview.
     *
     * @param modalness [0, 1] 0 being in context with other tasks, 1 being shown on its own.
     */
    public void setModalness(float modalness) {
        if (mModalness == modalness) {
            return;
        }
        mModalness = modalness;
        mIconView.setAlpha(comp(modalness));
        if (mContextualChipWrapper != null) {
            mContextualChipWrapper.setScaleX(comp(modalness));
            mContextualChipWrapper.setScaleY(comp(modalness));
        }
        mDigitalWellBeingToast.updateBannerOffset(modalness,
                mCurrentFullscreenParams.mCurrentDrawnInsets.top
                        + mCurrentFullscreenParams.mCurrentDrawnInsets.bottom);
    }

    public DigitalWellBeingToast getDigitalWellBeingToast() {
        return mDigitalWellBeingToast;
    }

    /**
     * Updates this task view to the given {@param task}.
     *
     * TODO(b/142282126) Re-evaluate if we need to pass in isMultiWindowMode after
     *   that issue is fixed
     */
    public void bind(Task task, RecentsOrientedState orientedState) {
        cancelPendingLoadTasks();
        mTask = task;
        mTaskIdContainer[0] = mTask.key.id;
        mSnapshotView.bind(task);
        setOrientationState(orientedState);
    }

    public Task getTask() {
        return mTask;
    }

    /**
     * @return integer array of two elements to be size consistent with max number of tasks possible
     *         index 0 will contain the taskId, index 1 will be -1 indicating a null taskID value
     */
    public int[] getTaskIds() {
        return mTaskIdContainer;
    }

    public TaskThumbnailView getThumbnail() {
        return mSnapshotView;
    }

    public IconView getIconView() {
        return mIconView;
    }

    private void onClick(View view) {
        if (getTask() == null) {
            return;
        }
        if (confirmSecondSplitSelectApp()) {
            return;
        }
        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && isRunningTask()) {
            if (!mIsClickableAsLiveTile) {
                return;
            }

            // Reset the minimized state since we force-toggled the minimized state when entering
            // overview, but never actually finished the recents animation
            SystemUiProxy p = SystemUiProxy.INSTANCE.getNoCreate();
            if (p != null) {
                p.setSplitScreenMinimized(false);
            }

            mIsClickableAsLiveTile = false;
            RecentsView recentsView = getRecentsView();
            final RemoteAnimationTargets targets = recentsView.getLiveTileParams().getTargetSet();
            if (targets == null) {
                // If the recents animation is cancelled somehow between the parent if block and
                // here, try to launch the task as a non live tile task.
                launchTaskAnimated();
                return;
            }

            AnimatorSet anim = new AnimatorSet();
            TaskViewUtils.composeRecentsLaunchAnimator(
                    anim, this, targets.apps,
                    targets.wallpapers, targets.nonApps, true /* launcherClosing */,
                    mActivity.getStateManager(), recentsView,
                    recentsView.getDepthController());
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    mIsClickableAsLiveTile = true;
                }
            });
            anim.start();
            recentsView.onTaskLaunchedInLiveTileMode();
        } else {
            launchTaskAnimated();
        }
        mActivity.getStatsLogManager().logger().withItemInfo(getItemInfo())
                .log(LAUNCHER_TASK_LAUNCH_TAP);
    }

    /**
     * @return {@code true} if user is already in split select mode and this tap was to choose the
     *         second app. {@code false} otherwise
     */
    private boolean confirmSecondSplitSelectApp() {
        boolean isSelectingSecondSplitApp = mActivity.isInState(OVERVIEW_SPLIT_SELECT);
        if (isSelectingSecondSplitApp) {
            getRecentsView().confirmSplitSelect(this);
        }
        return isSelectingSecondSplitApp;
    }

    /**
     * Starts the task associated with this view and animates the startup.
     * @return CompletionStage to indicate the animation completion or null if the launch failed.
     */
    public RunnableList launchTaskAnimated() {
        if (mTask != null) {
            TestLogging.recordEvent(
                    TestProtocol.SEQUENCE_MAIN, "startActivityFromRecentsAsync", mTask);
            ActivityOptionsWrapper opts =  mActivity.getActivityLaunchOptions(this, null);
            if (ActivityManagerWrapper.getInstance()
                    .startActivityFromRecents(mTask.key, opts.options)) {
                RecentsView recentsView = getRecentsView();
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() && recentsView.getRunningTaskViewId() != -1) {
                    recentsView.onTaskLaunchedInLiveTileMode();

                    // Return a fresh callback in the live tile case, so that it's not accidentally
                    // triggered by QuickstepTransitionManager.AppLaunchAnimationRunner.
                    RunnableList callbackList = new RunnableList();
                    recentsView.addSideTaskLaunchCallback(callbackList);
                    return callbackList;
                }
                return opts.onEndCallback;
            } else {
                notifyTaskLaunchFailed(TAG);
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Starts the task associated with this view without any animation
     */
    public void launchTask(@NonNull Consumer<Boolean> callback) {
        launchTask(callback, false /* freezeTaskList */);
    }

    /**
     * Starts the task associated with this view without any animation
     */
    public void launchTask(@NonNull Consumer<Boolean> callback, boolean freezeTaskList) {
        if (mTask != null) {
            TestLogging.recordEvent(
                    TestProtocol.SEQUENCE_MAIN, "startActivityFromRecentsAsync", mTask);

            // Indicate success once the system has indicated that the transition has started
            ActivityOptions opts = ActivityOptionsCompat.makeCustomAnimation(
                    getContext(), 0, 0, () -> callback.accept(true), MAIN_EXECUTOR.getHandler());
            if (freezeTaskList) {
                ActivityOptionsCompat.setFreezeRecentTasksList(opts);
            }
            Task.TaskKey key = mTask.key;
            UI_HELPER_EXECUTOR.execute(() -> {
                if (!ActivityManagerWrapper.getInstance().startActivityFromRecents(key, opts)) {
                    // If the call to start activity failed, then post the result immediately,
                    // otherwise, wait for the animation start callback from the activity options
                    // above
                    MAIN_EXECUTOR.post(() -> {
                        notifyTaskLaunchFailed(TAG);
                        callback.accept(false);
                    });
                }
            });
        } else {
            callback.accept(false);
        }
    }

    /**
     * See {@link TaskDataChanges}
     * @param visible If this task view will be visible to the user in overview or hidden
     */
    public void onTaskListVisibilityChanged(boolean visible) {
        onTaskListVisibilityChanged(visible, FLAG_UPDATE_ALL);
    }

    /**
     * See {@link TaskDataChanges}
     * @param visible If this task view will be visible to the user in overview or hidden
     */
    public void onTaskListVisibilityChanged(boolean visible, @TaskDataChanges int changes) {
        if (mTask == null) {
            return;
        }
        cancelPendingLoadTasks();
        if (visible) {
            // These calls are no-ops if the data is already loaded, try and load the high
            // resolution thumbnail if the state permits
            RecentsModel model = RecentsModel.INSTANCE.get(getContext());
            TaskThumbnailCache thumbnailCache = model.getThumbnailCache();
            TaskIconCache iconCache = model.getIconCache();

            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                mThumbnailLoadRequest = thumbnailCache.updateThumbnailInBackground(
                        mTask, thumbnail -> {
                            mSnapshotView.setThumbnail(mTask, thumbnail);
                        });
            }
            if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
                mIconLoadRequest = iconCache.updateIconInBackground(mTask,
                        (task) -> {
                            setIcon(task.icon);
                            mDigitalWellBeingToast.initialize(mTask);
                        });
            }
        } else {
            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                mSnapshotView.setThumbnail(null, null);
                // Reset the task thumbnail reference as well (it will be fetched from the cache or
                // reloaded next time we need it)
                mTask.thumbnail = null;
            }
            if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
                setIcon(null);
            }
        }
    }

    private boolean needsUpdate(@TaskDataChanges int dataChange, @TaskDataChanges int flag) {
        return (dataChange & flag) == flag;
    }

    private void cancelPendingLoadTasks() {
        if (mThumbnailLoadRequest != null) {
            mThumbnailLoadRequest.cancel();
            mThumbnailLoadRequest = null;
        }
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
    }

    private boolean showTaskMenu() {
        if (getRecentsView().mActivity.isInState(OVERVIEW_SPLIT_SELECT)) {
            // Don't show menu when selecting second split screen app
            return true;
        }

        if (!getRecentsView().isClearAllHidden()) {
            getRecentsView().snapToPage(getRecentsView().indexOfChild(this));
            return false;
        } else {
            mActivity.getStatsLogManager().logger().withItemInfo(getItemInfo())
                    .log(LAUNCHER_TASK_ICON_TAP_OR_LONGPRESS);
            return TaskMenuView.showForTask(this);
        }
    }

    private void setIcon(Drawable icon) {
        if (icon != null) {
            mIconView.setDrawable(icon);
            mIconView.setOnClickListener(v -> {
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() && isRunningTask()) {
                    RecentsView recentsView = getRecentsView();
                    recentsView.switchToScreenshot(
                            () -> recentsView.finishRecentsAnimation(true /* toRecents */,
                                    this::showTaskMenu));
                } else {
                    showTaskMenu();
                }
            });
            mIconView.setOnLongClickListener(v -> {
                requestDisallowInterceptTouchEvent(true);
                return showTaskMenu();
            });
        } else {
            mIconView.setDrawable(null);
            mIconView.setOnClickListener(null);
            mIconView.setOnLongClickListener(null);
        }
    }

    public void setOrientationState(RecentsOrientedState orientationState) {
        PagedOrientationHandler orientationHandler = orientationState.getOrientationHandler();
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        LayoutParams snapshotParams = (LayoutParams) mSnapshotView.getLayoutParams();
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        snapshotParams.topMargin = deviceProfile.overviewTaskThumbnailTopMarginPx;
        int taskIconMargin = deviceProfile.overviewTaskMarginPx;
        int taskIconHeight = deviceProfile.overviewTaskIconSizePx;
        LayoutParams iconParams = (LayoutParams) mIconView.getLayoutParams();
        switch (orientationHandler.getRotation()) {
            case ROTATION_90:
                iconParams.gravity = (isRtl ? START : END) | CENTER_VERTICAL;
                iconParams.rightMargin = -taskIconHeight - taskIconMargin / 2;
                iconParams.leftMargin = 0;
                iconParams.topMargin = snapshotParams.topMargin / 2;
                break;
            case ROTATION_180:
                iconParams.gravity = BOTTOM | CENTER_HORIZONTAL;
                iconParams.bottomMargin = -snapshotParams.topMargin;
                iconParams.leftMargin = iconParams.rightMargin = 0;
                iconParams.topMargin = taskIconMargin;
                break;
            case ROTATION_270:
                iconParams.gravity = (isRtl ? END : START) | CENTER_VERTICAL;
                iconParams.leftMargin = -taskIconHeight - taskIconMargin / 2;
                iconParams.rightMargin = 0;
                iconParams.topMargin = snapshotParams.topMargin / 2;
                break;
            case Surface.ROTATION_0:
            default:
                iconParams.gravity = TOP | CENTER_HORIZONTAL;
                iconParams.leftMargin = iconParams.rightMargin = 0;
                iconParams.topMargin = taskIconMargin;
                break;
        }
        mSnapshotView.setLayoutParams(snapshotParams);
        iconParams.width = iconParams.height = taskIconHeight;
        mIconView.setLayoutParams(iconParams);
        mIconView.setRotation(orientationHandler.getDegreesRotated());
        snapshotParams.topMargin = deviceProfile.overviewTaskThumbnailTopMarginPx;
        mSnapshotView.setLayoutParams(snapshotParams);
        mSnapshotView.getTaskOverlay().updateOrientationState(orientationState);
    }

    private void setIconAndDimTransitionProgress(float progress, boolean invert) {
        if (invert) {
            progress = 1 - progress;
        }
        mFocusTransitionProgress = progress;
        float iconScalePercentage = (float) SCALE_ICON_DURATION / DIM_ANIM_DURATION;
        float lowerClamp = invert ? 1f - iconScalePercentage : 0;
        float upperClamp = invert ? 1 : iconScalePercentage;
        float scale = Interpolators.clampToProgress(FAST_OUT_SLOW_IN, lowerClamp, upperClamp)
                .getInterpolation(progress);
        mIconView.setAlpha(scale);
        if (mContextualChipWrapper != null && mContextualChipWrapper != null) {
            mContextualChipWrapper.setAlpha(scale);
            mContextualChipWrapper.setScaleX(Math.min(scale, comp(mModalness)));
            mContextualChipWrapper.setScaleY(Math.min(scale, comp(mModalness)));
        }
        mDigitalWellBeingToast.updateBannerOffset(1f - scale,
                mCurrentFullscreenParams.mCurrentDrawnInsets.top
                        + mCurrentFullscreenParams.mCurrentDrawnInsets.bottom);
    }

    public void setIconScaleAnimStartProgress(float startProgress) {
        mIconScaleAnimStartProgress = startProgress;
    }

    public void animateIconScaleAndDimIntoView() {
        if (mIconAndDimAnimator != null) {
            mIconAndDimAnimator.cancel();
        }
        mIconAndDimAnimator = ObjectAnimator.ofFloat(this, FOCUS_TRANSITION, 1);
        mIconAndDimAnimator.setCurrentFraction(mIconScaleAnimStartProgress);
        mIconAndDimAnimator.setDuration(DIM_ANIM_DURATION).setInterpolator(LINEAR);
        mIconAndDimAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIconAndDimAnimator = null;
            }
        });
        mIconAndDimAnimator.start();
    }

    protected void setIconScaleAndDim(float iconScale) {
        setIconScaleAndDim(iconScale, false);
    }

    private void setIconScaleAndDim(float iconScale, boolean invert) {
        if (mIconAndDimAnimator != null) {
            mIconAndDimAnimator.cancel();
        }
        setIconAndDimTransitionProgress(iconScale, invert);
    }

    protected void resetPersistentViewTransforms() {
        mNonGridTranslationX = mNonGridTranslationY =
                mGridTranslationX = mGridTranslationY = mBoxTranslationY = 0f;
        resetViewTransforms();
    }

    protected void resetViewTransforms() {
        // fullscreenTranslation and accumulatedTranslation should not be reset, as
        // resetViewTransforms is called during Quickswitch scrolling.
        mDismissTranslationX = mTaskOffsetTranslationX = mTaskResistanceTranslationX =
                mSplitSelectTranslationX = 0f;
        mDismissTranslationY = mTaskOffsetTranslationY = mTaskResistanceTranslationY =
                mSplitSelectTranslationY = 0f;
        setSnapshotScale(1f);
        applyTranslationX();
        applyTranslationY();
        setTranslationZ(0);
        setAlpha(mStableAlpha);
        setIconScaleAndDim(1);
        setColorTint(0, 0);
    }

    public void setStableAlpha(float parentAlpha) {
        mStableAlpha = parentAlpha;
        setAlpha(mStableAlpha);
    }

    @Override
    public void onRecycle() {
        resetPersistentViewTransforms();
        // Clear any references to the thumbnail (it will be re-read either from the cache or the
        // system on next bind)
        mSnapshotView.setThumbnail(mTask, null);
        setOverlayEnabled(false);
        onTaskListVisibilityChanged(false);
    }

    /**
     * Sets the contextual chip.
     *
     * @param view Wrapper view containing contextual chip.
     */
    public void setContextualChip(View view) {
        if (mContextualChipWrapper != null) {
            removeView(mContextualChipWrapper);
        }
        if (view != null) {
            mContextualChipWrapper = view;
            LayoutParams layoutParams = new LayoutParams(((View) getParent()).getMeasuredWidth(),
                    LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = BOTTOM | CENTER_HORIZONTAL;
            int expectedChipHeight = getExpectedViewHeight(view);
            float chipOffset = getResources().getDimension(R.dimen.chip_hint_vertical_offset);
            layoutParams.bottomMargin = -expectedChipHeight - (int) chipOffset;
            mContextualChipWrapper.setScaleX(0f);
            mContextualChipWrapper.setScaleY(0f);
            addView(view, getChildCount(), layoutParams);
            if (mContextualChipWrapper != null) {
                float scale = comp(mModalness);
                mContextualChipWrapper.animate().scaleX(scale).scaleY(scale).setDuration(50);
                mChipTouchDelegate = new TransformingTouchDelegate(mContextualChipWrapper);
            }
        }
    }

    public float getTaskCornerRadius() {
        return TaskCornerRadius.get(mActivity);
    }

    /**
     * Clears the contextual chip from TaskView.
     *
     * @return The contextual chip wrapper view to be recycled.
     */
    public View clearContextualChip() {
        if (mContextualChipWrapper != null) {
            removeView(mContextualChipWrapper);
        }
        View oldContextualChipWrapper = mContextualChipWrapper;
        mContextualChipWrapper = null;
        mChipTouchDelegate = null;
        return oldContextualChipWrapper;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mActivity.getDeviceProfile().overviewShowAsGrid) {
            setPivotX(getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 0 : right - left);
            setPivotY(mSnapshotView.getTop());
        } else {
            setPivotX((right - left) * 0.5f);
            setPivotY(mSnapshotView.getTop() + mSnapshotView.getHeight() * 0.5f);
        }
        if (Utilities.ATLEAST_Q) {
            SYSTEM_GESTURE_EXCLUSION_RECT.get(0).set(0, 0, getWidth(), getHeight());
            setSystemGestureExclusionRects(SYSTEM_GESTURE_EXCLUSION_RECT);
        }
    }

    /**
     * How much to scale down pages near the edge of the screen.
     */
    public static float getEdgeScaleDownFactor(DeviceProfile deviceProfile) {
        return deviceProfile.overviewShowAsGrid ? EDGE_SCALE_DOWN_FACTOR_GRID
                : EDGE_SCALE_DOWN_FACTOR_CAROUSEL;
    }

    private void setNonGridScale(float nonGridScale) {
        mNonGridScale = nonGridScale;
        updateCornerRadius();
        applyScale();
    }

    public float getNonGridScale() {
        return mNonGridScale;
    }

    private void setSnapshotScale(float dismissScale) {
        mDismissScale = dismissScale;
        applyScale();
    }

    /**
     * Moves TaskView between carousel and 2 row grid.
     *
     * @param gridProgress 0 = carousel; 1 = 2 row grid.
     */
    public void setGridProgress(float gridProgress) {
        mGridProgress = gridProgress;
        applyTranslationX();
        applyTranslationY();
        applyScale();
    }

    private void applyScale() {
        float scale = 1;
        scale *= getPersistentScale();
        scale *= mDismissScale;
        setScaleX(scale);
        setScaleY(scale);
    }

    /**
     * Returns multiplication of scale that is persistent (e.g. fullscreen and grid), and does not
     * change according to a temporary state.
     */
    public float getPersistentScale() {
        float scale = 1;
        float gridProgress = GRID_INTERPOLATOR.getInterpolation(mGridProgress);
        scale *= Utilities.mapRange(gridProgress, mNonGridScale, 1f);
        return scale;
    }

    private void setSplitSelectTranslationX(float x) {
        mSplitSelectTranslationX = x;
        applyTranslationX();
    }

    private void setSplitSelectTranslationY(float y) {
        mSplitSelectTranslationY = y;
        applyTranslationY();
    }
    private void setDismissTranslationX(float x) {
        mDismissTranslationX = x;
        applyTranslationX();
    }

    private void setDismissTranslationY(float y) {
        mDismissTranslationY = y;
        applyTranslationY();
    }

    private void setTaskOffsetTranslationX(float x) {
        mTaskOffsetTranslationX = x;
        applyTranslationX();
    }

    private void setTaskOffsetTranslationY(float y) {
        mTaskOffsetTranslationY = y;
        applyTranslationY();
    }

    private void setTaskResistanceTranslationX(float x) {
        mTaskResistanceTranslationX = x;
        applyTranslationX();
    }

    private void setTaskResistanceTranslationY(float y) {
        mTaskResistanceTranslationY = y;
        applyTranslationY();
    }

    private void setNonGridTranslationX(float nonGridTranslationX) {
        mNonGridTranslationX = nonGridTranslationX;
        applyTranslationX();
    }

    private void setNonGridTranslationY(float nonGridTranslationY) {
        mNonGridTranslationY = nonGridTranslationY;
        applyTranslationY();
    }

    public void setGridTranslationX(float gridTranslationX) {
        mGridTranslationX = gridTranslationX;
        applyTranslationX();
    }

    public float getGridTranslationX() {
        return mGridTranslationX;
    }

    public void setGridTranslationY(float gridTranslationY) {
        mGridTranslationY = gridTranslationY;
        applyTranslationY();
    }

    public float getGridTranslationY() {
        return mGridTranslationY;
    }

    public float getScrollAdjustment(boolean fullscreenEnabled, boolean gridEnabled) {
        float scrollAdjustment = 0;
        if (gridEnabled) {
            scrollAdjustment += mGridTranslationX;
        } else {
            scrollAdjustment += getPrimaryNonGridTranslationProperty().get(this);
        }
        return scrollAdjustment;
    }

    public float getOffsetAdjustment(boolean fullscreenEnabled, boolean gridEnabled) {
        return getScrollAdjustment(fullscreenEnabled, gridEnabled);
    }

    public float getSizeAdjustment(boolean fullscreenEnabled) {
        float sizeAdjustment = 1;
        if (fullscreenEnabled) {
            sizeAdjustment *= mNonGridScale;
        }
        return sizeAdjustment;
    }

    private void setBoxTranslationY(float boxTranslationY) {
        mBoxTranslationY = boxTranslationY;
        applyTranslationY();
    }

    private void applyTranslationX() {
        setTranslationX(mDismissTranslationX + mTaskOffsetTranslationX + mTaskResistanceTranslationX
                + mSplitSelectTranslationX + getPersistentTranslationX());
    }

    private void applyTranslationY() {
        setTranslationY(mDismissTranslationY + mTaskOffsetTranslationY + mTaskResistanceTranslationY
                + mSplitSelectTranslationY + getPersistentTranslationY());
    }

    /**
     * Returns addition of translationX that is persistent (e.g. fullscreen and grid), and does not
     * change according to a temporary state (e.g. task offset).
     */
    public float getPersistentTranslationX() {
        return getNonGridTrans(mNonGridTranslationX) + getGridTrans(mGridTranslationX);
    }

    /**
     * Returns addition of translationY that is persistent (e.g. fullscreen and grid), and does not
     * change according to a temporary state (e.g. task offset).
     */
    public float getPersistentTranslationY() {
        return mBoxTranslationY
                + getNonGridTrans(mNonGridTranslationY)
                + getGridTrans(mGridTranslationY);
    }

    public FloatProperty<TaskView> getPrimarySplitTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                SPLIT_SELECT_TRANSLATION_X, SPLIT_SELECT_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getSecondarySplitTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                SPLIT_SELECT_TRANSLATION_X, SPLIT_SELECT_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getPrimaryDismissTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getSecondaryDissmissTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                DISMISS_TRANSLATION_X, DISMISS_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getPrimaryTaskOffsetTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                TASK_OFFSET_TRANSLATION_X, TASK_OFFSET_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getTaskResistanceTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                TASK_RESISTANCE_TRANSLATION_X, TASK_RESISTANCE_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getPrimaryNonGridTranslationProperty() {
        return getPagedOrientationHandler().getPrimaryValue(
                NON_GRID_TRANSLATION_X, NON_GRID_TRANSLATION_Y);
    }

    public FloatProperty<TaskView> getSecondaryNonGridTranslationProperty() {
        return getPagedOrientationHandler().getSecondaryValue(
                NON_GRID_TRANSLATION_X, NON_GRID_TRANSLATION_Y);
    }

    @Override
    public boolean hasOverlappingRendering() {
        // TODO: Clip-out the icon region from the thumbnail, since they are overlapping.
        return false;
    }

    public boolean isEndQuickswitchCuj() {
        return mEndQuickswitchCuj;
    }

    public void setEndQuickswitchCuj(boolean endQuickswitchCuj) {
        mEndQuickswitchCuj = endQuickswitchCuj;
    }

    private static final class TaskOutlineProvider extends ViewOutlineProvider {

        private int mMarginTop;
        private FullscreenDrawParams mFullscreenParams;

        TaskOutlineProvider(Context context, FullscreenDrawParams fullscreenParams, int topMargin) {
            mMarginTop = topMargin;
            mFullscreenParams = fullscreenParams;
        }

        public void updateParams(FullscreenDrawParams params, int topMargin) {
            mFullscreenParams = params;
            mMarginTop = topMargin;
        }

        @Override
        public void getOutline(View view, Outline outline) {
            RectF insets = mFullscreenParams.mCurrentDrawnInsets;
            float scale = mFullscreenParams.mScale;
            outline.setRoundRect(0,
                    (int) (mMarginTop * scale),
                    (int) ((insets.left + view.getWidth() + insets.right) * scale),
                    (int) ((insets.top + view.getHeight() + insets.bottom) * scale),
                    mFullscreenParams.mCurrentDrawnCornerRadius);
        }
    }

    private int getExpectedViewHeight(View view) {
        int expectedHeight;
        int h = view.getLayoutParams().height;
        if (h > 0) {
            expectedHeight = h;
        } else {
            int m = MeasureSpec.makeMeasureSpec(MeasureSpec.EXACTLY - 1, MeasureSpec.AT_MOST);
            view.measure(m, m);
            expectedHeight = view.getMeasuredHeight();
        }
        return expectedHeight;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        info.addAction(
                new AccessibilityNodeInfo.AccessibilityAction(R.string.accessibility_close,
                        getContext().getText(R.string.accessibility_close)));

        final Context context = getContext();
        for (SystemShortcut s : TaskOverlayFactory.getEnabledShortcuts(this,
                mActivity.getDeviceProfile())) {
            info.addAction(s.createAccessibilityAction(context));
        }

        if (mDigitalWellBeingToast.hasLimit()) {
            info.addAction(
                    new AccessibilityNodeInfo.AccessibilityAction(
                            R.string.accessibility_app_usage_settings,
                            getContext().getText(R.string.accessibility_app_usage_settings)));
        }

        final RecentsView recentsView = getRecentsView();
        final AccessibilityNodeInfo.CollectionItemInfo itemInfo =
                AccessibilityNodeInfo.CollectionItemInfo.obtain(
                        0, 1, recentsView.getTaskViewCount() - recentsView.indexOfChild(this) - 1,
                        1, false);
        info.setCollectionItemInfo(itemInfo);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.string.accessibility_close) {
            getRecentsView().dismissTask(this, true /*animateTaskView*/,
                    true /*removeTask*/);
            return true;
        }

        if (action == R.string.accessibility_app_usage_settings) {
            mDigitalWellBeingToast.openAppUsageSettings(this);
            return true;
        }

        for (SystemShortcut s : TaskOverlayFactory.getEnabledShortcuts(this,
                mActivity.getDeviceProfile())) {
            if (s.hasHandlerForAction(action)) {
                s.onClick(this);
                return true;
            }
        }

        return super.performAccessibilityAction(action, arguments);
    }

    public RecentsView getRecentsView() {
        return (RecentsView) getParent();
    }

    PagedOrientationHandler getPagedOrientationHandler() {
        return getRecentsView().mOrientationState.getOrientationHandler();
    }

    private void notifyTaskLaunchFailed(String tag) {
        String msg = "Failed to launch task";
        if (mTask != null) {
            msg += " (task=" + mTask.key.baseIntent + " userId=" + mTask.key.userId + ")";
        }
        Log.w(tag, msg);
        Toast.makeText(getContext(), R.string.activity_not_available, LENGTH_SHORT).show();
    }

    /**
     * Hides the icon and shows insets when this TaskView is about to be shown fullscreen.
     *
     * @param progress: 0 = show icon and no insets; 1 = don't show icon and show full insets.
     */
    public void setFullscreenProgress(float progress) {
        progress = Utilities.boundToRange(progress, 0, 1);
        mFullscreenProgress = progress;
        mIconView.setVisibility(progress < 1 ? VISIBLE : INVISIBLE);
        mSnapshotView.getTaskOverlay().setFullscreenProgress(progress);

        updateCornerRadius();

        mSnapshotView.setFullscreenParams(mCurrentFullscreenParams);
        mOutlineProvider.updateParams(
                mCurrentFullscreenParams,
                mActivity.getDeviceProfile().overviewTaskThumbnailTopMarginPx);
        invalidateOutline();
    }

    private void updateCornerRadius() {
        updateCurrentFullscreenParams(mSnapshotView.getPreviewPositionHelper());
    }

    void updateCurrentFullscreenParams(PreviewPositionHelper previewPositionHelper) {
        if (getRecentsView() == null) {
            return;
        }
        mCurrentFullscreenParams.setProgress(
                mFullscreenProgress,
                getRecentsView().getScaleX(),
                mNonGridScale,
                getWidth(), mActivity.getDeviceProfile(),
                previewPositionHelper);
    }

    /**
     * Updates TaskView scaling and translation required to support variable width if enabled, while
     * ensuring TaskView fits into screen in fullscreen.
     */
    void updateTaskSize() {
        ViewGroup.LayoutParams params = getLayoutParams();
        float nonGridScale;
        float boxTranslationY;
        int expectedWidth;
        int expectedHeight;
        int iconDrawableSize;
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        if (deviceProfile.overviewShowAsGrid) {
            final int thumbnailPadding = deviceProfile.overviewTaskThumbnailTopMarginPx;
            final Rect lastComputedTaskSize = getRecentsView().getLastComputedTaskSize();
            final int taskWidth = lastComputedTaskSize.width();
            final int taskHeight = lastComputedTaskSize.height();

            int boxWidth;
            int boxHeight;
            boolean isFocusedTask = isFocusedTask();
            if (isFocusedTask) {
                // Task will be focused and should use focused task size. Use focusTaskRatio
                // that is associated with the original orientation of the focused task.
                boxWidth = taskWidth;
                boxHeight = taskHeight;
                iconDrawableSize = deviceProfile.overviewTaskIconDrawableSizePx;
            } else {
                // Otherwise task is in grid, and should use lastComputedGridTaskSize.
                Rect lastComputedGridTaskSize = getRecentsView().getLastComputedGridTaskSize();
                boxWidth = lastComputedGridTaskSize.width();
                boxHeight = lastComputedGridTaskSize.height();
                iconDrawableSize = deviceProfile.overviewTaskIconDrawableSizeGridPx;
            }

            // Bound width/height to the box size.
            expectedWidth = boxWidth;
            expectedHeight = boxHeight + thumbnailPadding;

            // Scale to to fit task Rect.
            nonGridScale = taskWidth / (float) boxWidth;

            // Align to top of task Rect.
            boxTranslationY = (expectedHeight - thumbnailPadding - taskHeight) / 2.0f;
        } else {
            nonGridScale = 1f;
            boxTranslationY = 0f;
            expectedWidth = ViewGroup.LayoutParams.MATCH_PARENT;
            expectedHeight = ViewGroup.LayoutParams.MATCH_PARENT;
            iconDrawableSize = deviceProfile.overviewTaskIconDrawableSizePx;
        }

        setNonGridScale(nonGridScale);
        setBoxTranslationY(boxTranslationY);
        if (params.width != expectedWidth || params.height != expectedHeight) {
            params.width = expectedWidth;
            params.height = expectedHeight;
            setLayoutParams(params);
        }
        mIconView.setDrawableSize(iconDrawableSize, iconDrawableSize);
    }

    private float getGridTrans(float endTranslation) {
        float progress = GRID_INTERPOLATOR.getInterpolation(mGridProgress);
        return Utilities.mapRange(progress, 0, endTranslation);
    }

    private float getNonGridTrans(float endTranslation) {
        return endTranslation - getGridTrans(endTranslation);
    }

    public boolean isRunningTask() {
        if (getRecentsView() == null) {
            return false;
        }
        return this == getRecentsView().getRunningTaskView();
    }

    public boolean isFocusedTask() {
        if (getRecentsView() == null) {
            return false;
        }
        return this == getRecentsView().getFocusedTaskView();
    }

    public void setShowScreenshot(boolean showScreenshot) {
        mShowScreenshot = showScreenshot;
    }

    public boolean showScreenshot() {
        if (!isRunningTask()) {
            return true;
        }
        return mShowScreenshot;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        mSnapshotView.setOverlayEnabled(overlayEnabled);
    }

    public void initiateSplitSelect(SplitPositionOption splitPositionOption) {
        AbstractFloatingView.closeOpenViews(mActivity, false, TYPE_TASK_MENU);
        getRecentsView().initiateSplitSelect(this, splitPositionOption);
    }

    /**
     * Set a color tint on the snapshot and supporting views.
     */
    public void setColorTint(float amount, int tintColor) {
        mSnapshotView.setDimAlpha(amount);
        mIconView.setIconColorTint(tintColor, amount);
        mDigitalWellBeingToast.setBannerColorTint(tintColor, amount);
    }

    /**
     * We update and subsequently draw these in {@link #setFullscreenProgress(float)}.
     */
    public static class FullscreenDrawParams {

        private final float mCornerRadius;
        private final float mWindowCornerRadius;

        public RectF mCurrentDrawnInsets = new RectF();
        public float mCurrentDrawnCornerRadius;
        /** The current scale we apply to the thumbnail to adjust for new left/right insets. */
        public float mScale = 1;

        public FullscreenDrawParams(Context context) {
            mCornerRadius = TaskCornerRadius.get(context);
            mWindowCornerRadius = QuickStepContract.getWindowCornerRadius(context.getResources());

            mCurrentDrawnCornerRadius = mCornerRadius;
        }

        /**
         * Sets the progress in range [0, 1]
         */
        public void setProgress(float fullscreenProgress, float parentScale, float taskViewScale,
                int previewWidth, DeviceProfile dp, PreviewPositionHelper pph) {
            RectF insets = pph.getInsetsToDrawInFullscreen();

            float currentInsetsLeft = insets.left * fullscreenProgress;
            float currentInsetsRight = insets.right * fullscreenProgress;
            mCurrentDrawnInsets.set(currentInsetsLeft, insets.top * fullscreenProgress,
                    currentInsetsRight, insets.bottom * fullscreenProgress);
            float fullscreenCornerRadius = dp.isMultiWindowMode ? 0 : mWindowCornerRadius;

            mCurrentDrawnCornerRadius =
                    Utilities.mapRange(fullscreenProgress, mCornerRadius, fullscreenCornerRadius)
                            / parentScale / taskViewScale;

            // We scaled the thumbnail to fit the content (excluding insets) within task view width.
            // Now that we are drawing left/right insets again, we need to scale down to fit them.
            if (previewWidth > 0) {
                mScale = previewWidth / (previewWidth + currentInsetsLeft + currentInsetsRight);
            }
        }

    }
}
