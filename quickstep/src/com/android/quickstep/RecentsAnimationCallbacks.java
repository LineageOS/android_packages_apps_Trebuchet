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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME;

import android.graphics.Rect;
import android.util.ArraySet;
import android.view.RemoteAnimationTarget;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.Preconditions;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

/**
 * Wrapper around {@link com.android.systemui.shared.system.RecentsAnimationListener} which
 * delegates callbacks to multiple listeners on the main thread
 */
public class RecentsAnimationCallbacks implements
        com.android.systemui.shared.system.RecentsAnimationListener {

    private final Set<RecentsAnimationListener> mListeners = new ArraySet<>();
    private final SystemUiProxy mSystemUiProxy;
    private final boolean mAllowMinimizeSplitScreen;

    // TODO(141886704): Remove these references when they are no longer needed
    private RecentsAnimationController mController;

    private boolean mCancelled;

    public RecentsAnimationCallbacks(SystemUiProxy systemUiProxy,
            boolean allowMinimizeSplitScreen) {
        mSystemUiProxy = systemUiProxy;
        mAllowMinimizeSplitScreen = allowMinimizeSplitScreen;
    }

    @UiThread
    public void addListener(RecentsAnimationListener listener) {
        Preconditions.assertUIThread();
        mListeners.add(listener);
    }

    @UiThread
    public void removeListener(RecentsAnimationListener listener) {
        Preconditions.assertUIThread();
        mListeners.remove(listener);
    }

    @UiThread
    public void removeAllListeners() {
        Preconditions.assertUIThread();
        mListeners.clear();
    }

    public void notifyAnimationCanceled() {
        mCancelled = true;
        onAnimationCanceled(new HashMap<>());
    }

    // Called only in Q platform
    @BinderThread
    @Deprecated
    public final void onAnimationStart(RecentsAnimationControllerCompat controller,
            RemoteAnimationTargetCompat[] appTargets, Rect homeContentInsets,
            Rect minimizedHomeBounds) {
        onAnimationStart(controller, appTargets, new RemoteAnimationTargetCompat[0],
                homeContentInsets, minimizedHomeBounds);
    }

    // Called only in R+ platform
    @BinderThread
    public final void onAnimationStart(RecentsAnimationControllerCompat animationController,
            RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets,
            Rect homeContentInsets, Rect minimizedHomeBounds) {
        // Convert appTargets to type RemoteAnimationTarget for all apps except Home app
        RemoteAnimationTarget[] nonHomeApps = Arrays.stream(appTargets)
                .filter(remoteAnimationTarget ->
                        remoteAnimationTarget.activityType != ACTIVITY_TYPE_HOME)
                .map(RemoteAnimationTargetCompat::unwrap)
                .toArray(RemoteAnimationTarget[]::new);

        RemoteAnimationTarget[] nonAppTargets =
                mSystemUiProxy.onGoingToRecentsLegacy(mCancelled, nonHomeApps);

        RecentsAnimationTargets targets = new RecentsAnimationTargets(appTargets,
                wallpaperTargets, RemoteAnimationTargetCompat.wrap(nonAppTargets),
                homeContentInsets, minimizedHomeBounds);
        mController = new RecentsAnimationController(animationController,
                mAllowMinimizeSplitScreen, this::onAnimationFinished);

        if (mCancelled) {
            Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(),
                    mController::finishAnimationToApp);
        } else {
            Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> {
                for (RecentsAnimationListener listener : getListeners()) {
                    listener.onRecentsAnimationStart(mController, targets);
                }
            });
        }
    }

    @BinderThread
    @Override
    public final void onAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
        Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> {
            for (RecentsAnimationListener listener : getListeners()) {
                listener.onRecentsAnimationCanceled(thumbnailDatas);
            }
        });
    }

    @BinderThread
    @Override
    public void onTasksAppeared(RemoteAnimationTargetCompat[] apps) {
        Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> {
            for (RecentsAnimationListener listener : getListeners()) {
                listener.onTasksAppeared(apps);
            }
        });
    }

    private final void onAnimationFinished(RecentsAnimationController controller) {
        Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> {
            for (RecentsAnimationListener listener : getListeners()) {
                listener.onRecentsAnimationFinished(controller);
            }
        });
    }

    private RecentsAnimationListener[] getListeners() {
        return mListeners.toArray(new RecentsAnimationListener[mListeners.size()]);
    }

    /**
     * Listener for the recents animation callbacks.
     */
    public interface RecentsAnimationListener {
        default void onRecentsAnimationStart(RecentsAnimationController controller,
                RecentsAnimationTargets targets) {}

        /**
         * Callback from the system when the recents animation is canceled. {@param thumbnailData}
         * is passed back for rendering screenshot to replace live tile.
         */
        default void onRecentsAnimationCanceled(
                @NonNull HashMap<Integer, ThumbnailData> thumbnailDatas) {}

        /**
         * Callback made whenever the recents animation is finished.
         */
        default void onRecentsAnimationFinished(@NonNull RecentsAnimationController controller) {}

        /**
         * Callback made when a task started from the recents is ready for an app transition.
         */
        default void onTasksAppeared(@NonNull RemoteAnimationTargetCompat[] appearedTaskTarget) {}
    }
}
