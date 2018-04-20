package com.android.launcher3.allapps;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ComponentKeyMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PredictiveAppsProvider {
    private static final String TAG = "PredictiveAppsProvider";

    private static final int NUM_PREDICTIVE_APPS_TO_HOLD = 9; // since we can't have more than 9 columns

    private static final String PREDICTIVE_APPS_KEY = "predictive_apps";
    private static final String TOP_PREDICTIVE_APPS_KEY = "top_predictive_apps";

    private SharedPreferences mPrefs;

    public PredictiveAppsProvider(Context context) {
        this.mPrefs = Utilities.getPrefs(context.getApplicationContext());
    }

    public void updateComponentCount(ComponentName component) {
        if (component == null) {
            Log.w(TAG, "Can not update component count because component is null!");
            return;
        }

        String key = buildComponentString(component);
        long current = mPrefs.getLong(key, 0);

        mPrefs.edit().putLong(key, current + 1).apply();

        // ensure that the set of predictive apps contains this one
        Set<String> predictiveApps =
                mPrefs.getStringSet(PREDICTIVE_APPS_KEY, new HashSet<>());
        if (!predictiveApps.contains(key)) {
            predictiveApps.add(key);
            mPrefs.edit().putStringSet(PREDICTIVE_APPS_KEY, predictiveApps).apply();
        }
    }

    public void updateTopPredictedApps() {
        new Thread(() -> {
            List< PredictedApp > allPredictions = new ArrayList<>();
            Set<String> predictiveAppsSet =
                    mPrefs.getStringSet(PREDICTIVE_APPS_KEY, new HashSet<>());

            for (String s : predictiveAppsSet) {
                allPredictions.add(new PredictedApp(buildComponentFromString(s),
                        mPrefs.getLong(s, 0)));
            }

            Collections.sort(allPredictions, (result1, result2) ->
                    Long.valueOf(result2.count).compareTo(result1.count));

            if (allPredictions.size() > NUM_PREDICTIVE_APPS_TO_HOLD) {
                allPredictions = allPredictions.subList(0, NUM_PREDICTIVE_APPS_TO_HOLD);
            }

            mPrefs.edit().putString(TOP_PREDICTIVE_APPS_KEY,
                    buildStringFromAppList(allPredictions)).apply();
        }).start();
    }

    public List<ComponentKeyMapper<AppInfo>> getPredictions() {
        String predictions = mPrefs.getString(TOP_PREDICTIVE_APPS_KEY, "");
        if (predictions.isEmpty()) {
            return new ArrayList<>();
        }

        String[] topPredictions = predictions.split(" ");
        List<ComponentKeyMapper<AppInfo>> keys = new ArrayList<>();

        for (int i = 0; i < topPredictions.length - 1; i++) {
            keys.add(buildComponentKey(topPredictions[i] + " " + topPredictions[i + 1]));
        }

        return keys;
    }

    private String buildStringFromAppList(List<PredictedApp> apps) {
        StringBuilder string = new StringBuilder();
        for (PredictedApp app : apps) {
            string.append(buildComponentString(app.component)).append(" ");
        }

        try {
            return string.substring(0, string.length() - 1);
        } catch (StringIndexOutOfBoundsException e) {
            return "";
        }
    }

    private String buildComponentString(ComponentName component) {
        return component.getPackageName() + " " + component.getClassName();
    }

    private ComponentName buildComponentFromString(String key) {
        String[] arr = key.split(" ");
        return new ComponentName(arr[0], arr[1]);
    }

    private ComponentKeyMapper<AppInfo> buildComponentKey(String key) {
        return buildComponentKey(buildComponentFromString(key));
    }

    private ComponentKeyMapper<AppInfo> buildComponentKey(ComponentName component) {
        return new ComponentKeyMapper<>(new ComponentKey(component, Process.myUserHandle()));
    }

    private class PredictedApp {
        public ComponentName component;
        public long count;

        public PredictedApp(ComponentName component, long count) {
            this.component = component;
            this.count = count;
        }
    }

}