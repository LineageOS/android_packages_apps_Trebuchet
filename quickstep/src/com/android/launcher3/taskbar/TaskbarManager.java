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

import static android.content.pm.PackageManager.FEATURE_PC;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import static com.android.launcher3.util.DisplayController.CHANGE_DENSITY;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.unfold.NonDestroyableScopedUnfoldTransitionProgressProvider;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

import lineageos.providers.LineageSettings;

import java.io.PrintWriter;

/**
 * Class to manage taskbar lifecycle
 */
public class TaskbarManager {

    private static final Uri USER_SETUP_COMPLETE_URI = Settings.Secure.getUriFor(
            Settings.Secure.USER_SETUP_COMPLETE);

    private static final Uri NAV_BAR_KIDS_MODE = Settings.Secure.getUriFor(
            Settings.Secure.NAV_BAR_KIDS_MODE);

    private static final Uri ENABLE_TASKBAR_URI = LineageSettings.System.getUriFor(
            LineageSettings.System.ENABLE_TASKBAR);

    private final Context mContext;
    private final DisplayController mDisplayController;
    private final TaskbarNavButtonController mNavButtonController;
    private final SettingsCache.OnChangeListener mUserSetupCompleteListener;
    private final SettingsCache.OnChangeListener mNavBarKidsModeListener;
    private final SettingsCache.OnChangeListener mEnableTaskBarListener;
    private final ComponentCallbacks mComponentCallbacks;
    private final SimpleBroadcastReceiver mShutdownReceiver;

    // The source for this provider is set when Launcher is available
    // We use 'non-destroyable' version here so the original provider won't be destroyed
    // as it is tied to the activity lifecycle, not the taskbar lifecycle.
    // It's destruction/creation will be managed by the activity.
    private final ScopedUnfoldTransitionProgressProvider mUnfoldProgressProvider =
            new NonDestroyableScopedUnfoldTransitionProgressProvider();

    private TaskbarActivityContext mTaskbarActivityContext;
    private StatefulActivity mActivity;
    /**
     * Cache a copy here so we can initialize state whenever taskbar is recreated, since
     * this class does not get re-initialized w/ new taskbars.
     */
    private final TaskbarSharedState mSharedState = new TaskbarSharedState();

    /**
     * We use WindowManager's ComponentCallbacks() for most of the config changes, however for
     * navigation mode, that callback gets called too soon, before it's internal navigation mode
     * reflects the current one.
     * DisplayController's callback is delayed enough to get the correct nav mode value
     *
     * We also use density change here because DeviceProfile has had a chance to update it's state
     * whereas density for component callbacks registered in this class don't update DeviceProfile.
     * Confused? Me too. Make it less confusing (TODO: b/227669780)
     *
     * Flags used with {@link #mDispInfoChangeListener}
     */
    private static final int CHANGE_FLAGS = CHANGE_NAVIGATION_MODE | CHANGE_DENSITY;
    private final DisplayController.DisplayInfoChangeListener mDispInfoChangeListener;

    private boolean mUserUnlocked = false;

