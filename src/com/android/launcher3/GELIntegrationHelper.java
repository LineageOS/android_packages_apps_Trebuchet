package com.android.launcher3;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.service.gesture.EdgeGestureManager;
import com.android.internal.util.gesture.EdgeGesturePosition;

import java.util.List;

/**
 * A singleton wrapper class for GEL Integration.
 * Requires EdgeGestureManager functionality that is only available
 * in CyanogenMod.
 */
public class GELIntegrationHelper {
    // The Intent for the search activity (resolves to Google Now when installed)
    public final static String INTENT_ACTION_ASSIST = "android.intent.action.ASSIST";

    private static final String GEL_ACTIVITY = "com.google.android.velvet.ui.VelvetActivity";
    private static final String GEL_PACKAGE_NAME = "com.google.android.googlequicksearchbox";

    private EdgeGestureManager.EdgeGestureActivationListener mEdgeGestureActivationListener = null;
    private static GELIntegrationHelper sInstance;

    private GELIntegrationHelper() {}

    public static GELIntegrationHelper getInstance() {
        if(sInstance == null) {
            sInstance = new GELIntegrationHelper();
        }
        return sInstance;
    }

    /**
     * 1. Registers an EdgeGestureActivationListener with the EdgeGestureManager so that the user can return to
     *    Trebuchet when they swipe from the right edge of the device.
     * 2. Starts the Google Now Activity with an exit_out_right transition animation so that the new Activity appears to slide in
     *    as another screen (similar to GEL).
     */
    public void registerSwipeBackGestureListenerAndStartGEL(final Activity launcherActivity) {
        EdgeGestureManager edgeGestureManager = EdgeGestureManager.getInstance();
        if(mEdgeGestureActivationListener == null) {
            mEdgeGestureActivationListener = new EdgeGestureManager.EdgeGestureActivationListener() {
                ActivityManager mAm = (ActivityManager) launcherActivity.getSystemService(Activity.ACTIVITY_SERVICE);

                @Override
                public void onEdgeGestureActivation(int touchX, int touchY, EdgeGesturePosition position, int flags) {
                    // Retrieve the top level activity information
                    List< ActivityManager.RunningTaskInfo > taskInfo = mAm.getRunningTasks(1);
                    ComponentName topActivityComponentInfo = taskInfo.get(0).topActivity;
                    String topActivityClassName = topActivityComponentInfo.getClassName();
                    String topActivityPackageName = topActivityComponentInfo.getPackageName();

                    // If the top level activity is Google Now, return to home. Otherwise, do nothing.
                    if(GEL_ACTIVITY.equals(topActivityClassName) && GEL_PACKAGE_NAME.equals(topActivityPackageName)) {
                        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        homeIntent.addCategory(Intent.CATEGORY_HOME);
                        launcherActivity.startActivity(homeIntent);
                        launcherActivity.overridePendingTransition(0, R.anim.exit_out_left);
                        dropEventsUntilLift();
                    }
                }
            };
            edgeGestureManager.setEdgeGestureActivationListener(mEdgeGestureActivationListener);
        }
        mEdgeGestureActivationListener.restoreListenerState();
        edgeGestureManager.updateEdgeGestureActivationListener(mEdgeGestureActivationListener, 0x01 << 2);

        // Start the Google Now Activity
        Intent i = new Intent(INTENT_ACTION_ASSIST);
        launcherActivity.startActivity(i);
        launcherActivity.overridePendingTransition(0, R.anim.exit_out_right);
    }

    /**
     * Handle necessary cleanup and reset tasks for GEL integration, to be called from onResume.
     */
    public void handleGELResume() {
        // If there is an active EdgeGestureActivationListener for GEL integration,
        // it should stop listening when we have resumed the launcher.
        if(mEdgeGestureActivationListener != null) {
            EdgeGestureManager edgeGestureManager = EdgeGestureManager.getInstance();
            // Update the listener so it is not listening to any postiions (-1)
            edgeGestureManager.updateEdgeGestureActivationListener(mEdgeGestureActivationListener, -1);
        }
    }

}
