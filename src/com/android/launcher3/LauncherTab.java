/*
 * Copyright (C) 2017 Paranoid Android
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

package com.android.launcher3;

import com.android.launcher3.Launcher.LauncherOverlay;
import com.android.launcher3.Launcher.LauncherOverlayCallbacks;

import com.google.android.libraries.launcherclient.LauncherClient;
import com.google.android.libraries.launcherclient.LauncherClientCallbacksAdapter;

public class LauncherTab {

    public static final String SEARCH_PACKAGE = "com.google.android.googlequicksearchbox";

    private Launcher mLauncher;
    private LauncherClient mLauncherClient;
    private Workspace mWorkspace;

    public LauncherTab(Launcher launcher, boolean enabled) {
        mLauncher = launcher;
        mWorkspace = launcher.getWorkspace();

        updateLauncherTab(enabled);
        if (enabled && mLauncherClient.isConnected()) {
            launcher.setLauncherOverlay(new LauncherOverlays());
        }
    }

    protected void updateLauncherTab(boolean enabled) {
        if (enabled) {
            mLauncherClient = new LauncherClient(mLauncher,
                    new LauncherClientCallbacks(), SEARCH_PACKAGE, true);
            mLauncher.setLauncherOverlay(new LauncherOverlays());
        } else {
            mLauncher.setLauncherOverlay(null);
        }
    }

    protected LauncherClient getClient() {
        return mLauncherClient;
    }

    private class LauncherOverlays implements LauncherOverlay {
        @Override
        public void onScrollInteractionBegin() {
            mLauncherClient.startMove();
        }

        @Override
        public void onScrollInteractionEnd() {
            mLauncherClient.endMove();
        }

        @Override
        public void onScrollChange(float progress, boolean rtl) {
            mLauncherClient.updateMove(progress);
        }

        @Override
        public void setOverlayCallbacks(LauncherOverlayCallbacks callbacks) {
        }
    }

    private class LauncherClientCallbacks extends LauncherClientCallbacksAdapter {
        @Override
        public void onOverlayScrollChanged(float progress) {
            mWorkspace.onOverlayScrollChanged(progress);
        }
    }
}
