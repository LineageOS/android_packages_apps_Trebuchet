/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.launcher3;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;


/**
 * Represents a set of icons which display recommended apps.
 */
public class RemoteFolder extends Folder {
    public static final String TAG = "RemoteFolder";
    public static final int MAX_ITEMS = 6;
    private ScrollView mContentScrollView;
    private ImageView mFolderInfoIcon;
    private ImageView mRequiredIcon;
    private TextView mFolderHelpText;

    private enum LayoutStates {
        NO_CONNECTION,
        FOLDER_CONTENT,
        SHOW_HELP
    }
    private LayoutStates mLayoutState = LayoutStates.FOLDER_CONTENT;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs   The attribtues set containing the Workspace's customization values.
     */
    public RemoteFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Creates a new UserFolder, inflated from R.layout.remote_folder.
     *
     * @param context The application's context.
     * @param root The {@link View} parent of this folder.
     * @return A new UserFolder.
     */
    static RemoteFolder fromXml(Context context, ViewGroup view) {
        return (RemoteFolder) LayoutInflater.from(context)
                .inflate(R.layout.remote_folder, view, false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLayoutState = (isConnected()) ? LayoutStates.FOLDER_CONTENT : LayoutStates.NO_CONNECTION;

        mContentScrollView = (ScrollView) findViewById(R.id.scroll_view);

        mFolderInfoIcon = (ImageView) findViewById(R.id.folder_info);
        mFolderInfoIcon.setOnClickListener(this);

        mFolderHelpText = (TextView) findViewById(R.id.help_text_view);

        mRequiredIcon = (ImageView) findViewById(R.id.required_icon);
        mRequiredIcon.setOnClickListener(this);
    }

    public void onClick(View v) {
        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            mLauncher.onClick(v);
        }

        switch (v.getId()) {
            case R.id.folder_info:
                toggleInfoPane();
                break;
            case R.id.required_icon:
                handleRequiredIconClick();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        // Eat the event without doing anything. We do not allow users to remove items.
        return true;
    }

    private void toggleInfoPane() {
        if (mLayoutState == LayoutStates.SHOW_HELP) {
            mLayoutState = (isConnected() || getItemCount() > 0) ? LayoutStates.FOLDER_CONTENT : LayoutStates.NO_CONNECTION;
        } else {
            mLayoutState = LayoutStates.SHOW_HELP;
        }
        showViews();
    }

    private void handleRequiredIconClick() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().getString(R.string.required_icon_link)));
        mContext.startActivity(intent);
    }

    @Override
    public void animateOpen(Workspace workspace, int[] folderTouch) {
        super.animateOpen(workspace, folderTouch);
        mLayoutState = (isConnected() || getItemCount() > 0) ? LayoutStates.FOLDER_CONTENT : LayoutStates.NO_CONNECTION;
        showViews();
    }

    @Override
    public void onAdd(ShortcutInfo item) {
        item.setIcon(applyBadgeToShortcutIcon(item));
        super.onAdd(item);

        // Register each view with our updater for click handling.
        RemoteFolderUpdater updater = RemoteFolderUpdater.getInstance();
        updater.registerViewForInteraction(getViewForInfo(item), item.getIntent());

        final int count = getItemCount();
        Log.d(TAG, "RemoteFolder onAdd(): content size: " + count);

        // No need for an isConnected() check since we just got an item, but don't update if showing help UX
        if (mLayoutState != LayoutStates.SHOW_HELP) {
            mLayoutState = LayoutStates.FOLDER_CONTENT;
            requestLayout();
        }
    }

    private boolean isConnected() {
        return RemoteFolderUpdater.getInstance().isNetworkConnected(mContext);
    }

    private Bitmap applyBadgeToShortcutIcon(ShortcutInfo info) {

        int downloadIconDimens = (int)Utilities.convertDpToPixel(16, mContext);
        int mainImageDimens = downloadIconDimens * 2;   // double the icon height/width

        // Make sure the badge is scaled to the parent icon, then offset so it looks offset in the corner
        Bitmap badge = BitmapFactory.decodeResource(getResources(), R.drawable.download_badge);
        badge = Bitmap.createScaledBitmap(badge, downloadIconDimens, downloadIconDimens, false);

        LauncherAppState app = LauncherAppState.getInstance();
        IconCache iconCache = app.getIconCache();
        Bitmap mainImage = Bitmap.createScaledBitmap(info.getIcon(iconCache), mainImageDimens, mainImageDimens, false);

        int offsetX = badge.getWidth() / 2;
        int offsetY = badge.getHeight() / 2;

        int width = mainImage.getWidth() + (offsetX * 2);
        int height = mainImage.getHeight()  + (offsetY * 2);

        Bitmap finalImage = Bitmap.createBitmap(width, height, mainImage.getConfig());
        Canvas canvas = new Canvas(finalImage);
        canvas.drawBitmap(mainImage, offsetX, offsetY, null);
        canvas.drawBitmap(badge, canvas.getWidth() - badge.getWidth(), canvas.getHeight() - badge.getHeight(), null);

        mainImage.recycle();
        badge.recycle();

        return finalImage;
    }

    private void showViews() {
        if (getItemCount() == 0) {
            setupContentDimensions(MAX_ITEMS);
        }

        if (mLayoutState == LayoutStates.FOLDER_CONTENT) {
            mContentScrollView.setVisibility(VISIBLE);
            mFolderHelpText.setVisibility(GONE);
            mFolderInfoIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_launcher_info_normal_holo));
            mRequiredIcon.setVisibility(VISIBLE);
        } else if (mLayoutState == LayoutStates.NO_CONNECTION) {
            mFolderHelpText.setText(getResources().getString(R.string.offline_help_text));
            mFolderHelpText.setVisibility(VISIBLE);
            mContentScrollView.setVisibility(GONE);
            mFolderInfoIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_launcher_info_normal_holo));
            mRequiredIcon.setVisibility(VISIBLE);
        } else if (mLayoutState == LayoutStates.SHOW_HELP) {
            mFolderHelpText.setText(getResources().getString(R.string.recommendations_help_text));
            mFolderHelpText.setVisibility(VISIBLE);
            mContentScrollView.setVisibility(GONE);
            mFolderInfoIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_launcher_clear_normal_holo));
            mRequiredIcon.setVisibility(GONE);
        }

        this.requestLayout();
    }
}
