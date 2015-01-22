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

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import com.android.launcher3.settings.SettingsProvider;

import java.util.ArrayList;

public class DynamicGrid {
    @SuppressWarnings("unused")
    private static final String TAG = "DynamicGrid";

    private DeviceProfile mProfile;
    private float mMinWidth;
    private float mMinHeight;

    // This is a static that we use for the default icon size on a 4/5-inch phone
    static float DEFAULT_ICON_SIZE_DP = 66;
    static float DEFAULT_ICON_SIZE_PX = 0;

    public static float dpiFromPx(int size, DisplayMetrics metrics){
        float densityRatio = (float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT;
        return (size / densityRatio);
    }
    public static int pxFromDp(float size, DisplayMetrics metrics) {
        return (int) Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                size, metrics));
    }
    public static int pxFromSp(float size, DisplayMetrics metrics) {
        return (int) Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                size, metrics));
    }

    public DynamicGrid(Context context, Resources resources,
                       int minWidthPx, int minHeightPx,
                       int widthPx, int heightPx,
                       int awPx, int ahPx) {
        DisplayMetrics dm = resources.getDisplayMetrics();
        ArrayList<DeviceProfile> deviceProfiles =
                new ArrayList<DeviceProfile>();
        boolean hasAA = !LauncherAppState.isDisableAllApps();
        boolean useLargeIcons = SettingsProvider.getBoolean(context,
                SettingsProvider.SETTINGS_UI_GENERAL_ICONS_LARGE,
                R.bool.preferences_interface_general_icons_large_default);
        boolean launcherShortcutEnabled = LauncherApplication.LAUNCHER_SHORTCUT_ENABLED;
        int fourByFourDefaultLayout = launcherShortcutEnabled ? R.xml.ct_default_workspace_4x4
                               : R.xml.default_workspace_4x4;
        DEFAULT_ICON_SIZE_PX = pxFromDp(DEFAULT_ICON_SIZE_DP, dm);
        // Our phone profiles include the bar sizes in each orientation
        deviceProfiles.add(new DeviceProfile("Super Short Stubby",
                255, 300,  2, 3,  (useLargeIcons ? 58 : 46), 13, (hasAA ? 3 : 5),
                (useLargeIcons ? 58 : 46), fourByFourDefaultLayout,
                R.xml.default_workspace_4x4_no_all_apps));
        deviceProfiles.add(new DeviceProfile("Shorter Stubby",
                255, 400,  3, 3,  (useLargeIcons ? 58 : 46), 13, (hasAA ? 3 : 5),
                (useLargeIcons ? 58 : 46), fourByFourDefaultLayout,
                R.xml.default_workspace_4x4_no_all_apps));
        deviceProfiles.add(new DeviceProfile("Short Stubby",
                275, 420,  3, 4,  (useLargeIcons ? 58 : 46), 13, (hasAA ? 5 : 5),
                (useLargeIcons ? 58 : 46), fourByFourDefaultLayout,
                R.xml.default_workspace_4x4_no_all_apps));
        deviceProfiles.add(new DeviceProfile("Stubby",
                255, 450,  3, 4,  (useLargeIcons ? 58 : 46), 13, (hasAA ? 5 : 5),
                (useLargeIcons ? 58 : 46), fourByFourDefaultLayout,
                R.xml.default_workspace_4x4_no_all_apps));
        deviceProfiles.add(new DeviceProfile("Nexus S",
                296, 491.33f,  4, 4,  (useLargeIcons ? 58 : 46), 13, (hasAA ? 5 : 5),
                (useLargeIcons ? 58 : 46), fourByFourDefaultLayout,
                R.xml.default_workspace_4x4_no_all_apps));
        deviceProfiles.add(new DeviceProfile("Nexus 4",
                335, 567,  4, 4,  (useLargeIcons ? DEFAULT_ICON_SIZE_DP : 52), 13, (hasAA ? 5 : 5),
                (useLargeIcons ? 60 : 46), fourByFourDefaultLayout,
                R.xml.default_workspace_4x4_no_all_apps));
        deviceProfiles.add(new DeviceProfile("Nexus 5",
                359, 567,  4, 4,  (useLargeIcons ? DEFAULT_ICON_SIZE_DP : 52), 13, (hasAA ? 5 : 5),
                (useLargeIcons ? 60 : 46), fourByFourDefaultLayout,
                R.xml.default_workspace_4x4_no_all_apps));
        deviceProfiles.add(new DeviceProfile("Large Phone",
                406, 694,  5, 5,  (useLargeIcons ? 68 : 52), 14.4f,  5, (useLargeIcons ? 60 : 44),
                R.xml.default_workspace_5x5, R.xml.default_workspace_5x5_no_all_apps));
        // The tablet profile is odd in that the landscape orientation
        // also includes the nav bar on the side
        deviceProfiles.add(new DeviceProfile("Nexus 7",
                575, 904,  5, 6, (useLargeIcons ? 76 : 60), 14.4f,  7, (useLargeIcons ? 64 : 52),
                R.xml.default_workspace_5x6, R.xml.default_workspace_5x6_no_all_apps));
        // Larger tablet profiles always have system bars on the top & bottom
        deviceProfiles.add(new DeviceProfile("Nexus 10",
                727, 1207,  5, 6,  (useLargeIcons ? 80 : 64), 14.4f,  7, (useLargeIcons ? 68 : 56),
                R.xml.default_workspace_5x6, R.xml.default_workspace_5x6_no_all_apps));
        deviceProfiles.add(new DeviceProfile("20-inch Tablet",
                1527, 2527,  7, 7,  (useLargeIcons ? 104 : 80), 20,  7, (useLargeIcons ? 76 : 64),
                fourByFourDefaultLayout, R.xml.default_workspace_4x4_no_all_apps));
        mMinWidth = dpiFromPx(minWidthPx, dm);
        mMinHeight = dpiFromPx(minHeightPx, dm);
        mProfile = new DeviceProfile(context, deviceProfiles,
                mMinWidth, mMinHeight,
                widthPx, heightPx,
                awPx, ahPx,
                resources);
    }

    public DeviceProfile getDeviceProfile() {
        return mProfile;
    }

    public String toString() {
        return "-------- DYNAMIC GRID ------- \n" +
                "Wd: " + mProfile.minWidthDps + ", Hd: " + mProfile.minHeightDps +
                ", W: " + mProfile.widthPx + ", H: " + mProfile.heightPx +
                " [r: " + mProfile.numRows + ", c: " + mProfile.numColumns +
                ", is: " + mProfile.iconSizePx + ", its: " + mProfile.iconTextSizePx +
                ", cw: " + mProfile.cellWidthPx + ", ch: " + mProfile.cellHeightPx +
                ", hc: " + mProfile.numHotseatIcons + ", his: " + mProfile.hotseatIconSizePx + "]";
    }
}
