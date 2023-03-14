package com.android.launcher3;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.Partner;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.LauncherWidgetHolder;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

/**
 * Implements the layout parser with rules for internal layouts and partner layouts.
 */
public class DefaultLayoutParser extends AutoInstallsLayout {
    private static final String TAG = "DefaultLayoutParser";

    protected static final String TAG_RESOLVE = "resolve";
    private static final String TAG_FAVORITES = "favorites";
    protected static final String TAG_FAVORITE = "favorite";
    private static final String TAG_APPWIDGET = "appwidget";
    protected static final String TAG_SHORTCUT = "shortcut";
    private static final String TAG_FOLDER = "folder";
    private static final String TAG_PARTNER_FOLDER = "partner-folder";

    protected static final String ATTR_URI = "uri";
    private static final String ATTR_CONTAINER = "container";
    private static final String ATTR_SCREEN = "screen";
    private static final String ATTR_FOLDER_ITEMS = "folderItems";
    private static final String ATTR_SHORTCUT_ID = "shortcutId";
    private static final String ATTR_PACKAGE_NAME = "packageName";

    public static final String RES_PARTNER_FOLDER = "partner_folder";
    public static final String RES_PARTNER_DEFAULT_LAYOUT = "partner_default_layout";

    // TODO: Remove support for this broadcast, instead use widget options to send bind time options
    private static final String ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE =
            "com.android.launcher.action.APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE";

    public DefaultLayoutParser(Context context, LauncherWidgetHolder appWidgetHolder,
            LayoutParserCallback callback, Resources sourceRes, int layoutId) {
        super(context, appWidgetHolder, callback, sourceRes, layoutId, TAG_FAVORITES);
    }

    @Override
    protected ArrayMap<String, TagParser> getFolderElementsMap() {
        return getFolderElementsMap(mSourceRes);
    }

    @Thunk
    ArrayMap<String, TagParser> getFolderElementsMap(Resources res) {
        ArrayMap<String, TagParser> parsers = new ArrayMap<>();
        parsers.put(TAG_FAVORITE, new AppShortcutWithUriParser());
        parsers.put(TAG_SHORTCUT, new UriShortcutParser(res));
        return parsers;
    }

    @Override
    protected ArrayMap<String, TagParser> getLayoutElementsMap() {
        ArrayMap<String, TagParser> parsers = new ArrayMap<>();
        parsers.put(TAG_FAVORITE, new AppShortcutWithUriParser());
        parsers.put(TAG_APPWIDGET, new AppWidgetParser());
        parsers.put(TAG_SEARCH_WIDGET, new SearchWidgetParser());
        parsers.put(TAG_SHORTCUT, new UriShortcutParser(mSourceRes));
        parsers.put(TAG_RESOLVE, new ResolveParser());
        parsers.put(TAG_FOLDER, new MyFolderParser());
        parsers.put(TAG_PARTNER_FOLDER, new PartnerFolderParser());
        return parsers;
    }

    @Override
    protected void parseContainerAndScreen(XmlPullParser parser, int[] out) {
        out[0] = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        String strContainer = getAttributeValue(parser, ATTR_CONTAINER);
        if (strContainer != null) {
            out[0] = Integer.parseInt(strContainer);
        }
        out[1] = Integer.parseInt(getAttributeValue(parser, ATTR_SCREEN));
    }

    /**
     * AppShortcutParser which also supports adding URI based intents
     */
    public class AppShortcutWithUriParser extends AppShortcutParser {

