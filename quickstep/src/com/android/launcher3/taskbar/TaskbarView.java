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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.uioverrides.ApiWrapper;
import com.android.launcher3.views.ActivityContext;

/**
 * Hosts the Taskbar content such as Hotseat and Recent Apps. Drawn on top of other apps.
 */
public class TaskbarView extends FrameLayout implements FolderIcon.FolderIconParent, Insettable {

    private final int[] mTempOutLocation = new int[2];

    private final Rect mIconLayoutBounds = new Rect();
    private final int mIconTouchSize;
    private final int mItemMarginLeftRight;
    private final int mItemPadding;

    private final TaskbarActivityContext mActivityContext;

    // Initialized in init.
    private TaskbarViewController.TaskbarViewCallbacks mControllerCallbacks;
    private View.OnClickListener mIconClickListener;
    private View.OnLongClickListener mIconLongClickListener;

    // Prevents dispatching touches to children if true
    private boolean mTouchEnabled = true;

    // Only non-null when the corresponding Folder is open.
    private @Nullable FolderIcon mLeaveBehindFolderIcon;

    public TaskbarView(@NonNull Context context) {
        this(context, null);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mActivityContext = ActivityContext.lookupContext(context);

        Resources resources = getResources();
        mIconTouchSize = resources.getDimensionPixelSize(R.dimen.taskbar_icon_touch_size);

        int actualMargin = resources.getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
        int actualIconSize = mActivityContext.getDeviceProfile().iconSizePx;

        // We layout the icons to be of mIconTouchSize in width and height
        mItemMarginLeftRight = actualMargin - (mIconTouchSize - actualIconSize) / 2;
        mItemPadding = (mIconTouchSize - actualIconSize) / 2;

        // Needed to draw folder leave-behind when opening one.
        setWillNotDraw(false);
    }

    protected void init(TaskbarViewController.TaskbarViewCallbacks callbacks) {
        mControllerCallbacks = callbacks;
        mIconClickListener = mControllerCallbacks.getIconOnClickListener();
        mIconLongClickListener = mControllerCallbacks.getIconOnLongClickListener();

        setOnLongClickListener(mControllerCallbacks.getBackgroundOnLongClickListener());
    }

    private void removeAndRecycle(View view) {
        removeView(view);
        view.setOnClickListener(null);
        view.setOnLongClickListener(null);
        if (!(view.getTag() instanceof FolderInfo)) {
            mActivityContext.getViewCache().recycleView(view.getSourceLayoutResId(), view);
        }
        view.setTag(null);
    }

    /**
     * Inflates/binds the Hotseat views to show in the Taskbar given their ItemInfos.
     */
    protected void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
        int nextViewIndex = 0;
        int numViewsAnimated = 0;

