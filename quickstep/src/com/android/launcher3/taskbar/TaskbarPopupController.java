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

import static com.android.launcher3.util.SplitConfigurationOptions.getLogEventForPosition;

import android.content.ClipDescription;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.internal.logging.InstanceId;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.popup.PopupLiveUpdateHandler;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.LogUtils;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements interfaces required to show and allow interacting with a PopupContainerWithArrow.
 */
public class TaskbarPopupController implements TaskbarControllers.LoggableTaskbarController {

    private static final SystemShortcut.Factory<BaseTaskbarContext>
            APP_INFO = SystemShortcut.AppInfo::new;

    private final TaskbarActivityContext mContext;
    private final PopupDataProvider mPopupDataProvider;

    // Initialized in init.
    private TaskbarControllers mControllers;
    private boolean mHideSplitOptions;

    public TaskbarPopupController(TaskbarActivityContext context) {
        mContext = context;
        mPopupDataProvider = new PopupDataProvider(this::updateNotificationDots);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;

        NotificationListener.addNotificationsChangedListener(mPopupDataProvider);
    }

    public void onDestroy() {
        NotificationListener.removeNotificationsChangedListener(mPopupDataProvider);
    }

    @NonNull
    public PopupDataProvider getPopupDataProvider() {
        return mPopupDataProvider;
    }

