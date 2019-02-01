/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.uioverrides;

import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities.Consumer;
import com.android.launcher3.touch.TouchEventTranslator;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.RecentsModel;
import com.android.systemui.shared.recents.ISystemUiProxy;

public class StatusBarTouchController implements TouchController {
    private static final String TAG = "StatusBarController";

    private boolean mCanIntercept;
    private ISystemUiProxy mSysUiProxy;

    private final float mTouchSlop;
    private final Launcher mLauncher;

    protected final TouchEventTranslator mTranslator = new TouchEventTranslator(new StatusBarTouchControllerConsumer(this));

    public StatusBarTouchController(Launcher launcher) {
        mLauncher = launcher;
        mTouchSlop = ViewConfiguration.get(launcher).getScaledTouchSlop() * 2;
    }

    private void dispatchTouchEvent(MotionEvent ev) {
        try {
            if (mSysUiProxy != null) {
                mSysUiProxy.onStatusBarMotionEvent(ev);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception on sysUiProxy.", e);
        }
    }


    public final boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == 0) {
            mCanIntercept = canInterceptTouch(ev);
            if (!mCanIntercept) {
                return false;
            }
            mTranslator.reset();
            mTranslator.setDownParameters(0, ev);
        } else if (ev.getActionMasked() == 5) {
            mTranslator.setDownParameters(ev.getActionIndex(), ev);
        }
        if (mCanIntercept && action == 2) {
            float dy = ev.getY() - mTranslator.getDownY();
            float dx = ev.getX() - mTranslator.getDownX();
            if (dy > mTouchSlop && dy > Math.abs(dx)) {
                mTranslator.dispatchDownEvents(ev);
                mTranslator.processMotionEvent(ev);
                return true;
            } else if (Math.abs(dx) > this.mTouchSlop) {
                mCanIntercept = false;
            }
        }
        return false;
    }

    public final boolean onControllerTouchEvent(MotionEvent ev) {
        mTranslator.processMotionEvent(ev);
        return true;
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        boolean canIntercept = false;
        if (mLauncher.isInState(LauncherState.NORMAL)) {
            if (AbstractFloatingView.getTopOpenViewWithType(mLauncher, AbstractFloatingView.TYPE_STATUS_BAR_SWIPE_DOWN_DISALLOW) == null) {
                if (ev.getY() > mLauncher.getDragLayer().getHeight() - mLauncher.getDeviceProfile().getInsets().bottom) {
                    return false;
                }
                mSysUiProxy = RecentsModel.getInstance(mLauncher).getSystemUiProxy();
                return mSysUiProxy != null;
            }
        }
        return false;
    }

    public final class StatusBarTouchControllerConsumer implements Consumer {
        private final StatusBarTouchController statusBarTouchController;

        public StatusBarTouchControllerConsumer(StatusBarTouchController statusBarTouchController) {
            this.statusBarTouchController = statusBarTouchController;
        }

        public final void accept(Object obj) {
            this.statusBarTouchController.dispatchTouchEvent((MotionEvent) obj);
        }
    }
}