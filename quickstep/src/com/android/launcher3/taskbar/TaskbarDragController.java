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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.window.SurfaceSyncGroup;

import androidx.annotation.Nullable;

import com.android.internal.logging.InstanceId;
import com.android.internal.logging.InstanceIdSequence;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragDriver;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.systemui.shared.recents.model.Task;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Predicate;

/**
 * Handles long click on Taskbar items to start a system drag and drop operation.
 */
public class TaskbarDragController extends DragController<BaseTaskbarContext> implements
        TaskbarControllers.LoggableTaskbarController {

    private static boolean DEBUG_DRAG_SHADOW_SURFACE = false;

    private final int mDragIconSize;
    private final int[] mTempXY = new int[2];

    // Initialized in init.
    TaskbarControllers mControllers;

    // Where the initial touch was relative to the dragged icon.
    private int mRegistrationX;
    private int mRegistrationY;

    private boolean mIsSystemDragInProgress;

    // Animation for the drag shadow back into position after an unsuccessful drag
    private ValueAnimator mReturnAnimator;

    public TaskbarDragController(BaseTaskbarContext activity) {
        super(activity);
        Resources resources = mActivity.getResources();
        mDragIconSize = resources.getDimensionPixelSize(R.dimen.taskbar_icon_drag_icon_size);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    /**
     * Attempts to start a system drag and drop operation for the given View, using its tag to
     * generate the ClipDescription and Intent.
     * @return Whether {@link View#startDragAndDrop} started successfully.
     */
    public boolean startDragOnLongClick(View view) {
        return startDragOnLongClick(view, null, null);
    }

    protected boolean startDragOnLongClick(
            DeepShortcutView shortcutView, Point iconShift) {
        return startDragOnLongClick(
                shortcutView.getBubbleText(),
                new ShortcutDragPreviewProvider(shortcutView.getIconView(), iconShift),
                iconShift);
    }

    private boolean startDragOnLongClick(
            View view,
            @Nullable DragPreviewProvider dragPreviewProvider,
            @Nullable Point iconShift) {
        if (!(view instanceof BubbleTextView)) {
            return false;
        }
        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onTaskbarItemLongClick");
        BubbleTextView btv = (BubbleTextView) view;
        mActivity.onDragStart();
        btv.post(() -> {
            DragView dragView = startInternalDrag(btv, dragPreviewProvider);
            if (iconShift != null) {
                dragView.animateShift(-iconShift.x, -iconShift.y);
            }
            btv.getIcon().setIsDisabled(true);
            mControllers.taskbarAutohideSuspendController.updateFlag(
                    TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_DRAGGING, true);
        });
        return true;
    }

    private DragView startInternalDrag(
            BubbleTextView btv, @Nullable DragPreviewProvider dragPreviewProvider) {
        float iconScale = btv.getIcon().getAnimatedScale();

        // Clear the pressed state if necessary
        btv.clearFocus();
        btv.setPressed(false);
        btv.clearPressedBackground();

        final DragPreviewProvider previewProvider = dragPreviewProvider == null
                ? new DragPreviewProvider(btv) : dragPreviewProvider;
        final Drawable drawable = previewProvider.createDrawable();
        final float scale = previewProvider.getScaleAndPosition(drawable, mTempXY);
        int dragLayerX = mTempXY[0];
        int dragLayerY = mTempXY[1];

        Rect dragRect = new Rect();
        btv.getSourceVisualDragBounds(dragRect);
        dragLayerY += dragRect.top;

        DragOptions dragOptions = new DragOptions();
        dragOptions.preDragCondition = null;
        if (FeatureFlags.ENABLE_TASKBAR_POPUP_MENU.get()) {
            PopupContainerWithArrow<BaseTaskbarContext> popupContainer =
                    mControllers.taskbarPopupController.showForIcon(btv);
            if (popupContainer != null) {
                dragOptions.preDragCondition = popupContainer.createPreDragCondition(false);
            }
        }
        if (dragOptions.preDragCondition == null) {
            dragOptions.preDragCondition = new DragOptions.PreDragCondition() {
                private DragView mDragView;

                @Override
                public boolean shouldStartDrag(double distanceDragged) {
                    return mDragView != null && mDragView.isAnimationFinished();
                }

                @Override
                public void onPreDragStart(DropTarget.DragObject dragObject) {
                    mDragView = dragObject.dragView;

                    if (FeatureFlags.ENABLE_TASKBAR_POPUP_MENU.get()
                            && !shouldStartDrag(0)) {
                        // Immediately close the popup menu.
                        mDragView.setOnAnimationEndCallback(() -> callOnDragStart());
                    }
                }

                @Override
                public void onPreDragEnd(DropTarget.DragObject dragObject, boolean dragStarted) {
                    mDragView = null;
                }
            };
        }

        return startDrag(
                drawable,
                /* view = */ null,
                /* originalView = */ btv,
                dragLayerX,
                dragLayerY,
                (View target, DropTarget.DragObject d, boolean success) -> {} /* DragSource */,
                (ItemInfo) btv.getTag(),
                /* dragVisualizeOffset = */ null,
                dragRect,
                scale * iconScale,
                scale,
                dragOptions);
    }

    @Override
    protected DragView startDrag(@Nullable Drawable drawable, @Nullable View view,
            DraggableView originalView, int dragLayerX, int dragLayerY, DragSource source,
            ItemInfo dragInfo, Point dragOffset, Rect dragRegion, float initialDragViewScale,
            float dragViewScaleOnDrop, DragOptions options) {
        mOptions = options;

        mRegistrationX = mMotionDown.x - dragLayerX;
        mRegistrationY = mMotionDown.y - dragLayerY;

        final int dragRegionLeft = dragRegion == null ? 0 : dragRegion.left;
        final int dragRegionTop = dragRegion == null ? 0 : dragRegion.top;

        mLastDropTarget = null;

        mDragObject = new DropTarget.DragObject(mActivity.getApplicationContext());
        mDragObject.originalView = originalView;
        mDragObject.deferDragViewCleanupPostAnimation = false;

        mIsInPreDrag = mOptions.preDragCondition != null
                && !mOptions.preDragCondition.shouldStartDrag(0);

        float scalePx = mDragIconSize - dragRegion.width();
        final DragView dragView = mDragObject.dragView = new TaskbarDragView(
                mActivity,
                drawable,
                mRegistrationX,
                mRegistrationY,
                initialDragViewScale,
                dragViewScaleOnDrop,
                scalePx);
        dragView.setItemInfo(dragInfo);
        mDragObject.dragComplete = false;

        mDragObject.xOffset = mMotionDown.x - (dragLayerX + dragRegionLeft);
        mDragObject.yOffset = mMotionDown.y - (dragLayerY + dragRegionTop);

        mDragDriver = DragDriver.create(this, mOptions, /* secondaryEventConsumer = */ ev -> {});
        if (!mOptions.isAccessibleDrag) {
            mDragObject.stateAnnouncer = DragViewStateAnnouncer.createFor(dragView);
        }

        mDragObject.dragSource = source;
        mDragObject.dragInfo = dragInfo;
        mDragObject.originalDragInfo = mDragObject.dragInfo.makeShallowCopy();

        if (dragRegion != null) {
            dragView.setDragRegion(new Rect(dragRegion));
        }

        dragView.show(mLastTouch.x, mLastTouch.y);
        mDistanceSinceScroll = 0;

        if (!mIsInPreDrag) {
            callOnDragStart();
        } else if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragStart(mDragObject);
        }

        handleMoveEvent(mLastTouch.x, mLastTouch.y);

        return dragView;
    }

    @Override
    protected void callOnDragStart() {
        super.callOnDragStart();
        // Pre-drag has ended, start the global system drag.
        AbstractFloatingView.closeAllOpenViews(mActivity);
        startSystemDrag((BubbleTextView) mDragObject.originalView);
    }

    private void startSystemDrag(BubbleTextView btv) {
        View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(btv) {

            @Override
            public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
                int iconSize = Math.max(mDragIconSize, btv.getWidth());
                shadowSize.set(iconSize, iconSize);
                // The registration point was taken before the icon scaled to mDragIconSize, so
                // offset the registration to where the touch is on the new size.
                int offsetX = (mDragIconSize - mDragObject.dragView.getDragRegionWidth()) / 2;
                int offsetY = (mDragIconSize - mDragObject.dragView.getDragRegionHeight()) / 2;
                shadowTouchPoint.set(mRegistrationX + offsetX, mRegistrationY + offsetY);
            }

            @Override
            public void onDrawShadow(Canvas canvas) {
                canvas.save();
                if (DEBUG_DRAG_SHADOW_SURFACE) {
                    canvas.drawColor(0xffff0000);
                }
                float scale = mDragObject.dragView.getScaleX();
                canvas.scale(scale, scale);
                mDragObject.dragView.draw(canvas);
                canvas.restore();
            }
        };

        Object tag = btv.getTag();
        ClipDescription clipDescription = null;
        Intent intent = null;
        if (tag instanceof ItemInfo) {
            ItemInfo item = (ItemInfo) tag;
            LauncherApps launcherApps = mActivity.getSystemService(LauncherApps.class);
            clipDescription = new ClipDescription(item.title,
                    new String[] {
                            item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                                    ? ClipDescription.MIMETYPE_APPLICATION_SHORTCUT
                                    : ClipDescription.MIMETYPE_APPLICATION_ACTIVITY
                    });
            intent = new Intent();
            if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                String deepShortcutId = ((WorkspaceItemInfo) item).getDeepShortcutId();
                intent.putExtra(ClipDescription.EXTRA_PENDING_INTENT,
                        launcherApps.getShortcutIntent(
                                item.getIntent().getPackage(),
                                deepShortcutId,
                                null,
                                item.user));
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, item.getIntent().getPackage());
                intent.putExtra(Intent.EXTRA_SHORTCUT_ID, deepShortcutId);
            } else {
                intent.putExtra(ClipDescription.EXTRA_PENDING_INTENT,
                        launcherApps.getMainActivityLaunchIntent(item.getIntent().getComponent(),
                                null, item.user));
            }
            intent.putExtra(Intent.EXTRA_USER, item.user);
        } else if (tag instanceof Task) {
            Task task = (Task) tag;
            clipDescription = new ClipDescription(task.titleDescription,
                    new String[] {
                            ClipDescription.MIMETYPE_APPLICATION_TASK
                    });
            intent = new Intent();
            intent.putExtra(Intent.EXTRA_TASK_ID, task.key.id);
            intent.putExtra(Intent.EXTRA_USER, UserHandle.of(task.key.userId));
        }

        if (clipDescription != null && intent != null) {
            // Need to share the same InstanceId between launcher3 and WM Shell (internal).
            InstanceId internalInstanceId = new InstanceIdSequence(
                    com.android.launcher3.logging.InstanceId.INSTANCE_ID_MAX).newInstanceId();
            com.android.launcher3.logging.InstanceId launcherInstanceId =
                    new com.android.launcher3.logging.InstanceId(internalInstanceId.getId());

            intent.putExtra(ClipDescription.EXTRA_LOGGING_INSTANCE_ID, internalInstanceId);

            ClipData clipData = new ClipData(clipDescription, new ClipData.Item(intent));
            if (btv.startDragAndDrop(clipData, shadowBuilder, null /* localState */,
                    View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_OPAQUE
                            | View.DRAG_FLAG_REQUEST_SURFACE_FOR_RETURN_ANIMATION)) {
                onSystemDragStarted(btv);

                mActivity.getStatsLogManager().logger().withItemInfo(mDragObject.dragInfo)
                        .withInstanceId(launcherInstanceId)
                        .log(StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DRAG_STARTED);
            }
        }
    }

    private void onSystemDragStarted(BubbleTextView btv) {
        mIsSystemDragInProgress = true;
        mActivity.getDragLayer().setOnDragListener((view, dragEvent) -> {
            switch (dragEvent.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // Return true to tell system we are interested in events, so we get DRAG_ENDED.
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    mIsSystemDragInProgress = false;
                    if (dragEvent.getResult()) {
                        maybeOnDragEnd();
                    } else {
                        // This will take care of calling maybeOnDragEnd() after the animation
                        animateGlobalDragViewToOriginalPosition(btv, dragEvent);
                    }
                    return true;
            }
            return false;
        });
    }

    @Override
    public boolean isDragging() {
        return super.isDragging() || mIsSystemDragInProgress;
    }

    /** {@code true} if the system is currently handling the drag. */
    public boolean isSystemDragInProgress() {
        return mIsSystemDragInProgress;
    }

    private void maybeOnDragEnd() {
        if (!isDragging()) {
            ((BubbleTextView) mDragObject.originalView).getIcon().setIsDisabled(false);
            mControllers.taskbarAutohideSuspendController.updateFlag(
                    TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_DRAGGING, false);
            mActivity.onDragEnd();
        }
    }

    @Override
    protected void callOnDragEnd() {
        super.callOnDragEnd();
        maybeOnDragEnd();
    }

    private void animateGlobalDragViewToOriginalPosition(BubbleTextView btv,
            DragEvent dragEvent) {
        SurfaceControl dragSurface = dragEvent.getDragSurface();

        // For top level icons, the target is the icon itself
        View target = btv;
        Object tag = btv.getTag();
        if (tag instanceof ItemInfo) {
            ItemInfo item = (ItemInfo) tag;
            TaskbarViewController taskbarViewController = mControllers.taskbarViewController;
            if (item.container == CONTAINER_ALL_APPS || item.container == CONTAINER_PREDICTION) {
                // Since all apps closes when the drag starts, target the all apps button instead.
                target = taskbarViewController.getAllAppsButtonView();
            } else if (item.container >= 0) {
                // Since folders close when the drag starts, target the folder icon instead.
                Predicate<ItemInfo> matcher = ItemInfoMatcher.forFolderMatch(
                        ItemInfoMatcher.ofItemIds(IntSet.wrap(item.id)));
                target = taskbarViewController.getFirstIconMatch(matcher);
            } else if (item.itemType == ITEM_TYPE_DEEP_SHORTCUT) {
                // Find first icon with same package/user as the deep shortcut.
                Predicate<ItemInfo> packageUserMatcher = ItemInfoMatcher.ofPackages(
                        Collections.singleton(item.getTargetPackage()), item.user);
                target = taskbarViewController.getFirstIconMatch(packageUserMatcher);
            }
        }

        // Finish any pending return animation before starting a new drag
        if (mReturnAnimator != null) {
            mReturnAnimator.end();
        }

        float fromX = dragEvent.getX() - dragEvent.getOffsetX();
        float fromY = dragEvent.getY() - dragEvent.getOffsetY();
        int[] toPosition = target.getLocationOnScreen();
        float toScale = (float) target.getWidth() / mDragIconSize;
        float toAlpha = (target == btv) ? 1f : 0f;
        final ViewRootImpl viewRoot = target.getViewRootImpl();
        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mReturnAnimator = ValueAnimator.ofFloat(0f, 1f);
        mReturnAnimator.setDuration(300);
        mReturnAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mReturnAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = animation.getAnimatedFraction();
                float accelT = Interpolators.ACCEL_2.getInterpolation(t);
                float scale = 1f - t * (1f - toScale);
                float alpha = 1f - accelT * (1f - toAlpha);
                tx.setPosition(dragSurface, Utilities.mapRange(t, fromX, toPosition[0]),
                        Utilities.mapRange(t, fromY, toPosition[1]));
                tx.setScale(dragSurface, scale, scale);
                tx.setAlpha(dragSurface, alpha);
                tx.apply();
            }
        });
        mReturnAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCanceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cleanUpSurface();
                mCanceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCanceled) {
                    return;
                }
                cleanUpSurface();
            }

            private void cleanUpSurface() {
                tx.close();
                maybeOnDragEnd();
                // Synchronize removing the drag surface with the next draw after calling
                // maybeOnDragEnd()
                SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
                transaction.remove(dragSurface);
                SurfaceSyncGroup syncGroup = new SurfaceSyncGroup();
                syncGroup.addSyncCompleteCallback(mActivity.getMainExecutor(), transaction::close);
                syncGroup.addToSync(viewRoot);
                syncGroup.addTransactionToSync(transaction);
                syncGroup.markSyncReady();
                mReturnAnimator = null;
            }
        });
        mReturnAnimator.start();
    }

    @Override
    protected float getX(MotionEvent ev) {
        // We will resize to fill the screen while dragging, so use screen coordinates. This ensures
        // we start at the correct position even though touch down is on the smaller DragLayer size.
        return ev.getRawX();
    }

    @Override
    protected float getY(MotionEvent ev) {
        // We will resize to fill the screen while dragging, so use screen coordinates. This ensures
        // we start at the correct position even though touch down is on the smaller DragLayer size.
        return ev.getRawY();
    }

    @Override
    protected Point getClampedDragLayerPos(float x, float y) {
        // No need to clamp, as we will take up the entire screen.
        mTmpPoint.set(Math.round(x), Math.round(y));
        return mTmpPoint;
    }

    @Override
    protected void exitDrag() {
        if (mDragObject != null) {
            mActivity.getDragLayer().removeView(mDragObject.dragView);
        }
    }

    @Override
    public void addDropTarget(DropTarget target) {
        // No-op as Taskbar currently doesn't support any drop targets internally.
        // Note: if we do add internal DropTargets, we'll still need to ignore Folder.
    }

    @Override
    protected DropTarget getDefaultDropTarget(int[] dropCoordinates) {
        return null;
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarDragController:");

        pw.println(prefix + "\tmDragIconSize=" + mDragIconSize);
        pw.println(prefix + "\tmTempXY=" + Arrays.toString(mTempXY));
        pw.println(prefix + "\tmRegistrationX=" + mRegistrationX);
        pw.println(prefix + "\tmRegistrationY=" + mRegistrationY);
        pw.println(prefix + "\tmIsSystemDragInProgress=" + mIsSystemDragInProgress);
        pw.println(prefix + "\tisInternalDragInProgess=" + super.isDragging());
    }
}