/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

/*
 * Ths bar will manage the transition between the QSB search bar and the delete drop
 * targets so that each of the individual IconDropTargets don't have to.
 */
public class SearchDropTargetBar extends FrameLayout implements DragController.DragListener {

    private static final int sTransitionInDuration = 200;
    private static final int sTransitionOutDuration = 175;

    private ObjectAnimator mDropTargetBarAnim;
    private ValueAnimator mQSBSearchBarAnim;
    private static final AccelerateInterpolator sAccelerateInterpolator =
            new AccelerateInterpolator();

    private boolean mIsSearchBarHidden;
    private View mQSBSearchBar;
    private View mDropTargetBar;
    private ButtonDropTarget mInfoDropTarget;
    private ButtonDropTarget mDeleteDropTarget;
    private int mBarHeight;
    private boolean mDeferOnDragEnd = false;

    private boolean mEnableDropDownDropTargets;

    private Launcher mLauncher;

    public SearchDropTargetBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchDropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setup(Launcher launcher, DragController dragController) {
        dragController.addDragListener(this);
        dragController.addDragListener(mInfoDropTarget);
        dragController.addDragListener(mDeleteDropTarget);
        dragController.addDropTarget(mInfoDropTarget);
        dragController.addDropTarget(mDeleteDropTarget);
        dragController.setFlingToDeleteDropTarget(mDeleteDropTarget);
        mInfoDropTarget.setLauncher(launcher);
        mDeleteDropTarget.setLauncher(launcher);

        setupQSB(launcher);
    }

    public void setupQSB(Launcher launcher) {
        mLauncher = launcher;
        mQSBSearchBar = launcher.getQsbBar();
    }

    public void setQsbSearchBar(View qsb) {
        float alpha = 1f;
        int visibility = View.VISIBLE;
        if (mQSBSearchBar != null) {
            alpha = mQSBSearchBar.getAlpha();
            visibility = mQSBSearchBar.getVisibility();
        }
        mQSBSearchBar = qsb;
        if (mQSBSearchBar != null) {
            mQSBSearchBar.setAlpha(alpha);
            mQSBSearchBar.setVisibility(visibility);
            if (mEnableDropDownDropTargets) {
                mQSBSearchBarAnim = LauncherAnimUtils.ofFloat(mQSBSearchBar, "translationY", 0,
                        -mBarHeight);
            } else {
                mQSBSearchBarAnim = LauncherAnimUtils.ofFloat(mQSBSearchBar, "alpha", 1f, 0f);
            }
            setupAnimation(mQSBSearchBarAnim, mQSBSearchBar);
        } else {
            // Create a no-op animation of the search bar is null
            mQSBSearchBarAnim = ValueAnimator.ofFloat(0, 0);
            mQSBSearchBarAnim.setDuration(sTransitionInDuration);
        }
    }

    private void prepareStartAnimation(View v) {
        // Enable the hw layers before the animation starts (will be disabled in the onAnimationEnd
        // callback below)
        if (v != null) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    private void setupAnimation(ValueAnimator anim, final View v) {
        anim.setInterpolator(sAccelerateInterpolator);
        anim.setDuration(sTransitionInDuration);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (v != null) {
                    v.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the individual components
        mDropTargetBar = findViewById(R.id.drag_target_bar);
        mInfoDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.info_target_text);
        mDeleteDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.delete_target_text);

        mInfoDropTarget.setSearchDropTargetBar(this);
        mDeleteDropTarget.setSearchDropTargetBar(this);

        mEnableDropDownDropTargets =
            getResources().getBoolean(R.bool.config_useDropTargetDownTransition);

        // Create the various fade animations
        if (mEnableDropDownDropTargets) {
            LauncherAppState app = LauncherAppState.getInstance();
            DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
            mBarHeight = grid.searchBarSpaceHeightPx;
            mDropTargetBar.setTranslationY(-mBarHeight);
            mDropTargetBarAnim = LauncherAnimUtils.ofFloat(mDropTargetBar, "translationY",
                    -mBarHeight, 0f);

        } else {
            mDropTargetBar.setAlpha(0f);
            mDropTargetBarAnim = LauncherAnimUtils.ofFloat(mDropTargetBar, "alpha", 0f, 1f);
        }
        setupAnimation(mDropTargetBarAnim, mDropTargetBar);
    }

