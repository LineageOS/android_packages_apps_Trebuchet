/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar.overlay;

import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;

import android.content.Context;
import android.graphics.Insets;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;

/** Root drag layer for the Taskbar overlay window. */
public class TaskbarOverlayDragLayer extends
        BaseDragLayer<TaskbarOverlayContext> implements
        ViewTreeObserver.OnComputeInternalInsetsListener {

    TaskbarOverlayDragLayer(Context context) {
        super(context, null, 1);
        setClipChildren(false);
        recreateControllers();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnComputeInternalInsetsListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnComputeInternalInsetsListener(this);
    }

    @Override
    public void recreateControllers() {
        mControllers = new TouchController[]{mActivity.getDragController()};
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        TestLogging.recordMotionEvent(TestProtocol.SEQUENCE_MAIN, "Touch event", ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == ACTION_UP && event.getKeyCode() == KEYCODE_BACK) {
            AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mActivity);
            if (topView != null && topView.onBackPressed()) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onComputeInternalInsets(ViewTreeObserver.InternalInsetsInfo inoutInfo) {
        if (mActivity.getDragController().isSystemDragInProgress()) {
            inoutInfo.touchableRegion.setEmpty();
            inoutInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        return updateInsetsDueToStashing(insets);
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        mActivity.getOverlayController().maybeCloseWindow();
    }

    /**
     * Taskbar automatically stashes when opening all apps, but we don't report the insets as
     * changing to avoid moving the underlying app. But internally, the apps view should still
     * layout according to the stashed insets rather than the unstashed insets. So this method
     * does two things:
     * 1) Sets navigationBars bottom inset to stashedHeight.
     * 2) Sets tappableInsets bottom inset to 0.
     */
    private WindowInsets updateInsetsDueToStashing(WindowInsets oldInsets) {
        if (!mActivity.willTaskbarBeVisuallyStashed()) {
            return oldInsets;
        }
        WindowInsets.Builder updatedInsetsBuilder = new WindowInsets.Builder(oldInsets);

        Insets oldNavInsets = oldInsets.getInsets(WindowInsets.Type.navigationBars());
        Insets newNavInsets = Insets.of(oldNavInsets.left, oldNavInsets.top, oldNavInsets.right,
                mActivity.getStashedTaskbarHeight());
        updatedInsetsBuilder.setInsets(WindowInsets.Type.navigationBars(), newNavInsets);

        Insets oldTappableInsets = oldInsets.getInsets(WindowInsets.Type.tappableElement());
        Insets newTappableInsets = Insets.of(oldTappableInsets.left, oldTappableInsets.top,
                oldTappableInsets.right, 0);
        updatedInsetsBuilder.setInsets(WindowInsets.Type.tappableElement(), newTappableInsets);

        return updatedInsetsBuilder.build();
    }
}
