package com.android.launcher3;

import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.settings.SettingsProvider;

import com.cyngn.RemoteFolder.RemoteFolderUpdater;
import com.cyngn.RemoteFolder.RemoteFolderUpdater.RemoteFolderUpdateListener;
import com.cyngn.RemoteFolder.RemoteFolderUpdater.RemoteFolderInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages adding and removing the remote folder from the workspace.
 */
public class RemoteFolderManager {
    private static final String TAG = "RemoteFolderManager";

    private static final String REMOTE_APP_PACKAGE = "com.remote";
    private static final String REMOTE_APP_CLASS = "app";

    private final Launcher mLauncher;

    /** View which is displayed in the workspace **/
    private FolderIcon mRemoteFolder;
    /** Coordinates of the folder's position before being hidden **/
    private int[] mRemoteFolderCell;

    /** Remote apps shown in the drawer **/
    private ArrayList<AppInfo> mRemoteApps;

    public RemoteFolderManager(final Launcher launcher) {
        mLauncher = launcher;
        mRemoteFolderCell = new int[2];
    }

    public void setRemoteFolder(final FolderIcon remoteFolder) {
        mRemoteFolder = remoteFolder;
    }

    /**
     * Called when Launcher finishes binding items from the model.
     */
    public void bindFinished() {
        boolean remoteFolderEnabled = SettingsProvider.getBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_REMOTE_FOLDER,
                R.bool.preferences_interface_homescreen_remote_folder_default);
        if (remoteFolderEnabled && mRemoteFolder == null) {
            showRemoteFolder();
        } else if (!remoteFolderEnabled) {
            // We might load the remote folder on startup, but we shouldn't show it
            hideRemoteFolder();
        }
    }

    /**
     * Called when the setting for remote folder is updated.
     * @param newValue the new setting for remote folder
     */
    public void onSettingChanged(final boolean newValue) {
        if (newValue) {
            showRemoteFolder();
        } else {
            hideRemoteFolder();
        }
    }

    /**
     * Called when the remote folder is dropped into the delete area on the workspace.
     */
    public void onFolderDeleted() {
        hideRemoteFolder();
        SettingsProvider.putBoolean(mLauncher,
                SettingsProvider.SETTINGS_UI_HOMESCREEN_REMOTE_FOLDER, false);
        mLauncher.mOverviewSettingsPanel.notifyDataSetInvalidated();
    }

    /**
     * Called when the app drawer is opened.
     */
    public void onAppDrawerOpened() {
        // Don't actually look up the preference. Always use default value,
        // since app drawer recommendations cannot be removed.
        boolean remoteAppsEnabled = SettingsProvider.getBoolean(mLauncher, null,
                R.bool.preferences_interface_homescreen_remote_folder_default);

        if (remoteAppsEnabled) {
            syncRemoteApps();
        }
    }

    private void syncRemoteApps() {
        final AppDrawerListAdapter appDrawerListAdapter = mLauncher.getAppDrawerListAdapter();

        // Clear out old apps
        if (mRemoteApps != null) {
            appDrawerListAdapter.removeApps(mRemoteApps);
        }

        // Load enough apps to fill one row
        final int numDrawerColumns = appDrawerListAdapter.getNumColumns();
        mRemoteApps = new ArrayList<AppInfo>(numDrawerColumns);

        RemoteFolderUpdater updater = RemoteFolderUpdater.getInstance();
        updater.requestSync(mLauncher, numDrawerColumns, new RemoteFolderUpdateListener() {
            @Override
            public void onSuccess(List<RemoteFolderInfo> remoteFolderInfoList) {
                int i = 0;
                for (RemoteFolderInfo remoteFolderInfo : remoteFolderInfoList) {
                    // Create unique dummy component name so apps can be hashed into the drawer map.
                    ComponentName componentName =
                            new ComponentName(REMOTE_APP_PACKAGE + i++, REMOTE_APP_CLASS);
                    Intent intent = remoteFolderInfo.getIntent().setComponent(componentName);
                    AppInfo app = new AppInfo(intent, remoteFolderInfo.getTitle(),
                            remoteFolderInfo.getIcon(), UserHandleCompat.myUserHandle(), true);
                    mRemoteApps.add(app);
                }

                // Add apps to launcher
                appDrawerListAdapter.addApps(mRemoteApps);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "Failed to sync data for the remote folder's shortcuts. Reason: " + error);
            }
        });
    }

    private void showRemoteFolder() {
        int[] cell;
        long screen;
        long container;
        boolean findNewSpace = true;
        FolderInfo folderInfo = null;
        Workspace workspace = mLauncher.getWorkspace();

        // Check if we can re-add at our old location
        if (mRemoteFolder != null) {
            folderInfo = mRemoteFolder.getFolderInfo();
            CellLayout cellLayout = mLauncher.getCellLayout(
                    folderInfo.container, folderInfo.screenId);
            if (cellLayout != null
                    && !cellLayout.isOccupied(mRemoteFolderCell[0], mRemoteFolderCell[1])) {
                findNewSpace = false;
            }
        }
        if (findNewSpace) {
            // Try to find a new space to add.
            Pair<Long, int[]> space = LauncherModel.findNextAvailableIconSpace(mLauncher, null,
                    null, 0, new ArrayList<Long>(workspace.getWorkspaceScreenIds()));

            // All screens are full. Create a new screen.
            if (space == null) {
                workspace.addExtraEmptyScreen();
                screen = workspace.commitExtraEmptyScreen();
                cell = new int[2];
            } else {
                screen = space.first;
                cell = space.second;
            }

            container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        } else {
            screen = folderInfo.screenId;
            cell = mRemoteFolderCell;
            container = folderInfo.container;
        }

        // Create the folder if this is our first time showing it
        if (mRemoteFolder == null) {
            folderInfo = new FolderInfo();
            folderInfo.setTitle(mLauncher.getString(R.string.recommendations_title));
            folderInfo.setRemote(true);

            CellLayout cellLayout = mLauncher.getCellLayout(container, screen);
            mRemoteFolder = mLauncher.addFolder(cellLayout, container, screen,
                    cell[0], cell[1], folderInfo);

            mLauncher.getModel().syncRemoteFolder(folderInfo, mLauncher);
        } else {
            // Folder may be hidden by drop delete animation, so force visibility.
            mRemoteFolder.setVisibility(View.VISIBLE);
            workspace.addInScreen(mRemoteFolder, container, screen,
                    cell[0], cell[1], 1, 1, mLauncher.isWorkspaceLocked());
            mLauncher.getCellLayout(container, screen)
                    .getShortcutsAndWidgets().measureChild(mRemoteFolder);

            // Update the model
            folderInfo.cellX = cell[0];
            folderInfo.cellY = cell[1];
            folderInfo.container = container;
            folderInfo.screenId = screen;
            LauncherModel.updateItemInDatabase(mLauncher, folderInfo);
        }
    }

    private void hideRemoteFolder() {
        // Remote folder does not exist
        if (mRemoteFolder == null) {
            Log.e(TAG, "Remote folder is null");
            return;
        }

        FolderInfo info = mRemoteFolder.getFolderInfo();

        // Store our current location so we can try to re-add in the same spot later.
        mRemoteFolderCell[0] = info.cellX;
        mRemoteFolderCell[1] = info.cellY;

        // Clear the spot
        mLauncher.getCellLayout(info.container, info.screenId).removeView(mRemoteFolder);
        info.cellX = -1;
        info.cellY = -1;
        LauncherModel.updateItemInDatabase(mLauncher, info);

        // We may be removing from a screen we're on alone, so remove it if necessary.
        mLauncher.getWorkspace().removeExtraEmptyScreen(false, true);
    }
}
