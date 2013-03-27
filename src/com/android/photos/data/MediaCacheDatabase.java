/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.photos.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;

import com.android.photos.data.MediaRetriever.MediaSize;

import java.io.File;

class MediaCacheDatabase extends SQLiteOpenHelper {
    public static final int DB_VERSION = 1;
    public static final String DB_NAME = "mediacache.db";

    /** Internal database table used for the media cache */
    public static final String TABLE = "media_cache";

    private static interface Columns extends BaseColumns {
        /** The Content URI of the original image. */
        public static final String URI = "uri";
        /** MediaSize.getValue() values. */
        public static final String MEDIA_SIZE = "media_size";
        /** The last time this image was queried. */
        public static final String LAST_ACCESS = "last_access";
        /** The image size in bytes. */
        public static final String SIZE_IN_BYTES = "size";
    }

    static interface Action {
        void execute(Uri uri, long id, MediaSize size, Object parameter);
    }

    private static final String[] PROJECTION_ID = {
        Columns._ID,
    };

    private static final String[] PROJECTION_CACHED = {
        Columns._ID, Columns.MEDIA_SIZE, Columns.SIZE_IN_BYTES,
    };

    private static final String[] PROJECTION_CACHE_SIZE = {
        "SUM(" + Columns.SIZE_IN_BYTES + ")"
    };

    private static final String[] PROJECTION_DELETE_OLD = {
        Columns._ID, Columns.URI, Columns.MEDIA_SIZE, Columns.SIZE_IN_BYTES, Columns.LAST_ACCESS,
    };

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE + "("
            + Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + Columns.URI + " TEXT NOT NULL,"
            + Columns.MEDIA_SIZE + " INTEGER NOT NULL,"
            + Columns.LAST_ACCESS + " INTEGER NOT NULL,"
            + Columns.SIZE_IN_BYTES + " INTEGER NOT NULL,"
            + "UNIQUE(" + Columns.URI + ", " + Columns.MEDIA_SIZE + "))";

    public static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE;

    public static final String WHERE_THUMBNAIL = Columns.MEDIA_SIZE + " = "
            + MediaSize.Thumbnail.getValue();

    public static final String WHERE_NOT_THUMBNAIL = Columns.MEDIA_SIZE + " <> "
            + MediaSize.Thumbnail.getValue();

    public static final String WHERE_CLEAR_CACHE = Columns.LAST_ACCESS + " <= ?";

    public static final String WHERE_CLEAR_CACHE_LARGE = WHERE_CLEAR_CACHE + " AND "
            + WHERE_NOT_THUMBNAIL;

    static class QueryCacheResults {
        public QueryCacheResults(long id, int sizeVal) {
            this.id = id;
            this.size = MediaSize.fromInteger(sizeVal);
        }
        public long id;
        public MediaSize size;
    }

