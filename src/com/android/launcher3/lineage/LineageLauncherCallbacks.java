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

import android.content.SharedPreferences;
import android.os.Bundle;

import com.android.launcher3.LauncherCallbacks;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class LineageLauncherCallbacks implements LauncherCallbacks,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private final LineageLauncher mLauncher;

    public LineageLauncherCallbacks(LineageLauncher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

    }

    @Override
    public void onHomeIntent(boolean internalStateHandled) {

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) {

    }

    @Override
    public boolean startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData) {
        return false;
    }
}
