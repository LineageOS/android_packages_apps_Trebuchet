/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.appwidget.AppWidgetHost;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.Patterns;
import android.util.Xml;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherProvider.SqlArguments;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.qsb.QsbContainerView;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Thunk;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Layout parsing code for auto installs layout
 */
public class AutoInstallsLayout {
    private static final String TAG = "AutoInstalls";
    private static final boolean LOGD = false;

    /** Marker action used to discover a package which defines launcher customization */
    static final String ACTION_LAUNCHER_CUSTOMIZATION =
            "android.autoinstalls.config.action.PLAY_AUTO_INSTALL";

    /**
     * Layout resource which also includes grid size and hotseat count, e.g., default_layout_6x6_h5
     */
    private static final String FORMATTED_LAYOUT_RES_WITH_HOSTEAT = "default_layout_%dx%d_h%s";
    private static final String FORMATTED_LAYOUT_RES = "default_layout_%dx%d";
    private static final String LAYOUT_RES = "default_layout";

    static AutoInstallsLayout get(Context context, AppWidgetHost appWidgetHost,
            LayoutParserCallback callback) {
        Pair<String, Resources> customizationApkInfo = PackageManagerHelper.findSystemApk(
                ACTION_LAUNCHER_CUSTOMIZATION, context.getPackageManager());
        if (customizationApkInfo == null) {
            return null;
        }
        String pkg = customizationApkInfo.first;
        Resources targetRes = customizationApkInfo.second;
        InvariantDeviceProfile grid = LauncherAppState.getIDP(context);

        // Try with grid size and hotseat count
        String layoutName = String.format(Locale.ENGLISH, FORMATTED_LAYOUT_RES_WITH_HOSTEAT,
                grid.numColumns, grid.numRows, grid.numHotseatIcons);
        int layoutId = targetRes.getIdentifier(layoutName, "xml", pkg);

        // Try with only grid size
        if (layoutId == 0) {
            Log.d(TAG, "Formatted layout: " + layoutName
                    + " not found. Trying layout without hosteat");
            layoutName = String.format(Locale.ENGLISH, FORMATTED_LAYOUT_RES,
                    grid.numColumns, grid.numRows);
            layoutId = targetRes.getIdentifier(layoutName, "xml", pkg);
        }

        // Try the default layout
        if (layoutId == 0) {
            Log.d(TAG, "Formatted layout: " + layoutName + " not found. Trying the default layout");
            layoutId = targetRes.getIdentifier(LAYOUT_RES, "xml", pkg);
        }

        if (layoutId == 0) {
            Log.e(TAG, "Layout definition not found in package: " + pkg);
            return null;
        }
        return new AutoInstallsLayout(context, appWidgetHost, callback, targetRes, layoutId,
                TAG_WORKSPACE);
    }

    // Object Tags
    private static final String TAG_INCLUDE = "include";
    public static final String TAG_WORKSPACE = "workspace";
    private static final String TAG_APP_ICON = "appicon";
    private static final String TAG_AUTO_INSTALL = "autoinstall";
    private static final String TAG_FOLDER = "folder";
    private static final String TAG_APPWIDGET = "appwidget";
    protected static final String TAG_SEARCH_WIDGET = "searchwidget";
    private static final String TAG_SHORTCUT = "shortcut";
    private static final String TAG_EXTRA = "extra";

    private static final String ATTR_CONTAINER = "container";
    private static final String ATTR_RANK = "rank";

    private static final String ATTR_PACKAGE_NAME = "packageName";
    private static final String ATTR_CLASS_NAME = "className";
    private static final String ATTR_TITLE = "title";
    private static final String ATTR_SCREEN = "screen";

    // x and y can be specified as negative integers, in which case -1 represents the
    // last row / column, -2 represents the second last, and so on.
    private static final String ATTR_X = "x";
    private static final String ATTR_Y = "y";

    private static final String ATTR_SPAN_X = "spanX";
    private static final String ATTR_SPAN_Y = "spanY";
    private static final String ATTR_ICON = "icon";
    private static final String ATTR_URL = "url";

    // Attrs for "Include"
    private static final String ATTR_WORKSPACE = "workspace";

    // Style attrs -- "Extra"
    private static final String ATTR_KEY = "key";
    private static final String ATTR_VALUE = "value";

    private static final String HOTSEAT_CONTAINER_NAME =
            Favorites.containerToString(Favorites.CONTAINER_HOTSEAT);

    @Thunk
    final Context mContext;
    @Thunk
    final AppWidgetHost mAppWidgetHost;
    protected final LayoutParserCallback mCallback;

