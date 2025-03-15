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

import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.launcher3.BaseActivity.EVENT_DESTROYED;
import static com.android.launcher3.Flags.enableUnfoldStateAnimation;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarNoRecreate;
import static com.android.launcher3.util.DisplayController.CHANGE_DENSITY;
import static com.android.launcher3.util.DisplayController.CHANGE_DESKTOP_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_TASKBAR_PINNING;
import static com.android.launcher3.util.DisplayController.TASKBAR_NOT_DESTROYED_TAG;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.FlagDebugUtils.formatFlagChange;
import static com.android.quickstep.util.SystemActionConstants.ACTION_SHOW_TASKBAR;
import static com.android.quickstep.util.SystemActionConstants.SYSTEM_ACTION_ID_TASKBAR;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Trace;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.contextualeducation.ContextualEduStatsManager;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks;
import com.android.launcher3.taskbar.unfold.NonDestroyableScopedUnfoldTransitionProgressProvider;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.AllAppsActionManager;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.util.ContextualSearchInvoker;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

import lineageos.providers.LineageSettings;

import java.io.PrintWriter;
import java.util.StringJoiner;

/**
 * Class to manage taskbar lifecycle
 */
public class TaskbarManager {
    private static final String TAG = "TaskbarManager";
    private static final boolean DEBUG = false;

    /**
     * All the configurations which do not initiate taskbar recreation.
     * This includes all the configurations defined in Launcher's manifest entry and
     * ActivityController#filterConfigChanges
     */
    private static final int SKIP_RECREATE_CONFIG_CHANGES = ActivityInfo.CONFIG_WINDOW_CONFIGURATION
            | ActivityInfo.CONFIG_KEYBOARD
            | ActivityInfo.CONFIG_KEYBOARD_HIDDEN
            | ActivityInfo.CONFIG_MCC
            | ActivityInfo.CONFIG_MNC
            | ActivityInfo.CONFIG_NAVIGATION
            | ActivityInfo.CONFIG_ORIENTATION
            | ActivityInfo.CONFIG_SCREEN_SIZE
            | ActivityInfo.CONFIG_SCREEN_LAYOUT
            | ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE;

    private static final Uri USER_SETUP_COMPLETE_URI = Settings.Secure.getUriFor(
            Settings.Secure.USER_SETUP_COMPLETE);

    private static final Uri NAV_BAR_KIDS_MODE = Settings.Secure.getUriFor(
            Settings.Secure.NAV_BAR_KIDS_MODE);

    public static final Uri NAV_BAR_INVERSE = Settings.Secure.getUriFor(
            "sysui_nav_bar_inverse");

    public static final Uri ENABLE_TASKBAR = LineageSettings.System.getUriFor(
            LineageSettings.System.ENABLE_TASKBAR);

    public static final Uri NAVIGATION_BAR_HINT = LineageSettings.System.getUriFor(
            LineageSettings.System.NAVIGATION_BAR_HINT);

    private final Context mWindowContext;
    private final @Nullable Context mNavigationBarPanelContext;
    private WindowManager mWindowManager;
    private boolean mAddedWindow;
    private final TaskbarNavButtonController mDefaultNavButtonController;
    private final ComponentCallbacks mDefaultComponentCallbacks;

    private final SimpleBroadcastReceiver mShutdownReceiver =
            new SimpleBroadcastReceiver(UI_HELPER_EXECUTOR, i -> destroyAllTaskbars());

    // The source for this provider is set when Launcher is available
    // We use 'non-destroyable' version here so the original provider won't be destroyed
    // as it is tied to the activity lifecycle, not the taskbar lifecycle.
    // It's destruction/creation will be managed by the activity.
    private final ScopedUnfoldTransitionProgressProvider mUnfoldProgressProvider =
            new NonDestroyableScopedUnfoldTransitionProgressProvider();
    /** DisplayId - {@link TaskbarActivityContext} map for Connected Display. */
    private final SparseArray<TaskbarActivityContext> mTaskbars = new SparseArray<>();
    /** DisplayId - {@link FrameLayout} map for Connected Display. */
    private final SparseArray<FrameLayout> mRootLayouts = new SparseArray<>();
    private StatefulActivity mActivity;
    private RecentsViewContainer mRecentsViewContainer;

    /**
     * Cache a copy here so we can initialize state whenever taskbar is recreated, since
     * this class does not get re-initialized w/ new taskbars.
     */
    private final TaskbarSharedState mSharedState = new TaskbarSharedState();

