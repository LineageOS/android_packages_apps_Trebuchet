/*
 * Copyright 2021 The Android Open Source Project
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


import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS;
import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_KEY;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;

import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.IntDef;

import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.TestProtocol;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Controller for 3 button mode in the taskbar.
 * Handles all the functionality of the various buttons, making/routing the right calls into
 * launcher or sysui/system.
 */
public class TaskbarNavButtonController {

    /** Allow some time in between the long press for back and recents. */
    static final int SCREEN_PIN_LONG_PRESS_THRESHOLD = 200;
    static final int SCREEN_PIN_LONG_PRESS_RESET = SCREEN_PIN_LONG_PRESS_THRESHOLD + 100;

    private long mLastScreenPinLongPress;
    private boolean mScreenPinned;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            BUTTON_BACK,
            BUTTON_HOME,
            BUTTON_RECENTS,
            BUTTON_IME_SWITCH,
            BUTTON_A11Y,
    })

    public @interface TaskbarButton {}

    static final int BUTTON_BACK = 1;
    static final int BUTTON_HOME = BUTTON_BACK << 1;
    static final int BUTTON_RECENTS = BUTTON_HOME << 1;
    static final int BUTTON_IME_SWITCH = BUTTON_RECENTS << 1;
    static final int BUTTON_A11Y = BUTTON_IME_SWITCH << 1;

    private static final int SCREEN_UNPIN_COMBO = BUTTON_BACK | BUTTON_RECENTS;
    private int mLongPressedButtons = 0;

    private final TouchInteractionService mService;
    private final SystemUiProxy mSystemUiProxy;
    private final Handler mHandler;

    private final Runnable mResetLongPress = this::resetScreenUnpin;

    public TaskbarNavButtonController(TouchInteractionService service,
            SystemUiProxy systemUiProxy, Handler handler) {
        mService = service;
        mSystemUiProxy = systemUiProxy;
        mHandler = handler;
    }

    public void onButtonClick(@TaskbarButton int buttonType) {
        switch (buttonType) {
            case BUTTON_BACK:
                executeBack();
                break;
            case BUTTON_HOME:
                navigateHome();
                break;
            case BUTTON_RECENTS:
                navigateToOverview();
                break;
            case BUTTON_IME_SWITCH:
                showIMESwitcher();
                break;
            case BUTTON_A11Y:
                notifyA11yClick(false /* longClick */);
                break;
        }
    }

    public boolean onButtonLongClick(@TaskbarButton int buttonType) {
        switch (buttonType) {
            case BUTTON_HOME:
                startAssistant();
                return true;
            case BUTTON_A11Y:
                notifyA11yClick(true /* longClick */);
                return true;
            case BUTTON_BACK:
            case BUTTON_RECENTS:
                mLongPressedButtons |= buttonType;
                return determineScreenUnpin();
            case BUTTON_IME_SWITCH:
            default:
                return false;
        }
    }

    /**
     * Checks if the user has long pressed back and recents buttons
     * "together" (within {@link #SCREEN_PIN_LONG_PRESS_THRESHOLD})ms
     * If so, then requests the system to turn off screen pinning.
     *
     * @return true if the long press is a valid user action in attempting to unpin an app
     *         Will always return {@code false} when screen pinning is not active.
     *         NOTE: Returning true does not mean that screen pinning has stopped
     */
    private boolean determineScreenUnpin() {
        long timeNow = System.currentTimeMillis();
        if (!mScreenPinned) {
            return false;
        }

        if (mLastScreenPinLongPress == 0) {
            // First button long press registered, just mark time and wait for second button press
            mLastScreenPinLongPress = System.currentTimeMillis();
            mHandler.postDelayed(mResetLongPress, SCREEN_PIN_LONG_PRESS_RESET);
            return true;
        }

        if ((timeNow - mLastScreenPinLongPress) > SCREEN_PIN_LONG_PRESS_THRESHOLD) {
            // Too long in-between presses, reset the clock
            resetScreenUnpin();
            return false;
        }

        if ((mLongPressedButtons & SCREEN_UNPIN_COMBO) == SCREEN_UNPIN_COMBO) {
            // Hooray! They did it (finally...)
            mSystemUiProxy.stopScreenPinning();
            mHandler.removeCallbacks(mResetLongPress);
            resetScreenUnpin();
        }
        return true;
    }

    private void resetScreenUnpin() {
        mLongPressedButtons = 0;
        mLastScreenPinLongPress = 0;
    }

    public void updateSysuiFlags(int sysuiFlags) {
        mScreenPinned = (sysuiFlags & SYSUI_STATE_SCREEN_PINNING) != 0;
    }

    private void navigateHome() {
        mService.getOverviewCommandHelper().addCommand(OverviewCommandHelper.TYPE_HOME);
    }

    private void navigateToOverview() {
        if (mScreenPinned) {
            return;
        }
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onOverviewToggle");
        mService.getOverviewCommandHelper().addCommand(OverviewCommandHelper.TYPE_TOGGLE);
    }

    private void executeBack() {
        mSystemUiProxy.onBackPressed();
    }

    private void showIMESwitcher() {
        mSystemUiProxy.onImeSwitcherPressed();
    }

    private void notifyA11yClick(boolean longClick) {
        if (longClick) {
            mSystemUiProxy.notifyAccessibilityButtonLongClicked();
        } else {
            mSystemUiProxy.notifyAccessibilityButtonClicked(mService.getDisplayId());
        }
    }

    private void startAssistant() {
        if (mScreenPinned) {
            return;
        }
        Bundle args = new Bundle();
        args.putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS);
        mSystemUiProxy.startAssistant(args);
    }
}