    protected final PackageManager mPackageManager;
    protected final Resources mSourceRes;
    protected final Supplier<XmlPullParser> mInitialLayoutSupplier;

    private final InvariantDeviceProfile mIdp;
    private final int mRowCount;
    private final int mColumnCount;

    private final int[] mTemp = new int[2];
    @Thunk
    final ContentValues mValues;
    protected final String mRootTag;

    protected SQLiteDatabase mDb;

    public AutoInstallsLayout(Context context, AppWidgetHost appWidgetHost,
            LayoutParserCallback callback, Resources res,
            int layoutId, String rootTag) {
        this(context, appWidgetHost, callback, res, () -> res.getXml(layoutId), rootTag);
    }

    public AutoInstallsLayout(Context context, AppWidgetHost appWidgetHost,
            LayoutParserCallback callback, Resources res,
            Supplier<XmlPullParser> initialLayoutSupplier, String rootTag) {
        mContext = context;
        mAppWidgetHost = appWidgetHost;
        mCallback = callback;

        mPackageManager = context.getPackageManager();
        mValues = new ContentValues();
        mRootTag = rootTag;

        mSourceRes = res;
        mInitialLayoutSupplier = initialLayoutSupplier;

        mIdp = LauncherAppState.getIDP(context);
        mRowCount = mIdp.numRows;
        mColumnCount = mIdp.numColumns;
    }

    /**
     * Loads the layout in the db and returns the number of entries added on the desktop.
     */
    public int loadLayout(SQLiteDatabase db, IntArray screenIds) {
        mDb = db;
        try {
            return parseLayout(mInitialLayoutSupplier.get(), screenIds);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing layout: ", e);
            return -1;
        }
    }

