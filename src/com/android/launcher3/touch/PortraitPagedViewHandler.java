/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.touch;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_MAIN;

import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.util.FloatProperty;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import com.android.launcher3.util.SplitConfigurationOptions.StagedSplitBounds;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.List;

public class PortraitPagedViewHandler implements PagedOrientationHandler {

    private final Matrix mTmpMatrix = new Matrix();
    private final RectF mTmpRectF = new RectF();

    @Override
    public <T> T getPrimaryValue(T x, T y) {
        return x;
    }

    @Override
    public <T> T getSecondaryValue(T x, T y) {
        return y;
    }

    @Override
    public int getPrimaryValue(int x, int y) {
        return x;
    }

    @Override
    public int getSecondaryValue(int x, int y) {
        return y;
    }

    @Override
    public float getPrimaryValue(float x, float y) {
        return x;
    }

    @Override
    public float getSecondaryValue(float x, float y) {
        return y;
    }

    @Override
    public boolean isLayoutNaturalToLauncher() {
        return true;
    }

    @Override
    public void adjustFloatingIconStartVelocity(PointF velocity) {
        //no-op
    }

    @Override
    public <T> void setPrimary(T target, Int2DAction<T> action, int param) {
        action.call(target, param, 0);
    }

    @Override
    public <T> void setPrimary(T target, Float2DAction<T> action, float param) {
        action.call(target, param, 0);
    }

    @Override
    public <T> void setSecondary(T target, Float2DAction<T> action, float param) {
        action.call(target, 0, param);
    }

    @Override
    public <T> void set(T target, Int2DAction<T> action, int primaryParam,
            int secondaryParam) {
        action.call(target, primaryParam, secondaryParam);
    }

    @Override
    public float getPrimaryDirection(MotionEvent event, int pointerIndex) {
        return event.getX(pointerIndex);
    }

    @Override
    public float getPrimaryVelocity(VelocityTracker velocityTracker, int pointerId) {
        return velocityTracker.getXVelocity(pointerId);
    }

    @Override
    public int getMeasuredSize(View view) {
        return view.getMeasuredWidth();
    }

    @Override
    public int getPrimarySize(View view) {
        return view.getWidth();
    }

    @Override
    public float getPrimarySize(RectF rect) {
        return rect.width();
    }

    @Override
    public float getStart(RectF rect) {
        return rect.left;
    }

    @Override
    public float getEnd(RectF rect) {
        return rect.right;
    }

    @Override
    public int getClearAllSidePadding(View view, boolean isRtl) {
        return (isRtl ? view.getPaddingRight() : - view.getPaddingLeft()) / 2;
    }

    @Override
    public int getSecondaryDimension(View view) {
        return view.getHeight();
    }

    @Override
    public FloatProperty<View> getPrimaryViewTranslate() {
        return VIEW_TRANSLATE_X;
    }

    @Override
    public FloatProperty<View> getSecondaryViewTranslate() {
        return VIEW_TRANSLATE_Y;
    }

    @Override
    public int getSplitTaskViewDismissDirection(@StagePosition int stagePosition,
            DeviceProfile dp) {
        if (stagePosition == STAGE_POSITION_TOP_OR_LEFT) {
            if (dp.isLandscape) {
                // Left side
                return SPLIT_TRANSLATE_PRIMARY_NEGATIVE;
            } else {
                // Top side
                return SPLIT_TRANSLATE_SECONDARY_NEGATIVE;
            }
        } else if (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            // We don't have a bottom option, so should be right
            return SPLIT_TRANSLATE_PRIMARY_POSITIVE;
        }
        throw new IllegalStateException("Invalid split stage position: " + stagePosition);
    }

    @Override
    public int getPrimaryScroll(View view) {
        return view.getScrollX();
    }

    @Override
    public float getPrimaryScale(View view) {
        return view.getScaleX();
    }

    @Override
    public void setMaxScroll(AccessibilityEvent event, int maxScroll) {
        event.setMaxScrollX(maxScroll);
    }