    public void setDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mPopupDataProvider.setDeepShortcutMap(deepShortcutMapCopy);
    }

    public void setHideSplitOptions(boolean hideSplitOptions) {
        mHideSplitOptions = hideSplitOptions;
    }

    private void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        final PackageUserKey packageUserKey = new PackageUserKey(null, null);
        Predicate<ItemInfo> matcher = info -> !packageUserKey.updateFromItemInfo(info)
                || updatedDots.test(packageUserKey);

        LauncherBindableItemsContainer.ItemOperator op = (info, v) -> {
            if (info instanceof WorkspaceItemInfo && v instanceof BubbleTextView) {
                if (matcher.test(info)) {
                    ((BubbleTextView) v).applyDotState(info, true /* animate */);
                }
            } else if (info instanceof FolderInfo && v instanceof FolderIcon) {
                FolderInfo fi = (FolderInfo) info;
                if (fi.contents.stream().anyMatch(matcher)) {
                    FolderDotInfo folderDotInfo = new FolderDotInfo();
                    for (WorkspaceItemInfo si : fi.contents) {
                        folderDotInfo.addDotInfo(mPopupDataProvider.getDotInfoForItem(si));
                    }
                    ((FolderIcon) v).setDotInfo(folderDotInfo);
                }
            }

            // process all the shortcuts
            return false;
        };

        mControllers.taskbarViewController.mapOverItems(op);
        Folder folder = Folder.getOpen(mContext);
        if (folder != null) {
            folder.iterateOverItems(op);
        }
    }

    /**
     * Shows the notifications and deep shortcuts associated with a Taskbar {@param icon}.
     * @return the container if shown or null.
     */
    public PopupContainerWithArrow<BaseTaskbarContext> showForIcon(BubbleTextView icon) {
        BaseTaskbarContext context = ActivityContext.lookupContext(icon.getContext());
        if (PopupContainerWithArrow.getOpen(context) != null) {
            // There is already an items container open, so don't open this one.
            icon.clearFocus();
            return null;
        }
        ItemInfo item = (ItemInfo) icon.getTag();
        if (!ShortcutUtil.supportsShortcuts(item)) {
            return null;
        }

        final PopupContainerWithArrow<BaseTaskbarContext> container =
                (PopupContainerWithArrow<BaseTaskbarContext>) context.getLayoutInflater().inflate(
                        R.layout.popup_container, context.getDragLayer(), false);
        container.addOnAttachStateChangeListener(
                new PopupLiveUpdateHandler<BaseTaskbarContext>(context, container) {
                    @Override
                    protected void showPopupContainerForIcon(BubbleTextView originalIcon) {
                        showForIcon(originalIcon);
                    }
                });
        // TODO (b/198438631): configure for taskbar/context
        container.setPopupItemDragHandler(new TaskbarPopupItemDragHandler());
        mControllers.taskbarDragController.addDragListener(container);

        container.populateAndShow(icon,
                mPopupDataProvider.getShortcutCountForItem(item),
                mPopupDataProvider.getNotificationKeysForItem(item),
                // TODO (b/198438631): add support for INSTALL shortcut factory
                getSystemShortcuts()
                        .map(s -> s.getShortcut(context, item, icon))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));
        container.requestFocus();

        // Make focusable to receive back events
        context.onPopupVisibilityChanged(true);
        container.setOnCloseCallback(() -> {
            context.getDragLayer().post(() -> context.onPopupVisibilityChanged(false));
            container.setOnCloseCallback(null);
        });

        return container;
    }

    // Create a Stream of all applicable system shortcuts
    // TODO(b/227800345): Add "Split bottom" option when tablet is in portrait mode.
    private Stream<SystemShortcut.Factory> getSystemShortcuts() {
        // concat a Stream of split options with a Stream of APP_INFO
        Stream<SystemShortcut.Factory> appInfo = Stream.of(APP_INFO);
        if (mHideSplitOptions) {
            return appInfo;
        }

        return Stream.concat(
                Utilities.getSplitPositionOptions(mContext.getDeviceProfile())
                        .stream()
                        .map(this::createSplitShortcutFactory),
                appInfo
        );
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarPopupController:");

        mPopupDataProvider.dump(prefix + "\t", pw);
    }

    private class TaskbarPopupItemDragHandler implements
            PopupContainerWithArrow.PopupItemDragHandler {

        protected final Point mIconLastTouchPos = new Point();

        TaskbarPopupItemDragHandler() {}

        @Override
        public boolean onTouch(View view, MotionEvent ev) {
            // Touched a shortcut, update where it was touched so we can drag from there on
            // long click.
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    mIconLastTouchPos.set((int) ev.getX(), (int) ev.getY());
                    break;
            }
            return false;
        }

        @Override
        public boolean onLongClick(View v) {
            // Return early if not the correct view
            if (!(v.getParent() instanceof DeepShortcutView)) return false;

            DeepShortcutView sv = (DeepShortcutView) v.getParent();
            sv.setWillDrawIcon(false);

            // Move the icon to align with the center-top of the touch point
            Point iconShift = new Point();
            iconShift.x = mIconLastTouchPos.x - sv.getIconCenter().x;
            iconShift.y = mIconLastTouchPos.y - mContext.getDeviceProfile().iconSizePx;

            ((TaskbarDragController) ActivityContext.lookupContext(
                    v.getContext()).getDragController()).startDragOnLongClick(sv, iconShift);

            return false;
        }
    }

    /**
     * Creates a factory function representing a single "split position" menu item ("Split left,"
     * "Split right," or "Split top").
     * @param position A SplitPositionOption representing whether we are splitting top, left, or
     *                 right.
     * @return A factory function to be used in populating the long-press menu.
     */
    private SystemShortcut.Factory<BaseTaskbarContext> createSplitShortcutFactory(
            SplitPositionOption position) {
        return (context, itemInfo, originalView) -> new TaskbarSplitShortcut(context, itemInfo,
                originalView, position);
    }

     /**
     * A single menu item ("Split left," "Split right," or "Split top") that executes a split
     * from the taskbar, as if the user performed a drag and drop split.
     * Includes an onClick method that initiates the actual split.
     */
    private static class TaskbarSplitShortcut extends SystemShortcut<BaseTaskbarContext> {
        private final SplitPositionOption mPosition;

        TaskbarSplitShortcut(BaseTaskbarContext context, ItemInfo itemInfo, View originalView,
                SplitPositionOption position) {
            super(position.iconResId, position.textResId, context, itemInfo, originalView);
            mPosition = position;
        }

        @Override
        public void onClick(View view) {
            // Initiate splitscreen from the in-app Taskbar or Taskbar All Apps
            Pair<InstanceId, com.android.launcher3.logging.InstanceId> instanceIds =
                    LogUtils.getShellShareableInstanceId();
            mTarget.getStatsLogManager().logger()
                    .withItemInfo(mItemInfo)
                    .withInstanceId(instanceIds.second)
                    .log(getLogEventForPosition(mPosition.stagePosition));

            AbstractFloatingView.closeAllOpenViews(mTarget);
            if (mItemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) mItemInfo;
                SystemUiProxy.INSTANCE.get(mTarget).startShortcut(
                        workspaceItemInfo.getIntent().getPackage(),
                        workspaceItemInfo.getDeepShortcutId(),
                        mPosition.stagePosition,
                        null,
                        workspaceItemInfo.user,
                        instanceIds.first);
            } else {
                SystemUiProxy.INSTANCE.get(mTarget).startIntent(
                        mTarget.getSystemService(LauncherApps.class).getMainActivityLaunchIntent(
                                mItemInfo.getIntent().getComponent(),
                                null,
                                mItemInfo.user),
                        new Intent(),
                        mPosition.stagePosition,
                        null,
                        instanceIds.first);
            }
        }
    }
}

