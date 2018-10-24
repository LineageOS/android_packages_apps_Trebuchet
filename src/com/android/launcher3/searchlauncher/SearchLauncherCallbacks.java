package com.android.launcher3.searchlauncher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherCallbacks;
import com.android.launcher3.SettingsActivity;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ComponentKeyMapper;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link LauncherCallbacks} which integrates the Google -1 screen
 * with launcher
 */
public class SearchLauncherCallbacks implements LauncherCallbacks, OnSharedPreferenceChangeListener {
    public static final String SEARCH_PACKAGE = "com.google.android.googlequicksearchbox";

    private final Launcher mLauncher;

    private OverlayCallbackImpl mOverlayCallbacks;
    private LauncherClient mLauncherClient;

    private boolean mStarted;
    private boolean mResumed;
    private boolean mAlreadyOnHome;

    public SearchLauncherCallbacks(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = Utilities.getPrefs(mLauncher);
        mOverlayCallbacks = new OverlayCallbackImpl(mLauncher);
        mLauncherClient = new LauncherClient(mLauncher, mOverlayCallbacks, getClientOptions(prefs));
        mOverlayCallbacks.setClient(mLauncherClient);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDetachedFromWindow() {
        mLauncherClient.onDetachedFromWindow();
    }

    @Override
    public void onAttachedToWindow() {
        mLauncherClient.onAttachedToWindow();
    }

    @Override
    public void onHomeIntent() {
        mLauncherClient.hideOverlay(mAlreadyOnHome);
    }

    @Override
    public void onResume() {
        mResumed = true;
        if (mStarted) {
            mAlreadyOnHome = true;
        }
        mLauncherClient.onResume();
    }

    @Override
    public void onPause() {
        mResumed = false;
        mLauncherClient.onPause();
    }

    @Override
    public void onStart() {
        mStarted = true;
        mLauncherClient.onStart();
    }

    @Override
    public void onStop() {
        mStarted = false;
        if (!mResumed) {
            mAlreadyOnHome = false;
        }
        mLauncherClient.onStop();
    }

    @Override
    public void onDestroy() {
        mLauncherClient.onDestroy();
        Utilities.getPrefs(mLauncher).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (SettingsActivity.KEY_MINUS_ONE.equals(key)) {
            mLauncherClient.setClientOptions(getClientOptions(prefs));
        }
    }

    @Override
    public void preOnCreate() { }

    @Override
    public void preOnResume() { }

    @Override
    public void onSaveInstanceState(Bundle outState) { }

    @Override
    public void onPostCreate(Bundle savedInstanceState) { }

    @Override
    public void onNewIntent(Intent intent) { }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) { }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) { }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) { }

    @Override
    public boolean handleBackPressed() {
        return false;
    }

    @Override
    public void onTrimMemory(int level) { }

    @Override
    public void onLauncherProviderChange() { }

    @Override
    public void finishBindingItems(boolean upgradePath) { }

    @Override
    public void bindAllApplications(ArrayList<AppInfo> apps) { }

    @Override
    public void onWorkspaceLockedChanged() { }

    @Override
    public void onInteractionBegin() { }

    @Override
    public void onInteractionEnd() { }

    @Override
    public boolean hasCustomContentToLeft() {
        return false;
    }

    @Override
    public void populateCustomContentContainer() { }

    @Override
    public View getQsbBar() {
        return null;
    }

    @Override
    public Bundle getAdditionalSearchWidgetOptions() {
        return new Bundle();
    }

    @Override
    public boolean shouldMoveToDefaultScreenOnHomeIntent() {
        return true;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public List<ComponentKeyMapper<AppInfo>> getPredictedApps() {
        return null;
    }

    @Override
    public int getSearchBarHeight() {
        return SEARCH_BAR_HEIGHT_NORMAL;
    }

    @Override
    public boolean startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData) {
        return false;
    }

    private LauncherClient.ClientOptions getClientOptions(SharedPreferences prefs) {
        boolean hasPackage = Utilities.hasPackageInstalled(mLauncher, SEARCH_PACKAGE);
        boolean isEnabled = prefs.getBoolean(SettingsActivity.KEY_MINUS_ONE, true);

        return new LauncherClient.ClientOptions(hasPackage && isEnabled,
                true, /* enableHotword */
                true /* enablePrewarming */
        );
    }
}
