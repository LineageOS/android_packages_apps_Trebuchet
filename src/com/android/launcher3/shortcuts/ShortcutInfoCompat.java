/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.UserManagerCompat;

/**
 * Wrapper class for {@link android.content.pm.ShortcutInfo}, representing deep shortcuts into apps.
 *
 * Not to be confused with {@link com.android.launcher3.ShortcutInfo}.
 */
public class ShortcutInfoCompat {
    private static final String INTENT_CATEGORY = "com.android.launcher3.DEEP_SHORTCUT";
    public static final String EXTRA_SHORTCUT_ID = "shortcut_id";

    private ShortcutInfo mShortcutInfo;
    private String packageName;
    private String id;
    private CharSequence shortLabel;
    private CharSequence longLabel;
    private ComponentName activity;
    private Intent launchIntent;
    private UserHandle userHandle;
    private int rank;
    private boolean enabled;
    private CharSequence disabledMessage;
    private Drawable icon;

    public ShortcutInfoCompat(ShortcutInfo shortcutInfo) {
        mShortcutInfo = shortcutInfo;
    }

    public ShortcutInfoCompat(String packageName, String id, CharSequence shortLabel, CharSequence longLabel,
                              ComponentName activity, Intent launchIntent, UserHandle userHandle, int rank, boolean enabled, CharSequence disabledMessage, Drawable icon) {
        this.packageName = packageName;
        this.id = id;
        this.shortLabel = shortLabel;
        this.longLabel = longLabel;
        this.activity = activity;
        this.launchIntent = launchIntent;
        this.userHandle = userHandle;
        this.rank = rank;
        this.enabled = enabled;
        this.disabledMessage = disabledMessage;
        this.icon = icon;
    }

    public Intent makeIntent() {
        long serialNumber = UserManagerCompat.getInstance(LauncherAppState.getInstanceNoCreate().getContext())
                .getSerialNumberForUser(getUserHandle());
        Intent intent;
        if (useNative()) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(mShortcutInfo.getActivity());
        } else {
            intent = launchIntent;
        }
        return intent
                .addCategory(INTENT_CATEGORY)
                .setPackage(getPackage())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra("profile", serialNumber)
                .putExtra(EXTRA_SHORTCUT_ID, getId());
    }

    public ShortcutInfo getShortcutInfo() {
        return mShortcutInfo;
    }

    public String getPackage() {
        return useNative()?mShortcutInfo.getPackage():packageName;
    }

    public String getId() {
        return useNative()?mShortcutInfo.getId():id;
    }

    public CharSequence getShortLabel() {
        return useNative()?mShortcutInfo.getShortLabel():shortLabel;
    }

    public CharSequence getLongLabel() {
        return useNative()?mShortcutInfo.getLongLabel():longLabel;
    }

    public long getLastChangedTimestamp() {
        return useNative()?mShortcutInfo.getLastChangedTimestamp():0;
    }

    public ComponentName getActivity() {
        return useNative()?mShortcutInfo.getActivity():activity;
    }

    public UserHandle getUserHandle() {
        return useNative()?mShortcutInfo.getUserHandle():userHandle;
    }

    public boolean hasKeyFieldsOnly() {
        return useNative()?mShortcutInfo.hasKeyFieldsOnly():false;
    }

    public boolean isPinned() {
        return useNative()?mShortcutInfo.isPinned():false;
    }

    public boolean isDeclaredInManifest() {
        return useNative()?mShortcutInfo.isDeclaredInManifest():true;
    }

    public boolean isEnabled() {
        return useNative()?mShortcutInfo.isEnabled():enabled;
    }

    public boolean isDynamic() {
        return useNative()?mShortcutInfo.isDynamic():false;
    }

    public int getRank() {
        return useNative()?mShortcutInfo.getRank():rank;
    }

    public CharSequence getDisabledMessage() {
        return useNative()?mShortcutInfo.getDisabledMessage():disabledMessage;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean useNative() {
        return Utilities.isShortcutBackportEnabled() && mShortcutInfo != null;
    }

    @Override
    public String toString() {
        return useNative()?mShortcutInfo.toString():super.toString();
    }
}
