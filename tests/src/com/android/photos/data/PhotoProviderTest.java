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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.BaseColumns;
import android.test.InstrumentationTestCase;

import com.android.photos.data.PhotoProvider.Albums;
import com.android.photos.data.PhotoProvider.Metadata;
import com.android.photos.data.PhotoProvider.Photos;

public class PhotoProviderTest extends InstrumentationTestCase {
    @SuppressWarnings("unused")
    private static final String TAG = PhotoProviderTest.class.getSimpleName();

    private static final String MIME_TYPE = "test/test";
    private static final String ALBUM_NAME = "My Album";
    private static final long ALBUM_SERVER_ID = 100;
    private static final long PHOTO_SERVER_ID = 50;
    private static final String META_KEY = "mykey";
    private static final String META_VALUE = "myvalue";

    private static final Uri NO_TABLE_URI = PhotoProvider.BASE_CONTENT_URI;
    private static final Uri BAD_TABLE_URI = Uri.withAppendedPath(PhotoProvider.BASE_CONTENT_URI,
            "bad_table");

    private static final String WHERE_METADATA_PHOTOS_ID = Metadata.PHOTO_ID + " = ?";
    private static final String WHERE_METADATA = Metadata.PHOTO_ID + " = ? AND " + Metadata.KEY
            + " = ?";

    private static final long WAIT_FOR_CHANGE_MILLIS = 200;

    private long mAlbumId;
    private long mPhotoId;
    private long mMetadataId;

    private PhotoDatabase mDBHelper;
    private ContentResolver mResolver;

    private static class WatchContentObserverThread extends Thread {
        private WatchContentObserver mObserver;
        private Looper mLooper;

        @Override
        public void run() {
            Looper.prepare();
            mLooper = Looper.myLooper();
            WatchContentObserver observer = new WatchContentObserver();
            synchronized (this) {
                mObserver = observer;
                this.notifyAll();
            }
            Looper.loop();
        }

        public void waitForObserver() throws InterruptedException {
            synchronized (this) {
                while (mObserver == null) {
                    this.wait();
                }
            }
        }

        public WatchContentObserver getObserver() {
            return mObserver;
        }

        public void stopLooper() {
            mLooper.quit();
        }
    };

    private static class WatchContentObserver extends ContentObserver {
        private boolean mOnChangeReceived = false;
        private Uri mUri = null;

        public WatchContentObserver() {
            super(new Handler());
        }

        @Override
        public synchronized void onChange(boolean selfChange, Uri uri) {
            mOnChangeReceived = true;
            mUri = uri;
            notifyAll();
        }

        @Override
        public synchronized void onChange(boolean selfChange) {
            mOnChangeReceived = true;
            notifyAll();
        }

        public boolean waitForNotification() {
            synchronized (this) {
                if (!mOnChangeReceived) {
                    try {
                        wait(WAIT_FOR_CHANGE_MILLIS);
                    } catch (InterruptedException e) {
                    }
                }
            }
            return mOnChangeReceived;
        }
    };

