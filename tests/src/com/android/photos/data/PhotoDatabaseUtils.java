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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.photos.data.PhotoProvider.Albums;
import com.android.photos.data.PhotoProvider.Metadata;
import com.android.photos.data.PhotoProvider.Photos;

import junit.framework.AssertionFailedError;

public class PhotoDatabaseUtils {
    public static String[] PROJECTION_ALBUMS = {
        Albums._ID,
        Albums.PARENT_ID,
        Albums.VISIBILITY,
        Albums.NAME,
        Albums.SERVER_ID,
    };

    public static String[] PROJECTION_METADATA = {
        Metadata.PHOTO_ID,
        Metadata.KEY,
        Metadata.VALUE,
    };

    public static String[] PROJECTION_PHOTOS = {
        Photos._ID,
        Photos.SERVER_ID,
        Photos.WIDTH,
        Photos.HEIGHT,
        Photos.DATE_TAKEN,
        Photos.ALBUM_ID,
        Photos.MIME_TYPE,
    };

    private static String SELECTION_ALBUM_SERVER_ID = Albums.SERVER_ID + " = ?";
    private static String SELECTION_PHOTO_SERVER_ID = Photos.SERVER_ID + " = ?";

    public static long queryAlbumIdFromServerId(SQLiteDatabase db, long serverId) {
        return queryId(db, Albums.TABLE, PROJECTION_ALBUMS, SELECTION_ALBUM_SERVER_ID, serverId);
    }

    public static long queryPhotoIdFromServerId(SQLiteDatabase db, long serverId) {
        return queryId(db, Photos.TABLE, PROJECTION_PHOTOS, SELECTION_PHOTO_SERVER_ID, serverId);
    }

    public static long queryId(SQLiteDatabase db, String table, String[] projection,
            String selection, Object parameter) {
        String paramString = parameter == null ? null : parameter.toString();
        String[] selectionArgs = {
            paramString,
        };
        Cursor cursor = db.query(table, projection, selection, selectionArgs, null, null, null);
        try {
            if (cursor.getCount() != 1 || !cursor.moveToNext()) {
                throw new AssertionFailedError("Couldn't find item in table");
            }
            long id = cursor.getLong(0);
            return id;
        } finally {
            cursor.close();
        }
    }

    public static boolean insertPhoto(SQLiteDatabase db, Long serverId, Integer width,
            Integer height, Long dateTaken, Long albumId, String mimeType) {
        ContentValues values = new ContentValues();
        values.put(Photos.SERVER_ID, serverId);
        values.put(Photos.WIDTH, width);
        values.put(Photos.HEIGHT, height);
        values.put(Photos.DATE_TAKEN, dateTaken);
        values.put(Photos.ALBUM_ID, albumId);
        values.put(Photos.MIME_TYPE, mimeType);
        return db.insert(Photos.TABLE, null, values) != -1;
    }

    public static boolean insertAlbum(SQLiteDatabase db, Long parentId, String name,
            Integer privacy, Long serverId) {
        ContentValues values = new ContentValues();
        values.put(Albums.PARENT_ID, parentId);
        values.put(Albums.NAME, name);
        values.put(Albums.VISIBILITY, privacy);
        values.put(Albums.SERVER_ID, serverId);
        return db.insert(Albums.TABLE, null, values) != -1;
    }

    public static boolean insertMetadata(SQLiteDatabase db, Long photosId, String key, String value) {
        ContentValues values = new ContentValues();
        values.put(Metadata.PHOTO_ID, photosId);
        values.put(Metadata.KEY, key);
        values.put(Metadata.VALUE, value);
        return db.insert(Metadata.TABLE, null, values) != -1;
    }

    public static void deleteAllContent(SQLiteDatabase db) {
        db.delete(Metadata.TABLE, null, null);
        db.delete(Photos.TABLE, null, null);
        db.delete(Albums.TABLE, null, null);
    }
}
