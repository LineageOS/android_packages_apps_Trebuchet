package com.android.launcher3.stats.external;

import android.os.Bundle;
import android.text.TextUtils;

/**
 * <pre>
 *     Extension of a {@link Bundle} to provider streamline interfaces for
 *     the specific task of sending events
 * </pre>
 *
 * @see {@link Bundle}
 */
public class TrackingBundle {

    // Constants
    public static final String KEY_TRACKING_ID = "tracking_id";
    public static final String KEY_EVENT_CATEGORY = "category";
    public static final String KEY_EVENT_ACTION = "action";
    public static final String KEY_METADATA_VALUE = "value";
    public static final String KEY_METADATA_ORIGIN = "origin";
    public static final String KEY_METADATA_PACKAGE = "package";


    /**
     * Constructor
     *
     * @param trackingId {@link String}
     * @param category   {@link String}
     * @param action     {@link String}
     * @throws IllegalArgumentException {@link IllegalArgumentException}
     */
    public static Bundle createTrackingBundle(String trackingId, String category, String action)
            throws IllegalArgumentException {
        if (TextUtils.isEmpty(trackingId)) {
            throw new IllegalArgumentException("'trackingId' cannot be null or empty!");
        }
        if (TextUtils.isEmpty(category)) {
            throw new IllegalArgumentException("'category' cannot be null or empty!");
        }
        if (TextUtils.isEmpty(action)) {
            throw new IllegalArgumentException("'action' cannot be null or empty!");
        }
        Bundle bundle = new Bundle();
        bundle.putString(KEY_EVENT_CATEGORY, category);
        bundle.putString(KEY_EVENT_ACTION, action);
        bundle.putString(KEY_TRACKING_ID, trackingId);
        return bundle;
    }

}