    @Override
    protected void setUp() {
        Context context = getInstrumentation().getTargetContext();
        mDBHelper = new PhotoDatabase(context);
        mResolver = context.getContentResolver();
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            PhotoDatabaseUtils.insertAlbum(db, null, ALBUM_NAME, Albums.VISIBILITY_PRIVATE,
                    ALBUM_SERVER_ID);
            mAlbumId = PhotoDatabaseUtils.queryAlbumIdFromServerId(db, ALBUM_SERVER_ID);
            PhotoDatabaseUtils.insertPhoto(db, PHOTO_SERVER_ID, 100, 100,
                    System.currentTimeMillis(), mAlbumId, MIME_TYPE);
            mPhotoId = PhotoDatabaseUtils.queryPhotoIdFromServerId(db, PHOTO_SERVER_ID);
            PhotoDatabaseUtils.insertMetadata(db, mPhotoId, META_KEY, META_VALUE);
            String[] projection = {
                    BaseColumns._ID,
            };
            Cursor cursor = db.query(Metadata.TABLE, projection, null, null, null, null, null);
            cursor.moveToNext();
            mMetadataId = cursor.getLong(0);
            cursor.close();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    protected void tearDown() {
        SQLiteDatabase db = mDBHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            PhotoDatabaseUtils.deleteAllContent(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        mDBHelper.close();
        mDBHelper = null;
    }

    public void testDelete() {
        try {
            mResolver.delete(NO_TABLE_URI, null, null);
            fail("Exeption should be thrown when no table given");
        } catch (Exception e) {
            // expected exception
        }
        try {
            mResolver.delete(BAD_TABLE_URI, null, null);
            fail("Exeption should be thrown when deleting from a table that doesn't exist");
        } catch (Exception e) {
            // expected exception
        }

        String[] selectionArgs = {
            String.valueOf(mPhotoId)
        };
        // Delete some metadata
        assertEquals(1,
                mResolver.delete(Metadata.CONTENT_URI, WHERE_METADATA_PHOTOS_ID, selectionArgs));
        Uri photoUri = ContentUris.withAppendedId(Photos.CONTENT_URI, mPhotoId);
        assertEquals(1, mResolver.delete(photoUri, null, null));
        Uri albumUri = ContentUris.withAppendedId(Albums.CONTENT_URI, mAlbumId);
        assertEquals(1, mResolver.delete(albumUri, null, null));
        // now delete something that isn't there
        assertEquals(0, mResolver.delete(photoUri, null, null));
    }

    public void testDeleteMetadataId() {
        Uri metadataUri = ContentUris.withAppendedId(Metadata.CONTENT_URI, mMetadataId);
        assertEquals(1, mResolver.delete(metadataUri, null, null));
        Cursor cursor = mResolver.query(Metadata.CONTENT_URI, null, null, null, null);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }
    // Delete the album and ensure that the photos referring to the album are
    // deleted.
    public void testDeleteAlbumCascade() {
        WatchContentObserverThread observerThread = createObserverThread();
        WatchContentObserver observer = observerThread.getObserver();
        mResolver.registerContentObserver(Photos.CONTENT_URI, true, observer);
        try {
            Uri albumUri = ContentUris.withAppendedId(Albums.CONTENT_URI, mAlbumId);
            mResolver.delete(albumUri, null, null);
            assertTrue(observer.waitForNotification());
            assertEquals(observer.mUri, Photos.CONTENT_URI);
            Cursor cursor = mResolver.query(Photos.CONTENT_URI,
                    PhotoDatabaseUtils.PROJECTION_PHOTOS, null, null, null);
            assertEquals(0, cursor.getCount());
            cursor.close();
        } finally {
            mResolver.unregisterContentObserver(observer);
            observerThread.stopLooper();
        }
    }

    // Delete the album and ensure that the metadata referring to photos in that
    // album are deleted.
    public void testDeleteAlbumCascade2() {
        WatchContentObserverThread observerThread = createObserverThread();
        WatchContentObserver observer = observerThread.getObserver();
        mResolver.registerContentObserver(Metadata.CONTENT_URI, true, observer);
        try {
            Uri albumUri = ContentUris.withAppendedId(Albums.CONTENT_URI, mAlbumId);
            mResolver.delete(albumUri, null, null);
            assertTrue(observer.waitForNotification());
            assertEquals(observer.mUri, Metadata.CONTENT_URI);
            Cursor cursor = mResolver.query(Metadata.CONTENT_URI,
                    PhotoDatabaseUtils.PROJECTION_METADATA, null, null, null);
            assertEquals(0, cursor.getCount());
            cursor.close();
        } finally {
            mResolver.unregisterContentObserver(observer);
            observerThread.stopLooper();
        }
    }

    // Delete all albums and ensure that photos in any album are deleted.
    public void testDeleteAlbumCascade3() {
        WatchContentObserverThread observerThread = createObserverThread();
        WatchContentObserver observer = observerThread.getObserver();
        mResolver.registerContentObserver(Photos.CONTENT_URI, true, observer);
        try {
            mResolver.delete(Albums.CONTENT_URI, null, null);
            assertTrue(observer.waitForNotification());
            assertEquals(observer.mUri, Photos.CONTENT_URI);
            Cursor cursor = mResolver.query(Photos.CONTENT_URI,
                    PhotoDatabaseUtils.PROJECTION_PHOTOS, null, null, null);
            assertEquals(0, cursor.getCount());
            cursor.close();
        } finally {
            mResolver.unregisterContentObserver(observer);
            observerThread.stopLooper();
        }
    }

    // Delete a photo and ensure that the metadata for that photo are deleted.
    public void testDeletePhotoCascade() {
        WatchContentObserverThread observerThread = createObserverThread();
        WatchContentObserver observer = observerThread.getObserver();
        mResolver.registerContentObserver(Metadata.CONTENT_URI, true, observer);
        try {
            Uri albumUri = ContentUris.withAppendedId(Photos.CONTENT_URI, mPhotoId);
            mResolver.delete(albumUri, null, null);
            assertTrue(observer.waitForNotification());
            assertEquals(observer.mUri, Metadata.CONTENT_URI);
            Cursor cursor = mResolver.query(Metadata.CONTENT_URI,
                    PhotoDatabaseUtils.PROJECTION_METADATA, null, null, null);
            assertEquals(0, cursor.getCount());
            cursor.close();
        } finally {
            mResolver.unregisterContentObserver(observer);
            observerThread.stopLooper();
        }
    }

    public void testGetType() {
        // We don't return types for albums
        assertNull(mResolver.getType(Albums.CONTENT_URI));

        Uri noImage = ContentUris.withAppendedId(Photos.CONTENT_URI, mPhotoId + 1);
        assertNull(mResolver.getType(noImage));

        Uri image = ContentUris.withAppendedId(Photos.CONTENT_URI, mPhotoId);
        assertEquals(MIME_TYPE, mResolver.getType(image));
    }

    public void testInsert() {
        ContentValues values = new ContentValues();
        values.put(Albums.NAME, "don't add me");
        values.put(Albums.VISIBILITY, Albums.VISIBILITY_PRIVATE);
        assertNull(mResolver.insert(Albums.CONTENT_URI, values));
    }

    public void testUpdate() {
        ContentValues values = new ContentValues();
        // Normal update -- use an album.
        values.put(Albums.NAME, "foo");
        Uri albumUri = ContentUris.withAppendedId(Albums.CONTENT_URI, mAlbumId);
        assertEquals(1, mResolver.update(albumUri, values, null, null));
        String[] projection = {
            Albums.NAME,
        };
        Cursor cursor = mResolver.query(albumUri, projection, null, null, null);
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToNext());
        assertEquals("foo", cursor.getString(0));
        cursor.close();

        // Update a row that doesn't exist.
        Uri noAlbumUri = ContentUris.withAppendedId(Albums.CONTENT_URI, mAlbumId + 1);
        values.put(Albums.NAME, "bar");
        assertEquals(0, mResolver.update(noAlbumUri, values, null, null));

        // Update a metadata value that exists.
        ContentValues metadata = new ContentValues();
        metadata.put(Metadata.PHOTO_ID, mPhotoId);
        metadata.put(Metadata.KEY, META_KEY);
        metadata.put(Metadata.VALUE, "new value");
        assertEquals(1, mResolver.update(Metadata.CONTENT_URI, metadata, null, null));

        projection = new String[] {
            Metadata.VALUE,
        };

        String[] selectionArgs = {
                String.valueOf(mPhotoId), META_KEY,
        };

        cursor = mResolver.query(Metadata.CONTENT_URI, projection, WHERE_METADATA, selectionArgs,
                null);
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToNext());
        assertEquals("new value", cursor.getString(0));
        cursor.close();

        // Update a metadata value that doesn't exist.
        metadata.put(Metadata.KEY, "other stuff");
        assertEquals(1, mResolver.update(Metadata.CONTENT_URI, metadata, null, null));

        selectionArgs[1] = "other stuff";
        cursor = mResolver.query(Metadata.CONTENT_URI, projection, WHERE_METADATA, selectionArgs,
                null);
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToNext());
        assertEquals("new value", cursor.getString(0));
        cursor.close();