    @Override
    public boolean getRecentsRtlSetting(Resources resources) {
        return !Utilities.isRtl(resources);
    }

    @Override
    public float getDegreesRotated() {
        return 0;
    }

    @Override
    public int getRotation() {
        return Surface.ROTATION_0;
    }

    @Override
    public void setPrimaryScale(View view, float scale) {
        view.setScaleX(scale);
    }

    @Override
    public void setSecondaryScale(View view, float scale) {
        view.setScaleY(scale);
    }

    @Override
    public int getChildStart(View view) {
        return view.getLeft();
    }

    @Override
    public int getCenterForPage(View view, Rect insets) {
        return (view.getPaddingTop() + view.getMeasuredHeight() + insets.top
            - insets.bottom - view.getPaddingBottom()) / 2;
    }

    @Override
    public int getScrollOffsetStart(View view, Rect insets) {
        return insets.left + view.getPaddingLeft();
    }

    @Override
    public int getScrollOffsetEnd(View view, Rect insets) {
        return view.getWidth() - view.getPaddingRight() - insets.right;
    }

    public int getSecondaryTranslationDirectionFactor() {
        return -1;
    }

    @Override
    public int getSplitTranslationDirectionFactor(int stagePosition, DeviceProfile deviceProfile) {
        if (deviceProfile.isLandscape && stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView, int overScroll,
            DeviceProfile deviceProfile) {
        if (deviceProfile.isLandscape) {
            return x + overScroll
                    + (thumbnailView.getMeasuredWidth() - thumbnailView.getMeasuredHeight()) / 2f;
        } else {
            return x + overScroll;
        }
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView, int overScroll) {
        return y;
    }

    @Override
    public int getTaskMenuWidth(View view, DeviceProfile deviceProfile) {
        return deviceProfile.isLandscape && !deviceProfile.overviewShowAsGrid ?
                view.getMeasuredHeight() :
                view.getMeasuredWidth();
    }

    @Override
    public void setTaskOptionsMenuLayoutOrientation(DeviceProfile deviceProfile,
            LinearLayout taskMenuLayout, int dividerSpacing,
            ShapeDrawable dividerDrawable) {
        taskMenuLayout.setOrientation(LinearLayout.VERTICAL);
        dividerDrawable.setIntrinsicHeight(dividerSpacing);
        taskMenuLayout.setDividerDrawable(dividerDrawable);
    }

    @Override
    public void setLayoutParamsForTaskMenuOptionItem(LinearLayout.LayoutParams lp,
            LinearLayout viewGroup, DeviceProfile deviceProfile) {
        viewGroup.setOrientation(LinearLayout.HORIZONTAL);
        lp.width = LinearLayout.LayoutParams.MATCH_PARENT;
        lp.height = WRAP_CONTENT;
    }

    @Override
    public void setTaskMenuAroundTaskView(LinearLayout taskView, float margin) {
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) taskView.getLayoutParams();
        lp.topMargin += margin;
        lp.leftMargin += margin;
    }

    @Override
    public PointF getAdditionalInsetForTaskMenu(float margin) {
        return new PointF(0, 0);
    }

