/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.icons.calendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import com.android.launcher3.LauncherModel;
import com.android.launcher3.util.ComponentKey;

import java.util.HashSet;
import java.util.Set;

import com.android.launcher3.util.AppReloader;

/**
 * Listens for date change events and uses the IconReloader to reload all loaded calendar icons
 * when the date has changed.
 */
public class DateChangeReceiver extends BroadcastReceiver {
    private final Set<ComponentKey> mDynamicCalendars = new HashSet<>();

    public DateChangeReceiver(Context context) {
        super();

        IntentFilter filter = new IntentFilter(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

        Handler handler = new Handler(MODEL_EXECUTOR.getLooper());
        context.registerReceiver(this, filter, null, handler);
    }

    public void setIsDynamic(ComponentKey key, boolean calendar) {
        if (calendar) {
            mDynamicCalendars.add(key);
        } else {
            mDynamicCalendars.remove(key);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        AppReloader.get(context).reload(mDynamicCalendars);
    }
}
