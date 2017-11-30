package com.android.launcher3;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;

import com.android.launcher3.icons.IconsHandler;

public class QuickSettingsActivity extends AppCompatActivity {
    private static final String KEY_GRID_SIZE = "pref_grid_size";
    public static final String KEY_ICON_PACK = "pref_icon_pack";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.activity_quick_settings);

        View touchOutSide = findViewById(R.id.quick_settings_touch_outside);
        touchOutSide.setOnClickListener(v -> onBackPressed());

        getFragmentManager().beginTransaction()
                .replace(R.id.quick_settings_content, new QuickSettingsFragment())
                .commit();
    }


    public static class QuickSettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private Preference mGridPref;
        private Preference mIconPackPref;

        private SharedPreferences mPrefs;
        private IconsHandler mIconsHandler;
        private PackageManager mPackageManager;

        private String mDefaultIconPack;
        private boolean mShouldRestart = false;

        @Override
        public void onCreate(Bundle savedInstance) {
            super.onCreate(savedInstance);
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            addPreferencesFromResource(R.xml.launcher_quick_preferences);

            mPrefs = Utilities.getPrefs(getActivity().getApplicationContext());
            mPrefs.registerOnSharedPreferenceChangeListener(this);

            mIconPackPref = findPreference(KEY_ICON_PACK);
            mIconPackPref.setOnPreferenceClickListener(preference -> {
                mIconsHandler.showDialog(getActivity());
                return true;
            });

            mPackageManager = getActivity().getPackageManager();
            mDefaultIconPack = mPrefs.getString(KEY_ICON_PACK, getString(R.string.icon_pack_default));
            mIconsHandler = IconCache.getIconsHandler(getActivity().getApplicationContext());
            updateIconPackEntry();

            mGridPref = findPreference(KEY_GRID_SIZE);
            if (mGridPref != null) {
                mGridPref.setOnPreferenceClickListener(preference -> {
                    setCustomGridSize();
                    return true;
                });

                mGridPref.setSummary(mPrefs.getString(KEY_GRID_SIZE, getDefaulGridSize()));
            }

            Preference more = findPreference("pref_more");
            if (more != null) {
                more.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(getActivity(), SettingsActivity.class);
                    getActivity().startActivity(intent);
                    // Don't kill the upcoming settings activity
                    mShouldRestart = false;
                    getActivity().finish();
                    return true;
                });
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            mIconsHandler.hideDialog();
        }

        @Override
        public void onDestroy() {
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
                    mGridPref.setSummary(mPrefs.getString(KEY_GRID_SIZE, getDefaulGridSize()));
                    mShouldRestart = true;
                    break;
                case KEY_ICON_PACK:
                    updateIconPackEntry();
                    break;
            }
        }

        private void setCustomGridSize() {
            int minValue = 3;
            int maxValue = 9;

            String storedValue = mPrefs.getString(KEY_GRID_SIZE, "4x5");
            Pair<Integer, Integer> currentValues = Utilities.extractCustomGrid(storedValue);

            LayoutInflater inflater = (LayoutInflater)
                    getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (inflater == null) {
                return;
            }
            View contentView = inflater.inflate(R.layout.dialog_custom_grid, null);
            NumberPicker columnPicker = (NumberPicker)
                    contentView.findViewById(R.id.dialog_grid_column);
            NumberPicker rowPicker = (NumberPicker)
                    contentView.findViewById(R.id.dialog_grid_row);

            columnPicker.setMinValue(minValue);
            rowPicker.setMinValue(minValue);
            columnPicker.setMaxValue(maxValue);
            rowPicker.setMaxValue(maxValue);
            columnPicker.setValue(currentValues.first);
            rowPicker.setValue(currentValues.second);

            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.grid_size_text)
                    .setView(contentView)
                    .setPositiveButton(R.string.grid_size_custom_positive, (dialog, i) -> {
                        String newValues = Utilities.getGridValue(columnPicker.getValue(),
                                rowPicker.getValue());
                        mPrefs.edit().putString(KEY_GRID_SIZE, newValues).apply();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private String getDefaulGridSize() {
            InvariantDeviceProfile profile = new InvariantDeviceProfile(getActivity());
            return Utilities.getGridValue(profile.numColumns, profile.numRows);
        }

        private void updateIconPackEntry() {
            ApplicationInfo info = null;
            String iconPack = mPrefs.getString(KEY_ICON_PACK, mDefaultIconPack);
            String summary = getString(R.string.icon_pack_system);
            Drawable icon = getResources().getDrawable(android.R.mipmap.sym_def_app_icon);

            if (!mIconsHandler.isDefaultIconPack()) {
                try {
                    info = mPackageManager.getApplicationInfo(iconPack, PackageManager.GET_META_DATA);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
                if (info != null) {
                    summary = mPackageManager.getApplicationLabel(info).toString();
                    icon = mPackageManager.getApplicationIcon(info);
                }
            }

            mIconPackPref.setSummary(summary);
            mIconPackPref.setIcon(icon);
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
    }
}