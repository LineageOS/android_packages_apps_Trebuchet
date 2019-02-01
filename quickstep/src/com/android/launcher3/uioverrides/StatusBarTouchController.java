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

import android.content.SharedPreferences;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.touch.TouchEventTranslator;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.RecentsModel;
import com.android.systemui.shared.recents.ISystemUiProxy;

public class StatusBarTouchController implements TouchController {
    private static final String TAG = StatusBarTouchController.class.getSimpleName();
    private static final String PREF_STATUSBAR_EXPAND = "pref_expand_statusbar";

    private boolean mCanIntercept;
    private ISystemUiProxy mSysUiProxy;

    private final Launcher mLauncher;
    private final SharedPreferences mSharedPreferences;
    private final float mTouchSlop;

    protected final TouchEventTranslator mTranslator =
            new TouchEventTranslator(this::dispatchTouchEvent);

    public StatusBarTouchController(Launcher launcher) {
        mLauncher = launcher;
        mSharedPreferences = Utilities.getPrefs(launcher);
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
        if (action == MotionEvent.ACTION_DOWN) {
            mCanIntercept = canInterceptTouch(ev);
            if (!mCanIntercept) {
                return false;
            }
            mTranslator.reset();
            mTranslator.setDownParameters(0, ev);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            mTranslator.setDownParameters(ev.getActionIndex(), ev);
        }

        if (mCanIntercept && action == MotionEvent.ACTION_MOVE) {
            float dy = ev.getY() - mTranslator.getDownY();
            float dx = ev.getX() - mTranslator.getDownX();
            if (dy > mTouchSlop && dy > Math.abs(dx)) {
                mTranslator.dispatchDownEvents(ev);
                mTranslator.processMotionEvent(ev);
                return true;
            } else if (Math.abs(dx) > mTouchSlop) {
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
        if (!mSharedPreferences.getBoolean(PREF_STATUSBAR_EXPAND, true)) {
            return false;
        }

        if (mLauncher.isInState(LauncherState.NORMAL)) {
            if (AbstractFloatingView.getTopOpenViewWithType(
                    mLauncher, AbstractFloatingView.TYPE_STATUS_BAR_SWIPE_DOWN_DISALLOW) == null) {
                if (ev.getY() > mLauncher.getDragLayer().getHeight() -
                        mLauncher.getDeviceProfile().getInsets().bottom) {
                    return false;
                }
                mSysUiProxy = RecentsModel.getInstance(mLauncher).getSystemUiProxy();
                return mSysUiProxy != null;
            }
        }
        return false;
    }
}