    @Override
    public Pair<Float, Float> setDwbLayoutParamsAndGetTranslations(int taskViewWidth,
            int taskViewHeight, StagedSplitBounds splitBounds, DeviceProfile deviceProfile,
            View[] thumbnailViews, int desiredTaskId, View banner) {
        float translationX = 0;
        float translationY = 0;
        FrameLayout.LayoutParams bannerParams = (FrameLayout.LayoutParams) banner.getLayoutParams();
        banner.setPivotX(0);
        banner.setPivotY(0);
        banner.setRotation(getDegreesRotated());
        if (splitBounds == null) {
            // Single, fullscreen case
            bannerParams.width = MATCH_PARENT;
            bannerParams.gravity = BOTTOM | CENTER_HORIZONTAL;
            return new Pair<>(translationX, translationY);
        }

        bannerParams.gravity = BOTTOM | ((deviceProfile.isLandscape) ? START : CENTER_HORIZONTAL);

        // Set correct width
        if (desiredTaskId == splitBounds.leftTopTaskId) {
            bannerParams.width = thumbnailViews[0].getMeasuredWidth();
        } else {
            bannerParams.width = thumbnailViews[1].getMeasuredWidth();
        }

        // Set translations
        if (deviceProfile.isLandscape) {
            if (desiredTaskId == splitBounds.rightBottomTaskId) {
                translationX = ((taskViewWidth * splitBounds.leftTaskPercent)
                                + (taskViewWidth * splitBounds.dividerWidthPercent));
            }
        } else {
            if (desiredTaskId == splitBounds.leftTopTaskId) {
                FrameLayout.LayoutParams snapshotParams =
                        (FrameLayout.LayoutParams) thumbnailViews[0]
                                .getLayoutParams();
                translationY = -((taskViewHeight - snapshotParams.topMargin)
                        * (1f - splitBounds.topTaskPercent));
            }
        }
        return new Pair<>(translationX, translationY);
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */

    @Override
    public SingleAxisSwipeDetector.Direction getUpDownSwipeDirection() {
        return VERTICAL;
    }

    @Override
    public int getUpDirection(boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return SingleAxisSwipeDetector.DIRECTION_POSITIVE;
    }

    @Override
    public boolean isGoingUp(float displacement, boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return displacement < 0;
    }

    @Override
    public int getTaskDragDisplacementFactor(boolean isRtl) {
        // Ignore rtl since it only affects X value displacement, Y displacement doesn't change
        return 1;
    }

    /* -------------------- */

    @Override
    public ChildBounds getChildBounds(View child, int childStart, int pageCenter,
        boolean layoutChild) {
        final int childWidth = child.getMeasuredWidth();
        final int childRight = childStart + childWidth;
        final int childHeight = child.getMeasuredHeight();
        final int childTop = pageCenter - childHeight / 2;
        if (layoutChild) {
            child.layout(childStart, childTop, childRight, childTop + childHeight);
        }
        return new ChildBounds(childWidth, childHeight, childRight, childTop);
    }

    @Override
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return dp.heightPx - rect.bottom;
    }

