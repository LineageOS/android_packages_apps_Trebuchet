package com.android.launcher3;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Manages adding and removing the remote folder from the workspace.
 */
public class RemoteFolderManager {

    public RemoteFolderManager(final Launcher launcher) { }

    /**
     * Create a remote folder view.
     * @param icon folder icon view on the workspace.
     * @return a view for the remote folder.
     */
    public Folder createRemoteFolder(final FolderIcon icon, ViewGroup root) { return null; }

    /**
     * Called when Launcher finishes binding items from the model.
     */
    public void bindFinished() { }

    /**
     * Called when the setting for remote folder is updated.
     * @param newValue the new setting for remote folder
     */
    public void onSettingChanged(final boolean newValue) { }

    /**
     * Called when the remote folder is dropped into the delete area on the workspace.
     */
    public void onFolderDeleted() { }

    /**
     * Get the original icons (with no badge) to be used for the RemoteFolder's FolderIcon
     * @param items views currently in the folder
     * @return an array of drawables for each view
     */
    public Drawable[] getDrawablesForFolderIcon(ArrayList<View> items) {
        return null;
    }

    /**
     * Called when the app drawer is opened.
     */
    public void onAppDrawerOpened() { }

    /**
     * Called when new apps are added to launcher.
     * @param apps list of added apps.
     */
    public void onBindAddApps(ArrayList<AppInfo> apps) { }

    /**
     * Called when the info icon is clicked
     */
    public void onInfoIconClicked() { }

    /**
     * Change the appearance of FolderIcon for our RemoteFolder by adding a badge
     * @param icon the FolderIcon to update
     * @return a FolderIcon with an added ImageView
     */
    public static FolderIcon addBadgeToFolderIcon(FolderIcon icon) {
        return icon;
    }

    /**
     * Called when the view holder is created for the remote header.
     * @param holder remote view holder.
     */
    public void onCreateViewHolder(final AppDrawerListAdapter.ViewHolder holder) { }
    /**
     * Called when the view holder is bound for the remote header.
     * @param holder remote view holder.
     * @param indexedInfo header info.
     */
    public void onBindViewHolder(final AppDrawerListAdapter.ViewHolder holder,
                                 final AppDrawerListAdapter.AppItemIndexedInfo indexedInfo) { }

    /**
     * Called when the remote folder is opened
     */
    public void onFolderOpened() { }

    /**
     * Called when the remote folder creates a shortcut view.
     * @param view shortcut view for ad.
     * @param info model info for ad.
     */
    public void onShortcutCreated(View view, ShortcutInfo info) { }
}
