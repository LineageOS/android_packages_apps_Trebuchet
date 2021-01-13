/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.launcher3.FastBitmapDrawable.newIcon;
import static com.android.launcher3.InvariantDeviceProfile.KEY_SHOW_DESKTOP_LABELS;
import static com.android.launcher3.InvariantDeviceProfile.KEY_SHOW_DRAWER_LABELS;
import static com.android.launcher3.graphics.PreloadIconDrawable.newPendingIcon;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.Property;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.widget.TextView;

import com.android.launcher3.Launcher.OnResumeCallback;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.graphics.PreloadIconDrawable;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.IconCache.IconLoadRequest;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.PromiseAppInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.IconLabelDotView;

import java.text.NumberFormat;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends TextView implements ItemInfoUpdateReceiver, OnResumeCallback,
        IconLabelDotView, DraggableView, Reorderable {

    private static final int DISPLAY_WORKSPACE = 0;
    private static final int DISPLAY_ALL_APPS = 1;
    private static final int DISPLAY_FOLDER = 2;

    private static final int[] STATE_PRESSED = new int[] {android.R.attr.state_pressed};

    private final PointF mTranslationForReorderBounce = new PointF(0, 0);
    private final PointF mTranslationForReorderPreview = new PointF(0, 0);

    private float mScaleForReorderBounce = 1f;

    private static final Property<BubbleTextView, Float> DOT_SCALE_PROPERTY
            = new Property<BubbleTextView, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mDotParams.scale;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float value) {
            bubbleTextView.mDotParams.scale = value;
            bubbleTextView.invalidate();
        }
    };

    public static final Property<BubbleTextView, Float> TEXT_ALPHA_PROPERTY
            = new Property<BubbleTextView, Float>(Float.class, "textAlpha") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mTextAlpha;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float alpha) {
            bubbleTextView.setTextAlpha(alpha);
        }
    };

    private final ActivityContext mActivity;
    private Drawable mIcon;
    private boolean mCenterVertically;

    private final int mDisplay;

    private final CheckLongPressHelper mLongPressHelper;

    private final boolean mLayoutHorizontal;
    private final int mIconSize;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIsIconVisible = true;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mTextColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTextAlpha = 1;

    @ViewDebug.ExportedProperty(category = "launcher")
    private DotInfo mDotInfo;
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private DotRenderer.DrawParams mDotParams;
    private Animator mDotScaleAnim;
    private boolean mForceHideDot;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mStayPressed;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIgnorePressedStateChange;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDisableRelayout = false;

    private boolean mShouldShowLabel;

    private IconLoadRequest mIconLoadRequest;

    public BubbleTextView(Context context) {
        this(context, null, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivity = ActivityContext.lookupContext(context);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BubbleTextView, defStyle, 0);
        mLayoutHorizontal = a.getBoolean(R.styleable.BubbleTextView_layoutHorizontal, false);
        DeviceProfile grid = mActivity.getDeviceProfile();

        SharedPreferences prefs = Utilities.getPrefs(context.getApplicationContext());

        mDisplay = a.getInteger(R.styleable.BubbleTextView_iconDisplay, DISPLAY_WORKSPACE);
        final int defaultIconSize;
        if (mDisplay == DISPLAY_WORKSPACE) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.iconTextSizePx);
            setCompoundDrawablePadding(grid.iconDrawablePaddingPx);
            defaultIconSize = grid.iconSizePx;
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true);
        } else if (mDisplay == DISPLAY_ALL_APPS) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.allAppsIconTextSizePx);
            setCompoundDrawablePadding(grid.allAppsIconDrawablePaddingPx);
            defaultIconSize = grid.allAppsIconSizePx;
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DRAWER_LABELS, true);
        } else if (mDisplay == DISPLAY_FOLDER) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.folderChildTextSizePx);
            setCompoundDrawablePadding(grid.folderChildDrawablePaddingPx);
            defaultIconSize = grid.folderChildIconSizePx;
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true);
        } else {
            // widget_selection or shortcut_popup
            defaultIconSize = grid.iconSizePx;
            mShouldShowLabel = prefs.getBoolean(KEY_SHOW_DESKTOP_LABELS, true);
        }

        mCenterVertically = a.getBoolean(R.styleable.BubbleTextView_centerVertically, false);

        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride,
                defaultIconSize);
        a.recycle();

        mLongPressHelper = new CheckLongPressHelper(this);

        mDotParams = new DotRenderer.DrawParams();

        setEllipsize(TruncateAt.END);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
        setTextAlpha(1f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        // Disable marques when not focused to that, so that updating text does not cause relayout.
        setEllipsize(focused ? TruncateAt.MARQUEE : TruncateAt.END);
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * Resets the view so it can be recycled.
     */
    public void reset() {
        mDotInfo = null;
        mDotParams.color = Color.TRANSPARENT;
        cancelDotScaleAnim();
        mDotParams.scale = 0f;
        mForceHideDot = false;
        setBackground(null);
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    private void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    public void applyFromWorkspaceItem(WorkspaceItemInfo info) {
        applyFromWorkspaceItem(info, false);
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        if (delegate instanceof LauncherAccessibilityDelegate) {
            super.setAccessibilityDelegate(delegate);
        } else {
            // NO-OP
            // Workaround for b/129745295 where RecyclerView is setting our Accessibility
            // delegate incorrectly. There are no cases when we shouldn't be using the
            // LauncherAccessibilityDelegate for BubbleTextView.
        }
    }

    public void applyFromWorkspaceItem(WorkspaceItemInfo info, boolean promiseStateChanged) {
        applyIconAndLabel(info);
        setTag(info);
        if (promiseStateChanged || (info.hasPromiseIconUi())) {
            applyPromiseState(promiseStateChanged);
        }

        applyDotState(info, false /* animate */);
    }

    public void applyFromApplicationInfo(AppInfo info) {
        applyIconAndLabel(info);

        // We don't need to check the info since it's not a WorkspaceItemInfo
        super.setTag(info);

        // Verify high res immediately
        verifyHighRes();

        if (info instanceof PromiseAppInfo) {
            PromiseAppInfo promiseAppInfo = (PromiseAppInfo) info;
            applyProgressLevel(promiseAppInfo.level);
        }
        applyDotState(info, false /* animate */);
    }

    public void applyFromPackageItemInfo(PackageItemInfo info) {
        applyIconAndLabel(info);
        // We don't need to check the info since it's not a WorkspaceItemInfo
        super.setTag(info);

        // Verify high res immediately
        verifyHighRes();
    }

    private void applyIconAndLabel(ItemInfoWithIcon info) {
        FastBitmapDrawable iconDrawable = newIcon(getContext(), info);
        mDotParams.color = IconPalette.getMutedColor(info.bitmap.color, 0.54f);

        setIcon(iconDrawable);
        if (mShouldShowLabel) {
            setText(info.title);
        }
        if (info.contentDescription != null) {
            setContentDescription(info.isDisabled()
                    ? getContext().getString(R.string.disabled_app_label, info.contentDescription)
                    : info.contentDescription);
        }
    }

    /**
     * Overrides the default long press timeout.
     */
    public void setLongPressTimeoutFactor(float longPressTimeoutFactor) {
        mLongPressHelper.setLongPressTimeoutFactor(longPressTimeoutFactor);
    }

    @Override
    public void refreshDrawableState() {
        if (!mIgnorePressedStateChange) {
            super.refreshDrawableState();
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mStayPressed) {
            mergeDrawableStates(drawableState, STATE_PRESSED);
        }
        return drawableState;
    }

    /** Returns the icon for this view. */
    public Drawable getIcon() {
        return mIcon;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // ignore events if they happen in padding area
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && shouldIgnoreTouchDown(event.getX(), event.getY())) {
            return false;
        }
        if (isLongClickable()) {
            super.onTouchEvent(event);
            mLongPressHelper.onTouchEvent(event);
            // Keep receiving the rest of the events
            return true;
        } else {
            return super.onTouchEvent(event);
        }
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        return y < getPaddingTop()
                || x < getPaddingLeft()
                || y > getHeight() - getPaddingBottom()
                || x > getWidth() - getPaddingRight();
    }

    void setStayPressed(boolean stayPressed) {
        mStayPressed = stayPressed;
        refreshDrawableState();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (mIcon != null) {
            mIcon.setVisible(isVisible, false);
        }
    }

    @Override
    public void onLauncherResume() {
        // Reset the pressed state of icon that was locked in the press state while activity
        // was launching
        setStayPressed(false);
    }

    void clearPressedBackground() {
        setPressed(false);
        setStayPressed(false);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Unlike touch events, keypress event propagate pressed state change immediately,
        // without waiting for onClickHandler to execute. Disable pressed state changes here
        // to avoid flickering.
        mIgnorePressedStateChange = true;
        boolean result = super.onKeyUp(keyCode, event);
        mIgnorePressedStateChange = false;
        refreshDrawableState();
        return result;
    }

    @SuppressWarnings("wrongcall")
    protected void drawWithoutDot(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawDotIfNecessary(canvas);
    }

    /**
     * Draws the notification dot in the top right corner of the icon bounds.
     * @param canvas The canvas to draw to.
     */
    protected void drawDotIfNecessary(Canvas canvas) {
        if (!mForceHideDot && (hasDot() || mDotParams.scale > 0)) {
            getIconBounds(mDotParams.iconBounds);
            Utilities.scaleRectAboutCenter(mDotParams.iconBounds, IconShape.getNormalizationScale());
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.translate(scrollX, scrollY);
            mDotRenderer.draw(canvas, mDotParams);
            canvas.translate(-scrollX, -scrollY);
        }
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    private boolean hasDot() {
        return mDotInfo != null;
    }

    public void getIconBounds(Rect outBounds) {
        getIconBounds(this, outBounds, mIconSize);
    }

    public static void getIconBounds(View iconView, Rect outBounds, int iconSize) {
        int top = iconView.getPaddingTop();
        int left = (iconView.getWidth() - iconSize) / 2;
        int right = left + iconSize;
        int bottom = top + iconSize;
        outBounds.set(left, top, right, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mCenterVertically) {
            Paint.FontMetrics fm = getPaint().getFontMetrics();
            int cellHeightPx = mIconSize + getCompoundDrawablePadding() +
                    (int) Math.ceil(fm.bottom - fm.top);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setPadding(getPaddingLeft(), (height - cellHeightPx) / 2, getPaddingRight(),
                    getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        super.setTextColor(getModifiedColor());
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        mTextColor = colors.getDefaultColor();
        if (Float.compare(mTextAlpha, 1) == 0) {
            super.setTextColor(colors);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    public boolean shouldShowLabel() {
        return mShouldShowLabel;
    }

    public boolean shouldTextBeVisible() {
        // Text should be visible everywhere but the hotseat.
        Object tag = getParent() instanceof FolderIcon ? ((View) getParent()).getTag() : getTag();
        ItemInfo info = tag instanceof ItemInfo ? (ItemInfo) tag : null;
        return info == null || (info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT
                && info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION);
    }

    public void setTextVisibility(boolean visible) {
        setTextAlpha(visible ? 1 : 0);
    }

    private void setTextAlpha(float alpha) {
        mTextAlpha = alpha;
        super.setTextColor(getModifiedColor());
    }

    private int getModifiedColor() {
        if (mTextAlpha == 0) {
            // Special case to prevent text shadows in high contrast mode
            return Color.TRANSPARENT;
        }
        return setColorAlphaBound(mTextColor, Math.round(Color.alpha(mTextColor) * mTextAlpha));
    }

    /**
     * Creates an animator to fade the text in or out.
     * @param fadeIn Whether the text should fade in or fade out.
     */
    public ObjectAnimator createTextAlphaAnimator(boolean fadeIn) {
        float toAlpha = shouldTextBeVisible() && fadeIn ? 1 : 0;
        return ObjectAnimator.ofFloat(this, TEXT_ALPHA_PROPERTY, toAlpha);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    public void applyPromiseState(boolean promiseStateChanged) {
        if (getTag() instanceof WorkspaceItemInfo) {
            WorkspaceItemInfo info = (WorkspaceItemInfo) getTag();
            final boolean isPromise = info.hasPromiseIconUi();
            final int progressLevel = isPromise ?
                    ((info.hasStatusFlag(WorkspaceItemInfo.FLAG_INSTALL_SESSION_ACTIVE) ?
                            info.getInstallProgress() : 0)) : 100;

            PreloadIconDrawable preloadDrawable = applyProgressLevel(progressLevel);
            if (preloadDrawable != null && promiseStateChanged) {
                preloadDrawable.maybePerformFinishedAnimation();
            }
        }
    }

    public PreloadIconDrawable applyProgressLevel(int progressLevel) {
        if (getTag() instanceof ItemInfoWithIcon) {
            ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
            if (progressLevel >= 100) {
                setContentDescription(info.contentDescription != null
                        ? info.contentDescription : "");
            } else if (progressLevel > 0) {
                setContentDescription(getContext()
                        .getString(R.string.app_downloading_title, info.title,
                                NumberFormat.getPercentInstance().format(progressLevel * 0.01)));
            } else {
                setContentDescription(getContext()
                        .getString(R.string.app_waiting_download_title, info.title));
            }
            if (mIcon != null) {
                final PreloadIconDrawable preloadDrawable;
                if (mIcon instanceof PreloadIconDrawable) {
                    preloadDrawable = (PreloadIconDrawable) mIcon;
                    preloadDrawable.setLevel(progressLevel);
                } else {
                    preloadDrawable = newPendingIcon(getContext(), info);
                    preloadDrawable.setLevel(progressLevel);
                    setIcon(preloadDrawable);
                }
                return preloadDrawable;
            }
        }
        return null;
    }

    public void applyDotState(ItemInfo itemInfo, boolean animate) {
        if (mIcon instanceof FastBitmapDrawable) {
            boolean wasDotted = mDotInfo != null;
            mDotInfo = mActivity.getDotInfoForItem(itemInfo);
            boolean isDotted = mDotInfo != null;
            float newDotScale = isDotted ? 1f : 0;
            if (mDisplay == DISPLAY_ALL_APPS) {
                mDotRenderer = mActivity.getDeviceProfile().mDotRendererAllApps;
            } else {
                mDotRenderer = mActivity.getDeviceProfile().mDotRendererWorkSpace;
            }
            if (wasDotted || isDotted) {
                // Animate when a dot is first added or when it is removed.
                if (animate && (wasDotted ^ isDotted) && isShown()) {
                    animateDotScale(newDotScale);
                } else {
                    cancelDotScaleAnim();
                    mDotParams.scale = newDotScale;
                    invalidate();
                }
            }
            if (itemInfo.contentDescription != null) {
                if (itemInfo.isDisabled()) {
                    setContentDescription(getContext().getString(R.string.disabled_app_label,
                            itemInfo.contentDescription));
                } else if (hasDot()) {
                    int count = mDotInfo.getNotificationCount();
                    setContentDescription(getContext().getResources().getQuantityString(
                            R.plurals.dotted_app_label, count, itemInfo.contentDescription, count));
                } else {
                    setContentDescription(itemInfo.contentDescription);
                }
            }
        }
    }

    /**
     * Sets the icon for this view based on the layout direction.
     */
    private void setIcon(Drawable icon) {
        if (mIsIconVisible) {
            applyCompoundDrawables(icon);
        }
        mIcon = icon;
        if (mIcon != null) {
            mIcon.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
        }
    }

    @Override
    public void setIconVisible(boolean visible) {
        mIsIconVisible = visible;
        if (!mIsIconVisible) {
            resetIconScale();
        }
        Drawable icon = visible ? mIcon : new ColorDrawable(Color.TRANSPARENT);
        applyCompoundDrawables(icon);
    }

    protected void applyCompoundDrawables(Drawable icon) {
        // If we had already set an icon before, disable relayout as the icon size is the
        // same as before.
        mDisableRelayout = mIcon != null;

        icon.setBounds(0, 0, mIconSize, mIconSize);
        if (mLayoutHorizontal) {
            setCompoundDrawablesRelative(icon, null, null, null);
        } else {
            setCompoundDrawables(null, icon, null, null);
        }
        mDisableRelayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mDisableRelayout) {
            super.requestLayout();
        }
    }

    /**
     * Applies the item info if it is same as what the view is pointing to currently.
     */
    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            mDisableRelayout = true;

            // Optimization: Starting in N, pre-uploads the bitmap to RenderThread.
            info.bitmap.icon.prepareToDraw();

            if (info instanceof AppInfo) {
                applyFromApplicationInfo((AppInfo) info);
            } else if (info instanceof WorkspaceItemInfo) {
                applyFromWorkspaceItem((WorkspaceItemInfo) info);
                mActivity.invalidateParent(info);
            } else if (info instanceof PackageItemInfo) {
                applyFromPackageItemInfo((PackageItemInfo) info);
            }

            mDisableRelayout = false;
        }
    }

    /**
     * Verifies that the current icon is high-res otherwise posts a request to load the icon.
     */
    public void verifyHighRes() {
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
        if (getTag() instanceof ItemInfoWithIcon) {
            ItemInfoWithIcon info = (ItemInfoWithIcon) getTag();
            if (info.usingLowResIcon()) {
                mIconLoadRequest = LauncherAppState.getInstance(getContext()).getIconCache()
                        .updateIconInBackground(BubbleTextView.this, info);
            }
        }
    }

    public int getIconSize() {
        return mIconSize;
    }

    private void updateTranslation() {
        super.setTranslationX(mTranslationForReorderBounce.x + mTranslationForReorderPreview.x);
        super.setTranslationY(mTranslationForReorderBounce.y + mTranslationForReorderPreview.y);
    }

    public void setReorderBounceOffset(float x, float y) {
        mTranslationForReorderBounce.set(x, y);
        updateTranslation();
    }

    public void getReorderBounceOffset(PointF offset) {
        offset.set(mTranslationForReorderBounce);
    }

    @Override
    public void setReorderPreviewOffset(float x, float y) {
        mTranslationForReorderPreview.set(x, y);
        updateTranslation();
    }

    @Override
    public void getReorderPreviewOffset(PointF offset) {
        offset.set(mTranslationForReorderPreview);
    }

    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    public View getView() {
        return this;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        DeviceProfile grid = mActivity.getDeviceProfile();
        BubbleTextView.getIconBounds(this, bounds, grid.iconSizePx);
    }

    private int getIconSizeForDisplay(int display) {
        DeviceProfile grid = mActivity.getDeviceProfile();
        switch (display) {
            case DISPLAY_ALL_APPS:
                return grid.allAppsIconSizePx;
            case DISPLAY_WORKSPACE:
            case DISPLAY_FOLDER:
            default:
                return grid.iconSizePx;
        }
    }

    public void getSourceVisualDragBounds(Rect bounds) {
        BubbleTextView.getIconBounds(this, bounds, getIconSizeForDisplay(mDisplay));
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        resetIconScale();
        setForceHideDot(true);
        return () -> { };
    }

    private void resetIconScale() {
        if (mIcon instanceof FastBitmapDrawable) {
            ((FastBitmapDrawable) mIcon).setScale(1f);
        }
    }
}
