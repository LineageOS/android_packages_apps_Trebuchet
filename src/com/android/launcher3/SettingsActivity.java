/*
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

package com.android.launcher3;

import static com.android.launcher3.states.RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY;
import static com.android.launcher3.states.RotationHelper.getAllowRotationDefaultValue;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Adapter;
import android.widget.ListView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.widget.NumberPicker;

import com.android.launcher3.graphics.IconShapeOverride;
import com.android.launcher3.lineage.LineageLauncherCallbacks;
import com.android.launcher3.lineage.LineageUtils;
import com.android.launcher3.lineage.trust.TrustAppsActivity;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.ListViewHighlighter;
import com.android.launcher3.util.SettingsObserver;
import com.android.launcher3.views.ButtonPreference;

import java.util.Objects;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsActivity extends Activity {

    private static final String ICON_BADGING_PREFERENCE_KEY = "pref_icon_badging";
    /** Hidden field Settings.Secure.NOTIFICATION_BADGING */
    public static final String NOTIFICATION_BADGING = "notification_badging";
    /** Hidden field Settings.Secure.ENABLED_NOTIFICATION_LISTENERS */
    private static final String NOTIFICATION_ENABLED_LISTENERS = "enabled_notification_listeners";

    private static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    private static final String EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args";
    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    private static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    public static final String KEY_MINUS_ONE = "pref_enable_minus_one";
    private static final String KEY_GRID_SIZE = "pref_grid_size";
    private static final String KEY_SHOW_DESKTOP_LABELS = "pref_desktop_show_labels";
    private static final String KEY_SHOW_DRAWER_LABELS = "pref_drawer_show_labels";

    public static final String KEY_WORKSPACE_EDIT = "pref_workspace_edit";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, getNewFragment())
                    .commit();
        }
    }

    protected PreferenceFragment getNewFragment() {
        return new LauncherSettingsFragment();
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private IconBadgingObserver mIconBadgingObserver;

        private String mPreferenceKey;
        private boolean mPreferenceHighlighted = false;
        private boolean mShouldRestart = false;

        private SharedPreferences mPrefs;
        private Preference mGridPref;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.launcher_preferences);

            mPrefs = Utilities.getPrefs(getActivity().getApplicationContext());
            mPrefs.registerOnSharedPreferenceChangeListener(this);

            ContentResolver resolver = getActivity().getContentResolver();

            PreferenceCategory homeGroup = (PreferenceCategory)
                    findPreference("category_home");
            PreferenceCategory drawerGroup = (PreferenceCategory)
                    findPreference("category_drawer");
            PreferenceCategory iconGroup = (PreferenceCategory)
                    findPreference("category_icons");

            ButtonPreference iconBadgingPref =
                    (ButtonPreference) iconGroup.findPreference(ICON_BADGING_PREFERENCE_KEY);
            if (!Utilities.ATLEAST_OREO) {
                getPreferenceScreen().removePreference(
                        findPreference(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY));
                iconGroup.removePreference(iconBadgingPref);
            } else if (!getResources().getBoolean(R.bool.notification_badging_enabled)
                    || getContext().getSystemService(ActivityManager.class).isLowRamDevice()) {
                iconGroup.removePreference(iconBadgingPref);
            } else {
                // Listen to system notification badge settings while this UI is active.
                mIconBadgingObserver = new IconBadgingObserver(
                        iconBadgingPref, resolver, getFragmentManager());
                mIconBadgingObserver.register(NOTIFICATION_BADGING, NOTIFICATION_ENABLED_LISTENERS);
            }

            Preference iconShapeOverride = iconGroup.findPreference(
                    IconShapeOverride.KEY_PREFERENCE);
            if (iconShapeOverride != null) {
                if (IconShapeOverride.isSupported(getActivity())) {
                    IconShapeOverride.handlePreferenceUi((ListPreference) iconShapeOverride);
                } else {
                    iconGroup.removePreference(iconShapeOverride);
                }
            }

            // Setup allow rotation preference
            Preference rotationPref = homeGroup.findPreference(ALLOW_ROTATION_PREFERENCE_KEY);
            if (getResources().getBoolean(R.bool.allow_rotation)) {
                // Launcher supports rotation by default. No need to show this setting.
                homeGroup.removePreference(rotationPref);
            } else {
                // Initialize the UI once
                rotationPref.setDefaultValue(getAllowRotationDefaultValue());
            }

            SwitchPreference minusOne = (SwitchPreference) findPreference(KEY_MINUS_ONE);
            if (!LineageUtils.hasPackageInstalled(getActivity(),
                    LineageLauncherCallbacks.SEARCH_PACKAGE)) {
                homeGroup.removePreference(minusOne);
            }

            mGridPref = homeGroup.findPreference(KEY_GRID_SIZE);
            if (mGridPref != null) {
                mGridPref.setOnPreferenceClickListener(preference -> {
                    setCustomGridSize();
                    return true;
                });

                mGridPref.setSummary(mPrefs.getString(KEY_GRID_SIZE, getDefaultGridSize()));
            }

            Preference trustApps = drawerGroup.findPreference("pref_trust_apps");
            if (trustApps != null) {
                trustApps.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(getActivity(), TrustAppsActivity.class);
                    startActivity(intent);
                    return true;
                });
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
        }

        @Override
        public void onResume() {
            super.onResume();

            Intent intent = getActivity().getIntent();
            mPreferenceKey = intent.getStringExtra(EXTRA_FRAGMENT_ARG_KEY);
            if (isAdded() && !mPreferenceHighlighted && !TextUtils.isEmpty(mPreferenceKey)) {
                getView().postDelayed(this::highlightPreference, DELAY_HIGHLIGHT_DURATION_MILLIS);
            }
        }

        private void highlightPreference() {
            Preference pref = findPreference(mPreferenceKey);
            if (pref == null || getPreferenceScreen() == null) {
                return;
            }
            PreferenceScreen screen = getPreferenceScreen();
            if (Utilities.ATLEAST_OREO) {
                screen = selectPreferenceRecursive(pref, screen);
            }
            if (screen == null) {
                return;
            }

            View root = screen.getDialog() != null
                    ? screen.getDialog().getWindow().getDecorView() : getView();
            ListView list = root.findViewById(android.R.id.list);
            if (list == null || list.getAdapter() == null) {
                return;
            }
            Adapter adapter = list.getAdapter();

            // Find the position
            int position = -1;
            for (int i = adapter.getCount() - 1; i >= 0; i--) {
                if (pref == adapter.getItem(i)) {
                    position = i;
                    break;
                }
            }
            new ListViewHighlighter(list, position);
            mPreferenceHighlighted = true;
        }

        @Override
        public void onDestroy() {
            if (mIconBadgingObserver != null) {
                mIconBadgingObserver.unregister();
                mIconBadgingObserver = null;
            }
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);

            if (mShouldRestart) {
                triggerRestart();
            }
            super.onDestroy();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            switch (key) {
                case KEY_GRID_SIZE:
                    mGridPref.setSummary(mPrefs.getString(KEY_GRID_SIZE, getDefaultGridSize()));
                    mShouldRestart = true;
                    break;
                case KEY_SHOW_DESKTOP_LABELS:
                case KEY_SHOW_DRAWER_LABELS:
                    mShouldRestart = true;
                    break;
            }
        }

        private void setCustomGridSize() {
            int minValue = 3;
            int maxValue = 9;

            String storedValue = mPrefs.getString(KEY_GRID_SIZE, "4x4");
            Pair<Integer, Integer> currentValues = LineageUtils.extractCustomGrid(storedValue);

            LayoutInflater inflater = (LayoutInflater)
                    getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (inflater == null) {
                return;
            }
            View contentView = inflater.inflate(R.layout.dialog_custom_grid, null);
            NumberPicker columnPicker = contentView.findViewById(R.id.dialog_grid_column);
            NumberPicker rowPicker = contentView.findViewById(R.id.dialog_grid_row);

            columnPicker.setMinValue(minValue);
            rowPicker.setMinValue(minValue);
            columnPicker.setMaxValue(maxValue);
            rowPicker.setMaxValue(maxValue);
            columnPicker.setValue(currentValues.first);
            rowPicker.setValue(currentValues.second);

            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.grid_size_text)
                    .setMessage(R.string.grid_size_custom_message)
                    .setView(contentView)
                    .setPositiveButton(R.string.grid_size_custom_positive, (dialog, i) -> {
                        String newValues = LineageUtils.getGridValue(columnPicker.getValue(),
                                rowPicker.getValue());
                        mPrefs.edit().putString(KEY_GRID_SIZE, newValues).apply();
                    })
                    .show();
        }

        private String getDefaultGridSize() {
            InvariantDeviceProfile profile = new InvariantDeviceProfile(getActivity());
            return LineageUtils.getGridValue(profile.numColumns, profile.numRows);
        }

        private void triggerRestart() {
            Context context = getActivity().getApplicationContext();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(context, 41, intent,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            manager.set(AlarmManager.RTC, java.lang.System.currentTimeMillis() + 1, pi);
            java.lang.System.exit(0);
        }

        @TargetApi(Build.VERSION_CODES.O)
        private PreferenceScreen selectPreferenceRecursive(
                Preference pref, PreferenceScreen topParent) {
            if (!(pref.getParent() instanceof PreferenceScreen)) {
                return null;
            }

            PreferenceScreen parent = (PreferenceScreen) pref.getParent();
            if (Objects.equals(parent.getKey(), topParent.getKey())) {
                return parent;
            } else if (selectPreferenceRecursive(parent, topParent) != null) {
                ((PreferenceScreen) parent.getParent())
                        .onItemClick(null, null, parent.getOrder(), 0);
                return parent;
            } else {
                return null;
            }
        }
    }

    /**
     * Content observer which listens for system badging setting changes,
     * and updates the launcher badging setting subtext accordingly.
     */
    private static class IconBadgingObserver extends SettingsObserver.Secure
            implements Preference.OnPreferenceClickListener {

        private final ButtonPreference mBadgingPref;
        private final ContentResolver mResolver;
        private final FragmentManager mFragmentManager;

        public IconBadgingObserver(ButtonPreference badgingPref, ContentResolver resolver,
                FragmentManager fragmentManager) {
            super(resolver);
            mBadgingPref = badgingPref;
            mResolver = resolver;
            mFragmentManager = fragmentManager;
        }

        @Override
        public void onSettingChanged(boolean enabled) {
            int summary = enabled ?
                R.string.settings_icon_badging_desc_on :
                R.string.settings_icon_badging_desc_off;

            boolean serviceEnabled = true;
            if (enabled) {
                // Check if the listener is enabled or not.
                String enabledListeners =
                        Settings.Secure.getString(mResolver, NOTIFICATION_ENABLED_LISTENERS);
                ComponentName myListener =
                        new ComponentName(mBadgingPref.getContext(), NotificationListener.class);
                serviceEnabled = enabledListeners != null &&
                        (enabledListeners.contains(myListener.flattenToString()) ||
                                enabledListeners.contains(myListener.flattenToShortString()));
                if (!serviceEnabled) {
                    summary = R.string.title_missing_notification_access;
                }
            }
            mBadgingPref.setWidgetFrameVisible(!serviceEnabled);
            mBadgingPref.setOnPreferenceClickListener(serviceEnabled ? null : this);
            mBadgingPref.setSummary(summary);

        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            new NotificationAccessConfirmation().show(mFragmentManager, "notification_access");
            return true;
        }
    }

    public static class NotificationAccessConfirmation
            extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Context context = getActivity();
            String msg = context.getString(R.string.msg_missing_notification_access,
                    context.getString(R.string.derived_app_name));
            return new AlertDialog.Builder(context)
                    .setTitle(R.string.title_missing_notification_access)
                    .setMessage(msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.title_change_settings, this)
                    .create();
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            ComponentName cn = new ComponentName(getActivity(), NotificationListener.class);
            Bundle showFragmentArgs = new Bundle();
            showFragmentArgs.putString(EXTRA_FRAGMENT_ARG_KEY, cn.flattenToString());

            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_FRAGMENT_ARG_KEY, cn.flattenToString())
                    .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, showFragmentArgs);
            getActivity().startActivity(intent);
        }
    }
}