    public void finishAnimations() {
        prepareStartAnimation(mDropTargetBar);
        mDropTargetBarAnim.reverse();
        prepareStartAnimation(mQSBSearchBar);
        mQSBSearchBarAnim.reverse();
    }

    /*
     * Shows and hides the search bar.
     */
    public void showSearchBar(boolean animated) {
        boolean needToCancelOngoingAnimation = mQSBSearchBarAnim.isRunning() && !animated;
        if (!mIsSearchBarHidden && !needToCancelOngoingAnimation) return;
        if (animated) {
            prepareStartAnimation(mQSBSearchBar);
            mQSBSearchBarAnim.reverse();
        } else {
            mQSBSearchBarAnim.cancel();
            if (mQSBSearchBar != null && mEnableDropDownDropTargets) {
                mQSBSearchBar.setTranslationY(0);
            } else if (mQSBSearchBar != null) {
                mQSBSearchBar.setAlpha(1f);
            }
        }
        mIsSearchBarHidden = false;
    }
    public void hideSearchBar(boolean animated) {
        boolean needToCancelOngoingAnimation = mQSBSearchBarAnim.isRunning() && !animated;
        if (mIsSearchBarHidden && !needToCancelOngoingAnimation) return;
        if (animated) {
            prepareStartAnimation(mQSBSearchBar);
            mQSBSearchBarAnim.start();
        } else {
            mQSBSearchBarAnim.cancel();
            if (mQSBSearchBar != null && mEnableDropDownDropTargets) {
                mQSBSearchBar.setTranslationY(-mBarHeight);
            } else if (mQSBSearchBar != null) {
                mQSBSearchBar.setAlpha(0f);
            }
        }
        mIsSearchBarHidden = true;
    }

    /*
     * Gets various transition durations.
     */
    public int getTransitionInDuration() {
        return sTransitionInDuration;
    }
    public int getTransitionOutDuration() {
        return sTransitionOutDuration;
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        // Animate out the QSB search bar, and animate in the drop target bar
        prepareStartAnimation(mDropTargetBar);
        mDropTargetBarAnim.start();
        if (!isAnyFolderOpen() && (!mIsSearchBarHidden || mQSBSearchBar.getAlpha() > 0f)) {
            prepareStartAnimation(mQSBSearchBar);
            mQSBSearchBarAnim.start();
        }
    }

    public void deferOnDragEnd() {
        mDeferOnDragEnd = true;
    }

    private boolean isAnyFolderOpen() {
        if (mLauncher != null) {
            return mLauncher.getWorkspace().getOpenFolder() != null;
        }
        return false;
    }

    @Override
    public void onDragEnd() {
        if (!mDeferOnDragEnd) {
            // Restore the QSB search bar, and animate out the drop target bar
            prepareStartAnimation(mDropTargetBar);
            mDropTargetBarAnim.reverse();
            if (!isAnyFolderOpen() && (!mIsSearchBarHidden || mQSBSearchBar.getAlpha() < 1f)) {
                if (mLauncher != null && mLauncher.shouldShowSearchBar()
                        && mQSBSearchBar.getVisibility() != View.VISIBLE) {
                    mQSBSearchBar.setVisibility(View.VISIBLE);
                }
                prepareStartAnimation(mQSBSearchBar);
                mQSBSearchBarAnim.reverse();
            }
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public Rect getSearchBarBounds() {
        if (mQSBSearchBar != null) {
            final int[] pos = new int[2];
            mQSBSearchBar.getLocationOnScreen(pos);

            final Rect rect = new Rect();
            rect.left = pos[0];
            rect.top = pos[1];
            rect.right = pos[0] + mQSBSearchBar.getWidth();
            rect.bottom = pos[1] + mQSBSearchBar.getHeight();
            return rect;
        } else {
            return null;
        }
    }

    public View getDropTargetBar() {
        return mDropTargetBar;
    }
}