    @Override
    public List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp) {
        List<SplitPositionOption> options = new ArrayList<>(1);
        // Add both left and right options if we're in tablet mode
        if (dp.isTablet && dp.isLandscape) {
            options.add(new SplitPositionOption(
                    R.drawable.ic_split_right, R.string.split_screen_position_right,
                    STAGE_POSITION_BOTTOM_OR_RIGHT, STAGE_TYPE_MAIN));
            options.add(new SplitPositionOption(
                    R.drawable.ic_split_left, R.string.split_screen_position_left,
                    STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
        } else {
            if (dp.isSeascape()) {
                // Add left/right options
                options.add(new SplitPositionOption(
                        R.drawable.ic_split_right, R.string.split_screen_position_right,
                        STAGE_POSITION_BOTTOM_OR_RIGHT, STAGE_TYPE_MAIN));
            } else if (dp.isLandscape) {
                options.add(new SplitPositionOption(
                        R.drawable.ic_split_left, R.string.split_screen_position_left,
                        STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
            } else {
                // Only add top option
                options.add(new SplitPositionOption(
                        R.drawable.ic_split_top, R.string.split_screen_position_top,
                        STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
            }
        }
        return options;
    }

    @Override
    public void getInitialSplitPlaceholderBounds(int placeholderHeight, DeviceProfile dp,
            @StagePosition int stagePosition, Rect out) {
        int width = dp.widthPx;
        out.set(0, 0, width, placeholderHeight);
        if (!dp.isLandscape) {
            // portrait, phone or tablet - spans width of screen, nothing else to do
            return;
        }

        // Now we rotate the portrait rect depending on what side we want pinned
        boolean pinToRight = stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT;

        int screenHeight = dp.heightPx;
        float postRotateScale = (float) screenHeight / width;
        mTmpMatrix.reset();
        mTmpMatrix.postRotate(pinToRight ? 90 : 270);
        mTmpMatrix.postTranslate(pinToRight ? width : 0, pinToRight ? 0 : width);
        // The placeholder height stays constant after rotation, so we don't change width scale
        mTmpMatrix.postScale(1, postRotateScale);

        mTmpRectF.set(out);
        mTmpMatrix.mapRect(mTmpRectF);
        mTmpRectF.roundOut(out);
    }

    @Override
    public void getFinalSplitPlaceholderBounds(int splitDividerSize, DeviceProfile dp,
            @StagePosition int stagePosition, Rect out1, Rect out2) {
        int screenHeight = dp.heightPx;
        int screenWidth = dp.widthPx;
        out1.set(0, 0, screenWidth, screenHeight / 2 - splitDividerSize);
        out2.set(0, screenHeight / 2 + splitDividerSize, screenWidth, screenHeight);
        if (!dp.isLandscape) {
            // Portrait - the window bounds are always top and bottom half
            return;
        }

        // Now we rotate the portrait rect depending on what side we want pinned
        boolean pinToRight = stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT;
        float postRotateScale = (float) screenHeight / screenWidth;

        mTmpMatrix.reset();
        mTmpMatrix.postRotate(pinToRight ? 90 : 270);
        mTmpMatrix.postTranslate(pinToRight ? screenHeight : 0, pinToRight ? 0 : screenWidth);
        mTmpMatrix.postScale(1 / postRotateScale, postRotateScale);

        mTmpRectF.set(out1);
        mTmpMatrix.mapRect(mTmpRectF);
        mTmpRectF.roundOut(out1);

        mTmpRectF.set(out2);
        mTmpMatrix.mapRect(mTmpRectF);
        mTmpRectF.roundOut(out2);
    }

    @Override
    public void setSplitTaskSwipeRect(DeviceProfile dp, Rect outRect,
            StagedSplitBounds splitInfo, int desiredStagePosition) {
        boolean isLandscape = dp.isLandscape;
        if (desiredStagePosition == SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT) {
            if (isLandscape) {
                outRect.right = outRect.left + (int) (outRect.width() * splitInfo.leftTaskPercent);
            } else {
                outRect.bottom = outRect.top + (int) (outRect.height() * splitInfo.topTaskPercent);
            }
        } else {
            if (isLandscape) {
                outRect.left += (int) (outRect.width() *
                        (splitInfo.leftTaskPercent + splitInfo.dividerWidthPercent));
            } else {
                outRect.top += (int) (outRect.height() *
                        (splitInfo.topTaskPercent + splitInfo.dividerHeightPercent));
            }
        }
    }

    @Override
    public void measureGroupedTaskViewThumbnailBounds(View primarySnapshot, View secondarySnapshot,
            int parentWidth, int parentHeight,
            StagedSplitBounds splitBoundsConfig, DeviceProfile dp) {
        int spaceAboveSnapshot = dp.overviewTaskThumbnailTopMarginPx;
        int totalThumbnailHeight = parentHeight - spaceAboveSnapshot;
        int dividerBar = splitBoundsConfig.appsStackedVertically
                ? (int) (splitBoundsConfig.dividerHeightPercent * parentHeight)
                : (int) (splitBoundsConfig.dividerWidthPercent * parentWidth);
        int primarySnapshotHeight;
        int primarySnapshotWidth;
        int secondarySnapshotHeight;
        int secondarySnapshotWidth;
        float taskPercent = splitBoundsConfig.appsStackedVertically ?
                splitBoundsConfig.topTaskPercent : splitBoundsConfig.leftTaskPercent;
        if (dp.isLandscape) {
            primarySnapshotHeight = totalThumbnailHeight;
            primarySnapshotWidth = (int) (parentWidth * taskPercent);

            secondarySnapshotHeight = totalThumbnailHeight;
            secondarySnapshotWidth = parentWidth - primarySnapshotWidth - dividerBar;
            int translationX = primarySnapshotWidth + dividerBar;
            secondarySnapshot.setTranslationX(translationX);
            secondarySnapshot.setTranslationY(spaceAboveSnapshot);
        } else {
            primarySnapshotWidth = parentWidth;
            primarySnapshotHeight = (int) (totalThumbnailHeight * taskPercent);

            secondarySnapshotWidth = parentWidth;
            secondarySnapshotHeight = totalThumbnailHeight - primarySnapshotHeight - dividerBar;
            int translationY = primarySnapshotHeight + spaceAboveSnapshot + dividerBar;
            secondarySnapshot.setTranslationY(translationY);
            secondarySnapshot.setTranslationX(0);
        }
        primarySnapshot.measure(
                View.MeasureSpec.makeMeasureSpec(primarySnapshotWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(primarySnapshotHeight, View.MeasureSpec.EXACTLY));
        secondarySnapshot.measure(
                View.MeasureSpec.makeMeasureSpec(secondarySnapshotWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(secondarySnapshotHeight,
                        View.MeasureSpec.EXACTLY));
    }

    @Override
    public void setIconAndSnapshotParams(View iconView, int taskIconMargin, int taskIconHeight,
            FrameLayout.LayoutParams snapshotParams, boolean isRtl) {
        FrameLayout.LayoutParams iconParams =
                (FrameLayout.LayoutParams) iconView.getLayoutParams();
        iconParams.gravity = TOP | CENTER_HORIZONTAL;
        iconParams.leftMargin = iconParams.rightMargin = 0;
        iconParams.topMargin = taskIconMargin;
    }

    @Override
    public void setSplitIconParams(View primaryIconView, View secondaryIconView,
            int taskIconHeight, int primarySnapshotWidth, int primarySnapshotHeight,
            boolean isRtl, DeviceProfile deviceProfile, StagedSplitBounds splitConfig) {
        FrameLayout.LayoutParams primaryIconParams =
                (FrameLayout.LayoutParams) primaryIconView.getLayoutParams();
        FrameLayout.LayoutParams secondaryIconParams =
                new FrameLayout.LayoutParams(primaryIconParams);
        int dividerBar = (splitConfig.appsStackedVertically ?
                splitConfig.visualDividerBounds.height() :
                splitConfig.visualDividerBounds.width());

        if (deviceProfile.isLandscape) {
            primaryIconParams.gravity = TOP | START;
            primaryIconView.setTranslationX(
                    primarySnapshotWidth - primaryIconView.getMeasuredWidth());
            primaryIconView.setTranslationY(0);
            secondaryIconParams.gravity = TOP | START;
            secondaryIconView.setTranslationX(primarySnapshotWidth + dividerBar);
        } else {
            primaryIconParams.gravity = TOP | CENTER_HORIZONTAL;
            primaryIconView.setTranslationX(-(primaryIconView.getMeasuredWidth()) / 2f);
            primaryIconView.setTranslationY(0);

            secondaryIconParams.gravity = TOP | CENTER_HORIZONTAL;
            secondaryIconView.setTranslationX(secondaryIconView.getMeasuredWidth() / 2f);
        }
        secondaryIconView.setTranslationY(0);
        primaryIconView.setLayoutParams(primaryIconParams);
        secondaryIconView.setLayoutParams(secondaryIconParams);
    }

    @Override
    public int getDefaultSplitPosition(DeviceProfile deviceProfile) {
        if (!deviceProfile.isTablet) {
            throw new IllegalStateException("Default position available only for large screens");
        }
        if (deviceProfile.isLandscape) {
            return STAGE_POSITION_BOTTOM_OR_RIGHT;
        } else {
            return STAGE_POSITION_TOP_OR_LEFT;
        }
    }

    @Override
    public Pair<FloatProperty, FloatProperty> getSplitSelectTaskOffset(FloatProperty primary,
            FloatProperty secondary, DeviceProfile deviceProfile) {
        if (deviceProfile.isLandscape) { // or seascape
            return new Pair<>(primary, secondary);
        } else {
            return new Pair<>(secondary, primary);
        }
    }
}
