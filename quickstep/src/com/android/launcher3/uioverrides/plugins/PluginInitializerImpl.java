/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.launcher3.uioverrides.plugins;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.os.Looper;

import com.android.systemui.shared.plugins.PluginInitializer;

public class PluginInitializerImpl implements PluginInitializer {
    @Override
    public Looper getBgLooper() {
        return MODEL_EXECUTOR.getLooper();
    }

    @Override
    public void onPluginManagerInit() {
    }

    @Override
    public String[] getWhitelistedPlugins(Context context) {
        return new String[0];
    }

    @Override
    public PluginEnablerImpl getPluginEnabler(Context context) {
        return new PluginEnablerImpl(context);
    }

    @Override
    public void handleWtfs() {
    }
}