    public TaskbarManager(TouchInteractionService service) {
        mDisplayController = DisplayController.INSTANCE.get(service);
        Display display =
                service.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY);
        mContext = service.createWindowContext(display, TYPE_NAVIGATION_BAR_PANEL, null);
        mNavButtonController = new TaskbarNavButtonController(service,
                SystemUiProxy.INSTANCE.get(mContext), new Handler());
        mUserSetupCompleteListener = isUserSetupComplete -> recreateTaskbar();
        mNavBarKidsModeListener = isNavBarKidsMode -> recreateTaskbar();
        mEnableTaskBarListener = isTaskBarEnabled -> {
            // Create the illusion of this taking effect immediately
            // Also needed because TaskbarManager inits before SystemUiProxy on start
            boolean enabled = LineageSettings.System.getInt(mContext.getContentResolver(),
                    LineageSettings.System.ENABLE_TASKBAR, 0) == 1;
            SystemUiProxy.INSTANCE.get(mContext).setTaskbarEnabled(enabled);

            // Restart launcher
            System.exit(0);
        };
        // TODO(b/227669780): Consolidate this w/ DisplayController callbacks
        mComponentCallbacks = new ComponentCallbacks() {
            private Configuration mOldConfig = mContext.getResources().getConfiguration();

            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                DeviceProfile dp = mUserUnlocked
                        ? LauncherAppState.getIDP(mContext).getDeviceProfile(mContext)
                        : null;
                int configDiff = mOldConfig.diff(newConfig);
                int configsRequiringRecreate = ActivityInfo.CONFIG_ASSETS_PATHS
                        | ActivityInfo.CONFIG_LAYOUT_DIRECTION | ActivityInfo.CONFIG_UI_MODE
                        | ActivityInfo.CONFIG_SCREEN_SIZE;
                boolean requiresRecreate = (configDiff & configsRequiringRecreate) != 0;
                if ((configDiff & ActivityInfo.CONFIG_SCREEN_SIZE) != 0
                        && mTaskbarActivityContext != null && dp != null) {
                    // Additional check since this callback gets fired multiple times w/o
                    // screen size changing, or when simply rotating the device.
                    DeviceProfile oldDp = mTaskbarActivityContext.getDeviceProfile();
                    boolean isOrientationChange =
                            (configDiff & ActivityInfo.CONFIG_ORIENTATION) != 0;
                    int oldWidth = isOrientationChange ? oldDp.heightPx : oldDp.widthPx;
                    int oldHeight = isOrientationChange ? oldDp.widthPx : oldDp.heightPx;
                    if (dp.widthPx == oldWidth && dp.heightPx == oldHeight) {
                        configDiff &= ~ActivityInfo.CONFIG_SCREEN_SIZE;
                        requiresRecreate = (configDiff & configsRequiringRecreate) != 0;
                    }
                }

                if (requiresRecreate) {
                    recreateTaskbar();
                } else {
                    // Config change might be handled without re-creating the taskbar
                    if (mTaskbarActivityContext != null) {
                        if (dp != null && dp.isTaskbarPresent) {
                            mTaskbarActivityContext.updateDeviceProfile(dp);
                        }
                        mTaskbarActivityContext.onConfigurationChanged(configDiff);
                    }
                }
                mOldConfig = newConfig;
            }

