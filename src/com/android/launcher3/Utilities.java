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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import com.android.launcher3.settings.SettingsProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Various utilities shared amongst the Launcher's classes.
 */
public final class Utilities {

    private static final String TAG = "Launcher.Utilities";

    private static final Rect sOldBounds = new Rect();
    private static final Canvas sCanvas = new Canvas();

    private static final Pattern sTrimPattern =
            Pattern.compile("^[\\s|\\p{javaSpaceChar}]*(.*)[\\s|\\p{javaSpaceChar}]*$");

    static {
        sCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
                Paint.FILTER_BITMAP_FLAG));
    }
    static int sColors[] = { 0xffff0000, 0xff00ff00, 0xff0000ff };
    static int sColorIndex = 0;

    private static final int[] sLoc0 = new int[2];
    private static final int[] sLoc1 = new int[2];

    // TODO: use Build.VERSION_CODES when available
    public static final boolean ATLEAST_MARSHMALLOW = Build.VERSION.SDK_INT >= 23;

    public static final boolean ATLEAST_LOLLIPOP_MR1 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1;

    public static final boolean ATLEAST_LOLLIPOP =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

    public static final boolean ATLEAST_KITKAT =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

    public static final boolean ATLEAST_JB_MR1 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;

    public static final boolean ATLEAST_JB_MR2 =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;

    // To turn on these properties, type
    // adb shell setprop log.tag.PROPERTY_NAME [VERBOSE | SUPPRESS]
    private static final String FORCE_ENABLE_ROTATION_PROPERTY = "launcher_force_rotate";
    private static boolean sForceEnableRotation = isPropertyEnabled(FORCE_ENABLE_ROTATION_PROPERTY);

    public static boolean isPropertyEnabled(String propertyName) {
        return Log.isLoggable(propertyName, Log.VERBOSE);
    }

    public static boolean isAllowRotationPrefEnabled(Context context, boolean multiProcess) {
        boolean allowRotationPref = SettingsProvider.getBoolean(context,
                SettingsProvider.SETTINGS_UI_ALLOW_ROTATION,
                R.bool.preferences_interface_allow_rotation);
        return sForceEnableRotation || allowRotationPref;
    }

    public static boolean isRotationAllowedForDevice(Context context) {
        return sForceEnableRotation || context.getResources().getBoolean(R.bool.preferences_interface_allow_rotation);
    }

    public static Bitmap createIconBitmap(Cursor c, int iconIndex, Context context) {
        byte[] data = c.getBlob(iconIndex);
        try {
            return createIconBitmap(BitmapFactory.decodeByteArray(data, 0, data.length), context);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns a bitmap suitable for the all apps view. If the package or the resource do not
     * exist, it returns null.
     */
    public static Bitmap createIconBitmap(String packageName, String resourceName,
            Context context) {
        PackageManager packageManager = context.getPackageManager();
        // the resource
        try {
            Resources resources = packageManager.getResourcesForApplication(packageName);
            if (resources != null) {
                final int id = resources.getIdentifier(resourceName, null, null);
                return createIconBitmap(
                        resources.getDrawableForDensity(id, LauncherAppState.getInstance()
                                .getInvariantDeviceProfile().fillResIconDpi), context);
            }
        } catch (Exception e) {
            // Icon not found.
        }
        return null;
    }

    private static int getIconBitmapSize() {
        return LauncherAppState.getInstance().getInvariantDeviceProfile().iconBitmapSize;
    }

    /**
     * Returns a bitmap which is of the appropriate size to be displayed as an icon
     */
    public static Bitmap createIconBitmap(Bitmap icon, Context context) {
        final int iconBitmapSize = getIconBitmapSize();
        if (iconBitmapSize == icon.getWidth() && iconBitmapSize == icon.getHeight()) {
            return icon;
        }
        return createIconBitmap(new BitmapDrawable(context.getResources(), icon), context);
    }

    /**
     * Returns a bitmap suitable for the all apps view.
     */
    public static Bitmap createIconBitmap(Drawable icon, Context context) {
        synchronized (sCanvas) {
            final int iconBitmapSize = getIconBitmapSize();

            int width = iconBitmapSize;
            int height = iconBitmapSize;

            if (icon instanceof PaintDrawable) {
                PaintDrawable painter = (PaintDrawable) icon;
                painter.setIntrinsicWidth(width);
                painter.setIntrinsicHeight(height);
            } else if (icon instanceof BitmapDrawable) {
                // Ensure the bitmap has a density.
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = bitmapDrawable.getBitmap();
                if (bitmap.getDensity() == Bitmap.DENSITY_NONE) {
                    bitmapDrawable.setTargetDensity(context.getResources().getDisplayMetrics());
                }
            }
            int sourceWidth = icon.getIntrinsicWidth();
            int sourceHeight = icon.getIntrinsicHeight();
            if (sourceWidth > 0 && sourceHeight > 0) {
                // Scale the icon proportionally to the icon dimensions
                final float ratio = (float) sourceWidth / sourceHeight;
                if (sourceWidth > sourceHeight) {
                    height = (int) (width / ratio);
                } else if (sourceHeight > sourceWidth) {
                    width = (int) (height * ratio);
                }
            }

            // no intrinsic size --> use default size
            int textureWidth = iconBitmapSize;
            int textureHeight = iconBitmapSize;

            final Bitmap bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = sCanvas;
            canvas.setBitmap(bitmap);

            final int left = (textureWidth-width) / 2;
            final int top = (textureHeight-height) / 2;

            @SuppressWarnings("all") // suppress dead code warning
            final boolean debug = false;
            if (debug) {
                // draw a big box for the icon for debugging
                canvas.drawColor(sColors[sColorIndex]);
                if (++sColorIndex >= sColors.length) sColorIndex = 0;
                Paint debugPaint = new Paint();
                debugPaint.setColor(0xffcccc00);
                canvas.drawRect(left, top, left+width, top+height, debugPaint);
            }

            sOldBounds.set(icon.getBounds());
            icon.setBounds(left, top, left+width, top+height);
            icon.draw(canvas);
            icon.setBounds(sOldBounds);
            canvas.setBitmap(null);

            return bitmap;
        }
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in a parent view's
     * coordinates.
     *
     * @param descendant The descendant to which the passed coordinate is relative.
     * @param root The root view to make the coordinates relative to.
     * @param coord The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the descendant:
     *          sometimes this is relevant as in a child's coordinates within the descendant.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     *         this scale factor is assumed to be equal in X and Y, and so if at any point this
     *         assumption fails, we will need to return a pair of scale factors.
     */
    public static float getDescendantCoordRelativeToParent(View descendant, View root,
                                                           int[] coord, boolean includeRootScroll) {
        ArrayList<View> ancestorChain = new ArrayList<View>();

        float[] pt = {coord[0], coord[1]};

        View v = descendant;
        while(v != root && v != null) {
            ancestorChain.add(v);
            v = (View) v.getParent();
        }
        ancestorChain.add(root);

        float scale = 1.0f;
        int count = ancestorChain.size();
        for (int i = 0; i < count; i++) {
            View v0 = ancestorChain.get(i);
            // For TextViews, scroll has a meaning which relates to the text position
            // which is very strange... ignore the scroll.
            if (v0 != descendant || includeRootScroll) {
                pt[0] -= v0.getScrollX();
                pt[1] -= v0.getScrollY();
            }

            v0.getMatrix().mapPoints(pt);
            pt[0] += v0.getLeft();
            pt[1] += v0.getTop();
            scale *= v0.getScaleX();
        }

        coord[0] = (int) Math.round(pt[0]);
        coord[1] = (int) Math.round(pt[1]);
        return scale;
    }

    /**
     * Inverse of {@link #getDescendantCoordRelativeToSelf(View, int[])}.
     */
    public static float mapCoordInSelfToDescendent(View descendant, View root,
                                                   int[] coord) {
        ArrayList<View> ancestorChain = new ArrayList<View>();

        float[] pt = {coord[0], coord[1]};

        View v = descendant;
        while(v != root) {
            ancestorChain.add(v);
            v = (View) v.getParent();
        }
        ancestorChain.add(root);

        float scale = 1.0f;
        Matrix inverse = new Matrix();
        int count = ancestorChain.size();
        for (int i = count - 1; i >= 0; i--) {
            View ancestor = ancestorChain.get(i);
            View next = i > 0 ? ancestorChain.get(i-1) : null;

            pt[0] += ancestor.getScrollX();
            pt[1] += ancestor.getScrollY();

            if (next != null) {
                pt[0] -= next.getLeft();
                pt[1] -= next.getTop();
                next.getMatrix().invert(inverse);
                inverse.mapPoints(pt);
                scale *= next.getScaleX();
            }
        }

        coord[0] = (int) Math.round(pt[0]);
        coord[1] = (int) Math.round(pt[1]);
        return scale;
    }

    /**
     * Utility method to determine whether the given point, in local coordinates,
     * is inside the view, where the area of the view is expanded by the slop factor.
     * This method is called while processing touch-move events to determine if the event
     * is still within the view.
     */
    public static boolean pointInView(View v, float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < (v.getWidth() + slop) &&
                localY < (v.getHeight() + slop);
    }

    public static void scaleRect(Rect r, float scale) {
        if (scale != 1.0f) {
            r.left = (int) (r.left * scale + 0.5f);
            r.top = (int) (r.top * scale + 0.5f);
            r.right = (int) (r.right * scale + 0.5f);
            r.bottom = (int) (r.bottom * scale + 0.5f);
        }
    }

    public static int[] getCenterDeltaInScreenSpace(View v0, View v1, int[] delta) {
        v0.getLocationInWindow(sLoc0);
        v1.getLocationInWindow(sLoc1);

        sLoc0[0] += (v0.getMeasuredWidth() * v0.getScaleX()) / 2;
        sLoc0[1] += (v0.getMeasuredHeight() * v0.getScaleY()) / 2;
        sLoc1[0] += (v1.getMeasuredWidth() * v1.getScaleX()) / 2;
        sLoc1[1] += (v1.getMeasuredHeight() * v1.getScaleY()) / 2;

        if (delta == null) {
            delta = new int[2];
        }

        delta[0] = sLoc1[0] - sLoc0[0];
        delta[1] = sLoc1[1] - sLoc0[1];

        return delta;
    }

    public static void scaleRectAboutCenter(Rect r, float scale) {
        int cx = r.centerX();
        int cy = r.centerY();
        r.offset(-cx, -cy);
        Utilities.scaleRect(r, scale);
        r.offset(cx, cy);
    }

    public static void startActivityForResultSafely(
            Activity activity, Intent intent, int requestCode) {
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    static boolean isSystemApp(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        ComponentName cn = intent.getComponent();
        String packageName = null;
        if (cn == null) {
            ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if ((info != null) && (info.activityInfo != null)) {
                packageName = info.activityInfo.packageName;
            }
        } else {
            packageName = cn.getPackageName();
        }
        if (packageName != null) {
            try {
                PackageInfo info = pm.getPackageInfo(packageName, 0);
                return (info != null) && (info.applicationInfo != null) &&
                        ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            } catch (NameNotFoundException e) {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * This picks a dominant color, looking for high-saturation, high-value, repeated hues.
     * @param bitmap The bitmap to scan
     * @param samples The approximate max number of samples to use.
     */
    static int findDominantColorByHue(Bitmap bitmap, int samples) {
        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        int sampleStride = (int) Math.sqrt((height * width) / samples);
        if (sampleStride < 1) {
            sampleStride = 1;
        }

        // This is an out-param, for getting the hsv values for an rgb
        float[] hsv = new float[3];

        // First get the best hue, by creating a histogram over 360 hue buckets,
        // where each pixel contributes a score weighted by saturation, value, and alpha.
        float[] hueScoreHistogram = new float[360];
        float highScore = -1;
        int bestHue = -1;

        for (int y = 0; y < height; y += sampleStride) {
            for (int x = 0; x < width; x += sampleStride) {
                int argb = bitmap.getPixel(x, y);
                int alpha = 0xFF & (argb >> 24);
                if (alpha < 0x80) {
                    // Drop mostly-transparent pixels.
                    continue;
                }
                // Remove the alpha channel.
                int rgb = argb | 0xFF000000;
                Color.colorToHSV(rgb, hsv);
                // Bucket colors by the 360 integer hues.
                int hue = (int) hsv[0];
                if (hue < 0 || hue >= hueScoreHistogram.length) {
                    // Defensively avoid array bounds violations.
                    continue;
                }
                float score = hsv[1] * hsv[2];
                hueScoreHistogram[hue] += score;
                if (hueScoreHistogram[hue] > highScore) {
                    highScore = hueScoreHistogram[hue];
                    bestHue = hue;
                }
            }
        }

        SparseArray<Float> rgbScores = new SparseArray<Float>();
        int bestColor = 0xff000000;
        highScore = -1;
        // Go back over the RGB colors that match the winning hue,
        // creating a histogram of weighted s*v scores, for up to 100*100 [s,v] buckets.
        // The highest-scoring RGB color wins.
        for (int y = 0; y < height; y += sampleStride) {
            for (int x = 0; x < width; x += sampleStride) {
                int rgb = bitmap.getPixel(x, y) | 0xff000000;
                Color.colorToHSV(rgb, hsv);
                int hue = (int) hsv[0];
                if (hue == bestHue) {
                    float s = hsv[1];
                    float v = hsv[2];
                    int bucket = (int) (s * 100) + (int) (v * 10000);
                    // Score by cumulative saturation * value.
                    float score = s * v;
                    Float oldTotal = rgbScores.get(bucket);
                    float newTotal = oldTotal == null ? score : oldTotal + score;
                    rgbScores.put(bucket, newTotal);
                    if (newTotal > highScore) {
                        highScore = newTotal;
                        // All the colors in the winning bucket are very similar. Last in wins.
                        bestColor = rgb;
                    }
                }
            }
        }
        return bestColor;
    }

    /*
     * Finds a system apk which had a broadcast receiver listening to a particular action.
     * @param action intent action used to find the apk
     * @return a pair of apk package name and the resources.
     */
    static Pair<String, Resources> findSystemApk(String action, PackageManager pm) {
        final Intent intent = new Intent(action);
        for (ResolveInfo info : pm.queryBroadcastReceivers(intent, 0)) {
            if (info.activityInfo != null &&
                    (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                final String packageName = info.activityInfo.packageName;
                try {
                    final Resources res = pm.getResourcesForApplication(packageName);
                    return Pair.create(packageName, res);
                } catch (NameNotFoundException e) {
                    Log.w(TAG, "Failed to find resources for " + packageName);
                }
            }
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean isViewAttachedToWindow(View v) {
        if (ATLEAST_KITKAT) {
            return v.isAttachedToWindow();
        } else {
            // A proxy call which returns null, if the view is not attached to the window.
            return v.getKeyDispatcherState() != null;
        }
    }

    /**
     * Returns a widget with category {@link AppWidgetProviderInfo#WIDGET_CATEGORY_SEARCHBOX}
     * provided by the same package which is set to be global search activity.
     * If widgetCategory is not supported, or no such widget is found, returns the first widget
     * provided by the package.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static AppWidgetProviderInfo getSearchWidgetProvider(Context context) {
        SearchManager searchManager =
                (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        ComponentName searchComponent = searchManager.getGlobalSearchActivity();
        if (searchComponent == null) return null;
        String providerPkg = searchComponent.getPackageName();

        AppWidgetProviderInfo defaultWidgetForSearchPackage = null;

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        for (AppWidgetProviderInfo info : appWidgetManager.getInstalledProviders()) {
            if (info.provider.getPackageName().equals(providerPkg)) {
                if (ATLEAST_JB_MR1) {
                    if ((info.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0) {
                        return info;
                    } else if (defaultWidgetForSearchPackage == null) {
                        defaultWidgetForSearchPackage = info;
                    }
                } else {
                    return info;
                }
            }
        }
        return defaultWidgetForSearchPackage;
    }

    /**
     * Compresses the bitmap to a byte array for serialization.
     */
    public static byte[] flattenBitmap(Bitmap bitmap) {
        // Try go guesstimate how much space the icon will take when serialized
        // to avoid unnecessary allocations/copies during the write.
        int size = bitmap.getWidth() * bitmap.getHeight() * 4;
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return out.toByteArray();
        } catch (IOException e) {
            Log.w(TAG, "Could not write bitmap");
            return null;
        }
    }

    /**
     * Find the first vacant cell, if there is one.
     *
     * @param vacant Holds the x and y coordinate of the vacant cell
     * @param spanX Horizontal cell span.
     * @param spanY Vertical cell span.
     *
     * @return true if a vacant cell was found
     */
    public static boolean findVacantCell(int[] vacant, int spanX, int spanY,
            int xCount, int yCount, boolean[][] occupied) {

        for (int y = 0; (y + spanY) <= yCount; y++) {
            for (int x = 0; (x + spanX) <= xCount; x++) {
                boolean available = !occupied[x][y];
                out:            for (int i = x; i < x + spanX; i++) {
                    for (int j = y; j < y + spanY; j++) {
                        available = available && !occupied[i][j];
                        if (!available) break out;
                    }
                }

                if (available) {
                    vacant[0] = x;
                    vacant[1] = y;
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Trims the string, removing all whitespace at the beginning and end of the string.
     * Non-breaking whitespaces are also removed.
     */
    public static String trim(CharSequence s) {
        if (s == null) {
            return null;
        }

        // Just strip any sequence of whitespace or java space characters from the beginning and end
        Matcher m = sTrimPattern.matcher(s);
        return m.replaceAll("$1");
    }

    /**
     * Calculates the height of a given string at a specific text size.
     */
    public static float calculateTextHeight(float textSizePx) {
        Paint p = new Paint();
        p.setTextSize(textSizePx);
        Paint.FontMetrics fm = p.getFontMetrics();
        return -fm.top + fm.bottom;
    }

    /**
     * Convenience println with multiple args.
     */
    public static void println(String key, Object... args) {
        StringBuilder b = new StringBuilder();
        b.append(key);
        b.append(": ");
        boolean isFirstArgument = true;
        for (Object arg : args) {
            if (isFirstArgument) {
                isFirstArgument = false;
            } else {
                b.append(", ");
            }
            b.append(arg);
        }
        System.out.println(b.toString());
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean isRtl(Resources res) {
        return ATLEAST_JB_MR1 &&
                (res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
    }

    public static void assertWorkerThread() {
        if (LauncherAppState.isDogfoodBuild() &&
                (LauncherModel.sWorkerThread.getThreadId() != Process.myTid())) {
            throw new IllegalStateException();
        }
    }

    /**
     * Returns true if the intent is a valid launch intent for a launcher activity of an app.
     * This is used to identify shortcuts which are different from the ones exposed by the
     * applications' manifest file.
     *
     * @param launchIntent The intent that will be launched when the shortcut is clicked.
     */
    public static boolean isLauncherAppTarget(Intent launchIntent) {
        if (launchIntent != null
                && Intent.ACTION_MAIN.equals(launchIntent.getAction())
                && launchIntent.getComponent() != null
                && launchIntent.getCategories() != null
                && launchIntent.getCategories().size() == 1
                && launchIntent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && TextUtils.isEmpty(launchIntent.getDataString())) {
            // An app target can either have no extra or have ItemInfo.EXTRA_PROFILE.
            Bundle extras = launchIntent.getExtras();
            if (extras == null) {
                return true;
            } else {
                Set<String> keys = extras.keySet();
                return keys.size() == 1 && keys.contains(ItemInfo.EXTRA_PROFILE);
            }
        };
        return false;
    }

    public static float dpiFromPx(int size, DisplayMetrics metrics){
        return (size / metrics.density);
    }
    public static int pxFromDp(float size, DisplayMetrics metrics) {
        return (int) Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                size, metrics));
    }
    public static int pxFromSp(float size, DisplayMetrics metrics) {
        return (int) Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                size, metrics));
    }

    public static Point caluclateFolderContentDimensions(int count, int maxCountX, int maxCountY) {
        final Point point = new Point();
        final int maxItemsPerPage = maxCountX * maxCountY;
        boolean done;
        if (count >= maxItemsPerPage) {
            point.x = maxCountX;
            point.y = maxCountY;
            done = true;
        } else {
            done = false;
        }

        while (!done) {
            int oldCountX = point.x;
            int oldCountY = point.y;
            if (point.x * point.y < count) {
                // Current grid is too small, expand it
                if ((point.x <= point.y || point.y == maxCountY) && point.x < maxCountX) {
                    point.x++;
                } else if (point.y < maxCountY) {
                    point.y++;
                }
                if (point.y == 0) point.y++;
            } else if ((point.y - 1) * point.x >= count && point.y >= point.x) {
                point.y = Math.max(0, point.y - 1);
            } else if ((point.x - 1) * point.y >= count) {
                point.x = Math.max(0, point.x - 1);
            }
            done = point.x == oldCountX && point.y == oldCountY;
        }
        return point;
    }

    public static String createDbSelectionQuery(String columnName, Iterable<?> values) {
        return String.format(Locale.ENGLISH, "%s IN (%s)", columnName, TextUtils.join(", ", values));
    }

    public static boolean searchActivityExists(Context context) {
        final SearchManager searchManager =
                (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        ComponentName globalSearchActivity = searchManager.getGlobalSearchActivity();

        return globalSearchActivity != null;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo pi = packageManager.getPackageInfo(pkg, 0);
            return pi.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    public static boolean isConnectedToWiFi(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
    }
}
