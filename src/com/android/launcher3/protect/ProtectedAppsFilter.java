package com.android.launcher3.protect;

import android.content.ComponentName;
import android.content.Context;

import com.android.launcher3.AppFilter;

public class ProtectedAppsFilter extends AppFilter {
    private ProtectedDatabaseHelper mDbHelper;

    public ProtectedAppsFilter(Context context) {
        mDbHelper = ProtectedDatabaseHelper.getInstance(context);
    }

    @Override
    public boolean shouldShowApp(ComponentName app) {
        return !mDbHelper.isPackageProtected(app.getPackageName());
    }
}
