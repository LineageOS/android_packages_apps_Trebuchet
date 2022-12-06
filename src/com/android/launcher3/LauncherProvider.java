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

import static com.android.launcher3.provider.LauncherDbUtils.copyTable;
import static com.android.launcher3.provider.LauncherDbUtils.dropTable;
import static com.android.launcher3.provider.LauncherDbUtils.tableExists;

import android.annotation.TargetApi;
import android.app.backup.BackupManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.BaseColumns;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import com.android.launcher3.AutoInstallsLayout.LayoutParserCallback;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.DbDowngradeHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction;
import com.android.launcher3.provider.RestoreDbTask;
import com.android.launcher3.util.IOUtils;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.NoLocaleSQLiteHelper;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.LauncherAppWidgetHost;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class LauncherProvider extends ContentProvider {
    private static final String TAG = "LauncherProvider";
    private static final boolean LOGD = false;

    private static final String DOWNGRADE_SCHEMA_FILE = "downgrade_schema.json";
    private static final long RESTORE_BACKUP_TABLE_DELAY = TimeUnit.SECONDS.toMillis(30);

    /**
     * Represents the schema of the database. Changes in scheme need not be backwards compatible.
     * When increasing the scheme version, ensure that downgrade_schema.json is updated
     */
    public static final int SCHEMA_VERSION = 31;

    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".settings";
    public static final String KEY_LAYOUT_PROVIDER_AUTHORITY = "KEY_LAYOUT_PROVIDER_AUTHORITY";

    private static final int TEST_WORKSPACE_LAYOUT_RES_XML = R.xml.default_test_workspace;

    static final String EMPTY_DATABASE_CREATED = "EMPTY_DATABASE_CREATED";

    protected DatabaseHelper mOpenHelper;
    protected String mProviderAuthority;

    private long mLastRestoreTimestamp = 0L;

    private boolean mUseTestWorkspaceLayout;

    /**
     * $ adb shell dumpsys activity provider com.android.launcher3
     */
    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
        if (appState == null || !appState.getModel().isModelLoaded()) {
            return;
        }
        appState.getModel().dumpState("", fd, writer, args);
    }

    @Override
    public boolean onCreate() {
        if (FeatureFlags.IS_STUDIO_BUILD) {
            Log.d(TAG, "Launcher process started");
        }

        // The content provider exists for the entire duration of the launcher main process and
        // is the first component to get created.
        MainProcessInitializer.initialize(getContext().getApplicationContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        SqlArguments args = new SqlArguments(uri, null, null);
        if (TextUtils.isEmpty(args.where)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
    }

    /**
     * Overridden in tests
     */
    protected synchronized void createDbIfNotExists() {
        if (mOpenHelper == null) {
            mOpenHelper = DatabaseHelper.createDatabaseHelper(
                    getContext(), false /* forMigration */);

            RestoreDbTask.restoreIfNeeded(getContext(), mOpenHelper);
        }
    }

    private synchronized boolean prepForMigration(String dbFile, String targetTableName,
            Supplier<DatabaseHelper> src, Supplier<DatabaseHelper> dst) {
        if (TextUtils.equals(dbFile, mOpenHelper.getDatabaseName())) {
            Log.e(TAG, "prepForMigration - target db is same as current: " + dbFile);
            return false;
        }

        final DatabaseHelper helper = src.get();
        mOpenHelper = dst.get();
        copyTable(helper.getReadableDatabase(), Favorites.TABLE_NAME,
                mOpenHelper.getWritableDatabase(), targetTableName, getContext());
        helper.close();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        createDbIfNotExists();

        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(args.table);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Cursor result = qb.query(db, projection, args.where, args.args, null, null, sortOrder);
        final Bundle extra = new Bundle();
        extra.putString(LauncherSettings.Settings.EXTRA_DB_NAME, mOpenHelper.getDatabaseName());
        result.setExtras(extra);
        result.setNotificationUri(getContext().getContentResolver(), uri);

        return result;
    }

    @Thunk static int dbInsertAndCheck(DatabaseHelper helper,
            SQLiteDatabase db, String table, String nullColumnHack, ContentValues values) {
        if (values == null) {
            throw new RuntimeException("Error: attempting to insert null values");
        }
        if (!values.containsKey(LauncherSettings.Favorites._ID)) {
            throw new RuntimeException("Error: attempting to add item without specifying an id");
        }
        helper.checkId(values);
        return (int) db.insert(table, nullColumnHack, values);
    }

    private void reloadLauncherIfExternal() {
        if (Binder.getCallingPid() != Process.myPid()) {
            LauncherAppState app = LauncherAppState.getInstanceNoCreate();
            if (app != null) {
                app.getModel().forceReload();
            }
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri);

        // In very limited cases, we support system|signature permission apps to modify the db.
        if (Binder.getCallingPid() != Process.myPid()) {
            if (!initializeExternalAdd(initialValues)) {
                return null;
            }
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        addModifiedTime(initialValues);
        final int rowId = dbInsertAndCheck(mOpenHelper, db, args.table, null, initialValues);
        if (rowId < 0) return null;
        onAddOrDeleteOp(db);

        uri = ContentUris.withAppendedId(uri, rowId);
        reloadLauncherIfExternal();
        return uri;
    }

    private boolean initializeExternalAdd(ContentValues values) {
        // 1. Ensure that externally added items have a valid item id
        int id = mOpenHelper.generateNewItemId();
        values.put(LauncherSettings.Favorites._ID, id);

        // 2. In the case of an app widget, and if no app widget id is specified, we
        // attempt allocate and bind the widget.
        Integer itemType = values.getAsInteger(LauncherSettings.Favorites.ITEM_TYPE);
        if (itemType != null &&
                itemType.intValue() == LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET &&
                !values.containsKey(LauncherSettings.Favorites.APPWIDGET_ID)) {

            final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getContext());
            ComponentName cn = ComponentName.unflattenFromString(
                    values.getAsString(Favorites.APPWIDGET_PROVIDER));

            if (cn != null) {
                try {
                    AppWidgetHost widgetHost = mOpenHelper.newLauncherWidgetHost();
                    int appWidgetId = widgetHost.allocateAppWidgetId();
                    values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
                    if (!appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,cn)) {
                        widgetHost.deleteAppWidgetId(appWidgetId);
                        return false;
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "Failed to initialize external widget", e);
                    return false;
                }
            } else {
                return false;
            }
        }

        return true;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            int numValues = values.length;
            for (int i = 0; i < numValues; i++) {
                addModifiedTime(values[i]);
                if (dbInsertAndCheck(mOpenHelper, db, args.table, null, values[i]) < 0) {
                    return 0;
                }
            }
            onAddOrDeleteOp(db);
            t.commit();
        }

        reloadLauncherIfExternal();
        return values.length;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        createDbIfNotExists();
        try (SQLiteTransaction t = new SQLiteTransaction(mOpenHelper.getWritableDatabase())) {
            boolean isAddOrDelete = false;

            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                ContentProviderOperation op = operations.get(i);
                results[i] = op.apply(this, results, i);

                isAddOrDelete |= (op.isInsert() || op.isDelete()) &&
                        results[i].count != null && results[i].count > 0;
            }
            if (isAddOrDelete) {
                onAddOrDeleteOp(t.getDb());
            }

            t.commit();
            reloadLauncherIfExternal();
            return results;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (Binder.getCallingPid() != Process.myPid()
                && Favorites.TABLE_NAME.equalsIgnoreCase(args.table)) {
            mOpenHelper.removeGhostWidgets(mOpenHelper.getWritableDatabase());
        }
        int count = db.delete(args.table, args.where, args.args);
        if (count > 0) {
            onAddOrDeleteOp(db);
            reloadLauncherIfExternal();
        }
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        createDbIfNotExists();
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);

        addModifiedTime(values);
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.update(args.table, values, args.where, args.args);
        reloadLauncherIfExternal();
        return count;
    }

    @Override
    public Bundle call(String method, final String arg, final Bundle extras) {
        if (Binder.getCallingUid() != Process.myUid()) {
            return null;
        }
        createDbIfNotExists();

        switch (method) {
            case LauncherSettings.Settings.METHOD_CLEAR_EMPTY_DB_FLAG: {
                clearFlagEmptyDbCreated();
                return null;
            }
            case LauncherSettings.Settings.METHOD_WAS_EMPTY_DB_CREATED : {
                Bundle result = new Bundle();
                result.putBoolean(LauncherSettings.Settings.EXTRA_VALUE,
                        Utilities.getPrefs(getContext()).getBoolean(
                                mOpenHelper.getKey(EMPTY_DATABASE_CREATED), false));
                return result;
            }
            case LauncherSettings.Settings.METHOD_DELETE_EMPTY_FOLDERS: {
                Bundle result = new Bundle();
                result.putIntArray(LauncherSettings.Settings.EXTRA_VALUE, deleteEmptyFolders()
                        .toArray());
                return result;
            }
            case LauncherSettings.Settings.METHOD_NEW_ITEM_ID: {
                Bundle result = new Bundle();
                result.putInt(LauncherSettings.Settings.EXTRA_VALUE,
                        mOpenHelper.generateNewItemId());
                return result;
            }
            case LauncherSettings.Settings.METHOD_NEW_SCREEN_ID: {
                Bundle result = new Bundle();
                result.putInt(LauncherSettings.Settings.EXTRA_VALUE,
                        mOpenHelper.getNewScreenId());
                return result;
            }
            case LauncherSettings.Settings.METHOD_CREATE_EMPTY_DB: {
                mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
                return null;
            }
            case LauncherSettings.Settings.METHOD_SET_USE_TEST_WORKSPACE_LAYOUT_FLAG: {
                mUseTestWorkspaceLayout = true;
                return null;
            }
            case LauncherSettings.Settings.METHOD_CLEAR_USE_TEST_WORKSPACE_LAYOUT_FLAG: {
                mUseTestWorkspaceLayout = false;
                return null;
            }
            case LauncherSettings.Settings.METHOD_LOAD_DEFAULT_FAVORITES: {
                loadDefaultFavoritesIfNecessary();
                return null;
            }
            case LauncherSettings.Settings.METHOD_REMOVE_GHOST_WIDGETS: {
                mOpenHelper.removeGhostWidgets(mOpenHelper.getWritableDatabase());
                return null;
            }
            case LauncherSettings.Settings.METHOD_NEW_TRANSACTION: {
                Bundle result = new Bundle();
                result.putBinder(LauncherSettings.Settings.EXTRA_VALUE,
                        new SQLiteTransaction(mOpenHelper.getWritableDatabase()));
                return result;
            }
            case LauncherSettings.Settings.METHOD_REFRESH_BACKUP_TABLE: {
                mOpenHelper.mBackupTableExists = tableExists(mOpenHelper.getReadableDatabase(),
                        Favorites.BACKUP_TABLE_NAME);
                return null;
            }
            case LauncherSettings.Settings.METHOD_REFRESH_HOTSEAT_RESTORE_TABLE: {
                mOpenHelper.mHotseatRestoreTableExists = tableExists(
                        mOpenHelper.getReadableDatabase(), Favorites.HYBRID_HOTSEAT_BACKUP_TABLE);
                return null;
            }
            case LauncherSettings.Settings.METHOD_RESTORE_BACKUP_TABLE: {
                final long ts = System.currentTimeMillis();
                if (ts - mLastRestoreTimestamp > RESTORE_BACKUP_TABLE_DELAY) {
                    mLastRestoreTimestamp = ts;
                    RestoreDbTask.restoreIfPossible(
                            getContext(), mOpenHelper, new BackupManager(getContext()));
                }
                return null;
            }
            case LauncherSettings.Settings.METHOD_UPDATE_CURRENT_OPEN_HELPER: {
                Bundle result = new Bundle();
                result.putBoolean(LauncherSettings.Settings.EXTRA_VALUE,
                        prepForMigration(
                                arg /* dbFile */,
                                Favorites.TMP_TABLE,
                                () -> mOpenHelper,
                                () -> DatabaseHelper.createDatabaseHelper(
                                        getContext(), true /* forMigration */)));
                return result;
            }
            case LauncherSettings.Settings.METHOD_PREP_FOR_PREVIEW: {
                Bundle result = new Bundle();
                result.putBoolean(LauncherSettings.Settings.EXTRA_VALUE,
                        prepForMigration(
                                arg /* dbFile */,
                                Favorites.PREVIEW_TABLE_NAME,
                                () -> DatabaseHelper.createDatabaseHelper(
                                        getContext(), arg, true /* forMigration */),
                                () -> mOpenHelper));
                return result;
            }
            case LauncherSettings.Settings.METHOD_SWITCH_DATABASE: {
                if (TextUtils.equals(arg, mOpenHelper.getDatabaseName())) return null;
                final DatabaseHelper helper = mOpenHelper;
                if (extras == null || !extras.containsKey(KEY_LAYOUT_PROVIDER_AUTHORITY)) {
                    mProviderAuthority = null;
                } else {
                    mProviderAuthority = extras.getString(KEY_LAYOUT_PROVIDER_AUTHORITY);
                }
                mOpenHelper = DatabaseHelper.createDatabaseHelper(
                        getContext(), arg, false /* forMigration */);
                helper.close();
                LauncherAppState app = LauncherAppState.getInstanceNoCreate();
                if (app == null) return null;
                app.getModel().forceReload();
                return null;
            }
        }
        return null;
    }

    private void onAddOrDeleteOp(SQLiteDatabase db) {
        mOpenHelper.onAddOrDeleteOp(db);
    }

    /**
     * Deletes any empty folder from the DB.
     * @return Ids of deleted folders.
     */
    private IntArray deleteEmptyFolders() {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        try (SQLiteTransaction t = new SQLiteTransaction(db)) {
            // Select folders whose id do not match any container value.
            String selection = LauncherSettings.Favorites.ITEM_TYPE + " = "
                    + LauncherSettings.Favorites.ITEM_TYPE_FOLDER + " AND "
                    + LauncherSettings.Favorites._ID +  " NOT IN (SELECT " +
                            LauncherSettings.Favorites.CONTAINER + " FROM "
                                + Favorites.TABLE_NAME + ")";

            IntArray folderIds = LauncherDbUtils.queryIntArray(false, db, Favorites.TABLE_NAME,
                    Favorites._ID, selection, null, null);
            if (!folderIds.isEmpty()) {
                db.delete(Favorites.TABLE_NAME, Utilities.createDbSelectionQuery(
                        LauncherSettings.Favorites._ID, folderIds), null);
            }
            t.commit();
            return folderIds;
        } catch (SQLException ex) {
            Log.e(TAG, ex.getMessage(), ex);
            return new IntArray();
        }
    }

    @Thunk static void addModifiedTime(ContentValues values) {
        values.put(LauncherSettings.Favorites.MODIFIED, System.currentTimeMillis());
    }

    private void clearFlagEmptyDbCreated() {
        Utilities.getPrefs(getContext()).edit()
                .remove(mOpenHelper.getKey(EMPTY_DATABASE_CREATED)).commit();
    }

    /**
     * Loads the default workspace based on the following priority scheme:
     *   1) From the app restrictions
     *   2) From a package provided by play store
     *   3) From a partner configuration APK, already in the system image
     *   4) The default configuration for the particular device
     */
    synchronized private void loadDefaultFavoritesIfNecessary() {
        SharedPreferences sp = Utilities.getPrefs(getContext());

        if (sp.getBoolean(mOpenHelper.getKey(EMPTY_DATABASE_CREATED), false)) {
            Log.d(TAG, "loading default workspace");

            AppWidgetHost widgetHost = mOpenHelper.newLauncherWidgetHost();
            AutoInstallsLayout loader = createWorkspaceLoaderFromAppRestriction(widgetHost);
            if (loader == null) {
                loader = AutoInstallsLayout.get(getContext(),widgetHost, mOpenHelper);
            }
            if (loader == null) {
                final Partner partner = Partner.get(getContext().getPackageManager());
                if (partner != null && partner.hasDefaultLayout()) {
                    final Resources partnerRes = partner.getResources();
                    int workspaceResId = partnerRes.getIdentifier(Partner.RES_DEFAULT_LAYOUT,
                            "xml", partner.getPackageName());
                    if (workspaceResId != 0) {
                        loader = new DefaultLayoutParser(getContext(), widgetHost,
                                mOpenHelper, partnerRes, workspaceResId);
                    }
                }
            }

            final boolean usingExternallyProvidedLayout = loader != null;
            if (loader == null) {
                loader = getDefaultLayoutParser(widgetHost);
            }

            // There might be some partially restored DB items, due to buggy restore logic in
            // previous versions of launcher.
            mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
            // Populate favorites table with initial favorites
            if ((mOpenHelper.loadFavorites(mOpenHelper.getWritableDatabase(), loader) <= 0)
                    && usingExternallyProvidedLayout) {
                // Unable to load external layout. Cleanup and load the internal layout.
                mOpenHelper.createEmptyDB(mOpenHelper.getWritableDatabase());
                mOpenHelper.loadFavorites(mOpenHelper.getWritableDatabase(),
                        getDefaultLayoutParser(widgetHost));
            }
            clearFlagEmptyDbCreated();
        }
    }

    /**
     * Creates workspace loader from an XML resource listed in the app restrictions.
     *
     * @return the loader if the restrictions are set and the resource exists; null otherwise.
     */
    private AutoInstallsLayout createWorkspaceLoaderFromAppRestriction(AppWidgetHost widgetHost) {
        Context ctx = getContext();
        final String authority;
        if (!TextUtils.isEmpty(mProviderAuthority)) {
            authority = mProviderAuthority;
        } else {
            authority = Settings.Secure.getString(ctx.getContentResolver(),
                    "launcher3.layout.provider");
        }
        if (TextUtils.isEmpty(authority)) {
            return null;
        }

        ProviderInfo pi = ctx.getPackageManager().resolveContentProvider(authority, 0);
        if (pi == null) {
            Log.e(TAG, "No provider found for authority " + authority);
            return null;
        }
        Uri uri = getLayoutUri(authority, ctx);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            // Read the full xml so that we fail early in case of any IO error.
            String layout = new String(IOUtils.toByteArray(in));
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(layout));

            Log.d(TAG, "Loading layout from " + authority);
            return new AutoInstallsLayout(ctx, widgetHost, mOpenHelper,
                    ctx.getPackageManager().getResourcesForApplication(pi.applicationInfo),
                    () -> parser, AutoInstallsLayout.TAG_WORKSPACE);
        } catch (Exception e) {
            Log.e(TAG, "Error getting layout stream from: " + authority , e);
            return null;
        }
    }

    public static Uri getLayoutUri(String authority, Context ctx) {
        InvariantDeviceProfile grid = LauncherAppState.getIDP(ctx);
        return new Uri.Builder().scheme("content").authority(authority).path("launcher_layout")
                .appendQueryParameter("version", "1")
                .appendQueryParameter("gridWidth", Integer.toString(grid.numColumns))
                .appendQueryParameter("gridHeight", Integer.toString(grid.numRows))
                .appendQueryParameter("hotseatSize", Integer.toString(grid.numDatabaseHotseatIcons))
                .build();
    }

    private DefaultLayoutParser getDefaultLayoutParser(AppWidgetHost widgetHost) {
        InvariantDeviceProfile idp = LauncherAppState.getIDP(getContext());
        int defaultLayout = mUseTestWorkspaceLayout
                ? TEST_WORKSPACE_LAYOUT_RES_XML : idp.defaultLayoutId;

        if (getContext().getSystemService(UserManager.class).isDemoUser()
                && idp.demoModeLayoutId != 0) {
            defaultLayout = idp.demoModeLayoutId;
        }

        return new DefaultLayoutParser(getContext(), widgetHost,
                mOpenHelper, getContext().getResources(), defaultLayout);
    }

    /**
     * The class is subclassed in tests to create an in-memory db.
     */
    public static class DatabaseHelper extends NoLocaleSQLiteHelper implements
            LayoutParserCallback {
        private final Context mContext;
        private final boolean mForMigration;
        private int mMaxItemId = -1;
        private boolean mBackupTableExists;
        private boolean mHotseatRestoreTableExists;

        static DatabaseHelper createDatabaseHelper(Context context, boolean forMigration) {
            return createDatabaseHelper(context, null, forMigration);
        }

        static DatabaseHelper createDatabaseHelper(Context context, String dbName,
                boolean forMigration) {
            if (dbName == null) {
                dbName = InvariantDeviceProfile.INSTANCE.get(context).dbFile;
            }
            DatabaseHelper databaseHelper = new DatabaseHelper(context, dbName, forMigration);
            // Table creation sometimes fails silently, which leads to a crash loop.
            // This way, we will try to create a table every time after crash, so the device
            // would eventually be able to recover.
            if (!tableExists(databaseHelper.getReadableDatabase(), Favorites.TABLE_NAME)) {
                Log.e(TAG, "Tables are missing after onCreate has been called. Trying to recreate");
                // This operation is a no-op if the table already exists.
                databaseHelper.addFavoritesTable(databaseHelper.getWritableDatabase(), true);
            }
            databaseHelper.mHotseatRestoreTableExists = tableExists(
                    databaseHelper.getReadableDatabase(), Favorites.HYBRID_HOTSEAT_BACKUP_TABLE);

            databaseHelper.initIds();
            return databaseHelper;
        }

        /**
         * Constructor used in tests and for restore.
         */
        public DatabaseHelper(Context context, String dbName, boolean forMigration) {
            super(context, dbName, SCHEMA_VERSION);
            mContext = context;
            mForMigration = forMigration;
        }

        protected void initIds() {
            // In the case where neither onCreate nor onUpgrade gets called, we read the maxId from
            // the DB here
            if (mMaxItemId == -1) {
                mMaxItemId = initializeMaxItemId(getWritableDatabase());
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (LOGD) Log.d(TAG, "creating new launcher database");

            mMaxItemId = 1;

            addFavoritesTable(db, false);

            // Fresh and clean launcher DB.
            mMaxItemId = initializeMaxItemId(db);
            if (!mForMigration) {
                onEmptyDbCreated();
            }
        }

        protected void onAddOrDeleteOp(SQLiteDatabase db) {
            if (mBackupTableExists) {
                dropTable(db, Favorites.BACKUP_TABLE_NAME);
                mBackupTableExists = false;
            }
            if (mHotseatRestoreTableExists) {
                dropTable(db, Favorites.HYBRID_HOTSEAT_BACKUP_TABLE);
                mHotseatRestoreTableExists = false;
            }
        }

        /**
         * Re-composite given key in respect to database. If the current db is
         * {@link LauncherFiles#LAUNCHER_DB}, return the key as-is. Otherwise append the db name to
         * given key. e.g. consider key="EMPTY_DATABASE_CREATED", dbName="minimal.db", the returning
         * string will be "EMPTY_DATABASE_CREATED@minimal.db".
         */
        String getKey(final String key) {
            if (TextUtils.equals(getDatabaseName(), LauncherFiles.LAUNCHER_DB)) {
                return key;
            }
            return key + "@" + getDatabaseName();
        }

        /**
         * Overriden in tests.
         */
        protected void onEmptyDbCreated() {
            // Set the flag for empty DB
            Utilities.getPrefs(mContext).edit().putBoolean(getKey(EMPTY_DATABASE_CREATED), true)
                    .commit();
        }

        public long getSerialNumberForUser(UserHandle user) {
            return UserCache.INSTANCE.get(mContext).getSerialNumberForUser(user);
        }

        public long getDefaultUserSerial() {
            return getSerialNumberForUser(Process.myUserHandle());
        }

        private void addFavoritesTable(SQLiteDatabase db, boolean optional) {
            Favorites.addTableToDb(db, getDefaultUserSerial(), optional);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);

            File schemaFile = mContext.getFileStreamPath(DOWNGRADE_SCHEMA_FILE);
            if (!schemaFile.exists()) {
                handleOneTimeDataUpgrade(db);
            }
            DbDowngradeHelper.updateSchemaFile(schemaFile, SCHEMA_VERSION, mContext);
        }

        /**
         * One-time data updated before support of onDowngrade was added. This update is backwards
         * compatible and can safely be run multiple times.
         * Note: No new logic should be added here after release, as the new logic might not get
         * executed on an existing device.
         * TODO: Move this to db upgrade path, once the downgrade path is released.
         */
        protected void handleOneTimeDataUpgrade(SQLiteDatabase db) {
            // Remove "profile extra"
            UserCache um = UserCache.INSTANCE.get(mContext);
            for (UserHandle user : um.getUserProfiles()) {
                long serial = um.getSerialNumberForUser(user);
                String sql = "update favorites set intent = replace(intent, "
                        + "';l.profile=" + serial + ";', ';') where itemType = 0;";
                db.execSQL(sql);
            }
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (LOGD) Log.d(TAG, "onUpgrade triggered: " + oldVersion);
            switch (oldVersion) {
                // The version cannot be lower that 12, as Launcher3 never supported a lower
                // version of the DB.
                case 12:
                    // No-op
                case 13: {
                    try (SQLiteTransaction t = new SQLiteTransaction(db)) {
                        // Insert new column for holding widget provider name
                        db.execSQL("ALTER TABLE favorites " +
                                "ADD COLUMN appWidgetProvider TEXT;");
                        t.commit();
                    } catch (SQLException ex) {
                        Log.e(TAG, ex.getMessage(), ex);
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 14: {
                    if (!addIntegerColumn(db, Favorites.MODIFIED, 0)) {
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 15: {
                    if (!addIntegerColumn(db, Favorites.RESTORED, 0)) {
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 16:
                    // No-op
                case 17:
                    // No-op
                case 18:
                    // No-op
                case 19: {
                    // Add userId column
                    if (!addIntegerColumn(db, Favorites.PROFILE_ID, getDefaultUserSerial())) {
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 20:
                    if (!updateFolderItemsRank(db, true)) {
                        break;
                    }
                case 21:
                    // No-op
                case 22: {
                    if (!addIntegerColumn(db, Favorites.OPTIONS, 0)) {
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 23:
                    // No-op
                case 24:
                    // No-op
                case 25:
                    convertShortcutsToLauncherActivities(db);
                case 26:
                    // QSB was moved to the grid. Ignore overlapping items
                case 27: {
                    // Update the favorites table so that the screen ids are ordered based on
                    // workspace page rank.
                    IntArray finalScreens = LauncherDbUtils.queryIntArray(false, db,
                            "workspaceScreens", BaseColumns._ID, null, null, "screenRank");
                    int[] original = finalScreens.toArray();
                    Arrays.sort(original);
                    String updatemap = "";
                    for (int i = 0; i < original.length; i++) {
                        if (finalScreens.get(i) != original[i]) {
                            updatemap += String.format(Locale.ENGLISH, " WHEN %1$s=%2$d THEN %3$d",
                                    Favorites.SCREEN, finalScreens.get(i), original[i]);
                        }
                    }
                    if (!TextUtils.isEmpty(updatemap)) {
                        String query = String.format(Locale.ENGLISH,
                                "UPDATE %1$s SET %2$s=CASE %3$s ELSE %2$s END WHERE %4$s = %5$d",
                                Favorites.TABLE_NAME, Favorites.SCREEN, updatemap,
                                Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP);
                        db.execSQL(query);
                    }
                    dropTable(db, "workspaceScreens");
                }
                case 28: {
                    boolean columnAdded = addIntegerColumn(
                            db, Favorites.APPWIDGET_SOURCE, Favorites.CONTAINER_UNKNOWN);
                    if (!columnAdded) {
                        // Old version remains, which means we wipe old data
                        break;
                    }
                }
                case 29: {
                    // Remove widget panel related leftover workspace items
                    db.delete(Favorites.TABLE_NAME, Utilities.createDbSelectionQuery(
                            Favorites.SCREEN, IntArray.wrap(-777, -778)), null);
                }
                case 30: {
                    if (FeatureFlags.QSB_ON_FIRST_SCREEN) {
                        // Clean up first row in screen 0 as it might contain junk data.
                        Log.d(TAG, "Cleaning up first row");
                        db.delete(Favorites.TABLE_NAME,
                                String.format(Locale.ENGLISH,
                                        "%1$s = %2$d AND %3$s = %4$d AND %5$s = %6$d",
                                        Favorites.SCREEN, 0,
                                        Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP,
                                        Favorites.CELLY, 0), null);
                    }
                    return;
                }
                case 31: {
                    // DB Upgraded successfully
                    return;
                }
            }

            // DB was not upgraded
            Log.w(TAG, "Destroying all old data.");
            createEmptyDB(db);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            try {
                DbDowngradeHelper.parse(mContext.getFileStreamPath(DOWNGRADE_SCHEMA_FILE))
                        .onDowngrade(db, oldVersion, newVersion);
            } catch (Exception e) {
                Log.d(TAG, "Unable to downgrade from: " + oldVersion + " to " + newVersion +
                        ". Wiping databse.", e);
                createEmptyDB(db);
            }
        }

        /**
         * Clears all the data for a fresh start.
         */
        public void createEmptyDB(SQLiteDatabase db) {
            try (SQLiteTransaction t = new SQLiteTransaction(db)) {
                dropTable(db, Favorites.TABLE_NAME);
                dropTable(db, "workspaceScreens");
                onCreate(db);
                t.commit();
            }
        }

        /**
         * Removes widgets which are registered to the Launcher's host, but are not present
         * in our model.
         */
        public void removeGhostWidgets(SQLiteDatabase db) {
            // Get all existing widget ids.
            final AppWidgetHost host = newLauncherWidgetHost();
            final int[] allWidgets;
            try {
                // Although the method was defined in O, it has existed since the beginning of time,
                // so it might work on older platforms as well.
                allWidgets = host.getAppWidgetIds();
            } catch (IncompatibleClassChangeError e) {
                Log.e(TAG, "getAppWidgetIds not supported", e);
                return;
            }
            final IntSet validWidgets = IntSet.wrap(LauncherDbUtils.queryIntArray(false, db,
                    Favorites.TABLE_NAME, Favorites.APPWIDGET_ID,
                    "itemType=" + Favorites.ITEM_TYPE_APPWIDGET, null, null));
            for (int widgetId : allWidgets) {
                if (!validWidgets.contains(widgetId)) {
                    try {
                        FileLog.d(TAG, "Deleting invalid widget " + widgetId);
                        host.deleteAppWidgetId(widgetId);
                    } catch (RuntimeException e) {
                        // Ignore
                    }
                }
            }
        }

        /**
         * Replaces all shortcuts of type {@link Favorites#ITEM_TYPE_SHORTCUT} which have a valid
         * launcher activity target with {@link Favorites#ITEM_TYPE_APPLICATION}.
         */
        @Thunk void convertShortcutsToLauncherActivities(SQLiteDatabase db) {
            try (SQLiteTransaction t = new SQLiteTransaction(db);
                 // Only consider the primary user as other users can't have a shortcut.
                 Cursor c = db.query(Favorites.TABLE_NAME,
                         new String[] { Favorites._ID, Favorites.INTENT},
                         "itemType=" + Favorites.ITEM_TYPE_SHORTCUT +
                                 " AND profileId=" + getDefaultUserSerial(),
                         null, null, null, null);
                 SQLiteStatement updateStmt = db.compileStatement("UPDATE favorites SET itemType="
                         + Favorites.ITEM_TYPE_APPLICATION + " WHERE _id=?")
            ) {
                final int idIndex = c.getColumnIndexOrThrow(Favorites._ID);
                final int intentIndex = c.getColumnIndexOrThrow(Favorites.INTENT);

                while (c.moveToNext()) {
                    String intentDescription = c.getString(intentIndex);
                    Intent intent;
                    try {
                        intent = Intent.parseUri(intentDescription, 0);
                    } catch (URISyntaxException e) {
                        Log.e(TAG, "Unable to parse intent", e);
                        continue;
                    }

                    if (!PackageManagerHelper.isLauncherAppTarget(intent)) {
                        continue;
                    }

                    int id = c.getInt(idIndex);
                    updateStmt.bindLong(1, id);
                    updateStmt.executeUpdateDelete();
                }
                t.commit();
            } catch (SQLException ex) {
                Log.w(TAG, "Error deduping shortcuts", ex);
            }
        }

        @Thunk boolean updateFolderItemsRank(SQLiteDatabase db, boolean addRankColumn) {
            try (SQLiteTransaction t = new SQLiteTransaction(db)) {
                if (addRankColumn) {
                    // Insert new column for holding rank
                    db.execSQL("ALTER TABLE favorites ADD COLUMN rank INTEGER NOT NULL DEFAULT 0;");
                }

                // Get a map for folder ID to folder width
                Cursor c = db.rawQuery("SELECT container, MAX(cellX) FROM favorites"
                        + " WHERE container IN (SELECT _id FROM favorites WHERE itemType = ?)"
                        + " GROUP BY container;",
                        new String[] {Integer.toString(LauncherSettings.Favorites.ITEM_TYPE_FOLDER)});

                while (c.moveToNext()) {
                    db.execSQL("UPDATE favorites SET rank=cellX+(cellY*?) WHERE "
                            + "container=? AND cellX IS NOT NULL AND cellY IS NOT NULL;",
                            new Object[] {c.getLong(1) + 1, c.getLong(0)});
                }

                c.close();
                t.commit();
            } catch (SQLException ex) {
                // Old version remains, which means we wipe old data
                Log.e(TAG, ex.getMessage(), ex);
                return false;
            }
            return true;
        }

        private boolean addIntegerColumn(SQLiteDatabase db, String columnName, long defaultValue) {
            try (SQLiteTransaction t = new SQLiteTransaction(db)) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN "
                        + columnName + " INTEGER NOT NULL DEFAULT " + defaultValue + ";");
                t.commit();
            } catch (SQLException ex) {
                Log.e(TAG, ex.getMessage(), ex);
                return false;
            }
            return true;
        }

        // Generates a new ID to use for an object in your database. This method should be only
        // called from the main UI thread. As an exception, we do call it when we call the
        // constructor from the worker thread; however, this doesn't extend until after the
        // constructor is called, and we only pass a reference to LauncherProvider to LauncherApp
        // after that point
        @Override
        public int generateNewItemId() {
            if (mMaxItemId < 0) {
                throw new RuntimeException("Error: max item id was not initialized");
            }
            mMaxItemId += 1;
            return mMaxItemId;
        }

        public AppWidgetHost newLauncherWidgetHost() {
            return new LauncherAppWidgetHost(mContext);
        }

        @Override
        public int insertAndCheck(SQLiteDatabase db, ContentValues values) {
            return dbInsertAndCheck(this, db, Favorites.TABLE_NAME, null, values);
        }

        public void checkId(ContentValues values) {
            int id = values.getAsInteger(Favorites._ID);
            mMaxItemId = Math.max(id, mMaxItemId);
        }

        private int initializeMaxItemId(SQLiteDatabase db) {
            return getMaxId(db, "SELECT MAX(%1$s) FROM %2$s", Favorites._ID, Favorites.TABLE_NAME);
        }

        // Returns a new ID to use for an workspace screen in your database that is greater than all
        // existing screen IDs.
        private int getNewScreenId() {
            return getMaxId(getWritableDatabase(),
                    "SELECT MAX(%1$s) FROM %2$s WHERE %3$s = %4$d AND %1$s >= 0",
                    Favorites.SCREEN, Favorites.TABLE_NAME, Favorites.CONTAINER,
                    Favorites.CONTAINER_DESKTOP) + 1;
        }

        @Thunk int loadFavorites(SQLiteDatabase db, AutoInstallsLayout loader) {
            // TODO: Use multiple loaders with fall-back and transaction.
            int count = loader.loadLayout(db, new IntArray());

            // Ensure that the max ids are initialized
            mMaxItemId = initializeMaxItemId(db);
            return count;
        }
    }

    /**
     * @return the max _id in the provided table.
     */
    @Thunk static int getMaxId(SQLiteDatabase db, String query, Object... args) {
        int max = 0;
        try (SQLiteStatement prog = db.compileStatement(
                String.format(Locale.ENGLISH, query, args))) {
            max = (int) DatabaseUtils.longForQuery(prog, null);
            if (max < 0) {
                throw new RuntimeException("Error: could not query max id");
            }
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            if (message.contains("re-open") && message.contains("already-closed")) {
                // Don't crash trying to end a transaction an an already closed DB. See b/173162852.
            } else {
                throw exception;
            }
        }
        return max;
    }

    static class SqlArguments {
        public final String table;
        public final String where;
        public final String[] args;

        SqlArguments(Uri url, String where, String[] args) {
            if (url.getPathSegments().size() == 1) {
                this.table = url.getPathSegments().get(0);
                this.where = where;
                this.args = args;
            } else if (url.getPathSegments().size() != 2) {
                throw new IllegalArgumentException("Invalid URI: " + url);
            } else if (!TextUtils.isEmpty(where)) {
                throw new UnsupportedOperationException("WHERE clause not supported: " + url);
            } else {
                this.table = url.getPathSegments().get(0);
                this.where = "_id=" + ContentUris.parseId(url);
                this.args = null;
            }
        }

        SqlArguments(Uri url) {
            if (url.getPathSegments().size() == 1) {
                table = url.getPathSegments().get(0);
                where = null;
                args = null;
            } else {
                throw new IllegalArgumentException("Invalid URI: " + url);
            }
        }
    }
}
