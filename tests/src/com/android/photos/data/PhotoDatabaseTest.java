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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

import com.android.photos.data.PhotoProvider.Albums;
import com.android.photos.data.PhotoProvider.Metadata;
import com.android.photos.data.PhotoProvider.Photos;

import java.io.File;
import java.io.IOException;

public class PhotoDatabaseTest extends InstrumentationTestCase {

    private PhotoDatabase mDBHelper;
    private static final String DB_NAME = "dummy.db";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        context.deleteDatabase(DB_NAME);
        mDBHelper = new PhotoDatabase(context, DB_NAME);
    }

    @Override
    protected void tearDown() throws Exception {
        mDBHelper.close();
        mDBHelper = null;
        Context context = getInstrumentation().getTargetContext();
        context.deleteDatabase(DB_NAME);
        super.tearDown();
    }

    public void testCreateDatabase() throws IOException {
        Context context = getInstrumentation().getTargetContext();
        File dbFile = context.getDatabasePath(DB_NAME);
        SQLiteDatabase db = getReadableDB();
        db.beginTransaction();
        db.endTransaction();
        assertTrue(dbFile.exists());
    }

    public void testTables() {
        validateTable(Metadata.TABLE, PhotoDatabaseUtils.PROJECTION_METADATA);
        validateTable(Albums.TABLE, PhotoDatabaseUtils.PROJECTION_ALBUMS);
        validateTable(Photos.TABLE, PhotoDatabaseUtils.PROJECTION_PHOTOS);
    }

    public void testAlbumsConstraints() {
        SQLiteDatabase db = getWriteableDB();
        db.beginTransaction();
        try {
            // Test NOT NULL constraint on name
            assertFalse(PhotoDatabaseUtils
                    .insertAlbum(db, null, null, Albums.VISIBILITY_PRIVATE, null));

            // test NOT NULL constraint on privacy
            assertFalse(PhotoDatabaseUtils.insertAlbum(db, null, "hello", null, null));

            // Normal insert
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, null, "hello", Albums.VISIBILITY_PRIVATE,
                    100L));

            // Test server id uniqueness
            assertFalse(PhotoDatabaseUtils.insertAlbum(db, null, "world", Albums.VISIBILITY_PRIVATE,
                    100L));

            // Different server id allowed
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, null, "world", Albums.VISIBILITY_PRIVATE,
                    101L));

            // Allow null server id
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, null, "hello world",
                    Albums.VISIBILITY_PRIVATE, null));

            long albumId = PhotoDatabaseUtils.queryAlbumIdFromServerId(db, 100);

            // Assign a valid child
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, albumId, "hello", Albums.VISIBILITY_PRIVATE,
                    null));

            long otherAlbumId = PhotoDatabaseUtils.queryAlbumIdFromServerId(db, 101);
            assertNotSame(albumId, otherAlbumId);

            // This is a valid child of another album.
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, otherAlbumId, "hello",
                    Albums.VISIBILITY_PRIVATE, null));

            // This isn't allowed due to uniqueness constraint (parent_id/name)
            assertFalse(PhotoDatabaseUtils.insertAlbum(db, otherAlbumId, "hello",
                    Albums.VISIBILITY_PRIVATE, null));
        } finally {
            db.endTransaction();
        }
    }

    public void testPhotosConstraints() {
        SQLiteDatabase db = getWriteableDB();
        db.beginTransaction();
        try {
            int width = 100;
            int height = 100;
            long dateTaken = System.currentTimeMillis();
            String mimeType = "test/test";

            // Test NOT NULL mime-type
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, null, width, height, dateTaken, null,
                    null));

            // Test NOT NULL width
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, null, null, height, dateTaken, null,
                    mimeType));

            // Test NOT NULL height
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, null, width, null, dateTaken, null,
                    mimeType));

            // Test NOT NULL dateTaken
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, null, width, height, null, null,
                    mimeType));

            // Test normal insert
            assertTrue(PhotoDatabaseUtils.insertPhoto(db, null, width, height, dateTaken, null,
                    mimeType));
        } finally {
            db.endTransaction();
        }
    }

    public void testMetadataConstraints() {
        SQLiteDatabase db = getWriteableDB();
        db.beginTransaction();
        try {
            final String mimeType = "test/test";
            long photoServerId = 100;
            PhotoDatabaseUtils.insertPhoto(db, photoServerId, 100, 100, 100L, null, mimeType);
            long photoId = PhotoDatabaseUtils.queryPhotoIdFromServerId(db, photoServerId);

            // Test NOT NULL PHOTO_ID constraint.
            assertFalse(PhotoDatabaseUtils.insertMetadata(db, null, "foo", "bar"));

            // Normal insert.
            assertTrue(PhotoDatabaseUtils.insertMetadata(db, photoId, "foo", "bar"));

            // Test uniqueness constraint.
            assertFalse(PhotoDatabaseUtils.insertMetadata(db, photoId, "foo", "baz"));
        } finally {
            db.endTransaction();
        }
    }

    private SQLiteDatabase getReadableDB() {
        return mDBHelper.getReadableDatabase();
    }

    private SQLiteDatabase getWriteableDB() {
        return mDBHelper.getWritableDatabase();
    }

    private void validateTable(String table, String[] projection) {
        SQLiteDatabase db = getReadableDB();
        Cursor cursor = db.query(table, projection, null, null, null, null, null);
        assertNotNull(cursor);
        assertEquals(cursor.getCount(), 0);
        assertEquals(cursor.getColumnCount(), projection.length);
        for (int i = 0; i < projection.length; i++) {
            assertEquals(cursor.getColumnName(i), projection[i]);
        }
    }


}
