package com.cyanogenmod.trebuchet;

import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

public class ApplyIconPackReceiver extends BroadcastReceiver {

    public static final String EXTRA_ICON_PACK_NAME =
            "icon_pack_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        String pkgName = intent.getStringExtra(EXTRA_ICON_PACK_NAME);
        if (!TextUtils.isEmpty(pkgName)) {
            PreferencesProvider.Interface.General.setIconPack(context, pkgName);
            SharedPreferences preferences = context.getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, 0);
            preferences.edit().commit();
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
