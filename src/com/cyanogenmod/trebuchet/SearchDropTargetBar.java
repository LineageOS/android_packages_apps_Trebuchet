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

package com.cyanogenmod.trebuchet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.cyanogenmod.trebuchet.R;
import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

/*
 * Ths bar will manage the transition between the QSB search bar and the delete drop
 * targets so that each of the individual IconDropTargets don't have to.
 */
public class SearchDropTargetBar extends FrameLayout implements DragController.DragListener {

    private static final int sTransitionInDuration = 200;
    private static final int sTransitionOutDuration = 175;

    private AnimatorSet mDropTargetBarFadeInAnim;
    private AnimatorSet mDropTargetBarFadeOutAnim;
    private ObjectAnimator mQSBSearchBarFadeInAnim;
    private ObjectAnimator mQSBSearchBarFadeOutAnim;

    private boolean mShowQSBSearchBar;

    private boolean mIsSearchBarHidden;
    private View mQSBSearchBar;
    private View mDropTargetBar;
    private ButtonDropTarget mInfoDropTarget;
    private ButtonDropTarget mDeleteDropTarget;
    private int mBarHeight;
    private boolean mDeferOnDragEnd = false;

    private Drawable mPreviousBackground;

    public SearchDropTargetBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchDropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mShowQSBSearchBar = PreferencesProvider.Interface.Homescreen.getShowSearchBar(context);
    }

    public void setup(Launcher launcher, DragController dragController) {
        dragController.addDragListener(this);
        dragController.addDragListener(mInfoDropTarget);
        dragController.addDragListener(mDeleteDropTarget);
        dragController.addDropTarget(mInfoDropTarget);
        dragController.addDropTarget(mDeleteDropTarget);
        mInfoDropTarget.setLauncher(launcher);
        mDeleteDropTarget.setLauncher(launcher);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the individual components
        mQSBSearchBar = findViewById(R.id.qsb_search_bar);
        mDropTargetBar = findViewById(R.id.drag_target_bar);
        mInfoDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.info_target_text);
        mDeleteDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.delete_target_text);
        mBarHeight = getResources().getDimensionPixelSize(R.dimen.qsb_bar_height);

        mInfoDropTarget.setSearchDropTargetBar(this);
        mDeleteDropTarget.setSearchDropTargetBar(this);

        boolean enableDropDownDropTargets =
            getResources().getBoolean(R.bool.config_useDropTargetDownTransition);

        if (!mShowQSBSearchBar) {
            mQSBSearchBar.setVisibility(View.GONE);
        }

        // Create the various fade animations
        mDropTargetBar.setAlpha(0f);
        ObjectAnimator fadeInAlphaAnim = ObjectAnimator.ofFloat(mDropTargetBar, "alpha", 1f);
        fadeInAlphaAnim.setInterpolator(new DecelerateInterpolator());
        mDropTargetBarFadeInAnim = new AnimatorSet();
        AnimatorSet.Builder fadeInAnimators = mDropTargetBarFadeInAnim.play(fadeInAlphaAnim);
        if (enableDropDownDropTargets) {
            mDropTargetBar.setTranslationY(-mBarHeight);
            fadeInAnimators.with(ObjectAnimator.ofFloat(mDropTargetBar, "translationY", 0f));
        }
        mDropTargetBarFadeInAnim.setDuration(sTransitionInDuration);
        mDropTargetBarFadeInAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mDropTargetBar.setVisibility(View.VISIBLE);
            }
        });
        ObjectAnimator fadeOutAlphaAnim = ObjectAnimator.ofFloat(mDropTargetBar, "alpha", 0f);
        fadeOutAlphaAnim.setInterpolator(new AccelerateInterpolator());
        mDropTargetBarFadeOutAnim = new AnimatorSet();
        AnimatorSet.Builder fadeOutAnimators = mDropTargetBarFadeOutAnim.play(fadeOutAlphaAnim);
        if (enableDropDownDropTargets) {
            fadeOutAnimators.with(ObjectAnimator.ofFloat(mDropTargetBar, "translationY",
                    -mBarHeight));
        }
        mDropTargetBarFadeOutAnim.setDuration(sTransitionOutDuration);
        mDropTargetBarFadeOutAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDropTargetBar.setVisibility(View.INVISIBLE);
                mDropTargetBar.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        mQSBSearchBarFadeInAnim = ObjectAnimator.ofFloat(mQSBSearchBar, "alpha", 1f);
        mQSBSearchBarFadeInAnim.setDuration(sTransitionInDuration);
        mQSBSearchBarFadeInAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mQSBSearchBar.setVisibility(View.VISIBLE);
            }
        });
        mQSBSearchBarFadeOutAnim = ObjectAnimator.ofFloat(mQSBSearchBar, "alpha", 0f);
        mQSBSearchBarFadeOutAnim.setDuration(sTransitionOutDuration);
        mQSBSearchBarFadeOutAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mQSBSearchBar.setVisibility(View.INVISIBLE);
            }
        });
    }

    private void cancelAnimations() {
        mDropTargetBarFadeInAnim.cancel();
        mDropTargetBarFadeOutAnim.cancel();
        mQSBSearchBarFadeInAnim.cancel();
        mQSBSearchBarFadeOutAnim.cancel();
    }

    /*
     * Shows and hides the search bar.
     */
    public void showSearchBar(boolean animated) {
        cancelAnimations();
        if (animated) {
            if (mShowQSBSearchBar) {
                mQSBSearchBarFadeInAnim.start();
            }
        } else {
            if (mShowQSBSearchBar) {
                mQSBSearchBar.setVisibility(View.VISIBLE);
                mQSBSearchBar.setAlpha(1f);
            }
        }
        mIsSearchBarHidden = true;
    }
    public void hideSearchBar(boolean animated) {
        cancelAnimations();
        if (animated) {
            if (mShowQSBSearchBar) {
                mQSBSearchBarFadeOutAnim.start();
            }
        } else {
            if (mShowQSBSearchBar) {
                mQSBSearchBar.setVisibility(View.INVISIBLE);
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
        mDropTargetBar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mDropTargetBar.buildLayer();
        mDropTargetBarFadeOutAnim.cancel();
        mDropTargetBarFadeInAnim.start();
        if (!mIsSearchBarHidden && mShowQSBSearchBar) {
            mQSBSearchBarFadeInAnim.cancel();
            mQSBSearchBarFadeOutAnim.start();
        }
    }

    public void deferOnDragEnd() {
        mDeferOnDragEnd = true;
    }

    @Override
    public void onDragEnd() {
        if (!mDeferOnDragEnd) {
            // Restore the QSB search bar, and animate out the drop target bar
            mDropTargetBarFadeInAnim.cancel();
            mDropTargetBarFadeOutAnim.start();
            if (!mIsSearchBarHidden && mShowQSBSearchBar) {
                mQSBSearchBarFadeOutAnim.cancel();
                mQSBSearchBarFadeInAnim.start();
            }
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public void onSearchPackagesChanged(boolean searchVisible, boolean voiceVisible) {
        if (mQSBSearchBar != null) {
            Drawable bg = mQSBSearchBar.getBackground();
            if (bg != null && (!searchVisible && !voiceVisible)) {
                // Save the background and disable it
                mPreviousBackground = bg;
                mQSBSearchBar.setBackgroundResource(0);
            } else if (mPreviousBackground != null && (searchVisible || voiceVisible)) {
                // Restore the background
                mQSBSearchBar.setBackgroundDrawable(mPreviousBackground);
            }
        }
    }

    public Rect getSearchBarBounds() {
        if (mQSBSearchBar != null) {
            final float appScale = mQSBSearchBar.getContext().getResources()
                    .getCompatibilityInfo().applicationScale;
            final int[] pos = new int[2];
            mQSBSearchBar.getLocationOnScreen(pos);

            final Rect rect = new Rect();
            rect.left = (int) (pos[0] * appScale + 0.5f);
            rect.top = (int) (pos[1] * appScale + 0.5f);
            rect.right = (int) ((pos[0] + mQSBSearchBar.getWidth()) * appScale + 0.5f);
            rect.bottom = (int) ((pos[1] + mQSBSearchBar.getHeight()) * appScale + 0.5f);
            return rect;
        } else {
            return null;
        }
    }
}