    /**
     * We use WindowManager's ComponentCallbacks() for internal UI changes (similar to an Activity)
     * which comes via a different channel
     */
    private final RecreationListener mRecreationListener = new RecreationListener();

    private class RecreationListener implements DisplayController.DisplayInfoChangeListener {
        @Override
        public void onDisplayInfoChanged(Context context, DisplayController.Info info, int flags) {
            if ((flags & (CHANGE_DENSITY | CHANGE_NAVIGATION_MODE | CHANGE_DESKTOP_MODE
                    | CHANGE_TASKBAR_PINNING)) != 0) {
                recreateTaskbar();
            }
        }
    }
    private final SettingsCache.OnChangeListener mOnSettingsChangeListener = c -> recreateTaskbar();
    private final SettingsCache.OnChangeListener mOnTaskBarChangeListener = c -> System.exit(0);

    private boolean mUserUnlocked = false;

    private final SimpleBroadcastReceiver mTaskbarBroadcastReceiver =
            new SimpleBroadcastReceiver(UI_HELPER_EXECUTOR, this::showTaskbarFromBroadcast);

    private final AllAppsActionManager mAllAppsActionManager;

    private final Runnable mActivityOnDestroyCallback = new Runnable() {
        @Override
        public void run() {
            int displayId = getDefaultDisplayId();
            if (mActivity != null) {
                displayId = mActivity.getDisplayId();
                mActivity.removeOnDeviceProfileChangeListener(
                        mDebugActivityDeviceProfileChanged);
                Log.d(TASKBAR_NOT_DESTROYED_TAG,
                        "unregistering activity lifecycle callbacks from "
                                + "onActivityDestroyed.");
                mActivity.removeEventCallback(EVENT_DESTROYED, this);
            }
            if (mActivity == mRecentsViewContainer) {
                mRecentsViewContainer = null;
            }
            mActivity = null;
            debugWhyTaskbarNotDestroyed("clearActivity");
            TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
            if (taskbar != null) {
                taskbar.setUIController(TaskbarUIController.DEFAULT);
            }
            mUnfoldProgressProvider.setSourceProvider(null);
        }
    };

    UnfoldTransitionProgressProvider.TransitionProgressListener mUnfoldTransitionProgressListener =
            new UnfoldTransitionProgressProvider.TransitionProgressListener() {
                @Override
                public void onTransitionStarted() {
                    Log.d(TASKBAR_NOT_DESTROYED_TAG,
                            "fold/unfold transition started getting called.");
                }

                @Override
                public void onTransitionProgress(float progress) {
                    Log.d(TASKBAR_NOT_DESTROYED_TAG,
                            "fold/unfold transition progress : " + progress);
                }

                @Override
                public void onTransitionFinishing() {
                    Log.d(TASKBAR_NOT_DESTROYED_TAG,
                            "fold/unfold transition finishing getting called.");

                }

                @Override
                public void onTransitionFinished() {
                    Log.d(TASKBAR_NOT_DESTROYED_TAG,
                            "fold/unfold transition finished getting called.");

                }
            };

    @NonNull private final DesktopVisibilityController mDesktopVisibilityController;

