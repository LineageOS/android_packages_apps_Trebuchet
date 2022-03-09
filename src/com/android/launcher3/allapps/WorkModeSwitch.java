/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TURN_OFF_WORK_APPS_TAP;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.KeyboardInsetAnimationCallback;
import com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip;

/**
 * Work profile toggle switch shown at the bottom of AllApps work tab
 */
public class WorkModeSwitch extends Button implements Insettable, View.OnClickListener,
        KeyboardInsetAnimationCallback.KeyboardInsetListener,
        PersonalWorkSlidingTabStrip.OnActivePageChangedListener {

    private static final int FLAG_FADE_ONGOING = 1 << 1;
    private static final int FLAG_TRANSLATION_ONGOING = 1 << 2;
    private static final int FLAG_PROFILE_TOGGLE_ONGOING = 1 << 3;

    private final Rect mInsets = new Rect();
    private int mFlags;
    private boolean mWorkEnabled;
    private boolean mOnWorkTab;


    public WorkModeSwitch(Context context) {
        this(context, null, 0);
    }

    public WorkModeSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkModeSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setSelected(true);
        setOnClickListener(this);
        if (Utilities.ATLEAST_R) {
            KeyboardInsetAnimationCallback keyboardInsetAnimationCallback =
                    new KeyboardInsetAnimationCallback(this);
            setWindowInsetsAnimationCallback(keyboardInsetAnimationCallback);
        }
        DeviceProfile grid = BaseDraggingActivity.fromContext(getContext()).getDeviceProfile();
        setInsets(grid.getInsets());
    }

    @Override
    public void setInsets(Rect insets) {
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        ViewGroup.MarginLayoutParams marginLayoutParams =
                (ViewGroup.MarginLayoutParams) getLayoutParams();
        if (marginLayoutParams != null) {
            marginLayoutParams.bottomMargin = bottomInset + marginLayoutParams.bottomMargin;
        }
    }


    @Override
    public void onActivePageChanged(int page) {
        mOnWorkTab = page == AllAppsContainerView.AdapterHolder.WORK;
        updateVisibility();
    }

    @Override
    public void onClick(View view) {
        if (Utilities.ATLEAST_P && isEnabled()) {
            setFlag(FLAG_PROFILE_TOGGLE_ONGOING);
            Launcher launcher = Launcher.getLauncher(getContext());
            launcher.getStatsLogManager().logger().log(LAUNCHER_TURN_OFF_WORK_APPS_TAP);
            launcher.getAppsView().getWorkManager().setWorkProfileEnabled(false);
        }
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && getVisibility() == VISIBLE && mFlags == 0;
    }

    /**
     * Sets the enabled or disabled state of the button
     */
    public void updateCurrentState(boolean isEnabled) {
        removeFlag(FLAG_PROFILE_TOGGLE_ONGOING);
        if (mWorkEnabled != isEnabled) {
            mWorkEnabled = isEnabled;
            updateVisibility();
        }
    }


    private void updateVisibility() {
        clearAnimation();
        if (mWorkEnabled && mOnWorkTab) {
            setFlag(FLAG_FADE_ONGOING);
            setVisibility(VISIBLE);
            animate().alpha(1).withEndAction(() -> removeFlag(FLAG_FADE_ONGOING)).start();
        } else if (getVisibility() != GONE) {
            setFlag(FLAG_FADE_ONGOING);
            animate().alpha(0).withEndAction(() -> {
                removeFlag(FLAG_FADE_ONGOING);
                this.setVisibility(GONE);
            }).start();
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Utilities.ATLEAST_R && isEnabled()) {
            setTranslationY(0);
            if (insets.isVisible(WindowInsets.Type.ime())) {
                Insets keyboardInsets = insets.getInsets(WindowInsets.Type.ime());
                setTranslationY(mInsets.bottom - keyboardInsets.bottom);
            }
        }
        return insets;
    }

    @Override
    public void onTranslationStart() {
        setFlag(FLAG_TRANSLATION_ONGOING);
    }

    @Override
    public void onTranslationEnd() {
        removeFlag(FLAG_TRANSLATION_ONGOING);
    }

    private void setFlag(int flag) {
        mFlags |= flag;
    }

    private void removeFlag(int flag) {
        mFlags &= ~flag;
    }
}
