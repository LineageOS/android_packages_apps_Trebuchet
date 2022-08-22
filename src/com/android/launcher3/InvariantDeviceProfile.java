/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TWO_PANEL_HOME;
import static com.android.launcher3.util.DisplayController.CHANGE_DENSITY;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_SUPPORTED_BOUNDS;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.util.Xml;
import android.view.Display;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.model.DeviceGridState;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.WindowManagerProxy;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InvariantDeviceProfile {

    public static final String TAG = "IDP";
    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<InvariantDeviceProfile> INSTANCE =
            new MainThreadInitializedObject<>(InvariantDeviceProfile::new);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_PHONE, TYPE_MULTI_DISPLAY, TYPE_TABLET})
    public @interface DeviceType{}
    public static final int TYPE_PHONE = 0;
    public static final int TYPE_MULTI_DISPLAY = 1;
    public static final int TYPE_TABLET = 2;

    private static final String KEY_IDP_GRID_NAME = "idp_grid_name";

    private static final float ICON_SIZE_DEFINED_IN_APP_DP = 48;

    // Constants that affects the interpolation curve between statically defined device profile
    // buckets.
    private static final float KNEARESTNEIGHBOR = 3;
    private static final float WEIGHT_POWER = 5;

    // used to offset float not being able to express extremely small weights in extreme cases.
    private static final float WEIGHT_EFFICIENT = 100000f;

    // Used for arrays to specify different sizes (e.g. border spaces, width/height) in different
    // constraints
    static final int COUNT_SIZES = 4;
    static final int INDEX_DEFAULT = 0;
    static final int INDEX_LANDSCAPE = 1;
    static final int INDEX_TWO_PANEL_PORTRAIT = 2;
    static final int INDEX_TWO_PANEL_LANDSCAPE = 3;

    /**
     * Number of icons per row and column in the workspace.
     */
    public int numRows;
    public int numColumns;
    public int numSearchContainerColumns;

    /**
     * Number of icons per row and column in the folder.
     */
    public int numFolderRows;
    public int numFolderColumns;
    public float[] iconSize;
    public float[] iconTextSize;
    public int iconBitmapSize;
    public int fillResIconDpi;
    public @DeviceType int deviceType;

    public PointF[] minCellSize;

    public PointF[] borderSpaces;
    public float folderBorderSpace;
    public float[] hotseatBorderSpaces;

    public float[] horizontalMargin;

    public PointF[] allAppsCellSize;
    public float[] allAppsIconSize;
    public float[] allAppsIconTextSize;
    public PointF[] allAppsBorderSpaces;

    private SparseArray<TypedValue> mExtraAttrs;

    /**
     * Number of icons inside the hotseat area.
     */
    public int numShownHotseatIcons;

    /**
     * Number of icons inside the hotseat area when using 3 buttons navigation.
     */
    public int numShrunkenHotseatIcons;

    /**
     * Number of icons inside the hotseat area that is stored in the database. This is greater than
     * or equal to numnShownHotseatIcons, allowing for a seamless transition between two hotseat
     * sizes that share the same DB.
     */
    public int numDatabaseHotseatIcons;

    public int[] hotseatColumnSpan;

    /**
     * Number of columns in the all apps list.
     */
    public int numAllAppsColumns;
    public int numDatabaseAllAppsColumns;

    /**
     * Do not query directly. see {@link DeviceProfile#isScalableGrid}.
     */
    protected boolean isScalable;
    public int devicePaddingId;

    public String dbFile;
    public int defaultLayoutId;
    int demoModeLayoutId;
    boolean[] inlineQsb = new boolean[COUNT_SIZES];

    /**
     * An immutable list of supported profiles.
     */
    public List<DeviceProfile> supportedProfiles = Collections.EMPTY_LIST;

    @Nullable
    public DevicePaddings devicePaddings;

    public Point defaultWallpaperSize;
    public Rect defaultWidgetPadding;

    private final ArrayList<OnIDPChangeListener> mChangeListeners = new ArrayList<>();

    @VisibleForTesting
    public InvariantDeviceProfile() { }

    @TargetApi(23)
    private InvariantDeviceProfile(Context context) {
        String gridName = getCurrentGridName(context);
        String newGridName = initGrid(context, gridName);
        if (!newGridName.equals(gridName)) {
            Utilities.getPrefs(context).edit().putString(KEY_IDP_GRID_NAME, newGridName).apply();
        }
        new DeviceGridState(this).writeToPrefs(context);

        DisplayController.INSTANCE.get(context).setPriorityListener(
                (displayContext, info, flags) -> {
                    if ((flags & (CHANGE_DENSITY | CHANGE_SUPPORTED_BOUNDS
                            | CHANGE_NAVIGATION_MODE)) != 0) {
                        onConfigChanged(displayContext);
                    }
                });
    }

    /**
     * This constructor should NOT have any monitors by design.
     */
    public InvariantDeviceProfile(Context context, String gridName) {
        String newName = initGrid(context, gridName);
        if (newName == null || !newName.equals(gridName)) {
            throw new IllegalArgumentException("Unknown grid name");
        }
    }

    /**
     * This constructor should NOT have any monitors by design.
     */
    public InvariantDeviceProfile(Context context, Display display) {
        // Ensure that the main device profile is initialized
        INSTANCE.get(context);
        String gridName = getCurrentGridName(context);

        // Get the display info based on default display and interpolate it to existing display
        Info defaultInfo = DisplayController.INSTANCE.get(context).getInfo();
        @DeviceType int defaultDeviceType = getDeviceType(defaultInfo);
        DisplayOption defaultDisplayOption = invDistWeightedInterpolate(
                defaultInfo,
                getPredefinedDeviceProfiles(context, gridName, defaultDeviceType,
                        /*allowDisabledGrid=*/false),
                defaultDeviceType);

        Info myInfo = new Info(context, display);
        @DeviceType int deviceType = getDeviceType(myInfo);
        DisplayOption myDisplayOption = invDistWeightedInterpolate(
                myInfo,
                getPredefinedDeviceProfiles(context, gridName, deviceType,
                        /*allowDisabledGrid=*/false),
                deviceType);

        DisplayOption result = new DisplayOption(defaultDisplayOption.grid)
                .add(myDisplayOption);
        result.iconSizes[INDEX_DEFAULT] =
                defaultDisplayOption.iconSizes[INDEX_DEFAULT];
        for (int i = 1; i < COUNT_SIZES; i++) {
            result.iconSizes[i] = Math.min(
                    defaultDisplayOption.iconSizes[i], myDisplayOption.iconSizes[i]);
        }

        System.arraycopy(defaultDisplayOption.minCellSize, 0, result.minCellSize, 0,
                COUNT_SIZES);
        System.arraycopy(defaultDisplayOption.borderSpaces, 0, result.borderSpaces, 0,
                COUNT_SIZES);
        System.arraycopy(defaultDisplayOption.inlineQsb, 0, result.inlineQsb, 0,
                COUNT_SIZES);

        initGrid(context, myInfo, result, deviceType);
    }

    /**
     * Reinitialize the current grid after a restore, where some grids might now be disabled.
     */
    public void reinitializeAfterRestore(Context context) {
        String currentGridName = getCurrentGridName(context);
        String currentDbFile = dbFile;
        String newGridName = initGrid(context, currentGridName);
        String newDbFile = dbFile;
        if (!newDbFile.equals(currentDbFile)) {
            Log.d(TAG, "Restored grid is disabled : " + currentGridName
                    + ", migrating to: " + newGridName
                    + ", removing all other grid db files");
            for (String gridDbFile : LauncherFiles.GRID_DB_FILES) {
                if (gridDbFile.equals(currentDbFile)) {
                    continue;
                }
                if (context.getDatabasePath(gridDbFile).delete()) {
                    Log.d(TAG, "Removed old grid db file: " + gridDbFile);
                }
            }
            setCurrentGrid(context, newGridName);
        }
    }

    private static @DeviceType int getDeviceType(Info displayInfo) {
        int flagPhone = 1 << 0;
        int flagTablet = 1 << 1;

        int type = displayInfo.supportedBounds.stream()
                .mapToInt(bounds -> displayInfo.isTablet(bounds) ? flagTablet : flagPhone)
                .reduce(0, (a, b) -> a | b);
        if ((type == (flagPhone | flagTablet)) && ENABLE_TWO_PANEL_HOME.get()) {
            // device has profiles supporting both phone and table modes
            return TYPE_MULTI_DISPLAY;
        } else if (type == flagTablet) {
            return TYPE_TABLET;
        } else {
            return TYPE_PHONE;
        }
    }

    public static String getCurrentGridName(Context context) {
        return Utilities.isGridOptionsEnabled(context)
                ? Utilities.getPrefs(context).getString(KEY_IDP_GRID_NAME, null) : null;
    }

    private String initGrid(Context context, String gridName) {
        Info displayInfo = DisplayController.INSTANCE.get(context).getInfo();
        @DeviceType int deviceType = getDeviceType(displayInfo);

        ArrayList<DisplayOption> allOptions =
                getPredefinedDeviceProfiles(context, gridName, deviceType,
                        RestoreDbTask.isPending(context));
        DisplayOption displayOption =
                invDistWeightedInterpolate(displayInfo, allOptions, deviceType);
        initGrid(context, displayInfo, displayOption, deviceType);
        return displayOption.grid.name;
    }

    private void initGrid(Context context, Info displayInfo, DisplayOption displayOption,
            @DeviceType int deviceType) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        GridOption closestProfile = displayOption.grid;
        numRows = closestProfile.numRows;
        numColumns = closestProfile.numColumns;
        numSearchContainerColumns = closestProfile.numSearchContainerColumns;
        dbFile = closestProfile.dbFile;
        defaultLayoutId = closestProfile.defaultLayoutId;
        demoModeLayoutId = closestProfile.demoModeLayoutId;
        numFolderRows = closestProfile.numFolderRows;
        numFolderColumns = closestProfile.numFolderColumns;
        isScalable = closestProfile.isScalable;
        devicePaddingId = closestProfile.devicePaddingId;
        this.deviceType = deviceType;

        mExtraAttrs = closestProfile.extraAttrs;

        iconSize = displayOption.iconSizes;
        float maxIconSize = iconSize[0];
        for (int i = 1; i < iconSize.length; i++) {
            maxIconSize = Math.max(maxIconSize, iconSize[i]);
        }
        iconBitmapSize = ResourceUtils.pxFromDp(maxIconSize, metrics);
        fillResIconDpi = getLauncherIconDensity(iconBitmapSize);

        iconTextSize = displayOption.textSizes;

        minCellSize = displayOption.minCellSize;

        borderSpaces = displayOption.borderSpaces;
        folderBorderSpace = displayOption.folderBorderSpace;

        horizontalMargin = displayOption.horizontalMargin;

        numShownHotseatIcons = closestProfile.numHotseatIcons;
        numShrunkenHotseatIcons = closestProfile.numShrunkenHotseatIcons;
        numDatabaseHotseatIcons = deviceType == TYPE_MULTI_DISPLAY
                ? closestProfile.numDatabaseHotseatIcons : closestProfile.numHotseatIcons;
        hotseatColumnSpan = closestProfile.hotseatColumnSpan;
        hotseatBorderSpaces = displayOption.hotseatBorderSpaces;

        numAllAppsColumns = closestProfile.numAllAppsColumns;
        numDatabaseAllAppsColumns = deviceType == TYPE_MULTI_DISPLAY
                ? closestProfile.numDatabaseAllAppsColumns : closestProfile.numAllAppsColumns;

        allAppsCellSize = displayOption.allAppsCellSize;
        allAppsBorderSpaces = displayOption.allAppsBorderSpaces;
        allAppsIconSize = displayOption.allAppsIconSizes;
        allAppsIconTextSize = displayOption.allAppsIconTextSizes;
        if (!Utilities.isGridOptionsEnabled(context)) {
            allAppsIconSize = iconSize;
            allAppsIconTextSize = iconTextSize;
        }

        if (devicePaddingId != 0) {
            devicePaddings = new DevicePaddings(context, devicePaddingId);
        }

        inlineQsb = displayOption.inlineQsb;

        // If the partner customization apk contains any grid overrides, apply them
        // Supported overrides: numRows, numColumns, iconSize
        applyPartnerDeviceProfileOverrides(context, metrics);

        final List<DeviceProfile> localSupportedProfiles = new ArrayList<>();
        defaultWallpaperSize = new Point(displayInfo.currentSize);
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            localSupportedProfiles.add(new DeviceProfile.Builder(context, this, displayInfo)
                    .setUseTwoPanels(deviceType == TYPE_MULTI_DISPLAY)
                    .setWindowBounds(bounds)
                    .build());

            // Wallpaper size should be the maximum of the all possible sizes Launcher expects
            int displayWidth = bounds.bounds.width();
            int displayHeight = bounds.bounds.height();
            defaultWallpaperSize.y = Math.max(defaultWallpaperSize.y, displayHeight);

            // We need to ensure that there is enough extra space in the wallpaper
            // for the intended parallax effects
            float parallaxFactor =
                    dpiFromPx(Math.min(displayWidth, displayHeight), displayInfo.getDensityDpi())
                            < 720
                            ? 2
                            : wallpaperTravelToScreenWidthRatio(displayWidth, displayHeight);
            defaultWallpaperSize.x =
                    Math.max(defaultWallpaperSize.x, Math.round(parallaxFactor * displayWidth));
        }
        supportedProfiles = Collections.unmodifiableList(localSupportedProfiles);

        ComponentName cn = new ComponentName(context.getPackageName(), getClass().getName());
        defaultWidgetPadding = AppWidgetHostView.getDefaultPaddingForWidget(context, cn, null);
    }

    public void addOnChangeListener(OnIDPChangeListener listener) {
        mChangeListeners.add(listener);
    }

    public void removeOnChangeListener(OnIDPChangeListener listener) {
        mChangeListeners.remove(listener);
    }


    public void setCurrentGrid(Context context, String gridName) {
        Context appContext = context.getApplicationContext();
        Utilities.getPrefs(appContext).edit().putString(KEY_IDP_GRID_NAME, gridName).apply();
        MAIN_EXECUTOR.execute(() -> onConfigChanged(appContext));
    }

    private Object[] toModelState() {
        return new Object[]{
                numColumns, numRows, numSearchContainerColumns, numDatabaseHotseatIcons,
                iconBitmapSize, fillResIconDpi, numDatabaseAllAppsColumns, dbFile};
    }

    private void onConfigChanged(Context context) {
        Object[] oldState = toModelState();

        // Re-init grid
        String gridName = getCurrentGridName(context);
        initGrid(context, Utilities.getPrefs(context).getString(KEY_IDP_GRID_NAME, gridName));

        boolean modelPropsChanged = !Arrays.equals(oldState, toModelState());
        for (OnIDPChangeListener listener : mChangeListeners) {
            listener.onIdpChanged(modelPropsChanged);
        }
    }

    private static ArrayList<DisplayOption> getPredefinedDeviceProfiles(Context context,
            String gridName, @DeviceType int deviceType, boolean allowDisabledGrid) {
        ArrayList<DisplayOption> profiles = new ArrayList<>();

        try (XmlResourceParser parser = context.getResources().getXml(R.xml.device_profiles)) {
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG)
                        && GridOption.TAG_NAME.equals(parser.getName())) {

                    GridOption gridOption = new GridOption(context, Xml.asAttributeSet(parser),
                            deviceType);
                    if (gridOption.isEnabled || allowDisabledGrid) {
                        final int displayDepth = parser.getDepth();
                        while (((type = parser.next()) != XmlPullParser.END_TAG
                                || parser.getDepth() > displayDepth)
                                && type != XmlPullParser.END_DOCUMENT) {
                            if ((type == XmlPullParser.START_TAG) && "display-option".equals(
                                    parser.getName())) {
                                profiles.add(new DisplayOption(gridOption, context,
                                        Xml.asAttributeSet(parser)));
                            }
                        }
                    }
                }
            }
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }

        ArrayList<DisplayOption> filteredProfiles = new ArrayList<>();
        if (!TextUtils.isEmpty(gridName)) {
            for (DisplayOption option : profiles) {
                if (gridName.equals(option.grid.name)
                        && (option.grid.isEnabled || allowDisabledGrid)) {
                    filteredProfiles.add(option);
                }
            }
        }
        if (filteredProfiles.isEmpty()) {
            // No grid found, use the default options
            for (DisplayOption option : profiles) {
                if (option.canBeDefault) {
                    filteredProfiles.add(option);
                }
            }
        }
        if (filteredProfiles.isEmpty()) {
            throw new RuntimeException("No display option with canBeDefault=true");
        }
        return filteredProfiles;
    }

    /**
     * @return all the grid options that can be shown on the device
     */
    public List<GridOption> parseAllGridOptions(Context context) {
        List<GridOption> result = new ArrayList<>();

        try (XmlResourceParser parser = context.getResources().getXml(R.xml.device_profiles)) {
            final int depth = parser.getDepth();
            int type;
            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if ((type == XmlPullParser.START_TAG)
                        && GridOption.TAG_NAME.equals(parser.getName())) {
                    GridOption option =
                            new GridOption(context, Xml.asAttributeSet(parser), deviceType);
                    if (option.isEnabled) {
                        result.add(option);
                    }
                }
            }
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Error parsing device profile", e);
            return Collections.emptyList();
        }
        return result;
    }

    private int getLauncherIconDensity(int requiredSize) {
        // Densities typically defined by an app.
        int[] densityBuckets = new int[]{
                DisplayMetrics.DENSITY_LOW,
                DisplayMetrics.DENSITY_MEDIUM,
                DisplayMetrics.DENSITY_TV,
                DisplayMetrics.DENSITY_HIGH,
                DisplayMetrics.DENSITY_XHIGH,
                DisplayMetrics.DENSITY_XXHIGH,
                DisplayMetrics.DENSITY_XXXHIGH
        };

        int density = DisplayMetrics.DENSITY_XXXHIGH;
        for (int i = densityBuckets.length - 1; i >= 0; i--) {
            float expectedSize = ICON_SIZE_DEFINED_IN_APP_DP * densityBuckets[i]
                    / DisplayMetrics.DENSITY_DEFAULT;
            if (expectedSize >= requiredSize) {
                density = densityBuckets[i];
            }
        }

        return density;
    }

    /**
     * Apply any Partner customization grid overrides.
     *
     * Currently we support: all apps row / column count.
     */
    private void applyPartnerDeviceProfileOverrides(Context context, DisplayMetrics dm) {
        Partner p = Partner.get(context.getPackageManager());
        if (p != null) {
            p.applyInvariantDeviceProfileOverrides(this, dm);
        }
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x1 - x0, y1 - y0);
    }

    private static DisplayOption invDistWeightedInterpolate(
            Info displayInfo, ArrayList<DisplayOption> points, @DeviceType int deviceType) {
        int minWidthPx = Integer.MAX_VALUE;
        int minHeightPx = Integer.MAX_VALUE;
        for (WindowBounds bounds : displayInfo.supportedBounds) {
            boolean isTablet = displayInfo.isTablet(bounds);
            if (isTablet && deviceType == TYPE_MULTI_DISPLAY) {
                // For split displays, take half width per page
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.x / 2);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.y);

            } else if (!isTablet && bounds.isLandscape()) {
                // We will use transposed layout in this case
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.y);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.x);
            } else {
                minWidthPx = Math.min(minWidthPx, bounds.availableSize.x);
                minHeightPx = Math.min(minHeightPx, bounds.availableSize.y);
            }
        }

        float width = dpiFromPx(minWidthPx, displayInfo.getDensityDpi());
        float height = dpiFromPx(minHeightPx, displayInfo.getDensityDpi());

        // Sort the profiles based on the closeness to the device size
        Collections.sort(points, (a, b) ->
                Float.compare(dist(width, height, a.minWidthDps, a.minHeightDps),
                        dist(width, height, b.minWidthDps, b.minHeightDps)));

        DisplayOption closestPoint = points.get(0);
        GridOption closestOption = closestPoint.grid;
        float weights = 0;

        if (dist(width, height, closestPoint.minWidthDps, closestPoint.minHeightDps) == 0) {
            return closestPoint;
        }

        DisplayOption out = new DisplayOption(closestOption);
        for (int i = 0; i < points.size() && i < KNEARESTNEIGHBOR; ++i) {
            DisplayOption p = points.get(i);
            float w = weight(width, height, p.minWidthDps, p.minHeightDps, WEIGHT_POWER);
            weights += w;
            out.add(new DisplayOption().add(p).multiply(w));
        }
        out.multiply(1.0f / weights);

        // Since the bitmaps are persisted, ensure that all bitmap sizes are not larger than
        // predefined size to avoid cache invalidation
        for (int i = INDEX_DEFAULT; i < COUNT_SIZES; i++) {
            out.iconSizes[i] = Math.min(out.iconSizes[i], closestPoint.iconSizes[i]);
        }

        return out;
    }

    public DeviceProfile getDeviceProfile(Context context) {
        Resources res = context.getResources();
        Configuration config = context.getResources().getConfiguration();

        float screenWidth = config.screenWidthDp * res.getDisplayMetrics().density;
        float screenHeight = config.screenHeightDp * res.getDisplayMetrics().density;
        int rotation = WindowManagerProxy.INSTANCE.get(context).getRotation(context);

        if (Utilities.IS_DEBUG_DEVICE) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            DisplayController.INSTANCE.get(context).dump(printWriter);
            printWriter.flush();
            Log.d("b/231312158", "getDeviceProfile -"
                            + "\nconfig: " + config
                            + "\ndisplayMetrics: " + res.getDisplayMetrics()
                            + "\nrotation: " + rotation
                            + "\n" + stringWriter.toString(),
                    new Exception());
        }
        return getBestMatch(screenWidth, screenHeight, rotation);
    }

    /**
     * Returns the device profile matching the provided screen configuration
     */
    public DeviceProfile getBestMatch(float screenWidth, float screenHeight, int rotation) {
        DeviceProfile bestMatch = supportedProfiles.get(0);
        float minDiff = Float.MAX_VALUE;

        for (DeviceProfile profile : supportedProfiles) {
            float diff = Math.abs(profile.widthPx - screenWidth)
                    + Math.abs(profile.heightPx - screenHeight);
            if (diff < minDiff) {
                minDiff = diff;
                bestMatch = profile;
            } else if (diff == minDiff && profile.rotationHint == rotation) {
                bestMatch = profile;
            }
        }
        return bestMatch;
    }

    private static float weight(float x0, float y0, float x1, float y1, float pow) {
        float d = dist(x0, y0, x1, y1);
        if (Float.compare(d, 0f) == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return (float) (WEIGHT_EFFICIENT / Math.pow(d, pow));
    }

    /**
     * As a ratio of screen height, the total distance we want the parallax effect to span
     * horizontally
     */
    private static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.5 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.2 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (ie travel) at any aspect ratio:

        final float ASPECT_RATIO_LANDSCAPE = 16 / 10f;
        final float ASPECT_RATIO_PORTRAIT = 10 / 16f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.5f;
        final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.2f;

        // To find out the desired width at different aspect ratios, we use the following two
        // formulas, where the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.5
        //   (10/16)x + y = 1.2
        // We solve for x and y and end up with a final formula:
        final float x =
                (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE
                        - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT) /
                        (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        final float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    public interface OnIDPChangeListener {

        /**
         * Called when the device provide changes
         */
        void onIdpChanged(boolean modelPropertiesChanged);
    }


    public static final class GridOption {

        public static final String TAG_NAME = "grid-option";

        private static final int DEVICE_CATEGORY_PHONE = 1 << 0;
        private static final int DEVICE_CATEGORY_TABLET = 1 << 1;
        private static final int DEVICE_CATEGORY_MULTI_DISPLAY = 1 << 2;
        private static final int DEVICE_CATEGORY_ALL =
                DEVICE_CATEGORY_PHONE | DEVICE_CATEGORY_TABLET | DEVICE_CATEGORY_MULTI_DISPLAY;

        public final String name;
        public final int numRows;
        public final int numColumns;
        public final int numSearchContainerColumns;
        public final boolean isEnabled;

        private final int numFolderRows;
        private final int numFolderColumns;

        private final int numAllAppsColumns;
        private final int numDatabaseAllAppsColumns;
        private final int numHotseatIcons;
        private final int numShrunkenHotseatIcons;
        private final int numDatabaseHotseatIcons;
        private final int[] hotseatColumnSpan = new int[COUNT_SIZES];

        private final String dbFile;

        private final int defaultLayoutId;
        private final int demoModeLayoutId;

        private final boolean isScalable;
        private final int devicePaddingId;

        private final SparseArray<TypedValue> extraAttrs;

        public GridOption(Context context, AttributeSet attrs, @DeviceType int deviceType) {
            TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.GridDisplayOption);
            name = a.getString(R.styleable.GridDisplayOption_name);
            numRows = a.getInt(R.styleable.GridDisplayOption_numRows, 0);
            numColumns = a.getInt(R.styleable.GridDisplayOption_numColumns, 0);
            numSearchContainerColumns = a.getInt(
                    R.styleable.GridDisplayOption_numSearchContainerColumns, numColumns);

            dbFile = a.getString(R.styleable.GridDisplayOption_dbFile);
            defaultLayoutId = a.getResourceId(deviceType == TYPE_MULTI_DISPLAY && a.hasValue(
                    R.styleable.GridDisplayOption_defaultSplitDisplayLayoutId)
                    ? R.styleable.GridDisplayOption_defaultSplitDisplayLayoutId
                    : R.styleable.GridDisplayOption_defaultLayoutId, 0);
            demoModeLayoutId = a.getResourceId(
                    R.styleable.GridDisplayOption_demoModeLayoutId, defaultLayoutId);

            numAllAppsColumns = a.getInt(
                    R.styleable.GridDisplayOption_numAllAppsColumns, numColumns);
            numDatabaseAllAppsColumns = a.getInt(
                    R.styleable.GridDisplayOption_numExtendedAllAppsColumns, 2 * numAllAppsColumns);

            numHotseatIcons = a.getInt(
                    R.styleable.GridDisplayOption_numHotseatIcons, numColumns);
            numShrunkenHotseatIcons = a.getInt(
                    R.styleable.GridDisplayOption_numShrunkenHotseatIcons, numHotseatIcons / 2);
            numDatabaseHotseatIcons = a.getInt(
                    R.styleable.GridDisplayOption_numExtendedHotseatIcons, 2 * numHotseatIcons);
            hotseatColumnSpan[INDEX_DEFAULT] = a.getInt(
                    R.styleable.GridDisplayOption_hotseatColumnSpan, numColumns);
            hotseatColumnSpan[INDEX_LANDSCAPE] = a.getInt(
                    R.styleable.GridDisplayOption_hotseatColumnSpanLandscape, numColumns);
            hotseatColumnSpan[INDEX_TWO_PANEL_LANDSCAPE] = a.getInt(
                    R.styleable.GridDisplayOption_hotseatColumnSpanTwoPanelLandscape,
                    numColumns);
            hotseatColumnSpan[INDEX_TWO_PANEL_PORTRAIT] = a.getInt(
                    R.styleable.GridDisplayOption_hotseatColumnSpanTwoPanelPortrait,
                    numColumns);

            numFolderRows = a.getInt(
                    R.styleable.GridDisplayOption_numFolderRows, numRows);
            numFolderColumns = a.getInt(
                    R.styleable.GridDisplayOption_numFolderColumns, numColumns);

            isScalable = a.getBoolean(
                    R.styleable.GridDisplayOption_isScalable, false);
            devicePaddingId = a.getResourceId(
                    R.styleable.GridDisplayOption_devicePaddingId, 0);

            int deviceCategory = a.getInt(R.styleable.GridDisplayOption_deviceCategory,
                    DEVICE_CATEGORY_ALL);
            isEnabled = (deviceType == TYPE_PHONE
                        && ((deviceCategory & DEVICE_CATEGORY_PHONE) == DEVICE_CATEGORY_PHONE))
                    || (deviceType == TYPE_TABLET
                        && ((deviceCategory & DEVICE_CATEGORY_TABLET) == DEVICE_CATEGORY_TABLET))
                    || (deviceType == TYPE_MULTI_DISPLAY
                        && ((deviceCategory & DEVICE_CATEGORY_MULTI_DISPLAY)
                            == DEVICE_CATEGORY_MULTI_DISPLAY));

            a.recycle();
            extraAttrs = Themes.createValueMap(context, attrs,
                    IntArray.wrap(R.styleable.GridDisplayOption));
        }
    }

    @VisibleForTesting
    static final class DisplayOption {
        private static final int INLINE_QSB_FOR_PORTRAIT = 1 << 0;
        private static final int INLINE_QSB_FOR_LANDSCAPE = 1 << 1;
        private static final int INLINE_QSB_FOR_TWO_PANEL_PORTRAIT = 1 << 2;
        private static final int INLINE_QSB_FOR_TWO_PANEL_LANDSCAPE = 1 << 3;
        private static final int DONT_INLINE_QSB = 0;

        public final GridOption grid;

        private final float minWidthDps;
        private final float minHeightDps;
        private final boolean canBeDefault;
        private final boolean[] inlineQsb = new boolean[COUNT_SIZES];

        private final PointF[] minCellSize = new PointF[COUNT_SIZES];

        private float folderBorderSpace;
        private final PointF[] borderSpaces = new PointF[COUNT_SIZES];
        private final float[] horizontalMargin = new float[COUNT_SIZES];
        //TODO(http://b/228998082) remove this when 3 button spaces are fixed
        private final float[] hotseatBorderSpaces = new float[COUNT_SIZES];

        private final float[] iconSizes = new float[COUNT_SIZES];
        private final float[] textSizes = new float[COUNT_SIZES];

        private final PointF[] allAppsCellSize = new PointF[COUNT_SIZES];
        private final float[] allAppsIconSizes = new float[COUNT_SIZES];
        private final float[] allAppsIconTextSizes = new float[COUNT_SIZES];
        private final PointF[] allAppsBorderSpaces = new PointF[COUNT_SIZES];

        DisplayOption(GridOption grid, Context context, AttributeSet attrs) {
            this.grid = grid;

            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProfileDisplayOption);

            minWidthDps = a.getFloat(R.styleable.ProfileDisplayOption_minWidthDps, 0);
            minHeightDps = a.getFloat(R.styleable.ProfileDisplayOption_minHeightDps, 0);

            canBeDefault = a.getBoolean(R.styleable.ProfileDisplayOption_canBeDefault, false);

            int inlineForRotation = a.getInt(R.styleable.ProfileDisplayOption_inlineQsb,
                    DONT_INLINE_QSB);
            inlineQsb[INDEX_DEFAULT] =
                    (inlineForRotation & INLINE_QSB_FOR_PORTRAIT) == INLINE_QSB_FOR_PORTRAIT;
            inlineQsb[INDEX_LANDSCAPE] =
                    (inlineForRotation & INLINE_QSB_FOR_LANDSCAPE) == INLINE_QSB_FOR_LANDSCAPE;
            inlineQsb[INDEX_TWO_PANEL_PORTRAIT] =
                    (inlineForRotation & INLINE_QSB_FOR_TWO_PANEL_PORTRAIT)
                            == INLINE_QSB_FOR_TWO_PANEL_PORTRAIT;
            inlineQsb[INDEX_TWO_PANEL_LANDSCAPE] =
                    (inlineForRotation & INLINE_QSB_FOR_TWO_PANEL_LANDSCAPE)
                            == INLINE_QSB_FOR_TWO_PANEL_LANDSCAPE;

            float x;
            float y;

            x = a.getFloat(R.styleable.ProfileDisplayOption_minCellWidth, 0);
            y = a.getFloat(R.styleable.ProfileDisplayOption_minCellHeight, 0);
            minCellSize[INDEX_DEFAULT] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_minCellWidthLandscape,
                    minCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_minCellHeightLandscape,
                    minCellSize[INDEX_DEFAULT].y);
            minCellSize[INDEX_LANDSCAPE] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_minCellWidthTwoPanelPortrait,
                    minCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_minCellHeightTwoPanelPortrait,
                    minCellSize[INDEX_DEFAULT].y);
            minCellSize[INDEX_TWO_PANEL_PORTRAIT] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_minCellWidthTwoPanelLandscape,
                    minCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_minCellHeightTwoPanelLandscape,
                    minCellSize[INDEX_DEFAULT].y);
            minCellSize[INDEX_TWO_PANEL_LANDSCAPE] = new PointF(x, y);

            float borderSpace = a.getFloat(R.styleable.ProfileDisplayOption_borderSpace, 0);
            float borderSpaceLandscape = a.getFloat(
                    R.styleable.ProfileDisplayOption_borderSpaceLandscape, borderSpace);
            float borderSpaceTwoPanelPortrait = a.getFloat(
                    R.styleable.ProfileDisplayOption_borderSpaceTwoPanelPortrait, borderSpace);
            float borderSpaceTwoPanelLandscape = a.getFloat(
                    R.styleable.ProfileDisplayOption_borderSpaceTwoPanelLandscape, borderSpace);

            x = a.getFloat(R.styleable.ProfileDisplayOption_borderSpaceHorizontal, borderSpace);
            y = a.getFloat(R.styleable.ProfileDisplayOption_borderSpaceVertical, borderSpace);
            borderSpaces[INDEX_DEFAULT] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_borderSpaceLandscapeHorizontal,
                    borderSpaceLandscape);
            y = a.getFloat(R.styleable.ProfileDisplayOption_borderSpaceLandscapeVertical,
                    borderSpaceLandscape);
            borderSpaces[INDEX_LANDSCAPE] = new PointF(x, y);

            x = a.getFloat(
                    R.styleable.ProfileDisplayOption_borderSpaceTwoPanelPortraitHorizontal,
                    borderSpaceTwoPanelPortrait);
            y = a.getFloat(
                    R.styleable.ProfileDisplayOption_borderSpaceTwoPanelPortraitVertical,
                    borderSpaceTwoPanelPortrait);
            borderSpaces[INDEX_TWO_PANEL_PORTRAIT] = new PointF(x, y);

            x = a.getFloat(
                    R.styleable.ProfileDisplayOption_borderSpaceTwoPanelLandscapeHorizontal,
                    borderSpaceTwoPanelLandscape);
            y = a.getFloat(
                    R.styleable.ProfileDisplayOption_borderSpaceTwoPanelLandscapeVertical,
                    borderSpaceTwoPanelLandscape);
            borderSpaces[INDEX_TWO_PANEL_LANDSCAPE] = new PointF(x, y);

            folderBorderSpace = borderSpace;

            x = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellWidth,
                    minCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellHeight,
                    minCellSize[INDEX_DEFAULT].y);
            allAppsCellSize[INDEX_DEFAULT] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellWidthLandscape,
                    allAppsCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellHeightLandscape,
                    allAppsCellSize[INDEX_DEFAULT].y);
            allAppsCellSize[INDEX_LANDSCAPE] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellWidthTwoPanelPortrait,
                    allAppsCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellHeightTwoPanelPortrait,
                    allAppsCellSize[INDEX_DEFAULT].y);
            allAppsCellSize[INDEX_TWO_PANEL_PORTRAIT] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellWidthTwoPanelLandscape,
                    allAppsCellSize[INDEX_DEFAULT].x);
            y = a.getFloat(R.styleable.ProfileDisplayOption_allAppsCellHeightTwoPanelLandscape,
                    allAppsCellSize[INDEX_DEFAULT].y);
            allAppsCellSize[INDEX_TWO_PANEL_LANDSCAPE] = new PointF(x, y);

            float allAppsBorderSpace = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsBorderSpace, borderSpace);
            float allAppsBorderSpaceLandscape = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceLandscape,
                    allAppsBorderSpace);
            float allAppsBorderSpaceTwoPanelPortrait = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelPortrait,
                    allAppsBorderSpace);
            float allAppsBorderSpaceTwoPanelLandscape = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelLandscape,
                    allAppsBorderSpace);

            x = a.getFloat(R.styleable.ProfileDisplayOption_allAppsBorderSpaceHorizontal,
                    allAppsBorderSpace);
            y = a.getFloat(R.styleable.ProfileDisplayOption_allAppsBorderSpaceVertical,
                    allAppsBorderSpace);
            allAppsBorderSpaces[INDEX_DEFAULT] = new PointF(x, y);

            x = a.getFloat(R.styleable.ProfileDisplayOption_allAppsBorderSpaceLandscapeHorizontal,
                    allAppsBorderSpaceLandscape);
            y = a.getFloat(R.styleable.ProfileDisplayOption_allAppsBorderSpaceLandscapeVertical,
                    allAppsBorderSpaceLandscape);
            allAppsBorderSpaces[INDEX_LANDSCAPE] = new PointF(x, y);

            x = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelPortraitHorizontal,
                    allAppsBorderSpaceTwoPanelPortrait);
            y = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelPortraitVertical,
                    allAppsBorderSpaceTwoPanelPortrait);
            allAppsBorderSpaces[INDEX_TWO_PANEL_PORTRAIT] = new PointF(x, y);

            x = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelLandscapeHorizontal,
                    allAppsBorderSpaceTwoPanelLandscape);
            y = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsBorderSpaceTwoPanelLandscapeVertical,
                    allAppsBorderSpaceTwoPanelLandscape);
            allAppsBorderSpaces[INDEX_TWO_PANEL_LANDSCAPE] = new PointF(x, y);

            iconSizes[INDEX_DEFAULT] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconImageSize, 0);
            iconSizes[INDEX_LANDSCAPE] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconSizeLandscape,
                            iconSizes[INDEX_DEFAULT]);
            iconSizes[INDEX_TWO_PANEL_PORTRAIT] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconSizeTwoPanelPortrait,
                            iconSizes[INDEX_DEFAULT]);
            iconSizes[INDEX_TWO_PANEL_LANDSCAPE] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconSizeTwoPanelLandscape,
                            iconSizes[INDEX_DEFAULT]);

            allAppsIconSizes[INDEX_DEFAULT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsIconSize, iconSizes[INDEX_DEFAULT]);
            allAppsIconSizes[INDEX_LANDSCAPE] = allAppsIconSizes[INDEX_DEFAULT];
            allAppsIconSizes[INDEX_TWO_PANEL_PORTRAIT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsIconSizeTwoPanelPortrait,
                    allAppsIconSizes[INDEX_DEFAULT]);
            allAppsIconSizes[INDEX_TWO_PANEL_LANDSCAPE] = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsIconSizeTwoPanelLandscape,
                    allAppsIconSizes[INDEX_DEFAULT]);

            textSizes[INDEX_DEFAULT] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconTextSize, 0);
            textSizes[INDEX_LANDSCAPE] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconTextSizeLandscape,
                            textSizes[INDEX_DEFAULT]);
            textSizes[INDEX_TWO_PANEL_PORTRAIT] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconTextSizeTwoPanelPortrait,
                            textSizes[INDEX_DEFAULT]);
            textSizes[INDEX_TWO_PANEL_LANDSCAPE] =
                    a.getFloat(R.styleable.ProfileDisplayOption_iconTextSizeTwoPanelLandscape,
                            textSizes[INDEX_DEFAULT]);

            allAppsIconTextSizes[INDEX_DEFAULT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsIconTextSize, textSizes[INDEX_DEFAULT]);
            allAppsIconTextSizes[INDEX_LANDSCAPE] = allAppsIconTextSizes[INDEX_DEFAULT];
            allAppsIconTextSizes[INDEX_TWO_PANEL_PORTRAIT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsIconTextSizeTwoPanelPortrait,
                    allAppsIconTextSizes[INDEX_DEFAULT]);
            allAppsIconTextSizes[INDEX_TWO_PANEL_LANDSCAPE] = a.getFloat(
                    R.styleable.ProfileDisplayOption_allAppsIconTextSizeTwoPanelLandscape,
                    allAppsIconTextSizes[INDEX_DEFAULT]);

            horizontalMargin[INDEX_DEFAULT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_horizontalMargin, 0);
            horizontalMargin[INDEX_LANDSCAPE] = a.getFloat(
                    R.styleable.ProfileDisplayOption_horizontalMarginLandscape,
                    horizontalMargin[INDEX_DEFAULT]);
            horizontalMargin[INDEX_TWO_PANEL_LANDSCAPE] = a.getFloat(
                    R.styleable.ProfileDisplayOption_horizontalMarginTwoPanelLandscape,
                    horizontalMargin[INDEX_DEFAULT]);
            horizontalMargin[INDEX_TWO_PANEL_PORTRAIT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_horizontalMarginTwoPanelPortrait,
                    horizontalMargin[INDEX_DEFAULT]);

            hotseatBorderSpaces[INDEX_DEFAULT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_hotseatBorderSpace, borderSpace);
            hotseatBorderSpaces[INDEX_LANDSCAPE] = a.getFloat(
                    R.styleable.ProfileDisplayOption_hotseatBorderSpaceLandscape,
                    hotseatBorderSpaces[INDEX_DEFAULT]);
            hotseatBorderSpaces[INDEX_TWO_PANEL_LANDSCAPE] = a.getFloat(
                    R.styleable.ProfileDisplayOption_hotseatBorderSpaceTwoPanelLandscape,
                    hotseatBorderSpaces[INDEX_DEFAULT]);
            hotseatBorderSpaces[INDEX_TWO_PANEL_PORTRAIT] = a.getFloat(
                    R.styleable.ProfileDisplayOption_hotseatBorderSpaceTwoPanelPortrait,
                    hotseatBorderSpaces[INDEX_DEFAULT]);

            a.recycle();
        }

        DisplayOption() {
            this(null);
        }

        DisplayOption(GridOption grid) {
            this.grid = grid;
            minWidthDps = 0;
            minHeightDps = 0;
            canBeDefault = false;
            for (int i = 0; i < COUNT_SIZES; i++) {
                iconSizes[i] = 0;
                textSizes[i] = 0;
                borderSpaces[i] = new PointF();
                minCellSize[i] = new PointF();
                allAppsCellSize[i] = new PointF();
                allAppsIconSizes[i] = 0;
                allAppsIconTextSizes[i] = 0;
                allAppsBorderSpaces[i] = new PointF();
                inlineQsb[i] = false;
            }
        }

        private DisplayOption multiply(float w) {
            for (int i = 0; i < COUNT_SIZES; i++) {
                iconSizes[i] *= w;
                textSizes[i] *= w;
                borderSpaces[i].x *= w;
                borderSpaces[i].y *= w;
                minCellSize[i].x *= w;
                minCellSize[i].y *= w;
                horizontalMargin[i] *= w;
                hotseatBorderSpaces[i] *= w;
                allAppsCellSize[i].x *= w;
                allAppsCellSize[i].y *= w;
                allAppsIconSizes[i] *= w;
                allAppsIconTextSizes[i] *= w;
                allAppsBorderSpaces[i].x *= w;
                allAppsBorderSpaces[i].y *= w;
            }

            folderBorderSpace *= w;

            return this;
        }

        private DisplayOption add(DisplayOption p) {
            for (int i = 0; i < COUNT_SIZES; i++) {
                iconSizes[i] += p.iconSizes[i];
                textSizes[i] += p.textSizes[i];
                borderSpaces[i].x += p.borderSpaces[i].x;
                borderSpaces[i].y += p.borderSpaces[i].y;
                minCellSize[i].x += p.minCellSize[i].x;
                minCellSize[i].y += p.minCellSize[i].y;
                horizontalMargin[i] += p.horizontalMargin[i];
                hotseatBorderSpaces[i] += p.hotseatBorderSpaces[i];
                allAppsCellSize[i].x += p.allAppsCellSize[i].x;
                allAppsCellSize[i].y += p.allAppsCellSize[i].y;
                allAppsIconSizes[i] += p.allAppsIconSizes[i];
                allAppsIconTextSizes[i] += p.allAppsIconTextSizes[i];
                allAppsBorderSpaces[i].x += p.allAppsBorderSpaces[i].x;
                allAppsBorderSpaces[i].y += p.allAppsBorderSpaces[i].y;
                inlineQsb[i] |= p.inlineQsb[i];
            }

            folderBorderSpace += p.folderBorderSpace;

            return this;
        }
    }
}
