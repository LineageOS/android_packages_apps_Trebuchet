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

package com.android.launcher3.settings.preference;

import android.content.ComponentName;
import android.content.Context;

import androidx.preference.ListPreference;

import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.customization.IconDatabase;

public class IconPackPrefSetter implements ReloadingListPreference.OnReloadListener {
    private final Context mContext;
    private final ComponentName mFilter;

    public IconPackPrefSetter(Context context) {
        this(context, null);
    }

    public IconPackPrefSetter(Context context, ComponentName filter) {
        mContext = context;
        mFilter = filter;
    }

    @Override
    public Runnable listUpdater(ReloadingListPreference pref) {
        IconPackManager ipm = IconPackManager.get(mContext);
        Map<String, CharSequence> packList = ipm.getProviderNames();
        String globalPack = IconDatabase.getGlobal(mContext);

        if (mFilter != null) {
            // Filter for packs with icon for this app, or the global pack.
            for (String pkg : new HashSet<>(packList.keySet())) {
                if (!ipm.packContainsActivity(pkg, mFilter)) {
                    packList.remove(pkg);
                }
            }
        }

        CharSequence[] keys = new String[packList.size() + 1];
        CharSequence[] values = new String[keys.length];
        int i = 0;

        // First value, system default, or the current icon pack if that has no icon yet.
        keys[i] = mContext.getResources().getString(R.string.icon_pack_default_label);
        values[i++] = packList.containsKey(globalPack) ? "" : globalPack;

        // List of available icon packs
        List<Map.Entry<String, CharSequence>> packs = new ArrayList<>(packList.entrySet());
        Collections.sort(packs, (o1, o2) ->
                normalizeTitle(o1.getValue()).compareTo(normalizeTitle(o2.getValue())));
        for (Map.Entry<String, CharSequence> entry : packs) {
            keys[i] = entry.getValue();
            values[i++] = entry.getKey();
        }

        return () -> pref.setEntriesWithValues(keys, values);
    }

    private String normalizeTitle(CharSequence title) {
        return title.toString().toLowerCase();
    }
}
