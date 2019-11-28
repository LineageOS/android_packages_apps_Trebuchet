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
package com.android.quickstep;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.UiThread;

import com.android.launcher3.util.Preconditions;
import com.android.quickstep.inputconsumers.InputConsumer;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.systemui.shared.system.InputConsumerController;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Wrapper around RecentsAnimationController to help with some synchronization
 */
public class RecentsAnimationWrapper {

    // A list of callbacks to run when we receive the recents animation target. There are different
    // than the state callbacks as these run on the current worker thread.
    private final ArrayList<Runnable> mCallbacks = new ArrayList<>();

    public SwipeAnimationTargetSet targetSet;

    private boolean mWindowThresholdCrossed = false;

    private final InputConsumerController mInputConsumerController;
    private final Supplier<InputConsumer> mInputProxySupplier;

    private InputConsumer mInputConsumer;
    private boolean mTouchInProgress;

    private boolean mFinishPending;

    public RecentsAnimationWrapper(InputConsumerController inputConsumerController,
            Supplier<InputConsumer> inputProxySupplier) {
        mInputConsumerController = inputConsumerController;
        mInputProxySupplier = inputProxySupplier;
    }

    public boolean hasTargets() {
        return targetSet != null && targetSet.hasTargets();
    }

    @UiThread
    public synchronized void setController(SwipeAnimationTargetSet targetSet) {
        Preconditions.assertUIThread();
        this.targetSet = targetSet;

        if (targetSet == null) {
            return;
        }
        targetSet.setWindowThresholdCrossed(mWindowThresholdCrossed);

        if (!mCallbacks.isEmpty()) {
            for (Runnable action : new ArrayList<>(mCallbacks)) {
                action.run();
            }
            mCallbacks.clear();
        }
    }

    public synchronized void runOnInit(Runnable action) {
        if (targetSet == null) {
            mCallbacks.add(action);
        } else {
            action.run();
        }
    }

    /** See {@link #finish(boolean, Runnable, boolean)} */
    @UiThread
    public void finish(boolean toRecents, Runnable onFinishComplete) {
        finish(toRecents, onFinishComplete, false /* sendUserLeaveHint */);
    }

    /**
     * @param onFinishComplete A callback that runs on the main thread after the animation
     *                         controller has finished on the background thread.
     * @param sendUserLeaveHint Determines whether userLeaveHint flag will be set on the pausing
     *                          activity. If userLeaveHint is true, the activity will enter into
     *                          picture-in-picture mode upon being paused.
     */
    @UiThread
    public void finish(boolean toRecents, Runnable onFinishComplete, boolean sendUserLeaveHint) {
        Preconditions.assertUIThread();
        if (!toRecents) {
            finishAndClear(false, onFinishComplete, sendUserLeaveHint);
        } else {
            if (mTouchInProgress) {
                mFinishPending = true;
                // Execute the callback
                if (onFinishComplete != null) {
                    onFinishComplete.run();
                }
            } else {
                finishAndClear(true, onFinishComplete, sendUserLeaveHint);
            }
        }
    }

    private void finishAndClear(boolean toRecents, Runnable onFinishComplete,
            boolean sendUserLeaveHint) {
        SwipeAnimationTargetSet controller = targetSet;
        targetSet = null;
        if (controller != null) {
            controller.finishController(toRecents, onFinishComplete, sendUserLeaveHint);
        }
    }

    public void enableInputConsumer() {
        if (targetSet != null) {
            targetSet.enableInputConsumer();
        }
    }

    /**
     * Indicates that the gesture has crossed the window boundary threshold and system UI can be
     * update the represent the window behind
     */
    public void setWindowThresholdCrossed(boolean windowThresholdCrossed) {
        if (mWindowThresholdCrossed != windowThresholdCrossed) {
            mWindowThresholdCrossed = windowThresholdCrossed;
            if (targetSet != null) {
                targetSet.setWindowThresholdCrossed(windowThresholdCrossed);
            }
        }
    }

    public void enableInputProxy() {
        mInputConsumerController.setInputListener(this::onInputConsumerEvent);
    }

    private boolean onInputConsumerEvent(InputEvent ev) {
        if (ev instanceof MotionEvent) {
            onInputConsumerMotionEvent((MotionEvent) ev);
        } else if (ev instanceof KeyEvent) {
            if (mInputConsumer == null) {
                mInputConsumer = mInputProxySupplier.get();
            }
            mInputConsumer.onKeyEvent((KeyEvent) ev);
            return true;
        }
        return false;
    }

    private boolean onInputConsumerMotionEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == ACTION_DOWN) {
            mTouchInProgress = true;
            if (mInputConsumer == null) {
                mInputConsumer = mInputProxySupplier.get();
            }
        } else if (action == ACTION_CANCEL || action == ACTION_UP) {
            // Finish any pending actions
            mTouchInProgress = false;
            if (mFinishPending) {
                mFinishPending = false;
                finishAndClear(true /* toRecents */, null, false /* sendUserLeaveHint */);
            }
        }
        if (mInputConsumer != null) {
            mInputConsumer.onMotionEvent(ev);
        }

        return true;
    }

    public void setCancelWithDeferredScreenshot(boolean deferredWithScreenshot) {
        if (targetSet != null) {
            targetSet.controller.setCancelWithDeferredScreenshot(deferredWithScreenshot);
        }
    }

    public SwipeAnimationTargetSet getController() {
        return targetSet;
    }
}
