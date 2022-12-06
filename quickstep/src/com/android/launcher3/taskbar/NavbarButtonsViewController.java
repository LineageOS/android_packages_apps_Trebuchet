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

import static android.view.View.AccessibilityDelegate;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.SYSUI_SURFACE_PROGRESS_INDEX;
import static com.android.launcher3.taskbar.TaskbarManager.isPhoneButtonNavMode;
import static com.android.launcher3.taskbar.TaskbarManager.isPhoneMode;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_A11Y;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_BACK;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_HOME;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_IME_SWITCH;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_RECENTS;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_KEYGUARD;
import static com.android.launcher3.taskbar.TaskbarViewController.ALPHA_INDEX_SMALL_SCREEN;
import static com.android.launcher3.taskbar.Utilities.appendFlag;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BACK_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IME_SWITCHER_SHOWING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_OVERVIEW_DISABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.DrawableRes;
import android.annotation.IdRes;
import android.annotation.LayoutRes;
import android.content.pm.ActivityInfo.Config;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.PaintDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnClickListener;
import android.view.View.OnHoverListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarButton;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.AnimatedFloat;
import com.android.systemui.shared.rotation.FloatingRotationButton;
import com.android.systemui.shared.rotation.RotationButton;
import com.android.systemui.shared.rotation.RotationButtonController;
import com.android.systemui.shared.system.QuickStepContract;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.function.IntPredicate;

/**
 * Controller for managing nav bar buttons in taskbar
 */
public class NavbarButtonsViewController implements TaskbarControllers.LoggableTaskbarController {

    private final Rect mTempRect = new Rect();

    private static final int FLAG_SWITCHER_SHOWING = 1 << 0;
    private static final int FLAG_IME_VISIBLE = 1 << 1;
    private static final int FLAG_ROTATION_BUTTON_VISIBLE = 1 << 2;
    private static final int FLAG_A11Y_VISIBLE = 1 << 3;
    private static final int FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE = 1 << 4;
    private static final int FLAG_KEYGUARD_VISIBLE = 1 << 5;
    private static final int FLAG_KEYGUARD_OCCLUDED = 1 << 6;
    private static final int FLAG_DISABLE_HOME = 1 << 7;
    private static final int FLAG_DISABLE_RECENTS = 1 << 8;
    private static final int FLAG_DISABLE_BACK = 1 << 9;
    private static final int FLAG_NOTIFICATION_SHADE_EXPANDED = 1 << 10;
    private static final int FLAG_SCREEN_PINNING_ACTIVE = 1 << 11;
    private static final int FLAG_VOICE_INTERACTION_WINDOW_SHOWING = 1 << 12;
    private static final int FLAG_SMALL_SCREEN = 1 << 13;
    private static final int FLAG_SLIDE_IN_VIEW_VISIBLE = 1 << 14;

    /** Flags where a UI could be over a slide in view, so the color override should be disabled. */
    private static final int FLAGS_SLIDE_IN_VIEW_ICON_COLOR_OVERRIDE_DISABLED =
            FLAG_NOTIFICATION_SHADE_EXPANDED | FLAG_VOICE_INTERACTION_WINDOW_SHOWING;

    private static final String NAV_BUTTONS_SEPARATE_WINDOW_TITLE = "Taskbar Nav Buttons";

    public static final int ALPHA_INDEX_IMMERSIVE_MODE = 0;
    public static final int ALPHA_INDEX_KEYGUARD_OR_DISABLE = 1;
    private static final int NUM_ALPHA_CHANNELS = 2;

    private final ArrayList<StatePropertyHolder> mPropertyHolders = new ArrayList<>();
    private final ArrayList<ImageView> mAllButtons = new ArrayList<>();
    private int mState;

    private final TaskbarActivityContext mContext;
    private final FrameLayout mNavButtonsView;
    private final LinearLayout mNavButtonContainer;
    // Used for IME+A11Y buttons
    private final ViewGroup mEndContextualContainer;
    private final ViewGroup mStartContextualContainer;
    private final int mLightIconColor;
    private final int mDarkIconColor;
    /** Color to use for navigation bar buttons, if a slide in view is visible. */
    private final int mSlideInViewIconColor;

    private final AnimatedFloat mTaskbarNavButtonTranslationY = new AnimatedFloat(
            this::updateNavButtonTranslationY);
    private final AnimatedFloat mTaskbarNavButtonTranslationYForInAppDisplay = new AnimatedFloat(
            this::updateNavButtonTranslationY);
    private final AnimatedFloat mTaskbarNavButtonTranslationYForIme = new AnimatedFloat(
            this::updateNavButtonTranslationY);
    // Used for System UI state updates that should translate the nav button for in-app display.
    private final AnimatedFloat mNavButtonInAppDisplayProgressForSysui = new AnimatedFloat(
            this::updateNavButtonInAppDisplayProgressForSysui);
    private final AnimatedFloat mTaskbarNavButtonDarkIntensity = new AnimatedFloat(
            this::updateNavButtonDarkIntensity);
    private final AnimatedFloat mNavButtonDarkIntensityMultiplier = new AnimatedFloat(
            this::updateNavButtonDarkIntensity);
    /** Overrides the navigation button color to {@code mSlideInViewIconColor} when {@code 1}. */
    private final AnimatedFloat mSlideInViewNavButtonColorOverride = new AnimatedFloat(
            this::updateNavButtonDarkIntensity);
    private final RotationButtonListener mRotationButtonListener = new RotationButtonListener();

    private final Rect mFloatingRotationButtonBounds = new Rect();

    // Initialized in init.
    private TaskbarControllers mControllers;
    private boolean mIsImeRenderingNavButtons;
    private View mA11yButton;
    private int mSysuiStateFlags;
    private View mBackButton;
    private View mHomeButton;
    private MultiValueAlpha mBackButtonAlpha;
    private MultiValueAlpha mHomeButtonAlpha;
    private FloatingRotationButton mFloatingRotationButton;