    /**
     * Parses the layout and returns the number of elements added on the homescreen.
     */
    protected int parseLayout(XmlPullParser parser, IntArray screenIds)
            throws XmlPullParserException, IOException {
        beginDocument(parser, mRootTag);
        final int depth = parser.getDepth();
        int type;
        ArrayMap<String, TagParser> tagParserMap = getLayoutElementsMap();
        int count = 0;

        while (((type = parser.next()) != XmlPullParser.END_TAG ||
                parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            count += parseAndAddNode(parser, tagParserMap, screenIds);
        }
        return count;
    }

    /**
     * Parses container and screenId attribute from the current tag, and puts it in the out.
     * @param out array of size 2.
     */
    protected void parseContainerAndScreen(XmlPullParser parser, int[] out) {
        if (HOTSEAT_CONTAINER_NAME.equals(getAttributeValue(parser, ATTR_CONTAINER))) {
            out[0] = Favorites.CONTAINER_HOTSEAT;
            // Hack: hotseat items are stored using screen ids
            out[1] = Integer.parseInt(getAttributeValue(parser, ATTR_RANK));
        } else {
            out[0] = Favorites.CONTAINER_DESKTOP;
            out[1] = Integer.parseInt(getAttributeValue(parser, ATTR_SCREEN));
        }
    }

    /**
     * Parses the current node and returns the number of elements added.
     */
    protected int parseAndAddNode(
            XmlPullParser parser, ArrayMap<String, TagParser> tagParserMap, IntArray screenIds)
            throws XmlPullParserException, IOException {

        if (TAG_INCLUDE.equals(parser.getName())) {
            final int resId = getAttributeResourceValue(parser, ATTR_WORKSPACE, 0);
            if (resId != 0) {
                // recursively load some more favorites, why not?
                return parseLayout(mSourceRes.getXml(resId), screenIds);
            } else {
                return 0;
            }
        }

        mValues.clear();
        parseContainerAndScreen(parser, mTemp);
        final int container = mTemp[0];
        final int screenId = mTemp[1];

        mValues.put(Favorites.CONTAINER, container);
        mValues.put(Favorites.SCREEN, screenId);

        mValues.put(Favorites.CELLX,
                convertToDistanceFromEnd(getAttributeValue(parser, ATTR_X), mColumnCount));
        mValues.put(Favorites.CELLY,
                convertToDistanceFromEnd(getAttributeValue(parser, ATTR_Y), mRowCount));

        TagParser tagParser = tagParserMap.get(parser.getName());
        if (tagParser == null) {
            if (LOGD) Log.d(TAG, "Ignoring unknown element tag: " + parser.getName());
            return 0;
        }
        int newElementId = tagParser.parseAndAdd(parser);
        if (newElementId >= 0) {
            // Keep track of the set of screens which need to be added to the db.
            if (!screenIds.contains(screenId) &&
                    container == Favorites.CONTAINER_DESKTOP) {
                screenIds.add(screenId);
            }
            return 1;
        }
        return 0;
    }

    protected int addShortcut(String title, Intent intent, int type) {
        int id = mCallback.generateNewItemId();
        mValues.put(Favorites.INTENT, intent.toUri(0));
        mValues.put(Favorites.TITLE, title);
        mValues.put(Favorites.ITEM_TYPE, type);
        mValues.put(Favorites.SPANX, 1);
        mValues.put(Favorites.SPANY, 1);
        mValues.put(Favorites._ID, id);
        if (mCallback.insertAndCheck(mDb, mValues) < 0) {
            return -1;
        } else {
            return id;
        }
    }

    protected ArrayMap<String, TagParser> getFolderElementsMap() {
        ArrayMap<String, TagParser> parsers = new ArrayMap<>();
        parsers.put(TAG_APP_ICON, new AppShortcutParser());
        parsers.put(TAG_AUTO_INSTALL, new AutoInstallParser());
        parsers.put(TAG_SHORTCUT, new ShortcutParser(mSourceRes));
        return parsers;
    }

    protected ArrayMap<String, TagParser> getLayoutElementsMap() {
        ArrayMap<String, TagParser> parsers = new ArrayMap<>();
        parsers.put(TAG_APP_ICON, new AppShortcutParser());
        parsers.put(TAG_AUTO_INSTALL, new AutoInstallParser());
        parsers.put(TAG_FOLDER, new FolderParser());
        parsers.put(TAG_APPWIDGET, new PendingWidgetParser());
        parsers.put(TAG_SEARCH_WIDGET, new SearchWidgetParser());
        parsers.put(TAG_SHORTCUT, new ShortcutParser(mSourceRes));
        return parsers;
    }

    protected interface TagParser {
        /**
         * Parses the tag and adds to the db
         * @return the id of the row added or -1;
         */
        int parseAndAdd(XmlPullParser parser)
                throws XmlPullParserException, IOException;
    }

    /**
     * App shortcuts: required attributes packageName and className
     */
    protected class AppShortcutParser implements TagParser {

        @Override
        public int parseAndAdd(XmlPullParser parser) {
            final String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            final String className = getAttributeValue(parser, ATTR_CLASS_NAME);

            if (!TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(className)) {
                ActivityInfo info;
                try {
                    ComponentName cn;
                    try {
                        cn = new ComponentName(packageName, className);
                        info = mPackageManager.getActivityInfo(cn, 0);
                    } catch (PackageManager.NameNotFoundException nnfe) {
                        String[] packages = mPackageManager.currentToCanonicalPackageNames(
                                new String[]{packageName});
                        cn = new ComponentName(packages[0], className);
                        info = mPackageManager.getActivityInfo(cn, 0);
                    }
                    final Intent intent = new Intent(Intent.ACTION_MAIN, null)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(cn)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                    return addShortcut(info.loadLabel(mPackageManager).toString(),
                            intent, Favorites.ITEM_TYPE_APPLICATION);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Favorite not found: " + packageName + "/" + className);
                }
                return -1;
            } else {
                return invalidPackageOrClass(parser);
            }
        }

        /**
         * Helper method to allow extending the parser capabilities
         */
        protected int invalidPackageOrClass(XmlPullParser parser) {
            Log.w(TAG, "Skipping invalid <favorite> with no component");
            return -1;
        }
    }

    /**
     * AutoInstall: required attributes packageName and className
     */
    protected class AutoInstallParser implements TagParser {

        @Override
        public int parseAndAdd(XmlPullParser parser) {
            final String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            final String className = getAttributeValue(parser, ATTR_CLASS_NAME);
            if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(className)) {
                if (LOGD) Log.d(TAG, "Skipping invalid <favorite> with no component");
                return -1;
            }

            mValues.put(Favorites.RESTORED, WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON);
            final Intent intent = new Intent(Intent.ACTION_MAIN, null)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(new ComponentName(packageName, className))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return addShortcut(mContext.getString(R.string.package_state_unknown), intent,
                    Favorites.ITEM_TYPE_APPLICATION);
        }
    }

    /**
     * Parses a web shortcut. Required attributes url, icon, title
     */
    protected class ShortcutParser implements TagParser {

        private final Resources mIconRes;

        public ShortcutParser(Resources iconRes) {
            mIconRes = iconRes;
        }

        @Override
        public int parseAndAdd(XmlPullParser parser) {
            final int titleResId = getAttributeResourceValue(parser, ATTR_TITLE, 0);
            final int iconId = getAttributeResourceValue(parser, ATTR_ICON, 0);

            if (titleResId == 0 || iconId == 0) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut");
                return -1;
            }

