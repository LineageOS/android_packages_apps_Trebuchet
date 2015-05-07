package com.android.launcher3.stats.internal.model;

import android.os.Bundle;

import java.util.List;

/**
 * <pre>
 *     This is an action we want to perfrom from a report.
 *
 *     e.g.
 *          1. I want to get the COUNT of widgets added
 *          2. I want to get the origin of app launches
 * </pre>
 */
public interface ITrackingAction {

    /**
     * Creates a new bundle used to tracking events
     *
     * @param trackingId {@link String}
     * @param category {@link com.android.launcher3.stats.internal.model.TrackingEvent.Category}
     * @param eventList {@link List}
     * @return {@link List}
     */
    List<Bundle> createTrackingBundles(String trackingId, TrackingEvent.Category category,
                                            List<TrackingEvent> eventList);

}