    // Variables for moving nav buttons to a separate window above IME
    private boolean mAreNavButtonsInSeparateWindow = false;
    private BaseDragLayer<TaskbarActivityContext> mSeparateWindowParent; // Initialized in init.
    private final ViewTreeObserver.OnComputeInternalInsetsListener mSeparateWindowInsetsComputer =
            this::onComputeInsetsForSeparateWindow;
    private final RecentsHitboxExtender mHitboxExtender = new RecentsHitboxExtender();

    public NavbarButtonsViewController(TaskbarActivityContext context, FrameLayout navButtonsView) {
        mContext = context;
        mNavButtonsView = navButtonsView;
        mNavButtonContainer = mNavButtonsView.findViewById(R.id.end_nav_buttons);
        mEndContextualContainer = mNavButtonsView.findViewById(R.id.end_contextual_buttons);
        mStartContextualContainer = mNavButtonsView.findViewById(R.id.start_contextual_buttons);

        mLightIconColor = context.getColor(R.color.taskbar_nav_icon_light_color);
        mDarkIconColor = context.getColor(R.color.taskbar_nav_icon_dark_color);
        // Can precompute color since dark theme change recreates taskbar.
        mSlideInViewIconColor = Utilities.isDarkTheme(context) ? mLightIconColor : mDarkIconColor;
    }

    /**
     * Initializes the controller
     */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
        boolean isThreeButtonNav = mContext.isThreeButtonNav();
        DeviceProfile deviceProfile = mContext.getDeviceProfile();
        Resources resources = mContext.getResources();
        mNavButtonsView.getLayoutParams().height = !isPhoneMode(deviceProfile) ?
                deviceProfile.taskbarSize :
                resources.getDimensionPixelSize(R.dimen.taskbar_size);

        mIsImeRenderingNavButtons =
                InputMethodService.canImeRenderGesturalNavButtons() && mContext.imeDrawsImeNavBar();
        if (!mIsImeRenderingNavButtons) {
            // IME switcher
            View imeSwitcherButton = addButton(R.drawable.ic_ime_switcher, BUTTON_IME_SWITCH,
                    isThreeButtonNav ? mStartContextualContainer : mEndContextualContainer,
                    mControllers.navButtonController, R.id.ime_switcher);
            mPropertyHolders.add(new StatePropertyHolder(imeSwitcherButton,
                    flags -> ((flags & FLAG_SWITCHER_SHOWING) != 0)
                            && ((flags & FLAG_ROTATION_BUTTON_VISIBLE) == 0)));
        }

        mPropertyHolders.add(new StatePropertyHolder(
                mControllers.taskbarViewController.getTaskbarIconAlpha()
                        .getProperty(ALPHA_INDEX_KEYGUARD),
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0
                        && (flags & FLAG_SCREEN_PINNING_ACTIVE) == 0));

        mPropertyHolders.add(new StatePropertyHolder(
                mControllers.taskbarViewController.getTaskbarIconAlpha()
                        .getProperty(ALPHA_INDEX_SMALL_SCREEN),
                flags -> (flags & FLAG_SMALL_SCREEN) == 0));

        mPropertyHolders.add(new StatePropertyHolder(mControllers.taskbarDragLayerController
                .getKeyguardBgTaskbar(), flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0));

        // Force nav buttons (specifically back button) to be visible during setup wizard.
        boolean isInSetup = !mContext.isUserSetupComplete();
        boolean isInKidsMode = mContext.isNavBarKidsModeActive();
        boolean alwaysShowButtons = isThreeButtonNav || isInSetup;

        // Make sure to remove nav bar buttons translation when any of the following occur:
        // - Notification shade is expanded
        // - IME is showing (add separate translation for IME)
        // - VoiceInteractionWindow (assistant) is showing
        int flagsToRemoveTranslation = FLAG_NOTIFICATION_SHADE_EXPANDED | FLAG_IME_VISIBLE
                | FLAG_VOICE_INTERACTION_WINDOW_SHOWING;
        mPropertyHolders.add(new StatePropertyHolder(mNavButtonInAppDisplayProgressForSysui,
                flags -> (flags & flagsToRemoveTranslation) != 0, AnimatedFloat.VALUE,
                1, 0));
        // Center nav buttons in new height for IME.
        float transForIme = (mContext.getDeviceProfile().taskbarSize
                - mControllers.taskbarInsetsController.getTaskbarHeightForIme()) / 2f;
        // For gesture nav, nav buttons only show for IME anyway so keep them translated down.
        float defaultButtonTransY = alwaysShowButtons ? 0 : transForIme;
        mPropertyHolders.add(new StatePropertyHolder(mTaskbarNavButtonTranslationYForIme,
                flags -> (flags & FLAG_IME_VISIBLE) != 0 && !isInKidsMode, AnimatedFloat.VALUE,
                transForIme, defaultButtonTransY));

        mPropertyHolders.add(new StatePropertyHolder(
                mSlideInViewNavButtonColorOverride,
                flags -> ((flags & FLAG_SLIDE_IN_VIEW_VISIBLE) != 0)
                        && ((flags & FLAGS_SLIDE_IN_VIEW_ICON_COLOR_OVERRIDE_DISABLED) == 0)));

