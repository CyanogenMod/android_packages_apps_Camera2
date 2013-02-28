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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.photos.data.PhotoProvider.Albums;
import com.android.photos.data.PhotoProvider.Metadata;
import com.android.photos.data.PhotoProvider.Photos;

/**
 * Used in PhotoProvider to create and access the database containing
 * information about photo and video information stored on the server.
 */
public class PhotoDatabase extends SQLiteOpenHelper {
    @SuppressWarnings("unused")
    private static final String TAG = PhotoDatabase.class.getSimpleName();
    static final int DB_VERSION = 1;

    private static final String SQL_CREATE_TABLE = "CREATE TABLE ";

    private static final String[][] CREATE_PHOTO = {
        { Photos._ID, "INTEGER PRIMARY KEY AUTOINCREMENT" },
        { Photos.SERVER_ID, "INTEGER UNIQUE" },
        { Photos.WIDTH, "INTEGER NOT NULL" },
        { Photos.HEIGHT, "INTEGER NOT NULL" },
        { Photos.DATE_TAKEN, "INTEGER NOT NULL" },
        // Photos.ALBUM_ID is a foreign key to Albums._ID
        { Photos.ALBUM_ID, "INTEGER" },
        { Photos.MIME_TYPE, "TEXT NOT NULL" },
    };

    private static final String[][] CREATE_ALBUM = {
        { Albums._ID, "INTEGER PRIMARY KEY AUTOINCREMENT" },
        // Albums.PARENT_ID is a foriegn key to Albums._ID
        { Albums.PARENT_ID, "INTEGER" },
        { Albums.NAME, "Text NOT NULL" },
        { Albums.VISIBILITY, "INTEGER NOT NULL" },
        { Albums.SERVER_ID, "INTEGER UNIQUE" },
        createUniqueConstraint(Albums.PARENT_ID, Albums.NAME),
    };

    private static final String[][] CREATE_METADATA = {
        { Metadata._ID, "INTEGER PRIMARY KEY AUTOINCREMENT" },
        // Metadata.PHOTO_ID is a foreign key to Photos._ID
        { Metadata.PHOTO_ID, "INTEGER NOT NULL" },
        { Metadata.KEY, "TEXT NOT NULL" },
        { Metadata.VALUE, "TEXT NOT NULL" },
        createUniqueConstraint(Metadata.PHOTO_ID, Metadata.KEY),
    };

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTable(db, Albums.TABLE, CREATE_ALBUM);
        createTable(db, Photos.TABLE, CREATE_PHOTO);
        createTable(db, Metadata.TABLE, CREATE_METADATA);
    }

    public PhotoDatabase(Context context, String dbName) {
        super(context, dbName, null, DB_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    protected static void createTable(SQLiteDatabase db, String table, String[][] columns) {
        StringBuilder create = new StringBuilder(SQL_CREATE_TABLE);
        create.append(table).append('(');
        boolean first = true;
        for (String[] column : columns) {
            if (!first) {
                create.append(',');
            }
            first = false;
            for (String val: column) {
                create.append(val).append(' ');
            }
        }
        create.append(')');
        db.beginTransaction();
        try {
            db.execSQL(create.toString());
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    protected static String[] createUniqueConstraint(String column1, String column2) {
        return new String[] {
                "UNIQUE(", column1, ",", column2, ")"
        };
    }
}
