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

import com.android.launcher3.Launcher;

public class LineageLauncher extends Launcher {

    private final LineageLauncherCallbacks mCallbacks;

    public LineageLauncher() {
        mCallbacks = new LineageLauncherCallbacks(this);
        setLauncherCallbacks(mCallbacks);
    }

    public LineageLauncherCallbacks getCallbacks() {
        return mCallbacks;
    }
}
