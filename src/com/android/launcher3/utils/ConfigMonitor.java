package com.android.launcher3.util;

/**
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.util.Log;

import com.android.launcher3.Utilities;
import com.android.launcher3.lineage.icon.IconPackStore;

import java.util.function.Consumer;

/**
 * {@link BroadcastReceiver} which watches configuration changes and
 * notifies the callback in case changes which affect the device profile occur.
 */
public class ConfigMonitor extends BroadcastReceiver implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "ConfigMonitor";

    private final Context mContext;

    private Consumer<Context> mCallback;

    public ConfigMonitor(Context context, Consumer<Context> callback) {
        mContext = context;

        // Listen for configuration change
        Utilities.getPrefs(mContext).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // this onReceive event is here to fix an error
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // change icon pack when setting is changed
        if (IconPackStore.KEY_ICON_PACK.equals(key)) {
            notifyChange();
        }
    }

    private synchronized void notifyChange() {
        if (mCallback != null) {
            Consumer<Context> callback = mCallback;
            mCallback = null;
            MAIN_EXECUTOR.execute(() -> callback.accept(mContext));
        }
    }

    public void unregister() {
        try {
            mContext.unregisterReceiver(this);
            Utilities.getPrefs(mContext).unregisterOnSharedPreferenceChangeListener(this);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister config monitor", e);
        }
    }
}