        if (alwaysShowButtons) {
            initButtons(mNavButtonContainer, mEndContextualContainer,
                    mControllers.navButtonController);
            updateButtonLayoutSpacing();
            updateStateForFlag(FLAG_SMALL_SCREEN, isPhoneButtonNavMode(mContext));
            if (isInSetup) {
                // Since setup wizard only has back button enabled, it looks strange to be
                // end-aligned, so start-align instead.
                FrameLayout.LayoutParams navButtonsLayoutParams = (FrameLayout.LayoutParams)
                        mNavButtonContainer.getLayoutParams();
                navButtonsLayoutParams.setMarginStart(navButtonsLayoutParams.getMarginEnd());
                navButtonsLayoutParams.setMarginEnd(0);
                navButtonsLayoutParams.gravity = Gravity.START;
                mNavButtonContainer.requestLayout();

                // TODO(b/210906568) Dark intensity is currently not propagated during setup, so set
                //  it based on dark theme for now.
                int mode = resources.getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                boolean isDarkTheme = mode == Configuration.UI_MODE_NIGHT_YES;
                mTaskbarNavButtonDarkIntensity.updateValue(isDarkTheme ? 0 : 1);
            } else if (isInKidsMode) {
                int iconSize = resources.getDimensionPixelSize(
                        R.dimen.taskbar_icon_size_kids);
                int buttonWidth = resources.getDimensionPixelSize(
                        R.dimen.taskbar_nav_buttons_width_kids);
                int buttonHeight = resources.getDimensionPixelSize(
                        R.dimen.taskbar_nav_buttons_height_kids);
                int buttonRadius = resources.getDimensionPixelSize(
                        R.dimen.taskbar_nav_buttons_corner_radius_kids);
                int paddingleft = (buttonWidth - iconSize) / 2;
                int paddingRight = paddingleft;
                int paddingTop = (buttonHeight - iconSize) / 2;
                int paddingBottom = paddingTop;

                // Update icons
                ((ImageView) mBackButton).setImageDrawable(
                        mBackButton.getContext().getDrawable(R.drawable.ic_sysbar_back_kids));
                ((ImageView) mBackButton).setScaleType(ImageView.ScaleType.FIT_CENTER);
                mBackButton.setPadding(paddingleft, paddingTop, paddingRight, paddingBottom);
                ((ImageView) mHomeButton).setImageDrawable(
                        mHomeButton.getContext().getDrawable(R.drawable.ic_sysbar_home_kids));
                ((ImageView) mHomeButton).setScaleType(ImageView.ScaleType.FIT_CENTER);
                mHomeButton.setPadding(paddingleft, paddingTop, paddingRight, paddingBottom);

                // Home button layout
                LinearLayout.LayoutParams homeLayoutparams = new LinearLayout.LayoutParams(
                        buttonWidth,
                        buttonHeight
                );
                int homeButtonLeftMargin = resources.getDimensionPixelSize(
                        R.dimen.taskbar_home_button_left_margin_kids);
                homeLayoutparams.setMargins(homeButtonLeftMargin, 0, 0, 0);
                mHomeButton.setLayoutParams(homeLayoutparams);

                // Back button layout
                LinearLayout.LayoutParams backLayoutParams = new LinearLayout.LayoutParams(
                        buttonWidth,
                        buttonHeight
                );
                int backButtonLeftMargin = resources.getDimensionPixelSize(
                        R.dimen.taskbar_back_button_left_margin_kids);
                backLayoutParams.setMargins(backButtonLeftMargin, 0, 0, 0);
                mBackButton.setLayoutParams(backLayoutParams);

                // Button backgrounds
                int whiteWith10PctAlpha = Color.argb(0.1f, 1, 1, 1);
                PaintDrawable buttonBackground = new PaintDrawable(whiteWith10PctAlpha);
                buttonBackground.setCornerRadius(buttonRadius);
                mHomeButton.setBackground(buttonBackground);
                mBackButton.setBackground(buttonBackground);

                // Update alignment within taskbar
                FrameLayout.LayoutParams navButtonsLayoutParams = (FrameLayout.LayoutParams)
                        mNavButtonContainer.getLayoutParams();
                navButtonsLayoutParams.setMarginStart(navButtonsLayoutParams.getMarginEnd() / 2);
                navButtonsLayoutParams.setMarginEnd(navButtonsLayoutParams.getMarginStart());
                navButtonsLayoutParams.gravity = Gravity.CENTER;
                mNavButtonContainer.requestLayout();

                mHomeButton.setOnLongClickListener(null);
            }

            // Animate taskbar background when either..
            // notification shade expanded AND not on keyguard
            // back is visible for bouncer
            mPropertyHolders.add(new StatePropertyHolder(
                    mControllers.taskbarDragLayerController.getNavbarBackgroundAlpha(),
                    flags -> ((flags & FLAG_NOTIFICATION_SHADE_EXPANDED) != 0
                                && (flags & FLAG_KEYGUARD_VISIBLE) == 0)
                            || (flags & FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE) != 0));

            // Rotation button
            RotationButton rotationButton = new RotationButtonImpl(
                    addButton(mEndContextualContainer, R.id.rotate_suggestion,
                            R.layout.taskbar_contextual_button));
            rotationButton.hide();
            mControllers.rotationButtonController.setRotationButton(rotationButton, null);
        } else {
            mFloatingRotationButton = new FloatingRotationButton(mContext,
                    R.string.accessibility_rotate_button,
                    R.layout.rotate_suggestion,
                    R.id.rotate_suggestion,
                    R.dimen.floating_rotation_button_min_margin,
                    R.dimen.rounded_corner_content_padding,
                    R.dimen.floating_rotation_button_taskbar_left_margin,
                    R.dimen.floating_rotation_button_taskbar_bottom_margin,
                    R.dimen.floating_rotation_button_diameter,
                    R.dimen.key_button_ripple_max_width);
            mControllers.rotationButtonController.setRotationButton(mFloatingRotationButton,
                    mRotationButtonListener);

            if (!mIsImeRenderingNavButtons) {
                View imeDownButton = addButton(R.drawable.ic_sysbar_back, BUTTON_BACK,
                        mStartContextualContainer, mControllers.navButtonController, R.id.back);
                imeDownButton.setRotation(Utilities.isRtl(resources) ? 90 : -90);
                // Only show when IME is visible.
                mPropertyHolders.add(new StatePropertyHolder(imeDownButton,
                        flags -> (flags & FLAG_IME_VISIBLE) != 0));
            }
        }

        applyState();
        mPropertyHolders.forEach(StatePropertyHolder::endAnimation);

