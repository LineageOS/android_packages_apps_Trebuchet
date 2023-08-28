/*
 * Copyright (C) 2020 Shift GmbH
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
package com.android.launcher3.lineage.icon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.preference.Preference;

import com.android.launcher3.R;
import com.android.launcher3.lineage.settings.RadioPreference;
import com.android.launcher3.lineage.settings.RadioSettingsFragment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class IconPackSettingsFragment extends RadioSettingsFragment {
    private static final IntentFilter PKG_UPDATE_INTENT = new IntentFilter();
    static {
        PKG_UPDATE_INTENT.addAction(Intent.ACTION_PACKAGE_INSTALL);
        PKG_UPDATE_INTENT.addAction(Intent.ACTION_PACKAGE_ADDED);
        PKG_UPDATE_INTENT.addAction(Intent.ACTION_PACKAGE_CHANGED);
        PKG_UPDATE_INTENT.addAction(Intent.ACTION_PACKAGE_REMOVED);
        PKG_UPDATE_INTENT.addDataScheme("package");
    }

    private IconPackStore iconPackStore = null;
    private BroadcastReceiver broadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadPreferences();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadCastReceiver, PKG_UPDATE_INTENT);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(broadCastReceiver);
    }

    @Override
    protected List<RadioPreference> getRadioPreferences(Context context) {
        iconPackStore = new IconPackStore(context);
        final String currentIconPack = iconPackStore.getCurrent();
        final List<RadioPreference> prefsList = new ArrayList<>();
        final Set<IconPackInfo> iconPacks = getAvailableIconPacks(context);

        for (final IconPackInfo entry : iconPacks) {
            final boolean isCurrent = currentIconPack.equals(entry.pkgName);
            final RadioPreference pref = buildPreference(context,
                    entry.pkgName, entry.label, isCurrent);
            prefsList.add(pref);

            if (isCurrent) {
                setSelectedPreference(pref);
            }
        }

        return prefsList;
    }

    @Override
    public void onSelected(String key) {
        if (iconPackStore != null) {
            iconPackStore.setCurrent(key);
        }
        super.onSelected(key);
    }

    @Override
    protected IconPackHeaderPreference getHeader(Context context) {
        return new IconPackHeaderPreference(context);
    }

    private Set<IconPackInfo> getAvailableIconPacks(Context context) {
        final PackageManager pm = context.getPackageManager();
        final Set<IconPackInfo> availablePacks = new LinkedHashSet<>();
        final List<ResolveInfo> eligiblePacks = new ArrayList<>();
        eligiblePacks.addAll(pm.queryIntentActivities(
                new Intent("com.novalauncher.THEME"), 0));
        eligiblePacks.addAll(pm.queryIntentActivities(
                new Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0));

        // Add default
        final String defaultLabel = context.getString(R.string.icon_pack_default_label);
        availablePacks.add(new IconPackInfo(IconPackStore.SYSTEM_ICON_PACK, defaultLabel));
        // Add user-installed packs
        for (final ResolveInfo r : eligiblePacks) {
            availablePacks.add(new IconPackInfo(
                    r.activityInfo.packageName, (String) r.loadLabel(pm)));
        }
        return availablePacks;
    }

    private RadioPreference buildPreference(Context context, String pkgName,
            String label, boolean isChecked) {
        final RadioPreference pref = new RadioPreference(context);
        pref.setKey(pkgName);
        pref.setTitle(label);
        pref.setPersistent(false);
        pref.setChecked(isChecked);
        return pref;
    }

    private static class IconPackInfo {
        final String pkgName;
        final String label;

        IconPackInfo(String pkgName, String label) {
            this.pkgName = pkgName;
            this.label = label;
        }

        @Override
        public int hashCode() {
            return pkgName.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) return false;
            if (!(other instanceof IconPackInfo)) return false;
            return pkgName.equals(((IconPackInfo) other).pkgName);
        }
    }
}
