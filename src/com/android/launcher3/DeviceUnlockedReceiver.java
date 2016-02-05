package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.HashSet;
import java.util.Set;

public class DeviceUnlockedReceiver extends BroadcastReceiver {
    public static final String INTENT_ACTION = Intent.ACTION_USER_PRESENT;

    private final Set<DeviceUnlockedListener> mListeners;

    interface DeviceUnlockedListener {
        void onDeviceUnlocked();
    }

    public DeviceUnlockedReceiver() {
        mListeners = new HashSet<DeviceUnlockedListener>();
    }

    public void registerListener(final DeviceUnlockedListener listener) {
        mListeners.add(listener);
    }

    public void deregisterListener(final DeviceUnlockedListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(INTENT_ACTION)) return;

        for (DeviceUnlockedListener listener: mListeners) {
            listener.onDeviceUnlocked();
        }
    }
}