        for (int i = 0; i < hotseatItemInfos.length; i++) {
            ItemInfo hotseatItemInfo = hotseatItemInfos[i];
            if (hotseatItemInfo == null) {
                continue;
            }

            // Replace any Hotseat views with the appropriate type if it's not already that type.
            final int expectedLayoutResId;
            boolean isFolder = false;
            if (hotseatItemInfo.isPredictedItem()) {
                expectedLayoutResId = R.layout.taskbar_predicted_app_icon;
            } else if (hotseatItemInfo instanceof FolderInfo) {
                expectedLayoutResId = R.layout.folder_icon;
                isFolder = true;
            } else {
                expectedLayoutResId = R.layout.taskbar_app_icon;
            }

            View hotseatView = null;
            while (nextViewIndex < getChildCount()) {
                hotseatView = getChildAt(nextViewIndex);

                // see if the view can be reused
                if ((hotseatView.getSourceLayoutResId() != expectedLayoutResId)
                        || (isFolder && (hotseatView.getTag() != hotseatItemInfo))) {
                    // Unlike for BubbleTextView, we can't reapply a new FolderInfo after inflation,
                    // so if the info changes we need to reinflate. This should only happen if a new
                    // folder is dragged to the position that another folder previously existed.
                    removeAndRecycle(hotseatView);
                    hotseatView = null;
                } else {
                    // View found
                    break;
                }
            }

            if (hotseatView == null) {
                if (isFolder) {
                    FolderInfo folderInfo = (FolderInfo) hotseatItemInfo;
                    FolderIcon folderIcon = FolderIcon.inflateFolderAndIcon(expectedLayoutResId,
                            mActivityContext, this, folderInfo);
                    folderIcon.setTextVisible(false);
                    hotseatView = folderIcon;
                } else {
                    hotseatView = inflate(expectedLayoutResId);
                }
                LayoutParams lp = new LayoutParams(mIconTouchSize, mIconTouchSize);
                hotseatView.setPadding(mItemPadding, mItemPadding, mItemPadding, mItemPadding);
                addView(hotseatView, nextViewIndex, lp);
            }

            // Apply the Hotseat ItemInfos, or hide the view if there is none for a given index.
            if (hotseatView instanceof BubbleTextView
                    && hotseatItemInfo instanceof WorkspaceItemInfo) {
                BubbleTextView btv = (BubbleTextView) hotseatView;
                WorkspaceItemInfo workspaceInfo = (WorkspaceItemInfo) hotseatItemInfo;

                boolean animate = btv.shouldAnimateIconChange((WorkspaceItemInfo) hotseatItemInfo);
                btv.applyFromWorkspaceItem(workspaceInfo, animate, numViewsAnimated);
                if (animate) {
                    numViewsAnimated++;
                }
            }
            setClickAndLongClickListenersForIcon(hotseatView);
            nextViewIndex++;
        }
        // Remove remaining views
        while (nextViewIndex < getChildCount()) {
            removeAndRecycle(getChildAt(nextViewIndex));
        }
    }

    /**
     * Sets OnClickListener and OnLongClickListener for the given view.
     */
    public void setClickAndLongClickListenersForIcon(View icon) {
        icon.setOnClickListener(mIconClickListener);
        icon.setOnLongClickListener(mIconLongClickListener);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int count = getChildCount();
        int spaceNeeded = count * (mItemMarginLeftRight * 2 + mIconTouchSize);
        int navSpaceNeeded = ApiWrapper.getHotseatEndOffset(getContext());
        boolean layoutRtl = isLayoutRtl();
        int iconEnd = right - (right - left - spaceNeeded) / 2;
        boolean needMoreSpaceForNav = layoutRtl ?
                navSpaceNeeded > (iconEnd - spaceNeeded) :
                iconEnd > (right - navSpaceNeeded);
        if (needMoreSpaceForNav) {
            int offset = layoutRtl ?
                    navSpaceNeeded - (iconEnd - spaceNeeded) :
                    (right - navSpaceNeeded) - iconEnd;
            iconEnd += offset;
        }
        // Layout the children
        mIconLayoutBounds.right = iconEnd;
        mIconLayoutBounds.top = (bottom - top - mIconTouchSize) / 2;
        mIconLayoutBounds.bottom = mIconLayoutBounds.top + mIconTouchSize;
        for (int i = count; i > 0; i--) {
            View child = getChildAt(i - 1);
            iconEnd -= mItemMarginLeftRight;
            int iconStart = iconEnd - mIconTouchSize;
            child.layout(iconStart, mIconLayoutBounds.top, iconEnd, mIconLayoutBounds.bottom);
            iconEnd = iconStart - mItemMarginLeftRight;
        }
        mIconLayoutBounds.left = iconEnd;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!mTouchEnabled) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mTouchEnabled) {
            return true;
        }
        if (mIconLayoutBounds.contains((int) event.getX(), (int) event.getY())) {
            // Don't allow long pressing between icons.
            return true;
        }
        if (mControllerCallbacks.onTouchEvent(event)) {
            int oldAction = event.getAction();
            try {
                event.setAction(MotionEvent.ACTION_CANCEL);
                return super.onTouchEvent(event);
            } finally {
                event.setAction(oldAction);
            }
        }
        return super.onTouchEvent(event);
    }

    public void setTouchesEnabled(boolean touchEnabled) {
        this.mTouchEnabled = touchEnabled;
    }

    /**
     * Returns whether the given MotionEvent, *in screen coorindates*, is within any Taskbar item's
     * touch bounds.
     */
    public boolean isEventOverAnyItem(MotionEvent ev) {
        getLocationOnScreen(mTempOutLocation);
        int xInOurCoordinates = (int) ev.getX() - mTempOutLocation[0];
        int yInOurCoorindates = (int) ev.getY() - mTempOutLocation[1];
        return isShown() && mIconLayoutBounds.contains(xInOurCoordinates, yInOurCoorindates);
    }

    public Rect getIconLayoutBounds() {
        return mIconLayoutBounds;
    }

    /**
     * Returns the app icons currently shown in the taskbar.
     */
    public View[] getIconViews() {
        final int count = getChildCount();
        View[] icons = new View[count];
        for (int i = 0; i < count; i++) {
            icons[i] = getChildAt(i);
        }
        return icons;
    }

    // FolderIconParent implemented methods.

    @Override
    public void drawFolderLeaveBehindForIcon(FolderIcon child) {
        mLeaveBehindFolderIcon = child;
        invalidate();
    }

    @Override
    public void clearFolderLeaveBehind(FolderIcon child) {
        mLeaveBehindFolderIcon = null;
        invalidate();
    }

    // End FolderIconParent implemented methods.

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLeaveBehindFolderIcon != null) {
            canvas.save();
            canvas.translate(mLeaveBehindFolderIcon.getLeft(), mLeaveBehindFolderIcon.getTop());
            mLeaveBehindFolderIcon.getFolderBackground().drawLeaveBehind(canvas);
            canvas.restore();
        }
    }

    private View inflate(@LayoutRes int layoutResId) {
        return mActivityContext.getViewCache().getView(layoutResId, mActivityContext, this);
    }

    @Override
    public void setInsets(Rect insets) {
        // Ignore, we just implement Insettable to draw behind system insets.
    }

    public boolean areIconsVisible() {
        // Consider the overall visibility
        return getVisibility() == VISIBLE;
    }
}