    public MediaCacheDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(DROP_TABLE);
        onCreate(db);
        MediaCache.getInstance().clearCacheDir();
    }

    public Long getCached(Uri uri, MediaSize size) {
        String where = Columns.URI + " = ? AND " + Columns.MEDIA_SIZE + " = ?";
        SQLiteDatabase db = getWritableDatabase();
        String[] whereArgs = {
                uri.toString(), String.valueOf(size.getValue()),
        };
        Cursor cursor = db.query(TABLE, PROJECTION_ID, where, whereArgs, null, null, null);
        Long id = null;
        if (cursor.moveToNext()) {
            id = cursor.getLong(0);
        }
        cursor.close();
        if (id != null) {
            String[] updateArgs = {
                id.toString()
            };
            ContentValues values = new ContentValues();
            values.put(Columns.LAST_ACCESS, System.currentTimeMillis());
            db.beginTransaction();
            try {
                db.update(TABLE, values, Columns._ID + " = ?", updateArgs);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
        return id;
    }

    public MediaSize executeOnBestCached(Uri uri, MediaSize size, Action action) {
        String where = Columns.URI + " = ? AND " + Columns.MEDIA_SIZE + " < ?";
        String orderBy = Columns.MEDIA_SIZE + " DESC";
        SQLiteDatabase db = getReadableDatabase();
        String[] whereArgs = {
                uri.toString(), String.valueOf(size.getValue()),
        };
        Cursor cursor = db.query(TABLE, PROJECTION_CACHED, where, whereArgs, null, null, orderBy);
        MediaSize bestSize = null;
        if (cursor.moveToNext()) {
            long id = cursor.getLong(0);
            bestSize = MediaSize.fromInteger(cursor.getInt(1));
            long fileSize = cursor.getLong(2);
            action.execute(uri, id, bestSize, fileSize);
        }
        cursor.close();
        return bestSize;
    }

    public long insert(Uri uri, MediaSize size, Action action, File tempFile) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(Columns.LAST_ACCESS, System.currentTimeMillis());
            values.put(Columns.MEDIA_SIZE, size.getValue());
            values.put(Columns.URI, uri.toString());
            values.put(Columns.SIZE_IN_BYTES, tempFile.length());
            long id = db.insert(TABLE, null, values);
            if (id != -1) {
                action.execute(uri, id, size, tempFile);
                db.setTransactionSuccessful();
            }
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public void updateLength(long id, long fileSize) {
        ContentValues values = new ContentValues();
        values.put(Columns.SIZE_IN_BYTES, fileSize);
        String[] whereArgs = {
            String.valueOf(id)
        };
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.update(TABLE, values, Columns._ID + " = ?", whereArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void delete(Uri uri, MediaSize size, Action action) {
        String where = Columns.URI + " = ? AND " + Columns.MEDIA_SIZE + " = ?";
        String[] whereArgs = {
                uri.toString(), String.valueOf(size.getValue()),
        };
        deleteRows(uri, where, whereArgs, action);
    }

    public void delete(Uri uri, Action action) {
        String where = Columns.URI + " = ?";
        String[] whereArgs = {
            uri.toString()
        };
        deleteRows(uri, where, whereArgs, action);
    }

    private void deleteRows(Uri uri, String where, String[] whereArgs, Action action) {
        SQLiteDatabase db = getWritableDatabase();
        // Make this an atomic operation
        db.beginTransaction();
        Cursor cursor = db.query(TABLE, PROJECTION_CACHED, where, whereArgs, null, null, null);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(0);
            MediaSize size = MediaSize.fromInteger(cursor.getInt(1));
            long length = cursor.getLong(2);
            action.execute(uri, id, size, length);
        }
        cursor.close();
        try {
            db.delete(TABLE, where, whereArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void deleteOldCached(boolean includeThumbnails, long deleteSize, Action action) {
        String where = includeThumbnails ? null : WHERE_NOT_THUMBNAIL;
        long lastAccess = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor cursor = db.query(TABLE, PROJECTION_DELETE_OLD, where, null, null, null,
                    Columns.LAST_ACCESS);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String uri = cursor.getString(1);
                MediaSize size = MediaSize.fromInteger(cursor.getInt(2));
                long length = cursor.getLong(3);
                long imageLastAccess = cursor.getLong(4);

                if (imageLastAccess != lastAccess && deleteSize < 0) {
                    break; // We've deleted enough.
                }
                lastAccess = imageLastAccess;
                action.execute(Uri.parse(uri), id, size, length);
                deleteSize -= length;
            }
            cursor.close();
            String[] whereArgs = {
                String.valueOf(lastAccess),
            };
            String whereDelete = includeThumbnails ? WHERE_CLEAR_CACHE : WHERE_CLEAR_CACHE_LARGE;
            db.delete(TABLE, whereDelete, whereArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public long getCacheSize() {
        return getCacheSize(null);
    }

    public long getThumbnailCacheSize() {
        return getCacheSize(WHERE_THUMBNAIL);
    }

    private long getCacheSize(String where) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE, PROJECTION_CACHE_SIZE, where, null, null, null, null);
        long size = -1;
        if (cursor.moveToNext()) {
            size = cursor.getLong(0);
        }
        cursor.close();
        return size;
    }
}
