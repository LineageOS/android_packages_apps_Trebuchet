/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class LauncherApplication extends Application {

    private String mStkAppName = new String();
    private final String STK_PACKAGE_INTENT_ACTION_NAME =
            "org.codeaurora.carrier.ACTION_TELEPHONY_SEND_STK_TITLE";
    private final String STK_APP_NAME = "StkTitle";

    @Override
    public void onCreate() {
        super.onCreate();
        if (getResources().getBoolean(R.bool.config_launcher_stkAppRename)) {
            registerAppNameChangeReceiver();
        }
    }

    private void registerAppNameChangeReceiver() {
        IntentFilter intentFilter = new IntentFilter(STK_PACKAGE_INTENT_ACTION_NAME);
        registerReceiver(appNameChangeReceiver, intentFilter);
    }

    /**
     * Receiver for STK Name change broadcast
     */
    private BroadcastReceiver appNameChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mStkAppName = intent.getStringExtra(STK_APP_NAME);
        }
    };

    public String getStkAppName(){
        return mStkAppName;
    }
}
