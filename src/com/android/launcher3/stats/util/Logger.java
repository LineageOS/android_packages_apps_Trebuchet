package com.android.launcher3.stats.util;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

/**
 * <pre>
 *     Metrics debug logging
 * </pre>
 */
public class Logger {

    private static final String TAG = "TrebuchetStats";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG) ||
             "eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE);

    /**
     * Log a debug message
     *
     * @param tag {@link String}
     * @param msg {@link String }
     * @throws IllegalArgumentException {@link IllegalArgumentException}
     */
    public static void logd(String tag, String msg) throws IllegalArgumentException {
        if (TextUtils.isEmpty(tag)) {
            throw new IllegalArgumentException("'tag' cannot be empty!");
        }
        if (TextUtils.isEmpty(msg)) {
            throw new IllegalArgumentException("'msg' cannot be empty!");
        }
        if (DEBUG) {
            Log.d(TAG, tag + " [ " + msg + " ]");
        }
    }
}
