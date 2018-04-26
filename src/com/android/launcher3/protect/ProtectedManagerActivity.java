package com.android.launcher3.protect;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class ProtectedManagerActivity extends Activity implements
        ProtectedAppsAdapter.IProtectedApp {
    private static final int REQUEST_AUTH_CODE = 92;

    private RecyclerView mRecyclerView;
    private LinearLayout mLoadingLayout;

    private ProtectedAppsAdapter mAdapter;
    private ProtectedDatabaseHelper mDbHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.activity_protected_apps_manager);
        mRecyclerView = findViewById(R.id.protected_apps_list);
        mLoadingLayout = findViewById(R.id.protected_apps_loading);

        mAdapter = new ProtectedAppsAdapter(this);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);

        mDbHelper = ProtectedDatabaseHelper.getInstance(this);

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
        if (requestCode != REQUEST_AUTH_CODE || resultCode != Activity.RESULT_OK) {
            return;
        }

        showUi();
    }

    @Override
    public void onItemChanged(String packageName, boolean isNowHidden) {
        if (isNowHidden) {
            mDbHelper.addApp(packageName);
        } else {
            mDbHelper.removeApp(packageName);
        }
    }

    private void authenticate() {
        String title = getString(R.string.protected_apps_manager_name);
        String message = getString(R.string.protected_apps_auth_manager);

        KeyguardManager manager = getSystemService(KeyguardManager.class);
        if (manager == null) {
            finish();
            return;
        }

        Intent intent = manager.createConfirmDeviceCredentialIntent(title, message);
        if (intent == null) {
            Toast.makeText(this, R.string.protected_apps_no_lock_error, Toast.LENGTH_LONG)
                    .show();
            showUi();
            return;
        }
        startActivityForResult(intent, REQUEST_AUTH_CODE);
    }

    private void showUi() {
        PackageManager manager = getPackageManager();

        try {
            if (manager == null) {
                throw new NullPointerException("packageManager is null");
            }

            LoadComponentsTask task = new LoadComponentsTask(mDbHelper, manager);
            task.execute();

            List<ProtectedComponent> components = task.get();
            mAdapter.updateAppList(components);
            mLoadingLayout.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        } catch (NullPointerException | ExecutionException | InterruptedException e) {
            finish();
        }
    }

    static class LoadComponentsTask extends AsyncTask<Void, Void,
            List<ProtectedComponent>> {
        @NonNull
        private ProtectedDatabaseHelper mDbHelper;
        @NonNull
        private PackageManager mPackageManager;

        LoadComponentsTask(@NonNull ProtectedDatabaseHelper dbHelper,
                           @NonNull PackageManager packageManager) {
            mDbHelper = dbHelper;
            mPackageManager = packageManager;
        }

        @Override
        public List<ProtectedComponent> doInBackground(Void... args) {
            Intent filter = new Intent(Intent.ACTION_MAIN, null);
            filter.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> apps = mPackageManager.queryIntentActivities(filter,
                    PackageManager.GET_META_DATA);
            List<ProtectedComponent> components = new ArrayList<>();

            for (ResolveInfo info : apps) {
                try {
                    String packageName = info.activityInfo.packageName;
                    String appLabel = mPackageManager.getApplicationLabel(
                            mPackageManager.getApplicationInfo(packageName,
                                    PackageManager.GET_META_DATA)).toString();

                    components.add(new ProtectedComponent(
                            packageName,
                            info.loadIcon(mPackageManager),
                            appLabel,
                            mDbHelper.isPackageProtected(packageName)));
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }

            Collections.sort(components, (a, b) -> a.label.compareTo(b.label));

            return components;
        }
    }
}
