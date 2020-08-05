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

package com.android.launcher3.lineage.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.List;

public abstract class RadioSettingsFragment extends PreferenceFragment implements
        Preference.OnPreferenceClickListener {
    private RadioPreference selectedPreference = null;
    private RadioHeaderPreference headerPref = null;

    protected abstract List<RadioPreference> getRadioPreferences(Context context);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final PreferenceManager prefManager = getPreferenceManager();
        final Context context = prefManager.getContext();
        final PreferenceScreen screen = prefManager.createPreferenceScreen(context);
        final List<RadioPreference> prefs = getRadioPreferences(context);

        headerPref = getHeader(context);
        if (headerPref != null) {
            screen.addPreference(headerPref);
        }

        for (final RadioPreference p : prefs) {
            p.setOnPreferenceClickListener(this);
            screen.addPreference(p);
        }

        setPreferenceScreen(screen);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference instanceof RadioPreference) {
            onSelected(preference.getKey());

            if (selectedPreference != null) {
                selectedPreference.setChecked(false);
            }
            selectedPreference = (RadioPreference) preference;
            selectedPreference.setChecked(true);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onDestroyView() {
        selectedPreference = null;
        headerPref = null;
        super.onDestroyView();
    }

    protected RadioHeaderPreference getHeader(Context context) {
        return null;
    }

    protected final void setSelectedPreference(RadioPreference preference) {
        selectedPreference = preference;
    }

    protected void onSelected(String key) {
        if (headerPref != null) {
            headerPref.onRadioElementSelected(key);
        }
    }
}