    @SuppressLint("WrongConstant")
    public TaskbarManager(
            Context context,
            AllAppsActionManager allAppsActionManager,
            TaskbarNavButtonCallbacks navCallbacks,
            @NonNull DesktopVisibilityController desktopVisibilityController) {
        Display display =
                context.getSystemService(DisplayManager.class).getDisplay(context.getDisplayId());
        mWindowContext = context.createWindowContext(display,
                ENABLE_TASKBAR_NAVBAR_UNIFICATION ? TYPE_NAVIGATION_BAR : TYPE_NAVIGATION_BAR_PANEL,
                null);
        mAllAppsActionManager = allAppsActionManager;
        mNavigationBarPanelContext = ENABLE_TASKBAR_NAVBAR_UNIFICATION
                ? context.createWindowContext(display, TYPE_NAVIGATION_BAR_PANEL, null)
                : null;
        mDesktopVisibilityController = desktopVisibilityController;
        if (enableTaskbarNoRecreate()) {
            mWindowManager = mWindowContext.getSystemService(WindowManager.class);
            createTaskbarRootLayout(getDefaultDisplayId());
        }
        mDefaultNavButtonController = createDefaultNavButtonController(context, navCallbacks);
        mDefaultComponentCallbacks = createDefaultComponentCallbacks();
        SettingsCache.INSTANCE.get(mWindowContext)
                .register(USER_SETUP_COMPLETE_URI, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mWindowContext)
                .register(NAV_BAR_KIDS_MODE, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mWindowContext)
                .register(NAV_BAR_INVERSE, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mWindowContext)
                .register(ENABLE_TASKBAR, mOnTaskBarChangeListener);
        SettingsCache.INSTANCE.get(mWindowContext)
                .register(NAVIGATION_BAR_HINT, mOnTaskBarChangeListener);
        Log.d(TASKBAR_NOT_DESTROYED_TAG, "registering component callbacks from constructor.");
        mWindowContext.registerComponentCallbacks(mDefaultComponentCallbacks);
        mShutdownReceiver.register(mWindowContext, Intent.ACTION_SHUTDOWN);
        UI_HELPER_EXECUTOR.execute(() -> {
            mSharedState.taskbarSystemActionPendingIntent = PendingIntent.getBroadcast(
                    mWindowContext,
                    SYSTEM_ACTION_ID_TASKBAR,
                    new Intent(ACTION_SHOW_TASKBAR).setPackage(mWindowContext.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            mTaskbarBroadcastReceiver.register(
                    mWindowContext, RECEIVER_NOT_EXPORTED, ACTION_SHOW_TASKBAR);
        });

        debugWhyTaskbarNotDestroyed("TaskbarManager created");
        recreateTaskbar();
    }

    @NonNull
    private TaskbarNavButtonController createDefaultNavButtonController(Context context,
            TaskbarNavButtonCallbacks navCallbacks) {
        return new TaskbarNavButtonController(
                context,
                navCallbacks,
                SystemUiProxy.INSTANCE.get(mWindowContext),
                ContextualEduStatsManager.INSTANCE.get(mWindowContext),
                new Handler(),
                new ContextualSearchInvoker(mWindowContext));
    }

    private ComponentCallbacks createDefaultComponentCallbacks() {
        return new ComponentCallbacks() {
            private Configuration mOldConfig = mWindowContext.getResources().getConfiguration();

            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                Trace.instantForTrack(Trace.TRACE_TAG_APP, "TaskbarManager",
                        "onConfigurationChanged: " + newConfig);
                debugWhyTaskbarNotDestroyed(
                        "TaskbarManager#mComponentCallbacks.onConfigurationChanged: " + newConfig);
                // TODO: adapt this logic to be specific to different displays.
                DeviceProfile dp = mUserUnlocked
                        ? LauncherAppState.getIDP(mWindowContext).getDeviceProfile(mWindowContext)
                        : null;
                int configDiff = mOldConfig.diff(newConfig) & ~SKIP_RECREATE_CONFIG_CHANGES;

                if ((configDiff & ActivityInfo.CONFIG_UI_MODE) != 0) {
                    // Only recreate for theme changes, not other UI mode changes such as docking.
                    int oldUiNightMode = (mOldConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    int newUiNightMode = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    if (oldUiNightMode == newUiNightMode) {
                        configDiff &= ~ActivityInfo.CONFIG_UI_MODE;
                    }
                }

                debugWhyTaskbarNotDestroyed("ComponentCallbacks#onConfigurationChanged() "
                        + "configDiff=" + Configuration.configurationDiffToString(configDiff));
                if (configDiff != 0 || getCurrentActivityContext() == null) {
                    recreateTaskbar();
                } else {
                    // Config change might be handled without re-creating the taskbar
                    if (dp != null && !isTaskbarEnabled(dp)) {
                        destroyDefaultTaskbar();
                    } else {
                        if (dp != null && isTaskbarEnabled(dp)) {
                            if (ENABLE_TASKBAR_NAVBAR_UNIFICATION) {
                                // Re-initialize for screen size change? Should this be done
                                // by looking at screen-size change flag in configDiff in the
                                // block above?
                                recreateTaskbar();
                            } else {
                                getCurrentActivityContext().updateDeviceProfile(dp);
                            }
                        }
                        getCurrentActivityContext().onConfigurationChanged(configDiff);
                    }
                }
                mOldConfig = new Configuration(newConfig);
                // reset taskbar was pinned value, so we don't automatically unstash taskbar upon
                // user unfolding the device.
                mSharedState.setTaskbarWasPinned(false);
            }

            @Override
            public void onLowMemory() { }
        };
    }

    private void destroyAllTaskbars() {
        for (int i = 0; i < mTaskbars.size(); i++) {
            int displayId = mTaskbars.keyAt(i);
            destroyTaskbarForDisplay(displayId);
            removeTaskbarRootViewFromWindow(displayId);
        }
    }

    private void destroyDefaultTaskbar() {
        destroyTaskbarForDisplay(getDefaultDisplayId());
    }

    private void destroyTaskbarForDisplay(int displayId) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        debugWhyTaskbarNotDestroyed(
                "destroyTaskbarForDisplay: " + taskbar + " displayId=" + displayId);
        if (taskbar != null) {
            taskbar.onDestroy();
            // remove all defaults that we store
            removeTaskbarFromMap(displayId);
        }
        DeviceProfile dp = mUserUnlocked ?
                LauncherAppState.getIDP(mWindowContext).getDeviceProfile(mWindowContext) : null;
        if (dp == null || !isTaskbarEnabled(dp)) {
            removeTaskbarRootViewFromWindow(displayId);
        }
    }

    /**
     * Show Taskbar upon receiving broadcast
     */
    private void showTaskbarFromBroadcast(Intent intent) {
        // TODO: make this code displayId specific
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (ACTION_SHOW_TASKBAR.equals(intent.getAction()) && taskbar != null) {
            taskbar.showTaskbarFromBroadcast();
        }
    }

    /**
     * Toggles All Apps for Taskbar or Launcher depending on the current state.
     */
    public void toggleAllApps() {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar == null || taskbar.canToggleHomeAllApps()) {
            // Home All Apps should be toggled from this class, because the controllers are not
            // initialized when Taskbar is disabled (i.e. TaskbarActivityContext is null).
            if (mActivity instanceof Launcher l) l.toggleAllAppsSearch();
        } else {
            taskbar.toggleAllAppsSearch();
        }
    }

