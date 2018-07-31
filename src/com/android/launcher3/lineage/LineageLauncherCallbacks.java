/*
 * Copyright (C) 2018 The LineageOS Project
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

package com.android.launcher3.lineage;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherCallbacks;
import com.android.launcher3.Utilities;

import com.google.android.libraries.gsa.launcherclient.LauncherClient;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class LineageLauncherCallbacks implements LauncherCallbacks,
        DeviceProfile.OnDeviceProfileChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String KEY_ENABLE_MINUS_ONE = "pref_enable_minus_one";
    public static final String SEARCH_PACKAGE = "com.google.android.googlequicksearchbox";

    private final LineageLauncher mLauncher;

    private LauncherClient mLauncherClient;

    public LineageLauncherCallbacks(LineageLauncher launcher) {
        mLauncher = launcher;
    }

    public LauncherClient getLauncherClient() {
        return mLauncherClient;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = Utilities.getPrefs(mLauncher);
        OverlayCallbackImpl overlayCallbacks = new OverlayCallbackImpl(mLauncher);
        mLauncherClient = new LauncherClient(mLauncher, overlayCallbacks, getClientOptions(prefs));
        overlayCallbacks.setClient(mLauncherClient);
        prefs.registerOnSharedPreferenceChangeListener(this);

        mLauncher.addOnDeviceProfileChangeListener(this);
    }

    @Override
    public void onHomeIntent(boolean internalStateHandled) {
        mLauncherClient.hideOverlay(mLauncher.isStarted() && !mLauncher.isForceInvisible());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (KEY_ENABLE_MINUS_ONE.equals(key)) {
            mLauncherClient.setClientOptions(getClientOptions(prefs));
        }
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mLauncherClient.reattachOverlay();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) {
        mLauncherClient.dump(prefix, w);
    }

    @Override
    public boolean startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData) {
        return false;
    }

    private LauncherClient.ClientOptions getClientOptions(SharedPreferences prefs) {
        return new LauncherClient.ClientOptions(
                prefs.getBoolean(KEY_ENABLE_MINUS_ONE, true),
                true, /* enableHotword */
                true /* enablePrewarming */
        );
    }
}
