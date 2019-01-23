package com.android.launcher3.lineage.hidden;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import com.android.launcher3.lineage.hidden.db.HiddenComponent;
import com.android.launcher3.lineage.hidden.db.HiddenDatabaseHelper;

public class UpdateItemVisibilityTask extends AsyncTask<HiddenComponent, Void, Boolean> {
    @NonNull
    private HiddenDatabaseHelper mDbHelper;
    @NonNull
    private UpdateCallback mCallback;

    UpdateItemVisibilityTask(@NonNull HiddenDatabaseHelper dbHelper,
                             @NonNull UpdateCallback callback) {
        mDbHelper = dbHelper;
        mCallback = callback;
    }

    @Override
    protected Boolean doInBackground(HiddenComponent... hiddenComponents) {
        if (hiddenComponents.length < 1) {
            return false;
        }

        HiddenComponent component = hiddenComponents[0];
        component.invertVisibility();
        String pkgName = component.getPackageName();

        if (component.isHidden()) {
            mDbHelper.addApp(pkgName);
        } else {
            mDbHelper.removeApp(pkgName);
        }

        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        mCallback.onUpdated(result);
    }

    interface UpdateCallback {
        void onUpdated(boolean result);
    }
}