    /**
     * Displays a frame of the first Launcher reveal animation.
     *
     * This should be used to run a first Launcher reveal animation whose progress matches a swipe
     * progress.
     */
    public AnimatorPlaybackController createLauncherStartFromSuwAnim(int duration) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        return taskbar == null ? null : taskbar.createLauncherStartFromSuwAnim(duration);
    }

    /**
     * Called when the user is unlocked
     */
    public void onUserUnlocked() {
        mUserUnlocked = true;
        DisplayController.INSTANCE.get(mWindowContext).addChangeListener(mRecreationListener);
        recreateTaskbar();
        addTaskbarRootViewToWindow(getDefaultDisplayId());
    }

    /**
     * Sets a {@link StatefulActivity} to act as taskbar callback
     */
    public void setActivity(@NonNull StatefulActivity activity) {
        if (mActivity == activity) {
            return;
        }
        removeActivityCallbacksAndListeners();
        mActivity = activity;
        debugWhyTaskbarNotDestroyed("Set mActivity=" + mActivity);
        mActivity.addOnDeviceProfileChangeListener(mDebugActivityDeviceProfileChanged);
        Log.d(TASKBAR_NOT_DESTROYED_TAG,
                "registering activity lifecycle callbacks from setActivity().");
        mActivity.addEventCallback(EVENT_DESTROYED, mActivityOnDestroyCallback);
        UnfoldTransitionProgressProvider unfoldTransitionProgressProvider =
                getUnfoldTransitionProgressProviderForActivity(activity);
        if (unfoldTransitionProgressProvider != null) {
            unfoldTransitionProgressProvider.addCallback(mUnfoldTransitionProgressListener);
        }
        mUnfoldProgressProvider.setSourceProvider(unfoldTransitionProgressProvider);

        if (activity instanceof RecentsViewContainer recentsViewContainer) {
            setRecentsViewContainer(recentsViewContainer);
        }
    }

    /**
     * Sets the current RecentsViewContainer, from which we create a TaskbarUIController.
     */
    public void setRecentsViewContainer(@NonNull RecentsViewContainer recentsViewContainer) {
        if (mRecentsViewContainer == recentsViewContainer) {
            return;
        }
        if (mRecentsViewContainer == mActivity) {
            // When switching to RecentsWindowManager (not an Activity), the old mActivity is not
            // destroyed, nor is there a new Activity to replace it. Thus if we don't clear it here,
            // it will not get re-set properly if we return to the Activity (e.g. NexusLauncher).
            mActivityOnDestroyCallback.run();
        }
        mRecentsViewContainer = recentsViewContainer;
        TaskbarActivityContext taskbar = getCurrentActivityContext();
        if (taskbar != null) {
            taskbar.setUIController(
                    createTaskbarUIControllerForRecentsViewContainer(mRecentsViewContainer));
        }
    }

    /**
     * Returns an {@link UnfoldTransitionProgressProvider} to use while the given StatefulActivity
     * is active.
     */
    private UnfoldTransitionProgressProvider getUnfoldTransitionProgressProviderForActivity(
            StatefulActivity activity) {
        if (!enableUnfoldStateAnimation()) {
            if (activity instanceof QuickstepLauncher ql) {
                return ql.getUnfoldTransitionProgressProvider();
            }
        } else {
            return SystemUiProxy.INSTANCE.get(mWindowContext).getUnfoldTransitionProvider();
        }
        return null;
    }

    /**
     * Creates a {@link TaskbarUIController} to use while the given StatefulActivity is active.
     */
    private TaskbarUIController createTaskbarUIControllerForRecentsViewContainer(
            RecentsViewContainer container) {
        if (container instanceof QuickstepLauncher quickstepLauncher) {
            return new LauncherTaskbarUIController(quickstepLauncher);
        }
        // If a 3P Launcher is default, always use FallbackTaskbarUIController regardless of
        // whether the recents container is RecentsActivity or RecentsWindowManager.
        if (container instanceof RecentsActivity recentsActivity) {
            return new FallbackTaskbarUIController<>(recentsActivity);
        }
        if (container instanceof RecentsWindowManager recentsWindowManager) {
            return new FallbackTaskbarUIController<>(recentsWindowManager);
        }
        return TaskbarUIController.DEFAULT;
    }

    /**
     * This method is called multiple times (ex. initial init, then when user unlocks) in which case
     * we fully want to destroy the existing default display's taskbar and create a new one.
     * In other case (folding/unfolding) we don't need to remove and add window.
     */
    @VisibleForTesting
    public synchronized void recreateTaskbar() {
        // TODO: make this recreate all taskbars in map.
        recreateTaskbarForDisplay(getDefaultDisplayId());
    }

    /**
     * This method is called multiple times (ex. initial init, then when user unlocks) in which case
     * we fully want to destroy an existing taskbar for a specified display and create a new one.
     * In other case (folding/unfolding) we don't need to remove and add window.
     */
    private void recreateTaskbarForDisplay(int displayId) {
        Trace.beginSection("recreateTaskbar");
        try {
            DeviceProfile dp = mUserUnlocked ?
                    LauncherAppState.getIDP(mWindowContext).getDeviceProfile(mWindowContext) : null;

            // All Apps action is unrelated to navbar unification, so we only need to check DP.
            final boolean isLargeScreenTaskbar = dp != null && dp.isTaskbarPresent;
            mAllAppsActionManager.setTaskbarPresent(isLargeScreenTaskbar);

            destroyTaskbarForDisplay(displayId);

            boolean isTaskbarEnabled = dp != null && isTaskbarEnabled(dp);
            debugWhyTaskbarNotDestroyed("recreateTaskbar: isTaskbarEnabled=" + isTaskbarEnabled
                + " [dp != null (i.e. mUserUnlocked)]=" + (dp != null)
                + " FLAG_HIDE_NAVBAR_WINDOW=" + ENABLE_TASKBAR_NAVBAR_UNIFICATION
                + " dp.isTaskbarPresent=" + (dp == null ? "null" : dp.isTaskbarPresent));
            if (!isTaskbarEnabled || !isLargeScreenTaskbar) {
                SystemUiProxy.INSTANCE.get(mWindowContext)
                    .notifyTaskbarStatus(/* visible */ false, /* stashed */ false);
                if (!isTaskbarEnabled) {
                    return;
                }
            }

            TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
            if (enableTaskbarNoRecreate() || taskbar == null) {
                taskbar = createTaskbarActivityContext(dp, displayId);
            } else {
                taskbar.updateDeviceProfile(dp);
            }
            mSharedState.startTaskbarVariantIsTransient =
                    DisplayController.isTransientTaskbar(taskbar);
            mSharedState.allAppsVisible = mSharedState.allAppsVisible && isLargeScreenTaskbar;
            taskbar.init(mSharedState);

            if (mRecentsViewContainer != null) {
                taskbar.setUIController(
                        createTaskbarUIControllerForRecentsViewContainer(mRecentsViewContainer));
            }

            if (enableTaskbarNoRecreate()) {
                addTaskbarRootViewToWindow(displayId);
                FrameLayout taskbarRootLayout = getTaskbarRootLayoutForDisplay(displayId);
                taskbarRootLayout.removeAllViews();
                taskbarRootLayout.addView(taskbar.getDragLayer());
                taskbar.notifyUpdateLayoutParams();
            }
        } finally {
            Trace.endSection();
        }
    }

    public void onSystemUiFlagsChanged(@SystemUiStateFlags long systemUiStateFlags) {
        if (DEBUG) {
            Log.d(TAG, "SysUI flags changed: " + formatFlagChange(systemUiStateFlags,
                    mSharedState.sysuiStateFlags, QuickStepContract::getSystemUiStateString));
        }
        mSharedState.sysuiStateFlags = systemUiStateFlags;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar != null) {
            taskbar.updateSysuiStateFlags(systemUiStateFlags, false /* fromInit */);
        }
    }

    public void onLongPressHomeEnabled(boolean assistantLongPressEnabled) {
        if (mDefaultNavButtonController != null) {
            mDefaultNavButtonController.setAssistantLongPressEnabled(assistantLongPressEnabled);
        }
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    public void setSetupUIVisible(boolean isVisible) {
        mSharedState.setupUIVisible = isVisible;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar != null) {
            taskbar.setSetupUIVisible(isVisible);
        }
    }

    public void setWallpaperVisible(boolean isVisible) {
        mSharedState.wallpaperVisible = isVisible;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar != null) {
            taskbar.setWallpaperVisible(isVisible);
        }
    }

    public void checkNavBarModes(int displayId) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.checkNavBarModes();
        }
    }

    public void finishBarAnimations(int displayId) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.finishBarAnimations();
        }
    }

    public void touchAutoDim(int displayId, boolean reset) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.touchAutoDim(reset);
        }
    }

    public void transitionTo(int displayId, @BarTransitions.TransitionMode int barMode,
            boolean animate) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.transitionTo(barMode, animate);
        }
    }

    public void appTransitionPending(boolean pending) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar != null) {
            taskbar.appTransitionPending(pending);
        }
    }

    private boolean isTaskbarEnabled(DeviceProfile deviceProfile) {
        return ENABLE_TASKBAR_NAVBAR_UNIFICATION || deviceProfile.isTaskbarPresent;
    }

    public void onRotationProposal(int rotation, boolean isValid) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar != null) {
            taskbar.onRotationProposal(rotation, isValid);
        }
    }

    public void disableNavBarElements(int displayId, int state1, int state2, boolean animate) {
        mSharedState.disableNavBarDisplayId = displayId;
        mSharedState.disableNavBarState1 = state1;
        mSharedState.disableNavBarState2 = state2;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.disableNavBarElements(displayId, state1, state2, animate);
        }
    }

    public void onSystemBarAttributesChanged(int displayId, int behavior) {
        mSharedState.systemBarAttrsDisplayId = displayId;
        mSharedState.systemBarAttrsBehavior = behavior;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.onSystemBarAttributesChanged(displayId, behavior);
        }
    }

    public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {
        mSharedState.barMode = barMode;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar != null) {
            taskbar.onTransitionModeUpdated(barMode, checkBarModes);
        }
    }

    public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
        mSharedState.navButtonsDarkIntensity = darkIntensity;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar != null) {
            taskbar.onNavButtonsDarkIntensityChanged(darkIntensity);
        }
    }

    public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
        mSharedState.mLumaSamplingDisplayId = displayId;
        mSharedState.mIsLumaSamplingEnabled = enable;
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (taskbar != null) {
            taskbar.onNavigationBarLumaSamplingEnabled(displayId, enable);
        }
    }

    private void removeActivityCallbacksAndListeners() {
        if (mActivity != null) {
            mActivity.removeOnDeviceProfileChangeListener(mDebugActivityDeviceProfileChanged);
            Log.d(TASKBAR_NOT_DESTROYED_TAG,
                    "unregistering activity lifecycle callbacks from "
                            + "removeActivityCallbackAndListeners().");
            mActivity.removeEventCallback(EVENT_DESTROYED, mActivityOnDestroyCallback);
            UnfoldTransitionProgressProvider unfoldTransitionProgressProvider =
                    getUnfoldTransitionProgressProviderForActivity(mActivity);
            if (unfoldTransitionProgressProvider != null) {
                unfoldTransitionProgressProvider.removeCallback(mUnfoldTransitionProgressListener);
            }
        }
    }

    /**
     * Called when the manager is no longer needed
     */
    public void destroy() {
        mRecentsViewContainer = null;
        debugWhyTaskbarNotDestroyed("TaskbarManager#destroy()");
        removeActivityCallbacksAndListeners();
        mTaskbarBroadcastReceiver.unregisterReceiverSafely(mWindowContext);
        destroyAllTaskbars();
        if (mUserUnlocked) {
            DisplayController.INSTANCE.get(mWindowContext).removeChangeListener(
                    mRecreationListener);
        }
        SettingsCache.INSTANCE.get(mWindowContext)
                .unregister(USER_SETUP_COMPLETE_URI, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mWindowContext)
                .unregister(NAV_BAR_KIDS_MODE, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mWindowContext)
                .unregister(NAV_BAR_INVERSE, mOnSettingsChangeListener);
        SettingsCache.INSTANCE.get(mWindowContext)
                .unregister(ENABLE_TASKBAR, mOnTaskBarChangeListener);
        SettingsCache.INSTANCE.get(mWindowContext)
                .unregister(NAVIGATION_BAR_HINT, mOnTaskBarChangeListener);
        Log.d(TASKBAR_NOT_DESTROYED_TAG, "unregistering component callbacks from destroy().");
        mWindowContext.unregisterComponentCallbacks(mDefaultComponentCallbacks);
        mShutdownReceiver.unregisterReceiverSafely(mWindowContext);
    }

    public @Nullable TaskbarActivityContext getCurrentActivityContext() {
        return getTaskbarForDisplay(mWindowContext.getDisplayId());
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarManager:");
        TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
        if (taskbar == null) {
            pw.println(prefix + "\tTaskbarActivityContext: null");
        } else {
            taskbar.dumpLogs(prefix + "\t", pw);
        }
    }

    private void addTaskbarRootViewToWindow(int displayId) {
        TaskbarActivityContext taskbar = getTaskbarForDisplay(displayId);
        if (enableTaskbarNoRecreate() && !mAddedWindow && taskbar != null) {
            mWindowManager.addView(getTaskbarRootLayoutForDisplay(displayId),
                    taskbar.getWindowLayoutParams());
            mAddedWindow = true;
        }
    }

    private void removeTaskbarRootViewFromWindow(int displayId) {
        FrameLayout rootLayout = getTaskbarRootLayoutForDisplay(displayId);
        if (enableTaskbarNoRecreate() && mAddedWindow && rootLayout != null) {
            mWindowManager.removeViewImmediate(rootLayout);
            mAddedWindow = false;
            removeTaskbarRootLayoutFromMap(displayId);
        }
    }

    /**
     * Returns the {@link TaskbarActivityContext} associated with the given display ID.
     *
     * @param displayId The ID of the display to retrieve the taskbar for.
     * @return The {@link TaskbarActivityContext} for the specified display, or
     *         {@code null} if no taskbar is associated with that display.
     */
    private TaskbarActivityContext getTaskbarForDisplay(int displayId) {
        return mTaskbars.get(displayId);
    }


    /**
     * Creates a {@link TaskbarActivityContext} for the given display and adds it to the map.
     */
    private TaskbarActivityContext createTaskbarActivityContext(DeviceProfile dp, int displayId) {
        TaskbarActivityContext newTaskbar = new TaskbarActivityContext(mWindowContext,
                mNavigationBarPanelContext, dp, mDefaultNavButtonController,
                mUnfoldProgressProvider, mDesktopVisibilityController);

        addTaskbarToMap(displayId, newTaskbar);
        return newTaskbar;
    }

    /**
     * Adds the {@link TaskbarActivityContext} associated with the given display ID to taskbar
     * map if there is not already a taskbar mapped to that displayId.
     *
     * @param displayId The ID of the display to retrieve the taskbar for.
     * @param newTaskbar The new {@link TaskbarActivityContext} to add to the map.
     */
    private void addTaskbarToMap(int displayId, TaskbarActivityContext newTaskbar) {
        if (!mTaskbars.contains(displayId)) {
            mTaskbars.put(displayId, newTaskbar);
        }
    }

    /**
     * Removes the taskbar associated with the given display ID from the taskbar map.
     *
     * @param displayId The ID of the display for which to remove the taskbar.
     */
    private void removeTaskbarFromMap(int displayId) {
        mTaskbars.delete(displayId);
    }

    /**
     * Creates {@link FrameLayout} for the taskbar on the specified display and adds it to map.
     * @param displayId The ID of the display for which to create the taskbar root layout.
     */
    private void createTaskbarRootLayout(int displayId) {
        FrameLayout newTaskbarRootLayout = new FrameLayout(mWindowContext) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                // The motion events can be outside the view bounds of task bar, and hence
                // manually dispatching them to the drag layer here.
                TaskbarActivityContext taskbar = getTaskbarForDisplay(getDefaultDisplayId());
                if (taskbar != null && taskbar.getDragLayer().isAttachedToWindow()) {
                    return taskbar.getDragLayer().dispatchTouchEvent(ev);
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        addTaskbarRootLayoutToMap(displayId, newTaskbarRootLayout);
    }

    /**
     * Retrieves the root layout of the taskbar for the specified display.
     *
     * @param displayId The ID of the display for which to retrieve the taskbar root layout.
     * @return The taskbar root layout {@link FrameLayout} for a given display or {@code null}.
     */
    private FrameLayout getTaskbarRootLayoutForDisplay(int displayId) {
        return mRootLayouts.get(displayId);
    }

    /**
     * Adds the taskbar root layout {@link FrameLayout} to taskbar map, mapped to display ID.
     *
     * @param displayId The ID of the display to associate with the taskbar root layout.
     * @param rootLayout The taskbar root layout {@link FrameLayout} to add to the map.
     */
    private void addTaskbarRootLayoutToMap(int displayId, FrameLayout rootLayout) {
        if (!mRootLayouts.contains(displayId) && rootLayout != null) {
            mRootLayouts.put(displayId, rootLayout);
        }
    }

    /**
     * Removes taskbar root layout {@link FrameLayout} for given display ID from the taskbar map.
     *
     * @param displayId The ID of the display for which to remove the taskbar root layout.
     */
    private void removeTaskbarRootLayoutFromMap(int displayId) {
        if (mRootLayouts.contains(displayId)) {
            mRootLayouts.delete(displayId);
        }
    }

    private int getDefaultDisplayId() {
        return mWindowContext.getDisplayId();
    }

    /** Temp logs for b/254119092. */
    public void debugWhyTaskbarNotDestroyed(String debugReason) {
        StringJoiner log = new StringJoiner("\n");
        log.add(debugReason);

        boolean activityTaskbarPresent = mActivity != null
                && mActivity.getDeviceProfile().isTaskbarPresent;
        boolean contextTaskbarPresent = mUserUnlocked && LauncherAppState.getIDP(mWindowContext)
                .getDeviceProfile(mWindowContext).isTaskbarPresent;
        if (activityTaskbarPresent == contextTaskbarPresent) {
            log.add("mActivity and mWindowContext agree taskbarIsPresent=" + contextTaskbarPresent);
            Log.d(TASKBAR_NOT_DESTROYED_TAG, log.toString());
            return;
        }

        log.add("mActivity & mWindowContext device profiles have different values, add more logs.");

        log.add("\tmActivity logs:");
        log.add("\t\tmActivity=" + mActivity);
        if (mActivity != null) {
            log.add("\t\tmActivity.getResources().getConfiguration()="
                    + mActivity.getResources().getConfiguration());
            log.add("\t\tmActivity.getDeviceProfile().isTaskbarPresent="
                    + activityTaskbarPresent);
        }
        log.add("\tmWindowContext logs:");
        log.add("\t\tmWindowContext=" + mWindowContext);
        log.add("\t\tmWindowContext.getResources().getConfiguration()="
                + mWindowContext.getResources().getConfiguration());
        if (mUserUnlocked) {
            log.add("\t\tLauncherAppState.getIDP().getDeviceProfile(mWindowContext)"
                    + ".isTaskbarPresent=" + contextTaskbarPresent);
        } else {
            log.add("\t\tCouldn't get DeviceProfile because !mUserUnlocked");
        }

        Log.d(TASKBAR_NOT_DESTROYED_TAG, log.toString());
    }

    private final DeviceProfile.OnDeviceProfileChangeListener mDebugActivityDeviceProfileChanged =
            dp -> debugWhyTaskbarNotDestroyed("mActivity onDeviceProfileChanged");

    @VisibleForTesting
    public Context getWindowContext() {
        return mWindowContext;
    }
}
