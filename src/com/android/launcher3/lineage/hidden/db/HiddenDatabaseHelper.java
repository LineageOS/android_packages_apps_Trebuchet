/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.launcher3.lineage.hidden.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class HiddenDatabaseHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABSE_NAME = "hidden_apps_db";

    private static final String TABLE_NAME = "hidden_apps";
    private static final String KEY_UID = "uid";
    private static final String KEY_PKGNAME = "pkgname";

    private static final String CMD_CREATE_TABLE = "CREATE TABLE %1$s " +
            "(%2$s INTEGER PRIMARY KEY AUTOINCREMENT, %3$s TEXT);";
    private static final String CMD_LOOK_FOR_PKG = "SELECT * FROM %1$s WHERE %2$s = \'%3$s\'";
    private static final String CMD_DELETE_PKG = "DELETE FROM %1$s WHERE %2$s = \'%3$s\'";

    @Nullable
    private static HiddenDatabaseHelper sInstance = null;

    private HiddenDatabaseHelper(@NonNull Context context) {
        super(context, DATABSE_NAME, null, DATABASE_VERSION);
    }

    public static HiddenDatabaseHelper getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new HiddenDatabaseHelper(context);
        }

        return sInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(String.format(CMD_CREATE_TABLE, TABLE_NAME, KEY_UID, KEY_PKGNAME));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void addApp(@NonNull String packageName) {
        if (isPackageHidden(packageName)) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_PKGNAME, packageName);

            db.insertOrThrow(TABLE_NAME, null, values);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            // Ignored
        } finally {
            db.endTransaction();
        }
    }

    public void removeApp(@NonNull String packageName) {
        if (!isPackageHidden(packageName)) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            db.delete(TABLE_NAME, KEY_PKGNAME + "=?", new String[]{packageName});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            // Ignored
        } finally {
            db.endTransaction();
        }
    }

    public boolean isPackageHidden(@NonNull String packageName) {
        String query = String.format(CMD_LOOK_FOR_PKG, TABLE_NAME, KEY_PKGNAME, packageName);
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(query, null);
        boolean result = false;
        try {
            result = cursor.getCount() != 0;
        } catch (Exception e) {
            // Ignored
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        return result;
    }
}
