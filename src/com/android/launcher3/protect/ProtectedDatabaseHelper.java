package com.android.launcher3.protect;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

public final class ProtectedDatabaseHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "protected_apps_db";

    private static final String TABLE_NAME = "protected_apps";
    private static final String KEY_UID = "uid";
    private static final String KEY_PKGNAME = "pkgname";

    private static final String CMD_CREATE_TABLE = "CREATE TABLE %1$s " +
            "(%2$s INTEGER PRIMARY KEY AUTOINCREMENT, %3$s TEXT);";
    private static final String CMD_LOOK_FOR_PKG = "SELECT * FROM %1$s WHERE %2$s = \'%3$s\'";
    private static final String CMD_DELETE_PKG = "DELETE FROM %1$s WHERE %2$s = \'%3$s\'";
    private SQLiteDatabase db;

    private boolean checkTimeout = false;

    private static ProtectedDatabaseHelper sInstance = null;

    private ProtectedDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static ProtectedDatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ProtectedDatabaseHelper(context);
            try {sInstance.setIdleConnectionTimeout(Long.MAX_VALUE);}
            catch (java.lang.NoSuchMethodError e){sInstance.checkTimeout=true;}
            sInstance.db = sInstance.getWritableDatabase();
        }
        else sInstance.checkTimeout();

        return sInstance;
    }

    public void checkTimeout() {
        if (checkTimeout && !db.isOpen())
            db = getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(String.format(CMD_CREATE_TABLE, TABLE_NAME, KEY_UID, KEY_PKGNAME));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void addApp(@NonNull String packageName) {
        sInstance.checkTimeout();
        if (isPackageProtected(packageName)) {
            return;
        }

        ContentValues values = new ContentValues();

        values.put(KEY_PKGNAME, packageName);
        db.insert(TABLE_NAME, null, values);
    }

    public void removeApp(@NonNull String packageName) {
        sInstance.checkTimeout();
        if (!isPackageProtected(packageName)) {
            return;
        }

        db.execSQL(String.format(CMD_DELETE_PKG, TABLE_NAME, KEY_PKGNAME, packageName));
    }

    public boolean isPackageProtected(@NonNull String packageName) {
        sInstance.checkTimeout();
        String query = String.format(CMD_LOOK_FOR_PKG, TABLE_NAME, KEY_PKGNAME, packageName);
        Cursor cursor = db.rawQuery(query, null);
        boolean result = cursor.getCount() != 0;

        cursor.close();

        return result;
    }
}