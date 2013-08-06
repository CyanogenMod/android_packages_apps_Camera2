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
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

import com.android.photos.data.PhotoProvider.Accounts;
import com.android.photos.data.PhotoProvider.Albums;
import com.android.photos.data.PhotoProvider.Metadata;
import com.android.photos.data.PhotoProvider.Photos;

import java.io.File;
import java.io.IOException;

public class PhotoDatabaseTest extends InstrumentationTestCase {

    private PhotoDatabase mDBHelper;
    private static final String DB_NAME = "dummy.db";
    private static final long PARENT_ID1 = 100;
    private static final long PARENT_ID2 = 101;

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
        SQLiteDatabase db = getWritableDB();
        db.beginTransaction();
        try {
            long accountId = 100;
            // Test NOT NULL constraint on name
            assertFalse(PhotoDatabaseUtils.insertAlbum(db, null, null, Albums.VISIBILITY_PRIVATE,
                    accountId));

            // test NOT NULL constraint on privacy
            assertFalse(PhotoDatabaseUtils.insertAlbum(db, null, "hello", null, accountId));

            // test NOT NULL constraint on account_id
            assertFalse(PhotoDatabaseUtils.insertAlbum(db, null, "hello",
                    Albums.VISIBILITY_PRIVATE, null));

            // Normal insert
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, PARENT_ID1, "hello",
                    Albums.VISIBILITY_PRIVATE, accountId));

            long albumId = PhotoDatabaseUtils.queryAlbumIdFromParentId(db, PARENT_ID1);

            // Assign a valid child
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, PARENT_ID2, "hello",
                    Albums.VISIBILITY_PRIVATE, accountId));

            long otherAlbumId = PhotoDatabaseUtils.queryAlbumIdFromParentId(db, PARENT_ID2);
            assertNotSame(albumId, otherAlbumId);

            // This is a valid child of another album.
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, otherAlbumId, "hello",
                    Albums.VISIBILITY_PRIVATE, accountId));

            // This isn't allowed due to uniqueness constraint (parent_id/name)
            assertFalse(PhotoDatabaseUtils.insertAlbum(db, otherAlbumId, "hello",
                    Albums.VISIBILITY_PRIVATE, accountId));
        } finally {
            db.endTransaction();
        }
    }

    public void testPhotosConstraints() {
        SQLiteDatabase db = getWritableDB();
        db.beginTransaction();
        try {
            int width = 100;
            int height = 100;
            long dateTaken = System.currentTimeMillis();
            String mimeType = "test/test";
            long accountId = 100;

            // Test NOT NULL mime-type
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, width, height, dateTaken, null, null,
                    accountId));

            // Test NOT NULL width
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, null, height, dateTaken, null, mimeType,
                    accountId));

            // Test NOT NULL height
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, width, null, dateTaken, null, mimeType,
                    accountId));

            // Test NOT NULL dateTaken
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, width, height, null, null, mimeType,
                    accountId));

            // Test NOT NULL accountId
            assertFalse(PhotoDatabaseUtils.insertPhoto(db, width, height, dateTaken, null,
                    mimeType, null));

            // Test normal insert
            assertTrue(PhotoDatabaseUtils.insertPhoto(db, width, height, dateTaken, null, mimeType,
                    accountId));
        } finally {
            db.endTransaction();
        }
    }

    public void testMetadataConstraints() {
        SQLiteDatabase db = getWritableDB();
        db.beginTransaction();
        try {
            final String mimeType = "test/test";
            PhotoDatabaseUtils.insertPhoto(db, 100, 100, 100L, PARENT_ID1, mimeType, 100L);
            long photoId = PhotoDatabaseUtils.queryPhotoIdFromAlbumId(db, PARENT_ID1);

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

    public void testAccountsConstraints() {
        SQLiteDatabase db = getWritableDB();
        db.beginTransaction();
        try {
            assertFalse(PhotoDatabaseUtils.insertAccount(db, null));
            assertTrue(PhotoDatabaseUtils.insertAccount(db, "hello"));
            assertTrue(PhotoDatabaseUtils.insertAccount(db, "hello"));
        } finally {
            db.endTransaction();
        }
    }

    public void testUpgrade() {
        SQLiteDatabase db = getWritableDB();
        db.beginTransaction();
        try {
            assertTrue(PhotoDatabaseUtils.insertAccount(db, "Hello"));
            assertTrue(PhotoDatabaseUtils.insertAlbum(db, PARENT_ID1, "hello",
                    Albums.VISIBILITY_PRIVATE, 100L));
            final String mimeType = "test/test";
            assertTrue(PhotoDatabaseUtils.insertPhoto(db, 100, 100, 100L, PARENT_ID1, mimeType,
                    100L));
            // Normal insert.
            assertTrue(PhotoDatabaseUtils.insertMetadata(db, 100L, "foo", "bar"));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        mDBHelper.close();
        Context context = getInstrumentation().getTargetContext();
        mDBHelper = new PhotoDatabase(context, DB_NAME, PhotoDatabase.DB_VERSION + 1);
        db = getReadableDB();
        assertEquals(0, DatabaseUtils.queryNumEntries(db, Accounts.TABLE));
        assertEquals(0, DatabaseUtils.queryNumEntries(db, Photos.TABLE));
        assertEquals(0, DatabaseUtils.queryNumEntries(db, Albums.TABLE));
        assertEquals(0, DatabaseUtils.queryNumEntries(db, Metadata.TABLE));
    }

    private SQLiteDatabase getReadableDB() {
        return mDBHelper.getReadableDatabase();
    }

    private SQLiteDatabase getWritableDB() {
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