            @Override
            public void onLowMemory() { }
        };
        mShutdownReceiver = new SimpleBroadcastReceiver(i -> destroyExistingTaskbar());
        mDispInfoChangeListener = (context, info, flags) -> {
            if ((flags & CHANGE_FLAGS) != 0) {
                recreateTaskbar();
            }
        };
        mDisplayController.addChangeListener(mDispInfoChangeListener);
        SettingsCache.INSTANCE.get(mContext).register(USER_SETUP_COMPLETE_URI,
                mUserSetupCompleteListener);
        SettingsCache.INSTANCE.get(mContext).register(NAV_BAR_KIDS_MODE,
                mNavBarKidsModeListener);
        SettingsCache.INSTANCE.get(mContext).register(ENABLE_TASKBAR_URI,
                mEnableTaskBarListener);
        mContext.registerComponentCallbacks(mComponentCallbacks);
        mShutdownReceiver.register(mContext, Intent.ACTION_SHUTDOWN);

        recreateTaskbar();
    }

    private void destroyExistingTaskbar() {
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onDestroy();
            mTaskbarActivityContext = null;
        }
    }

    /**
     * Displays a frame of the first Launcher reveal animation.
     *
     * This should be used to run a first Launcher reveal animation whose progress matches a swipe
     * progress.
     */
    public AnimatorPlaybackController createLauncherStartFromSuwAnim(int duration) {
        return mTaskbarActivityContext == null
                ? null : mTaskbarActivityContext.createLauncherStartFromSuwAnim(duration);
    }

    /**
     * Called when the user is unlocked
     */
    public void onUserUnlocked() {
        mUserUnlocked = true;
        recreateTaskbar();
    }

    /**
     * Sets a {@link StatefulActivity} to act as taskbar callback
     */
    public void setActivity(@NonNull StatefulActivity activity) {
        if (mActivity == activity) {
            return;
        }
        mActivity = activity;
        mUnfoldProgressProvider.setSourceProvider(getUnfoldTransitionProgressProviderForActivity(
                activity));

        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.setUIController(
                    createTaskbarUIControllerForActivity(mActivity));
        }
    }

    /**
     * Returns an {@link UnfoldTransitionProgressProvider} to use while the given StatefulActivity
     * is active.
     */
    private UnfoldTransitionProgressProvider getUnfoldTransitionProgressProviderForActivity(
            StatefulActivity activity) {
        if (activity instanceof BaseQuickstepLauncher) {
            return ((BaseQuickstepLauncher) activity).getUnfoldTransitionProgressProvider();
        }
        return null;
    }

    /**
     * Creates a {@link TaskbarUIController} to use while the given StatefulActivity is active.
     */
    private TaskbarUIController createTaskbarUIControllerForActivity(StatefulActivity activity) {
        if (activity instanceof BaseQuickstepLauncher) {
            if (mTaskbarActivityContext.getPackageManager().hasSystemFeature(FEATURE_PC)) {
                return new DesktopTaskbarUIController((BaseQuickstepLauncher) activity);
            }
            return new LauncherTaskbarUIController((BaseQuickstepLauncher) activity);
        }
        if (activity instanceof RecentsActivity) {
            return new FallbackTaskbarUIController((RecentsActivity) activity);
        }
        return TaskbarUIController.DEFAULT;
    }

    /**
     * Clears a previously set {@link StatefulActivity}
     */
    public void clearActivity(@NonNull StatefulActivity activity) {
        if (mActivity == activity) {
            mActivity = null;
            if (mTaskbarActivityContext != null) {
                mTaskbarActivityContext.setUIController(TaskbarUIController.DEFAULT);
            }
            mUnfoldProgressProvider.setSourceProvider(null);
        }
    }

    private void recreateTaskbar() {
        destroyExistingTaskbar();

        DeviceProfile dp =
                mUserUnlocked ? LauncherAppState.getIDP(mContext).getDeviceProfile(mContext) : null;

        boolean isTaskBarEnabled = dp != null && dp.isTaskbarPresent;

        SystemUiProxy sysui = SystemUiProxy.INSTANCE.get(mContext);
        sysui.setTaskbarEnabled(isTaskBarEnabled);
        if (!isTaskBarEnabled) {
            sysui.notifyTaskbarStatus(/* visible */ false, /* stashed */ false);
            return;
        }

        mTaskbarActivityContext = new TaskbarActivityContext(mContext, dp, mNavButtonController,
                mUnfoldProgressProvider);

        mTaskbarActivityContext.init(mSharedState);
        if (mActivity != null) {
            mTaskbarActivityContext.setUIController(
                    createTaskbarUIControllerForActivity(mActivity));
        }
    }

    public void onSystemUiFlagsChanged(int systemUiStateFlags) {
        mSharedState.sysuiStateFlags = systemUiStateFlags;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.updateSysuiStateFlags(systemUiStateFlags, false /* fromInit */);
        }
    }

    /**
     * Sets the flag indicating setup UI is visible
     */
    public void setSetupUIVisible(boolean isVisible) {
        mSharedState.setupUIVisible = isVisible;
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.setSetupUIVisible(isVisible);
        }
    }

    public void onRotationProposal(int rotation, boolean isValid) {
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onRotationProposal(rotation, isValid);
        }
    }

    public void disableNavBarElements(int displayId, int state1, int state2, boolean animate) {
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.disableNavBarElements(displayId, state1, state2, animate);
        }
    }

    public void onSystemBarAttributesChanged(int displayId, int behavior) {
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onSystemBarAttributesChanged(displayId, behavior);
        }
    }

    public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
        if (mTaskbarActivityContext != null) {
            mTaskbarActivityContext.onNavButtonsDarkIntensityChanged(darkIntensity);
        }
    }

    /**
     * Called when the manager is no longer needed
     */
    public void destroy() {
        destroyExistingTaskbar();
        mDisplayController.removeChangeListener(mDispInfoChangeListener);
        SettingsCache.INSTANCE.get(mContext).unregister(USER_SETUP_COMPLETE_URI,
                mUserSetupCompleteListener);
        SettingsCache.INSTANCE.get(mContext).unregister(NAV_BAR_KIDS_MODE,
                mNavBarKidsModeListener);
        mContext.unregisterComponentCallbacks(mComponentCallbacks);
        mContext.unregisterReceiver(mShutdownReceiver);
    }

    public @Nullable TaskbarActivityContext getCurrentActivityContext() {
        return mTaskbarActivityContext;
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarManager:");
        if (mTaskbarActivityContext == null) {
            pw.println(prefix + "\tTaskbarActivityContext: null");
        } else {
            mTaskbarActivityContext.dumpLogs(prefix + "\t", pw);
        }
    }
}
