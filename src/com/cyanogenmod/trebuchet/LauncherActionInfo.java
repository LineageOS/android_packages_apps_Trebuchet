/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.cyanogenmod.trebuchet;

import android.content.ContentValues;

class LauncherActionInfo extends ShortcutInfo {

    /*
     * The launcher action
     */
    LauncherAction.Action action;

    LauncherActionInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_LAUNCHER_ACTION;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);

        String actionText = action != null ? action.name() : null;
        values.put(LauncherSettings.Favorites.LAUNCHER_ACTION, actionText);
    }
}
