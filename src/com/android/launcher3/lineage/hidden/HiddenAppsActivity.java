/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.launcher3.lineage.hidden;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.lineage.hidden.db.HiddenComponent;
import com.android.launcher3.lineage.hidden.db.HiddenDatabaseHelper;

import java.util.List;

public class HiddenAppsActivity extends Activity implements HiddenAppsAdapter.Listener,
        LoadHiddenComponentsTask.Callback {
    private static final int REQUEST_AUTH_CODE = 92;

    private RecyclerView mRecyclerView;
    private LinearLayout mLoadingView;
    private ProgressBar mProgressBar;

    private HiddenDatabaseHelper mDbHelper;
    private HiddenAppsAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.activity_hidden_apps);
        mRecyclerView = findViewById(R.id.hidden_apps_list);
        mLoadingView = findViewById(R.id.hidden_apps_loading);
        mProgressBar = findViewById(R.id.hidden_apps_progress_bar);

        mAdapter = new HiddenAppsAdapter(this);
        mDbHelper = HiddenDatabaseHelper.getInstance(this);

        authenticate();
    }

    @Override
    public void onPause() {
        LauncherAppState state = LauncherAppState.getInstanceNoCreate();
        if (state != null) {
            state.getModel().forceReload();
        }

        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_AUTH_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                showUi();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onItemChanged(@NonNull HiddenComponent component) {
        new UpdateItemVisibilityTask(mDbHelper).execute(component);
    }

    @Override
    public void onLoadListProgress(int progress) {
        mProgressBar.setProgress(progress);
    }

    @Override
    public void onLoadCompleted(List<HiddenComponent> result) {
        mLoadingView.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
        mAdapter.update(result);
    }

    private void authenticate() {
        KeyguardManager manager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                getSystemService(KeyguardManager.class) :
                (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (manager == null) {
            throw new NullPointerException("No KeyguardManager found!");
        }

        String title = getString(R.string.hidden_apps_manager_name);
        String message = getString(R.string.hidden_apps_auth_manager);
        Intent intent = manager.createConfirmDeviceCredentialIntent(title, message);

        if (intent != null) {
            startActivityForResult(intent, REQUEST_AUTH_CODE);
        }

        Toast.makeText(this, R.string.hidden_apps_no_lock_error,
                Toast.LENGTH_LONG).show();
        finish();
    }

    private void showUi() {
        mLoadingView.setVisibility(View.VISIBLE);

        new LoadHiddenComponentsTask(mDbHelper, getPackageManager(), this).execute();
    }

    static class UpdateItemVisibilityTask extends AsyncTask<HiddenComponent, Void, Boolean> {
        @NonNull
        HiddenDatabaseHelper mDbHelper;

        UpdateItemVisibilityTask(@NonNull HiddenDatabaseHelper dbHelper) {
            mDbHelper = dbHelper;
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
    }
}