        // Remove a metadata value using update.
        metadata.putNull(Metadata.VALUE);
        assertEquals(1, mResolver.update(Metadata.CONTENT_URI, metadata, null, null));
        cursor = mResolver.query(Metadata.CONTENT_URI, projection, WHERE_METADATA, selectionArgs,
                null);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    public void testQuery() {
        // Query a photo that exists.
        Cursor cursor = mResolver.query(Photos.CONTENT_URI, PhotoDatabaseUtils.PROJECTION_PHOTOS,
                null, null, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToNext());
        assertEquals(mPhotoId, cursor.getLong(0));
        cursor.close();

        // Query a photo that doesn't exist.
        Uri noPhotoUri = ContentUris.withAppendedId(Photos.CONTENT_URI, mPhotoId + 1);
        cursor = mResolver.query(noPhotoUri, PhotoDatabaseUtils.PROJECTION_PHOTOS, null, null,
                null);
        assertNotNull(cursor);
        assertEquals(0, cursor.getCount());
        cursor.close();

        // Query a photo that exists using selection arguments.
        String[] selectionArgs = {
            String.valueOf(mPhotoId),
        };

        cursor = mResolver.query(Photos.CONTENT_URI, PhotoDatabaseUtils.PROJECTION_PHOTOS,
                Photos._ID + " = ?", selectionArgs, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToNext());
        assertEquals(mPhotoId, cursor.getLong(0));
        cursor.close();
    }

