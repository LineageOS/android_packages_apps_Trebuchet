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

    @Nullable
    private static HiddenDatabaseHelper sSingleton;

    private HiddenDatabaseHelper(@NonNull Context context) {
        super(context, DATABSE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized HiddenDatabaseHelper getInstance(@NonNull Context context) {
        if (sSingleton == null) {
            sSingleton = new HiddenDatabaseHelper(context);
        }

        return sSingleton;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CMD_CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
                "(" +
                    KEY_UID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    KEY_PKGNAME + " TEXT" +
                ")";
        db.execSQL(CMD_CREATE_TABLE);
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
        String query = String.format("SELECT * FROM %s WHERE %s = ?", TABLE_NAME, KEY_PKGNAME);
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(query, new String[]{packageName});
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
