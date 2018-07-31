package com.android.launcher3.lineage;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public class LineageUtils {

    public static boolean isPackageEnabled(Context context, String pkgName) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(pkgName, 0);
            return ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

}