        // Initialize things needed to move nav buttons to separate window.
        mSeparateWindowParent = new BaseDragLayer<TaskbarActivityContext>(mContext, null, 0) {
            @Override
            public void recreateControllers() {
                mControllers = new TouchController[0];
            }

            @Override
            protected boolean canFindActiveController() {
                // We don't have any controllers, but we don't want any floating views such as
                // folder to intercept, either. This ensures nav buttons can always be pressed.
                return false;
            }
        };
        mSeparateWindowParent.recreateControllers();
    }

    private void initButtons(ViewGroup navContainer, ViewGroup endContainer,
            TaskbarNavButtonController navButtonController) {

        mBackButton = addButton(R.drawable.ic_sysbar_back, BUTTON_BACK,
                mNavButtonContainer, mControllers.navButtonController, R.id.back);
        mBackButtonAlpha = new MultiValueAlpha(mBackButton, NUM_ALPHA_CHANNELS);
        mBackButtonAlpha.setUpdateVisibility(true);
        mPropertyHolders.add(new StatePropertyHolder(
                mBackButtonAlpha.getProperty(ALPHA_INDEX_KEYGUARD_OR_DISABLE),
                flags -> {
                    // Show only if not disabled, and if not on the keyguard or otherwise only when
                    // the bouncer or a lockscreen app is showing above the keyguard
                    boolean showingOnKeyguard = (flags & FLAG_KEYGUARD_VISIBLE) == 0 ||
                            (flags & FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE) != 0 ||
                            (flags & FLAG_KEYGUARD_OCCLUDED) != 0;
                    return (flags & FLAG_DISABLE_BACK) == 0
                            && ((flags & FLAG_KEYGUARD_VISIBLE) == 0 || showingOnKeyguard);
                }));
        boolean isRtl = Utilities.isRtl(mContext.getResources());
        mPropertyHolders.add(new StatePropertyHolder(mBackButton,
                flags -> (flags & FLAG_IME_VISIBLE) != 0 && !mContext.isNavBarKidsModeActive(),
                View.ROTATION, isRtl ? 90 : -90, 0));
        // Translate back button to be at end/start of other buttons for keyguard
        int navButtonSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.taskbar_nav_buttons_size);
        mPropertyHolders.add(new StatePropertyHolder(
                mBackButton, flags -> (flags & FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE) != 0
                        || (flags & FLAG_KEYGUARD_VISIBLE) != 0,
                VIEW_TRANSLATE_X, navButtonSize * (isRtl ? -2 : 2), 0));

        // home button
        mHomeButton = addButton(R.drawable.ic_sysbar_home, BUTTON_HOME, navContainer,
                navButtonController, R.id.home);
        mHomeButtonAlpha = new MultiValueAlpha(mHomeButton, NUM_ALPHA_CHANNELS);
        mHomeButtonAlpha.setUpdateVisibility(true);
        mPropertyHolders.add(
                new StatePropertyHolder(mHomeButtonAlpha.getProperty(
                        ALPHA_INDEX_KEYGUARD_OR_DISABLE),
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0 &&
                        (flags & FLAG_DISABLE_HOME) == 0));

        // Recents button
        View recentsButton = addButton(R.drawable.ic_sysbar_recent, BUTTON_RECENTS,
                navContainer, navButtonController, R.id.recent_apps);
        mHitboxExtender.init(recentsButton, mNavButtonsView, mContext.getDeviceProfile(),
                () -> {
                    float[] recentsCoords = new float[2];
                    getDescendantCoordRelativeToAncestor(recentsButton, mNavButtonsView,
                            recentsCoords, false);
                    return recentsCoords;
                }, new Handler());
        recentsButton.setOnClickListener(v -> {
            navButtonController.onButtonClick(BUTTON_RECENTS, v);
            mHitboxExtender.onRecentsButtonClicked();
        });
        mPropertyHolders.add(new StatePropertyHolder(recentsButton,
                flags -> (flags & FLAG_KEYGUARD_VISIBLE) == 0 && (flags & FLAG_DISABLE_RECENTS) == 0
                        && !mContext.isNavBarKidsModeActive()));

        // A11y button
        mA11yButton = addButton(R.drawable.ic_sysbar_accessibility_button, BUTTON_A11Y,
                endContainer, navButtonController, R.id.accessibility_button,
                R.layout.taskbar_contextual_button);
        mPropertyHolders.add(new StatePropertyHolder(mA11yButton,
                flags -> (flags & FLAG_A11Y_VISIBLE) != 0
                        && (flags & FLAG_ROTATION_BUTTON_VISIBLE) == 0));
    }

    private void parseSystemUiFlags(int sysUiStateFlags) {
        mSysuiStateFlags = sysUiStateFlags;
        boolean isImeVisible = (sysUiStateFlags & SYSUI_STATE_IME_SHOWING) != 0;
        boolean isImeSwitcherShowing = (sysUiStateFlags & SYSUI_STATE_IME_SWITCHER_SHOWING) != 0;
        boolean a11yVisible = (sysUiStateFlags & SYSUI_STATE_A11Y_BUTTON_CLICKABLE) != 0;
        boolean isHomeDisabled = (sysUiStateFlags & SYSUI_STATE_HOME_DISABLED) != 0;
        boolean isRecentsDisabled = (sysUiStateFlags & SYSUI_STATE_OVERVIEW_DISABLED) != 0;
        boolean isBackDisabled = (sysUiStateFlags & SYSUI_STATE_BACK_DISABLED) != 0;
        int shadeExpandedFlags = SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
                | SYSUI_STATE_QUICK_SETTINGS_EXPANDED;
        boolean isNotificationShadeExpanded = (sysUiStateFlags & shadeExpandedFlags) != 0;
        boolean isScreenPinningActive = (sysUiStateFlags & SYSUI_STATE_SCREEN_PINNING) != 0;
        boolean isVoiceInteractionWindowShowing =
                (sysUiStateFlags & SYSUI_STATE_VOICE_INTERACTION_WINDOW_SHOWING) != 0;

        // TODO(b/202218289) we're getting IME as not visible on lockscreen from system
        updateStateForFlag(FLAG_IME_VISIBLE, isImeVisible);
        updateStateForFlag(FLAG_SWITCHER_SHOWING, isImeSwitcherShowing);
        updateStateForFlag(FLAG_A11Y_VISIBLE, a11yVisible);
        updateStateForFlag(FLAG_DISABLE_HOME, isHomeDisabled);
        updateStateForFlag(FLAG_DISABLE_RECENTS, isRecentsDisabled);
        updateStateForFlag(FLAG_DISABLE_BACK, isBackDisabled);
        updateStateForFlag(FLAG_NOTIFICATION_SHADE_EXPANDED, isNotificationShadeExpanded);
        updateStateForFlag(FLAG_SCREEN_PINNING_ACTIVE, isScreenPinningActive);
        updateStateForFlag(FLAG_VOICE_INTERACTION_WINDOW_SHOWING, isVoiceInteractionWindowShowing);

        if (mA11yButton != null) {
            // Only used in 3 button
            boolean a11yLongClickable =
                    (sysUiStateFlags & SYSUI_STATE_A11Y_BUTTON_LONG_CLICKABLE) != 0;
            mA11yButton.setLongClickable(a11yLongClickable);
            updateButtonLayoutSpacing();
        }
    }

    public void updateStateForSysuiFlags(int systemUiStateFlags, boolean skipAnim) {
        if (systemUiStateFlags == mSysuiStateFlags) {
            return;
        }
        parseSystemUiFlags(systemUiStateFlags);
        applyState();
        if (skipAnim) {
            mPropertyHolders.forEach(StatePropertyHolder::endAnimation);
        }
    }

    /**
     * @return {@code true} if A11y is showing in 3 button nav taskbar
     */
    private boolean isContextualButtonShowing() {
        return mContext.isThreeButtonNav() && (mState & FLAG_A11Y_VISIBLE) != 0;
    }

    /**
     * Should be called when we need to show back button for bouncer
     */
    public void setBackForBouncer(boolean isBouncerVisible) {
        updateStateForFlag(FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE, isBouncerVisible);
        applyState();
    }

    /**
     * Slightly misnamed, but should be called when keyguard OR AOD is showing.
     * We consider keyguardVisible when it's showing bouncer OR is occlucded by another app
     */
    public void setKeyguardVisible(boolean isKeyguardVisible, boolean isKeyguardOccluded) {
        updateStateForFlag(FLAG_KEYGUARD_VISIBLE, isKeyguardVisible || isKeyguardOccluded);
        updateStateForFlag(FLAG_KEYGUARD_OCCLUDED, isKeyguardOccluded);
        applyState();
    }

    /** {@code true} if a slide in view is currently visible over taskbar. */
    public void setSlideInViewVisible(boolean isSlideInViewVisible) {
        updateStateForFlag(FLAG_SLIDE_IN_VIEW_VISIBLE, isSlideInViewVisible);
        applyState();
    }

    /**
     * Returns true if IME bar is visible
     */
    public boolean isImeVisible() {
        return (mState & FLAG_IME_VISIBLE) != 0;
    }

    /**
     * Returns true if IME switcher is visible
     */
    public boolean isImeSwitcherVisible() {
        return (mState & FLAG_SWITCHER_SHOWING) != 0;
    }

    /**
     * Returns true if the home button is disabled
     */
    public boolean isHomeDisabled() {
        return (mState & FLAG_DISABLE_HOME) != 0;
    }

    /**
     * Returns true if the recents (overview) button is disabled
     */
    public boolean isRecentsDisabled() {
        return (mState & FLAG_DISABLE_RECENTS) != 0;
    }

    /**
     * Adds the bounds corresponding to all visible buttons to provided region
     */
    public void addVisibleButtonsRegion(BaseDragLayer<?> parent, Region outRegion) {
        int count = mAllButtons.size();
        for (int i = 0; i < count; i++) {
            View button = mAllButtons.get(i);
            if (button.getVisibility() == View.VISIBLE) {
                parent.getDescendantRectRelativeToSelf(button, mTempRect);
                if (mHitboxExtender.extendedHitboxEnabled()) {
                    mTempRect.bottom += mContext.getDeviceProfile().getTaskbarOffsetY();
                }
                outRegion.op(mTempRect, Op.UNION);
            }
        }
    }

    /**
     * Returns multi-value alpha controller for back button.
     */
    public MultiValueAlpha getBackButtonAlpha() {
        return mBackButtonAlpha;
    }

    /**
     * Returns multi-value alpha controller for home button.
     */
    public MultiValueAlpha getHomeButtonAlpha() {
        return mHomeButtonAlpha;
    }

    /**
     * Sets the AccessibilityDelegate for the home button.
     */
    public void setHomeButtonAccessibilityDelegate(AccessibilityDelegate accessibilityDelegate) {
        if (mHomeButton == null) {
            return;
        }
        mHomeButton.setAccessibilityDelegate(accessibilityDelegate);
    }

    /**
     * Sets the AccessibilityDelegate for the back button.
     */
    public void setBackButtonAccessibilityDelegate(AccessibilityDelegate accessibilityDelegate) {
        if (mBackButton == null) {
            return;
        }
        mBackButton.setAccessibilityDelegate(accessibilityDelegate);
    }

    /** Use to set the translationY for the all nav+contextual buttons */
    public AnimatedFloat getTaskbarNavButtonTranslationY() {
        return mTaskbarNavButtonTranslationY;
    }

    /** Use to set the translationY for the all nav+contextual buttons when in Launcher */
    public AnimatedFloat getTaskbarNavButtonTranslationYForInAppDisplay() {
        return mTaskbarNavButtonTranslationYForInAppDisplay;
    }

    /** Use to set the dark intensity for the all nav+contextual buttons */
    public AnimatedFloat getTaskbarNavButtonDarkIntensity() {
        return mTaskbarNavButtonDarkIntensity;
    }

    /** Use to determine whether to use the dark intensity requested by the underlying app */
    public AnimatedFloat getNavButtonDarkIntensityMultiplier() {
        return mNavButtonDarkIntensityMultiplier;
    }

    /**
     * Does not call {@link #applyState()}. Don't forget to!
     */
    private void updateStateForFlag(int flag, boolean enabled) {
        if (enabled) {
            mState |= flag;
        } else {
            mState &= ~flag;
        }
    }

    private void applyState() {
        int count = mPropertyHolders.size();
        for (int i = 0; i < count; i++) {
            mPropertyHolders.get(i).setState(mState);
        }
    }

    private void updateNavButtonInAppDisplayProgressForSysui() {
        TaskbarUIController uiController = mControllers.uiController;
        if (uiController instanceof LauncherTaskbarUIController) {
            ((LauncherTaskbarUIController) uiController).onTaskbarInAppDisplayProgressUpdate(
                    mNavButtonInAppDisplayProgressForSysui.value, SYSUI_SURFACE_PROGRESS_INDEX);
        }
    }

    private void updateNavButtonTranslationY() {
        if (isPhoneButtonNavMode(mContext)) {
            return;
        }
        final float normalTranslationY = mTaskbarNavButtonTranslationY.value;
        final float imeAdjustmentTranslationY = mTaskbarNavButtonTranslationYForIme.value;
        TaskbarUIController uiController = mControllers.uiController;
        final float inAppDisplayAdjustmentTranslationY =
                (uiController instanceof LauncherTaskbarUIController
                        && ((LauncherTaskbarUIController) uiController).shouldUseInAppLayout())
                        ? mTaskbarNavButtonTranslationYForInAppDisplay.value : 0;

        mNavButtonsView.setTranslationY(normalTranslationY
                + imeAdjustmentTranslationY
                + inAppDisplayAdjustmentTranslationY);
    }

    private void updateNavButtonDarkIntensity() {
        float darkIntensity = mTaskbarNavButtonDarkIntensity.value
                * mNavButtonDarkIntensityMultiplier.value;
        ArgbEvaluator argbEvaluator = ArgbEvaluator.getInstance();
        int iconColor = (int) argbEvaluator.evaluate(
                darkIntensity, mLightIconColor, mDarkIconColor);
        iconColor = (int) argbEvaluator.evaluate(
                mSlideInViewNavButtonColorOverride.value, iconColor, mSlideInViewIconColor);
        for (ImageView button : mAllButtons) {
            button.setImageTintList(ColorStateList.valueOf(iconColor));
        }
    }

    protected ImageView addButton(@DrawableRes int drawableId, @TaskbarButton int buttonType,
            ViewGroup parent, TaskbarNavButtonController navButtonController, @IdRes int id) {
        return addButton(drawableId, buttonType, parent, navButtonController, id,
                R.layout.taskbar_nav_button);
    }

    private ImageView addButton(@DrawableRes int drawableId, @TaskbarButton int buttonType,
            ViewGroup parent, TaskbarNavButtonController navButtonController, @IdRes int id,
            @LayoutRes int layoutId) {
        ImageView buttonView = addButton(parent, id, layoutId);
        buttonView.setImageResource(drawableId);
        buttonView.setContentDescription(parent.getContext().getString(
                navButtonController.getButtonContentDescription(buttonType)));
        buttonView.setOnClickListener(view -> navButtonController.onButtonClick(buttonType, view));
        buttonView.setOnLongClickListener(view ->
                navButtonController.onButtonLongClick(buttonType, view));
        return buttonView;
    }

    private ImageView addButton(ViewGroup parent, @IdRes int id, @LayoutRes int layoutId) {
        ImageView buttonView = (ImageView) mContext.getLayoutInflater()
                .inflate(layoutId, parent, false);
        buttonView.setId(id);
        parent.addView(buttonView);
        mAllButtons.add(buttonView);
        return buttonView;
    }

    public boolean isEventOverAnyItem(MotionEvent ev) {
        return mFloatingRotationButtonBounds.contains((int) ev.getX(), (int) ev.getY());
    }

    public void onConfigurationChanged(@Config int configChanges) {
        if (mFloatingRotationButton != null) {
            mFloatingRotationButton.onConfigurationChanged(configChanges);
        }
        updateButtonLayoutSpacing();
    }

    /**
     * Adds the correct spacing to 3 button nav container. No-op if using gesture nav or kids mode.
     */
    private void updateButtonLayoutSpacing() {
        if (!mContext.isThreeButtonNav() || mContext.isNavBarKidsModeActive()) {
            return;
        }

        if (isPhoneButtonNavMode(mContext)) {
            updatePhoneButtonSpacing();
            return;
        }

        DeviceProfile dp = mContext.getDeviceProfile();
        Resources res = mContext.getResources();

        // Add spacing after the end of the last nav button
        FrameLayout.LayoutParams navButtonParams =
                (FrameLayout.LayoutParams) mNavButtonContainer.getLayoutParams();
        navButtonParams.gravity = Gravity.END;
        navButtonParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        navButtonParams.height = MATCH_PARENT;

        int navMarginEnd = (int) res.getDimension(dp.inv.inlineNavButtonsEndSpacing);
        int contextualWidth = mEndContextualContainer.getWidth();
        // If contextual buttons are showing, we check if the end margin is enough for the
        // contextual button to be showing - if not, move the nav buttons over a smidge
        if (isContextualButtonShowing() && navMarginEnd < contextualWidth) {
            // Additional spacing, eat up half of space between last icon and nav button
            navMarginEnd += res.getDimensionPixelSize(R.dimen.taskbar_hotseat_nav_spacing) / 2;
        }
        navButtonParams.setMarginEnd(navMarginEnd);
        mNavButtonContainer.setLayoutParams(navButtonParams);

        // Add the spaces in between the nav buttons
        int spaceInBetween = res.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween);
        for (int i = 0; i < mNavButtonContainer.getChildCount(); i++) {
            View navButton = mNavButtonContainer.getChildAt(i);
            LinearLayout.LayoutParams buttonLayoutParams =
                    (LinearLayout.LayoutParams) navButton.getLayoutParams();
            buttonLayoutParams.weight = 0;
            if (i == 0) {
                buttonLayoutParams.setMarginEnd(spaceInBetween / 2);
            } else if (i == mNavButtonContainer.getChildCount() - 1) {
                buttonLayoutParams.setMarginStart(spaceInBetween / 2);
            } else {
                buttonLayoutParams.setMarginStart(spaceInBetween / 2);
                buttonLayoutParams.setMarginEnd(spaceInBetween / 2);
            }
        }
    }

    /** Uniformly spaces out the 3 button nav for smaller phone screens */
    private void updatePhoneButtonSpacing() {
        DeviceProfile dp = mContext.getDeviceProfile();
        Resources res = mContext.getResources();

        // TODO: Polish pending, this is just to make it usable
        FrameLayout.LayoutParams navContainerParams =
                (FrameLayout.LayoutParams) mNavButtonContainer.getLayoutParams();
        int endStartMargins = res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size);
        navContainerParams.gravity = Gravity.CENTER;
        navContainerParams.setMarginEnd(endStartMargins);
        navContainerParams.setMarginStart(endStartMargins);
        mNavButtonContainer.setLayoutParams(navContainerParams);

        // Add the spaces in between the nav buttons
        int spaceInBetween = res.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween_phone);
        for (int i = 0; i < mNavButtonContainer.getChildCount(); i++) {
            View navButton = mNavButtonContainer.getChildAt(i);
            LinearLayout.LayoutParams buttonLayoutParams =
                    (LinearLayout.LayoutParams) navButton.getLayoutParams();
            buttonLayoutParams.weight = 1;
            if (i == 0) {
                buttonLayoutParams.setMarginEnd(spaceInBetween / 2);
            } else if (i == mNavButtonContainer.getChildCount() - 1) {
                buttonLayoutParams.setMarginStart(spaceInBetween / 2);
            } else {
                buttonLayoutParams.setMarginStart(spaceInBetween / 2);
                buttonLayoutParams.setMarginEnd(spaceInBetween / 2);
            }
        }
    }

    public void onDestroy() {
        mPropertyHolders.clear();
        mControllers.rotationButtonController.unregisterListeners();
        if (mFloatingRotationButton != null) {
            mFloatingRotationButton.hide();
        }

        moveNavButtonsBackToTaskbarWindow();
        mNavButtonContainer.removeAllViews();
        mAllButtons.clear();
    }

    /**
     * Moves mNavButtonsView from TaskbarDragLayer to a placeholder BaseDragLayer on a new window.
     */
    public void moveNavButtonsToNewWindow() {
        if (mAreNavButtonsInSeparateWindow) {
            return;
        }

        if (mIsImeRenderingNavButtons) {
            // IME is rendering the nav buttons, so we don't need to create a new layer for them.
            return;
        }

        mSeparateWindowParent.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                mSeparateWindowParent.getViewTreeObserver().addOnComputeInternalInsetsListener(
                        mSeparateWindowInsetsComputer);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                mSeparateWindowParent.removeOnAttachStateChangeListener(this);
                mSeparateWindowParent.getViewTreeObserver().removeOnComputeInternalInsetsListener(
                        mSeparateWindowInsetsComputer);
            }
        });

        mAreNavButtonsInSeparateWindow = true;
        mContext.getDragLayer().removeView(mNavButtonsView);
        mSeparateWindowParent.addView(mNavButtonsView);
        WindowManager.LayoutParams windowLayoutParams = mContext.createDefaultWindowLayoutParams();
        windowLayoutParams.setTitle(NAV_BUTTONS_SEPARATE_WINDOW_TITLE);
        mContext.addWindowView(mSeparateWindowParent, windowLayoutParams);

    }

    /**
     * Moves mNavButtonsView from its temporary window and reattaches it to TaskbarDragLayer.
     */
    public void moveNavButtonsBackToTaskbarWindow() {
        if (!mAreNavButtonsInSeparateWindow) {
            return;
        }

        mAreNavButtonsInSeparateWindow = false;
        mContext.removeWindowView(mSeparateWindowParent);
        mSeparateWindowParent.removeView(mNavButtonsView);
        mContext.getDragLayer().addView(mNavButtonsView);
    }

    private void onComputeInsetsForSeparateWindow(ViewTreeObserver.InternalInsetsInfo insetsInfo) {
        addVisibleButtonsRegion(mSeparateWindowParent, insetsInfo.touchableRegion);
        insetsInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "NavbarButtonsViewController:");

        pw.println(prefix + "\tmState=" + getStateString(mState));
        pw.println(prefix + "\tmLightIconColor=" + Integer.toHexString(mLightIconColor));
        pw.println(prefix + "\tmDarkIconColor=" + Integer.toHexString(mDarkIconColor));
        pw.println(prefix + "\tmFloatingRotationButtonBounds=" + mFloatingRotationButtonBounds);
        pw.println(prefix + "\tmSysuiStateFlags=" + QuickStepContract.getSystemUiStateString(
                mSysuiStateFlags));
    }

    private static String getStateString(int flags) {
        StringJoiner str = new StringJoiner("|");
        appendFlag(str, flags, FLAG_SWITCHER_SHOWING, "FLAG_SWITCHER_SHOWING");
        appendFlag(str, flags, FLAG_IME_VISIBLE, "FLAG_IME_VISIBLE");
        appendFlag(str, flags, FLAG_ROTATION_BUTTON_VISIBLE, "FLAG_ROTATION_BUTTON_VISIBLE");
        appendFlag(str, flags, FLAG_A11Y_VISIBLE, "FLAG_A11Y_VISIBLE");
        appendFlag(str, flags, FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE,
                "FLAG_ONLY_BACK_FOR_BOUNCER_VISIBLE");
        appendFlag(str, flags, FLAG_KEYGUARD_VISIBLE, "FLAG_KEYGUARD_VISIBLE");
        appendFlag(str, flags, FLAG_KEYGUARD_OCCLUDED, "FLAG_KEYGUARD_OCCLUDED");
        appendFlag(str, flags, FLAG_DISABLE_HOME, "FLAG_DISABLE_HOME");
        appendFlag(str, flags, FLAG_DISABLE_RECENTS, "FLAG_DISABLE_RECENTS");
        appendFlag(str, flags, FLAG_DISABLE_BACK, "FLAG_DISABLE_BACK");
        appendFlag(str, flags, FLAG_NOTIFICATION_SHADE_EXPANDED,
                "FLAG_NOTIFICATION_SHADE_EXPANDED");
        appendFlag(str, flags, FLAG_SCREEN_PINNING_ACTIVE, "FLAG_SCREEN_PINNING_ACTIVE");
        appendFlag(str, flags, FLAG_VOICE_INTERACTION_WINDOW_SHOWING,
                "FLAG_VOICE_INTERACTION_WINDOW_SHOWING");
        return str.toString();
    }

    public TouchController getTouchController() {
        return mHitboxExtender;
    }

    /**
     * @param alignment 0 -> Taskbar, 1 -> Workspace
     */
    public void updateTaskbarAlignment(float alignment) {
        mHitboxExtender.onAnimationProgressToOverview(alignment);
    }

    private class RotationButtonListener implements RotationButton.RotationButtonUpdatesCallback {
        @Override
        public void onVisibilityChanged(boolean isVisible) {
            if (isVisible) {
                mFloatingRotationButton.getCurrentView()
                        .getBoundsOnScreen(mFloatingRotationButtonBounds);
            } else {
                mFloatingRotationButtonBounds.setEmpty();
            }
        }
    }

    private class RotationButtonImpl implements RotationButton {

        private final ImageView mButton;
        private AnimatedVectorDrawable mImageDrawable;

        RotationButtonImpl(ImageView button) {
            mButton = button;
        }

        @Override
        public void setRotationButtonController(RotationButtonController rotationButtonController) {
            // TODO(b/187754252) UI polish, different icons based on light/dark context, etc
            mImageDrawable = (AnimatedVectorDrawable) mButton.getContext()
                    .getDrawable(rotationButtonController.getIconResId());
            mButton.setImageDrawable(mImageDrawable);
            mButton.setContentDescription(mButton.getResources()
                    .getString(R.string.accessibility_rotate_button));
            mImageDrawable.setCallback(mButton);
        }

        @Override
        public View getCurrentView() {
            return mButton;
        }

        @Override
        public boolean show() {
            mButton.setVisibility(View.VISIBLE);
            mState |= FLAG_ROTATION_BUTTON_VISIBLE;
            applyState();
            return true;
        }

        @Override
        public boolean hide() {
            mButton.setVisibility(View.GONE);
            mState &= ~FLAG_ROTATION_BUTTON_VISIBLE;
            applyState();
            return true;
        }

        @Override
        public boolean isVisible() {
            return mButton.getVisibility() == View.VISIBLE;
        }

        @Override
        public void updateIcon(int lightIconColor, int darkIconColor) {
            // TODO(b/187754252): UI Polish
        }

        @Override
        public void setOnClickListener(OnClickListener onClickListener) {
            mButton.setOnClickListener(onClickListener);
        }

        @Override
        public void setOnHoverListener(OnHoverListener onHoverListener) {
            mButton.setOnHoverListener(onHoverListener);
        }

        @Override
        public AnimatedVectorDrawable getImageDrawable() {
            return mImageDrawable;
        }

        @Override
        public void setDarkIntensity(float darkIntensity) {
            // TODO(b/187754252) UI polish
        }

        @Override
        public boolean acceptRotationProposal() {
            return mButton.isAttachedToWindow();
        }
    }

    private static class StatePropertyHolder {

        private final float mEnabledValue, mDisabledValue;
        private final ObjectAnimator mAnimator;
        private final IntPredicate mEnableCondition;

        private boolean mIsEnabled = true;

        StatePropertyHolder(View view, IntPredicate enableCondition) {
            this(view, enableCondition, LauncherAnimUtils.VIEW_ALPHA, 1, 0);
            mAnimator.addListener(new AlphaUpdateListener(view));
        }

        StatePropertyHolder(MultiValueAlpha.AlphaProperty alphaProperty,
                IntPredicate enableCondition) {
            this(alphaProperty, enableCondition, MultiValueAlpha.VALUE, 1, 0);
        }

        StatePropertyHolder(AnimatedFloat animatedFloat, IntPredicate enableCondition) {
            this(animatedFloat, enableCondition, AnimatedFloat.VALUE, 1, 0);
        }

        <T> StatePropertyHolder(T target, IntPredicate enabledCondition,
                Property<T, Float> property, float enabledValue, float disabledValue) {
            mEnableCondition = enabledCondition;
            mEnabledValue = enabledValue;
            mDisabledValue = disabledValue;
            mAnimator = ObjectAnimator.ofFloat(target, property, enabledValue, disabledValue);
        }

        public void setState(int flags) {
            boolean isEnabled = mEnableCondition.test(flags);
            if (mIsEnabled != isEnabled) {
                mIsEnabled = isEnabled;
                mAnimator.cancel();
                mAnimator.setFloatValues(mIsEnabled ? mEnabledValue : mDisabledValue);
                mAnimator.start();
            }
        }

        public void endAnimation() {
            if (mAnimator.isRunning()) {
                mAnimator.end();
            }
        }
    }
}