        @Override
        protected int invalidPackageOrClass(XmlPullParser parser) {
            final String uri = getAttributeValue(parser, ATTR_URI);
            if (TextUtils.isEmpty(uri)) {
                Log.e(TAG, "Skipping invalid <favorite> with no component or uri");
                return -1;
            }

            final Intent metaIntent;
            try {
                metaIntent = Intent.parseUri(uri, 0);
            } catch (URISyntaxException e) {
                Log.e(TAG, "Unable to add meta-favorite: " + uri, e);
                return -1;
            }

            ResolveInfo resolved = mPackageManager.resolveActivity(metaIntent,
                    PackageManager.MATCH_DEFAULT_ONLY);
            final List<ResolveInfo> appList = mPackageManager.queryIntentActivities(
                    metaIntent, PackageManager.MATCH_DEFAULT_ONLY);

            // Verify that the result is an app and not just the resolver dialog asking which
            // app to use.
            if (wouldLaunchResolverActivity(resolved, appList)) {
                // If only one of the results is a system app then choose that as the default.
                final ResolveInfo systemApp = getSingleSystemActivity(appList);
                if (systemApp == null) {
                    // There is no logical choice for this meta-favorite, so rather than making
                    // a bad choice just add nothing.
                    Log.w(TAG, "No preference or single system activity found for "
                            + metaIntent.toString());
                    return -1;
                }
                resolved = systemApp;
            }
            final ActivityInfo info = resolved.activityInfo;
            final Intent intent = mPackageManager.getLaunchIntentForPackage(info.packageName);
            if (intent == null) {
                return -1;
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            return addShortcut(info.loadLabel(mPackageManager).toString(), intent,
                    Favorites.ITEM_TYPE_APPLICATION);
        }

        private ResolveInfo getSingleSystemActivity(List<ResolveInfo> appList) {
            ResolveInfo systemResolve = null;
            final int N = appList.size();
            for (int i = 0; i < N; ++i) {
                try {
                    ApplicationInfo info = mPackageManager.getApplicationInfo(
                            appList.get(i).activityInfo.packageName, 0);
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        if (systemResolve != null) {
                            return null;
                        } else {
                            systemResolve = appList.get(i);
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Unable to get info about resolve results", e);
                    return null;
                }
            }
            return systemResolve;
        }

        private boolean wouldLaunchResolverActivity(ResolveInfo resolved,
                List<ResolveInfo> appList) {
            // If the list contains the above resolved activity, then it can't be
            // ResolverActivity itself.
            for (int i = 0; i < appList.size(); ++i) {
                ResolveInfo tmp = appList.get(i);
                if (tmp.activityInfo.name.equals(resolved.activityInfo.name)
                        && tmp.activityInfo.packageName.equals(resolved.activityInfo.packageName)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Shortcut parser which allows any uri and not just web urls.
     */
    public class UriShortcutParser extends ShortcutParser {

        public UriShortcutParser(Resources iconRes) {
            super(iconRes);
        }

        @Override
        public int parseAndAdd(XmlPullParser parser) {
            final String packageName = getAttributeValue(parser, ATTR_PACKAGE_NAME);
            final String shortcutId = getAttributeValue(parser, ATTR_SHORTCUT_ID);
            if (!TextUtils.isEmpty(packageName) && !TextUtils.isEmpty(shortcutId)) {
                return parseAndAddDeepShortcut(shortcutId, packageName);
            }
            return super.parseAndAdd(parser);
        }

        /**
         * This method parses and adds a deep shortcut.
         * @return item id if the shortcut is successfully added else -1
         */
        private int parseAndAddDeepShortcut(String shortcutId, String packageName) {
            try {
                LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
                launcherApps.pinShortcuts(packageName, Collections.singletonList(shortcutId),
                        Process.myUserHandle());
                Intent intent = ShortcutKey.makeIntent(shortcutId, packageName);
                mValues.put(Favorites.RESTORED, WorkspaceItemInfo.FLAG_RESTORED_ICON);
                return addShortcut(null, intent, Favorites.ITEM_TYPE_DEEP_SHORTCUT);
            } catch (Exception e) {
                Log.e(TAG, "Unable to pin the shortcut for shortcut id = " + shortcutId
                        + " and package name = " + packageName);
            }
            return -1;
        }

        @Override
        protected Intent parseIntent(XmlPullParser parser) {
            String uri = null;
            try {
                uri = getAttributeValue(parser, ATTR_URI);
                return Intent.parseUri(uri, 0);
            } catch (URISyntaxException e) {
                Log.w(TAG, "Shortcut has malformed uri: " + uri);
                return null; // Oh well
            }
        }
    }

    /**
     * Contains a list of <favorite> nodes, and accepts the first successfully parsed node.
     */
    public class ResolveParser implements TagParser {

        private final AppShortcutWithUriParser mChildParser = new AppShortcutWithUriParser();

        @Override
        public int parseAndAdd(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            final int groupDepth = parser.getDepth();
            int type;
            int addedId = -1;
            while ((type = parser.next()) != XmlPullParser.END_TAG ||
                    parser.getDepth() > groupDepth) {
                if (type != XmlPullParser.START_TAG || addedId > -1) {
                    continue;
                }
                final String fallback_item_name = parser.getName();
                if (TAG_FAVORITE.equals(fallback_item_name)) {
                    addedId = mChildParser.parseAndAdd(parser);
                } else {
                    Log.e(TAG, "Fallback groups can contain only favorites, found "
                            + fallback_item_name);
                }
            }
            return addedId;
        }
    }

    /**
     * A parser which adds a folder whose contents come from partner apk.
     */
    @Thunk
    class PartnerFolderParser implements TagParser {

        @Override
        public int parseAndAdd(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            // Folder contents come from an external XML resource
            final Partner partner = Partner.get(mPackageManager);
            if (partner != null) {
                final int resId = partner.getXmlResId(RES_PARTNER_FOLDER);
                if (resId != 0) {
                    final Resources partnerRes = partner.getResources();
                    final XmlPullParser partnerParser = partnerRes.getXml(resId);
                    beginDocument(partnerParser, TAG_FOLDER);

                    FolderParser folderParser = new FolderParser(getFolderElementsMap(partnerRes));
                    return folderParser.parseAndAdd(partnerParser);
                }
            }
            return -1;
        }
    }

    /**
     * An extension of FolderParser which allows adding items from a different xml.
     */
    @Thunk
    class MyFolderParser extends FolderParser {

        @Override
        public int parseAndAdd(XmlPullParser parser) throws XmlPullParserException,
                IOException {
            final int resId = getAttributeResourceValue(parser, ATTR_FOLDER_ITEMS, 0);
            if (resId != 0) {
                parser = mSourceRes.getXml(resId);
                beginDocument(parser, TAG_FOLDER);
            }
            return super.parseAndAdd(parser);
        }
    }


    /**
     * AppWidget parser which enforces that the app is already installed when the layout is parsed.
     */
    protected class AppWidgetParser extends PendingWidgetParser {

        @Override
        protected int verifyAndInsert(ComponentName cn, Bundle extras) {
            try {
                mPackageManager.getReceiverInfo(cn, 0);
            } catch (Exception e) {
                String[] packages = mPackageManager.currentToCanonicalPackageNames(
                        new String[]{cn.getPackageName()});
                cn = new ComponentName(packages[0], cn.getClassName());
                try {
                    mPackageManager.getReceiverInfo(cn, 0);
                } catch (Exception e1) {
                    Log.d(TAG, "Can't find widget provider: " + cn.getClassName());
                    return -1;
                }
            }

            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(mContext);
            int insertedId = -1;
            try {
                int appWidgetId = mAppWidgetHolder.allocateAppWidgetId();

                if (!appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, cn)) {
                    Log.e(TAG, "Unable to bind app widget id " + cn);
                    mAppWidgetHolder.deleteAppWidgetId(appWidgetId);
                    return -1;
                }

                mValues.put(Favorites.APPWIDGET_ID, appWidgetId);
                mValues.put(Favorites.APPWIDGET_PROVIDER, cn.flattenToString());
                mValues.put(Favorites._ID, mCallback.generateNewItemId());
                insertedId = mCallback.insertAndCheck(mDb, mValues);
                if (insertedId < 0) {
                    mAppWidgetHolder.deleteAppWidgetId(appWidgetId);
                    return insertedId;
                }

                // Send a broadcast to configure the widget
                if (!extras.isEmpty()) {
                    Intent intent = new Intent(ACTION_APPWIDGET_DEFAULT_WORKSPACE_CONFIGURE);
                    intent.setComponent(cn);
                    intent.putExtras(extras);
                    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    mContext.sendBroadcast(intent);
                }
            } catch (RuntimeException ex) {
                Log.e(TAG, "Problem allocating appWidgetId", ex);
            }
            return insertedId;
        }
    }
}
