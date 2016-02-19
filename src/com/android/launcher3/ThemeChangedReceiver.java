/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.util.ArrayList;

public class ThemeChangedReceiver extends BroadcastReceiver {
    private static final String EXTRA_COMPONENTS = "components";

    public static final String MODIFIES_ICONS = "mods_icons";
    public static final String MODIFIES_FONTS = "mods_fonts";
    public static final String MODIFIES_OVERLAYS = "mods_overlays";

    public void onReceive(Context context, Intent intent) {
        // components is a string array of the components that changed
        ArrayList<String> components = intent.getStringArrayListExtra(EXTRA_COMPONENTS);
        if (isInterestingThemeChange(components)) {
            LauncherAppState app = LauncherAppState.getInstanceNoCreate();
            if (app == null) {
                LauncherAppState.setApplicationContext(context);
                app = LauncherAppState.getInstance();
            }
            clearAppIconCache(context);
            clearWidgetPreviewCache(context);
            app.recreateWidgetPreviewDb();
            app.getIconCache().flush();
            app.getModel().forceReload();

            if (Launcher.sRemoteFolderManager != null) {
                Launcher.sRemoteFolderManager.onThemeChanged();
            }
        }
    }

    /**
     * We consider this an "interesting" theme change if it modifies icons, overlays, or fonts.
     * @param components
     * @return
     */
    private boolean isInterestingThemeChange(ArrayList<String> components) {
        if (components != null) {
            for (String component : components) {
                if (component.equals(MODIFIES_ICONS) ||
                        component.equals(MODIFIES_FONTS) ||
                        component.equals(MODIFIES_OVERLAYS)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void clearWidgetPreviewCache(Context context) {
        context.deleteDatabase(LauncherFiles.WIDGET_PREVIEWS_DB);
    }

    private void clearAppIconCache(Context context) {
        context.deleteDatabase(LauncherFiles.APP_ICONS_DB);
    }
}
