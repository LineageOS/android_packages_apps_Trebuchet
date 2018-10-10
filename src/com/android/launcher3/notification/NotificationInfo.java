/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.notification;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.util.PackageUserKey;

/**
 * An object that contains relevant information from a {@link StatusBarNotification}. This should
 * only be created when we need to show the notification contents on the UI; until then, a
 * {@link com.android.launcher3.badge.BadgeInfo} with only the notification key should
 * be passed around, and then this can be constructed using the StatusBarNotification from
 * {@link NotificationListener#getNotificationsForKeys(java.util.List)}.
 */
public class NotificationInfo implements View.OnClickListener {

    public final PackageUserKey packageUserKey;
    public final String notificationKey;
    public final CharSequence title;
    public final CharSequence text;
    public final PendingIntent intent;
    public final boolean autoCancel;
    public final boolean dismissable;

    private int mBadgeIcon;
    private Drawable mIconDrawable;
    private int mIconColor;
    private boolean mIsIconLarge;

    /**
     * Extracts the data that we need from the StatusBarNotification.
     */
    public NotificationInfo(Context context, StatusBarNotification statusBarNotification) {
        Icon icon = null;
        packageUserKey = PackageUserKey.fromNotification(statusBarNotification);
        notificationKey = statusBarNotification.getKey();
        Notification notification = statusBarNotification.getNotification();
        title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);

        if (Utilities.ATLEAST_OREO) mBadgeIcon = notification.getBadgeIconType();
        mBadgeIcon = Utilities.ATLEAST_OREO ? notification.getBadgeIconType() :
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? Notification.BADGE_ICON_LARGE : Notification.BADGE_ICON_SMALL; // We need some kind of compat for this
        // Load the icon. Since it is backed by ashmem, we won't copy the entire bitmap
        // into our process as long as we don't touch it and it exists in systemui.
        if (mBadgeIcon != Notification.BADGE_ICON_SMALL && Utilities.ATLEAST_MARSHMALLOW) icon = notification.getLargeIcon();
        if (icon == null) {
            try {
                Resources res = context.getPackageManager().getResourcesForApplication(statusBarNotification.getPackageName());
                if (Utilities.ATLEAST_MARSHMALLOW) {
                    mIconDrawable = notification.getSmallIcon().loadDrawable(context);
                } else {
                    mIconDrawable = res.getDrawable(notification.icon);
                }
                mIconColor = statusBarNotification.getNotification().color;
                mIsIconLarge = false;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        } else if (Utilities.ATLEAST_MARSHMALLOW) {
            // Use the large icon.
            mIconDrawable = icon.loadDrawable(context);
            mIsIconLarge = true;
        }
        if (mIconDrawable == null) {
            mIconDrawable = new BitmapDrawable(context.getResources(), LauncherAppState
                    .getInstance(context).getIconCache()
                    .getDefaultIcon(statusBarNotification.getUser()));
            mBadgeIcon = Notification.BADGE_ICON_NONE;
        }
        intent = notification.contentIntent;
        autoCancel = (notification.flags & Notification.FLAG_AUTO_CANCEL) != 0;
        dismissable = (notification.flags & Notification.FLAG_ONGOING_EVENT) == 0;
    }

    @Override
    public void onClick(View view) {
        if (intent == null) {
            return;
        }
        final Launcher launcher = Launcher.getLauncher(view.getContext());
        Bundle activityOptions = null;
        if (Utilities.ATLEAST_MARSHMALLOW) activityOptions = ActivityOptions.makeClipRevealAnimation(
                view, 0, 0, view.getWidth(), view.getHeight()).toBundle();
        try {
            if (Utilities.ATLEAST_MARSHMALLOW)
                intent.send(null, 0, null, null, null, null, activityOptions);
            else intent.send(null, 0, null, null, null, null);
            launcher.getUserEventDispatcher().logNotificationLaunch(view, intent);
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
        if (autoCancel) {
            launcher.getPopupDataProvider().cancelNotification(notificationKey);
        }
        AbstractFloatingView.closeOpenContainer(launcher, AbstractFloatingView
                .TYPE_POPUP_CONTAINER_WITH_ARROW);
    }

    public Drawable getIconForBackground(Context context, int background) {
        if (mIsIconLarge) {
            // Only small icons should be tinted.
            return mIconDrawable;
        }
        mIconColor = IconPalette.resolveContrastColor(context, mIconColor, background);
        Drawable icon = mIconDrawable.mutate();
        // DrawableContainer ignores the color filter if it's already set, so clear it first to
        // get it set and invalidated properly.
        icon.setTintList(null);
        icon.setTint(mIconColor);
        return icon;
    }

    public boolean isIconLarge() {
        return mIsIconLarge;
    }

    public boolean shouldShowIconInBadge() {
        // If the icon we're using for this notification matches what the Notification
        // specified should show in the badge, then return true.
        return mIsIconLarge && mBadgeIcon == Notification.BADGE_ICON_LARGE
                || !mIsIconLarge && mBadgeIcon == Notification.BADGE_ICON_SMALL;
    }
}