    public void testUpdatePhotoNotification() {
        WatchContentObserverThread observerThread = createObserverThread();
        WatchContentObserver observer = observerThread.getObserver();
        mResolver.registerContentObserver(Photos.CONTENT_URI, true, observer);
        try {
            Uri photoUri = ContentUris.withAppendedId(Photos.CONTENT_URI, mPhotoId);
            ContentValues values = new ContentValues();
            values.put(Photos.MIME_TYPE, "not-a/mime-type");
            mResolver.update(photoUri, values, null, null);
            assertTrue(observer.waitForNotification());
            assertEquals(observer.mUri, photoUri);
        } finally {
            mResolver.unregisterContentObserver(observer);
            observerThread.stopLooper();
        }
    }

    public void testUpdateMetadataNotification() {
        WatchContentObserverThread observerThread = createObserverThread();
        WatchContentObserver observer = observerThread.getObserver();
        mResolver.registerContentObserver(Metadata.CONTENT_URI, true, observer);
        try {
            ContentValues values = new ContentValues();
            values.put(Metadata.PHOTO_ID, mPhotoId);
            values.put(Metadata.KEY, META_KEY);
            values.put(Metadata.VALUE, "hello world");
            mResolver.update(Metadata.CONTENT_URI, values, null, null);
            assertTrue(observer.waitForNotification());
            assertEquals(observer.mUri, Metadata.CONTENT_URI);
        } finally {
            mResolver.unregisterContentObserver(observer);
            observerThread.stopLooper();
        }
    }

    public void testDeletePhotoNotification() {
        WatchContentObserverThread observerThread = createObserverThread();
        WatchContentObserver observer = observerThread.getObserver();
        mResolver.registerContentObserver(Photos.CONTENT_URI, true, observer);
        try {
            Uri photoUri = ContentUris.withAppendedId(Photos.CONTENT_URI, mPhotoId);
            mResolver.delete(photoUri, null, null);
            assertTrue(observer.waitForNotification());
            assertEquals(observer.mUri, photoUri);
        } finally {
            mResolver.unregisterContentObserver(observer);
            observerThread.stopLooper();
        }
    }

    private WatchContentObserverThread createObserverThread() {
        WatchContentObserverThread thread = new WatchContentObserverThread();
        thread.start();
        try {
            thread.waitForObserver();
            return thread;
        } catch (InterruptedException e) {
            thread.stopLooper();
            fail("Interruption while waiting for observer being created.");
            return null;
        }
    }
}
