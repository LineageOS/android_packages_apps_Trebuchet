/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.launcher3.lineage.hidden;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.android.launcher3.lineage.hidden.db.HiddenComponent;
import com.android.launcher3.lineage.hidden.db.HiddenDatabaseHelper;

public class UpdateItemVisibilityTask extends AsyncTask<HiddenComponent, Void, Boolean> {
    @NonNull
    private HiddenDatabaseHelper mDbHelper;
    @NonNull
    private UpdateCallback mCallback;

    UpdateItemVisibilityTask(@NonNull HiddenDatabaseHelper dbHelper,
                             @NonNull UpdateCallback callback) {
        mDbHelper = dbHelper;
        mCallback = callback;
    }

    @Override
    protected Boolean doInBackground(HiddenComponent... hiddenComponents) {
        if (hiddenComponents.length < 1) {
            return false;
        }

        HiddenComponent component = hiddenComponents[0];
        String pkgName = component.getPackageName();

        if (component.isHidden()) {
            mDbHelper.addApp(pkgName);
        } else {
            mDbHelper.removeApp(pkgName);
        }

        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        mCallback.onUpdated(result);
    }

    interface UpdateCallback {
        void onUpdated(boolean result);
    }
}
