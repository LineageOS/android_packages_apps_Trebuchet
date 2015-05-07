package com.android.launcher3.stats.internal.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import com.android.launcher3.LauncherApplication;
import com.android.launcher3.stats.external.StatsUtil;
import com.android.launcher3.stats.external.TrackingBundle;
import com.android.launcher3.stats.internal.db.DatabaseHelper;
import com.android.launcher3.stats.internal.model.CountAction;
import com.android.launcher3.stats.internal.model.CountOriginByPackageAction;
import com.android.launcher3.stats.internal.model.ITrackingAction;
import com.android.launcher3.stats.internal.model.TrackingEvent;
import com.android.launcher3.stats.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * <pre>
 *     Service that starts on a timer and handles aggregating events and sending them to
 *     CyanogenStats
 * </pre>
 *
 * @see {@link IntentService}
 */
public class AggregationIntentService extends IntentService {

    // Constants
    private static final String TAG = AggregationIntentService.class.getSimpleName();
    private static final String TRACKING_ID = "com.cyanogenmod.trebuchet";
    public static final String ACTION_AGGREGATE_AND_TRACK =
            "com.cyanogenmod.filemanager.AGGREGATE_AND_TRACK";
    private static final List<ITrackingAction> TRACKED_ACTIONS = new ArrayList<ITrackingAction>() {
        {
            add(new CountAction());
            add(new CountOriginByPackageAction());
        }
    };
    private static final String KEY_LAST_TIME_RAN = "last_time_stats_ran";

    // Members
    private DatabaseHelper mDatabaseHelper = null;
    private int mInstanceId = -1;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public AggregationIntentService() {
        super(AggregationIntentService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (ACTION_AGGREGATE_AND_TRACK.equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putLong(KEY_LAST_TIME_RAN, System.currentTimeMillis()).apply();
            mInstanceId = (int) System.currentTimeMillis();
            mDatabaseHelper = DatabaseHelper.createInstance(this);
            performAggregation();
            deleteTrackingEventsForInstance();
            handleNonEventMetrics();
        }
    }

    private void performAggregation() {

        // Iterate available categories
        for (TrackingEvent.Category category : TrackingEvent.Category.values()) {

            // Fetch the events from the database based on the category
            List<TrackingEvent> eventList =
                    mDatabaseHelper.getTrackingEventsByCategory(mInstanceId, category);

            Logger.logd(TAG, "Event list size: " + eventList.size());
            // Short circuit if no events for the category
            if (eventList.size() < 1) {
                continue;
            }

            // Now crunch the data into actionable events for the server
            for (ITrackingAction action : TRACKED_ACTIONS) {
                try {
                    for (Bundle bundle : action.createTrackingBundles(TRACKING_ID, category,
                            eventList)) {
                        performTrackingCall(bundle);
                    }
                } catch (NullPointerException e) {
                    Log.e(TAG, "NPE fetching bundle list!", e);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Illegal argument!", e);
                }
            }

        }
    }

    private void deleteTrackingEventsForInstance() {
        mDatabaseHelper.deleteEventsByInstanceId(mInstanceId);
    }

    /**
     * These are metrics that are not event based and need a snapshot every INTERVAL
     */
    private void handleNonEventMetrics() {
        sendPageCountStats();
        sendWidgetCountStats();

    }

    private void sendPageCountStats() {
        int pageCount = LauncherApplication.getLauncher().getWorkspace().getPageCount();
        Bundle bundle = TrackingBundle
                .createTrackingBundle(TRACKING_ID, TrackingEvent.Category.HOMESCREEN_PAGE.name(),
                        "count");
        bundle.putInt(TrackingEvent.KEY_VALUE, pageCount);
        StatsUtil.sendEvent(this, bundle);
    }

    private void sendWidgetCountStats() {
        int widgetCount = LauncherApplication.getLauncher().getWorkspace().getWidgetCount();
        Bundle bundle = TrackingBundle
                .createTrackingBundle(TRACKING_ID, TrackingEvent.Category.WIDGET.name(), "count");
        bundle.putInt(TrackingEvent.KEY_VALUE, widgetCount);
        StatsUtil.sendEvent(this, bundle);

    }

    private void performTrackingCall(Bundle bundle) throws IllegalArgumentException {
        StatsUtil.sendEvent(this, bundle);
    }

    /**
     * Launch the aggregation service
     *
     * @param context {@link Context}
     * @throws IllegalArgumentException {@link IllegalArgumentException}
     */
    public static void launchService(Context context) throws IllegalArgumentException {
        if (context == null) {
            throw new IllegalArgumentException("'context' cannot be null!");
        }
        Intent intent = new Intent(context, AggregationIntentService.class);
        intent.setAction(ACTION_AGGREGATE_AND_TRACK);
        context.startService(intent);
    }

    // [TODO][MSB]: Remove me
    private static final long ALARM_INTERVAL = 60000; //86400000; // 1 day

    /**
     * Schedule an alarm service, will cancel existing
     *
     * @param context {@link Context}
     * @throws IllegalArgumentException {@link IllegalArgumentException}
     */
    public static void scheduleService(Context context) throws IllegalArgumentException {
        if (context == null) {
            throw new IllegalArgumentException("'context' cannot be null!");
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long lastTimeRan = prefs.getLong(KEY_LAST_TIME_RAN, 0);
        Intent intent = new Intent(context, AggregationIntentService.class);
        intent.setAction(ACTION_AGGREGATE_AND_TRACK);
        PendingIntent pi = PendingIntent.getService(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pi);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, lastTimeRan + ALARM_INTERVAL,
                ALARM_INTERVAL, pi);
    }

}