            final Intent intent = parseIntent(parser);
            if (intent == null) {
                return -1;
            }

            Drawable icon = mIconRes.getDrawable(iconId);
            if (icon == null) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut, can't load icon");
                return -1;
            }

            // Auto installs should always support the current platform version.
            LauncherIcons li = LauncherIcons.obtain(mContext);
            mValues.put(LauncherSettings.Favorites.ICON, GraphicsUtils.flattenBitmap(
                    li.createBadgedIconBitmap(icon, Process.myUserHandle(), VERSION.SDK_INT).icon));
            li.recycle();

            mValues.put(Favorites.ICON_PACKAGE, mIconRes.getResourcePackageName(iconId));
            mValues.put(Favorites.ICON_RESOURCE, mIconRes.getResourceName(iconId));

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return addShortcut(mSourceRes.getString(titleResId),
                    intent, Favorites.ITEM_TYPE_SHORTCUT);
        }

        protected Intent parseIntent(XmlPullParser parser) {
            final String url = getAttributeValue(parser, ATTR_URL);
            if (TextUtils.isEmpty(url) || !Patterns.WEB_URL.matcher(url).matches()) {
                if (LOGD) Log.d(TAG, "Ignoring shortcut, invalid url: " + url);
                return null;
            }
            return new Intent(Intent.ACTION_VIEW, null).setData(Uri.parse(url));
        }
    }

    /**
     * AppWidget parser: Required attributes packageName, className, spanX and spanY.
     * Options child nodes: <extra key=... value=... />
     * It adds a pending widget which allows the widget to come later. If there are extras, those
     * are passed to widget options during bind.
     * The config activity for the widget (if present) is not shown, so any optional configurations
     * should be passed as extras and the widget should support reading these widget options.
     */
    protected class PendingWidgetParser implements TagParser {

        @Nullable
        public ComponentName getComponentName(XmlPullParser parser) {
            final String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            final String className = getAttributeValue(parser, ATTR_CLASS_NAME);
            if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(className)) {
                return null;
            }
            return new ComponentName(packageName, className);
        }


        @Override
        public int parseAndAdd(XmlPullParser parser)
                throws XmlPullParserException, IOException {
            ComponentName cn = getComponentName(parser);
            if (cn == null) {
                if (LOGD) Log.d(TAG, "Skipping invalid <appwidget> with no component");
                return -1;
            }

            mValues.put(Favorites.SPANX, getAttributeValue(parser, ATTR_SPAN_X));
            mValues.put(Favorites.SPANY, getAttributeValue(parser, ATTR_SPAN_Y));
            mValues.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPWIDGET);

            // Read the extras
            Bundle extras = new Bundle();
            int widgetDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > widgetDepth) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }

                if (TAG_EXTRA.equals(parser.getName())) {
                    String key = getAttributeValue(parser, ATTR_KEY);
                    String value = getAttributeValue(parser, ATTR_VALUE);
                    if (key != null && value != null) {
                        extras.putString(key, value);
                    } else {
                        throw new RuntimeException("Widget extras must have a key and value");
                    }
                } else {
                    throw new RuntimeException("Widgets can contain only extras");
                }
            }
            return verifyAndInsert(cn, extras);
        }

        protected int verifyAndInsert(ComponentName cn, Bundle extras) {
            mValues.put(Favorites.APPWIDGET_PROVIDER, cn.flattenToString());
            mValues.put(Favorites.RESTORED,
                    LauncherAppWidgetInfo.FLAG_ID_NOT_VALID
                            | LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY
                            | LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG);
            mValues.put(Favorites._ID, mCallback.generateNewItemId());
            if (!extras.isEmpty()) {
                mValues.put(Favorites.INTENT, new Intent().putExtras(extras).toUri(0));
            }

            int insertedId = mCallback.insertAndCheck(mDb, mValues);
            if (insertedId < 0) {
                return -1;
            } else {
                return insertedId;
            }
        }
    }

    protected class SearchWidgetParser extends PendingWidgetParser {
        @Override
        @Nullable
        public ComponentName getComponentName(XmlPullParser parser) {
            return QsbContainerView.getSearchComponentName(mContext);
        }

        @Override
        protected int verifyAndInsert(ComponentName cn, Bundle extras) {
            mValues.put(Favorites.OPTIONS, LauncherAppWidgetInfo.OPTION_SEARCH_WIDGET);
            int flags = mValues.getAsInteger(Favorites.RESTORED)
                    | WorkspaceItemInfo.FLAG_RESTORE_STARTED;
            mValues.put(Favorites.RESTORED, flags);
            return super.verifyAndInsert(cn, extras);
        }
    }

    protected class FolderParser implements TagParser {
        private final ArrayMap<String, TagParser> mFolderElements;

        public FolderParser() {
            this(getFolderElementsMap());
        }

        public FolderParser(ArrayMap<String, TagParser> elements) {
            mFolderElements = elements;
        }

        @Override
        public int parseAndAdd(XmlPullParser parser)
                throws XmlPullParserException, IOException {
            final String title;
            final int titleResId = getAttributeResourceValue(parser, ATTR_TITLE, 0);
            if (titleResId != 0) {
                title = mSourceRes.getString(titleResId);
            } else {
                title = "";
            }

            mValues.put(Favorites.TITLE, title);
            mValues.put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_FOLDER);
            mValues.put(Favorites.SPANX, 1);
            mValues.put(Favorites.SPANY, 1);
            mValues.put(Favorites._ID, mCallback.generateNewItemId());
            int folderId = mCallback.insertAndCheck(mDb, mValues);
            if (folderId < 0) {
                if (LOGD) Log.e(TAG, "Unable to add folder");
                return -1;
            }

            final ContentValues myValues = new ContentValues(mValues);
            IntArray folderItems = new IntArray();

            int type;
            int folderDepth = parser.getDepth();
            int rank = 0;
            while ((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > folderDepth) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                mValues.clear();
                mValues.put(Favorites.CONTAINER, folderId);
                mValues.put(Favorites.RANK, rank);

                TagParser tagParser = mFolderElements.get(parser.getName());
                if (tagParser != null) {
                    final int id = tagParser.parseAndAdd(parser);
                    if (id >= 0) {
                        folderItems.add(id);
                        rank++;
                    }
                } else {
                    throw new RuntimeException("Invalid folder item " + parser.getName());
                }
            }

            int addedId = folderId;

            // We can only have folders with >= 2 items, so we need to remove the
            // folder and clean up if less than 2 items were included, or some
            // failed to add, and less than 2 were actually added
            if (folderItems.size() < 2) {
                // Delete the folder
                Uri uri = Favorites.getContentUri(folderId);
                SqlArguments args = new SqlArguments(uri, null, null);
                mDb.delete(args.table, args.where, args.args);
                addedId = -1;

                // If we have a single item, promote it to where the folder
                // would have been.
                if (folderItems.size() == 1) {
                    final ContentValues childValues = new ContentValues();
                    copyInteger(myValues, childValues, Favorites.CONTAINER);
                    copyInteger(myValues, childValues, Favorites.SCREEN);
                    copyInteger(myValues, childValues, Favorites.CELLX);
                    copyInteger(myValues, childValues, Favorites.CELLY);

                    addedId = folderItems.get(0);
                    mDb.update(Favorites.TABLE_NAME, childValues,
                            Favorites._ID + "=" + addedId, null);
                }
            }
            return addedId;
        }
    }

    protected static void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG
                && type != XmlPullParser.END_DOCUMENT);

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                    ", expected " + firstElementName);
        }
    }

    private static String convertToDistanceFromEnd(String value, int endValue) {
        if (!TextUtils.isEmpty(value)) {
            int x = Integer.parseInt(value);
            if (x < 0) {
                return Integer.toString(endValue + x);
            }
        }
        return value;
    }

    /**
     * Return attribute value, attempting launcher-specific namespace first
     * before falling back to anonymous attribute.
     */
    protected static String getAttributeValue(XmlPullParser parser, String attribute) {
        String value = parser.getAttributeValue(
                "http://schemas.android.com/apk/res-auto/com.android.launcher3", attribute);
        if (value == null) {
            value = parser.getAttributeValue(null, attribute);
        }
        return value;
    }

    /**
     * Return attribute resource value, attempting launcher-specific namespace
     * first before falling back to anonymous attribute.
     */
    protected static int getAttributeResourceValue(XmlPullParser parser, String attribute,
            int defaultValue) {
        AttributeSet attrs = Xml.asAttributeSet(parser);
        int value = attrs.getAttributeResourceValue(
                "http://schemas.android.com/apk/res-auto/com.android.launcher3", attribute,
                defaultValue);
        if (value == defaultValue) {
            value = attrs.getAttributeResourceValue(null, attribute, defaultValue);
        }
        return value;
    }

    public interface LayoutParserCallback {
        int generateNewItemId();

        int insertAndCheck(SQLiteDatabase db, ContentValues values);
    }

    @Thunk
    static void copyInteger(ContentValues from, ContentValues to, String key) {
        to.put(key, from.getAsInteger(key));
    }
}
