package com.cyngn.RemoteFolder;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import java.util.List;

public class RemoteFolderUpdater {

    public interface RemoteFolderUpdateListener {
        void onSuccess(List<RemoteFolderInfo> remoteFolderInfoList);
        void onFailure(String error);
    }

    /**
     * Requests data needed by remote folders.
     * @param context
     * @param size
     * @param listener
     */
    public synchronized void requestSync(Context context, final int size, final RemoteFolderUpdateListener listener) {
        if (listener != null) {
            listener.onFailure("RemoteFolderUpdater may not have been properly setup");
        }
    }

    /**
     * Holds important information that the launcher will need for each item in the remote folder.
     */
    public class RemoteFolderInfo {

        public void setRecommendationData(View view) {
            return;
        }

        public String getTitle() {
            return null;
        }

        public Bitmap getIcon() {
            return null;
        }

        public String getIconUrl() {
            return null;
        }

        public Intent getIntent() {
            return null;
        }
    }
}