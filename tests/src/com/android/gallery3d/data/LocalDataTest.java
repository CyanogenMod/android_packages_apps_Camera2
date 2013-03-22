/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.data;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LocalDataTest extends AndroidTestCase {
    @SuppressWarnings("unused")
    private static final String TAG = "LocalDataTest";
    private static final long DEFAULT_TIMEOUT = 1000; // one second

    @MediumTest
    public void testLocalAlbum() throws Exception {
        new TestZeroImage().run();
        new TestOneImage().run();
        new TestMoreImages().run();
        new TestZeroVideo().run();
        new TestOneVideo().run();
        new TestMoreVideos().run();
        new TestDeleteOneImage().run();
        new TestDeleteOneAlbum().run();
    }

    abstract class TestLocalAlbumBase {
        private boolean mIsImage;
        protected GalleryAppStub mApp;
        protected LocalAlbumSet mAlbumSet;

        TestLocalAlbumBase(boolean isImage) {
            mIsImage = isImage;
        }

        public void run() throws Exception {
            SQLiteDatabase db = SQLiteDatabase.create(null);
            prepareData(db);
            mApp = newGalleryContext(db, Looper.getMainLooper());
            Path.clearAll();
            Path path = Path.fromString(
                    mIsImage ? "/local/image" : "/local/video");
            mAlbumSet = new LocalAlbumSet(path, mApp);
            mAlbumSet.reload();
            verifyResult();
        }

        abstract void prepareData(SQLiteDatabase db);
        abstract void verifyResult() throws Exception;
    }

    abstract class TestLocalImageAlbum extends TestLocalAlbumBase {
        TestLocalImageAlbum() {
            super(true);
        }
    }

    abstract class TestLocalVideoAlbum extends TestLocalAlbumBase {
        TestLocalVideoAlbum() {
            super(false);
        }
    }

    class TestZeroImage extends TestLocalImageAlbum {
        @Override
        public void prepareData(SQLiteDatabase db) {
            createImageTable(db);
        }

        @Override
        public void verifyResult() {
            assertEquals(0, mAlbumSet.getMediaItemCount());
            assertEquals(0, mAlbumSet.getSubMediaSetCount());
            assertEquals(0, mAlbumSet.getTotalMediaItemCount());
         }
    }

    class TestOneImage extends TestLocalImageAlbum {
        @Override
        public void prepareData(SQLiteDatabase db) {
            createImageTable(db);
            insertImageData(db);
        }

        @Override
        public void verifyResult() {
            assertEquals(0, mAlbumSet.getMediaItemCount());
            assertEquals(1, mAlbumSet.getSubMediaSetCount());
            assertEquals(1, mAlbumSet.getTotalMediaItemCount());
            MediaSet sub = mAlbumSet.getSubMediaSet(0);
            assertEquals(1, sub.getMediaItemCount());
            assertEquals(0, sub.getSubMediaSetCount());
            LocalMediaItem item = (LocalMediaItem) sub.getMediaItem(0, 1).get(0);
            assertEquals(1, item.id);
            assertEquals("IMG_0072", item.caption);
            assertEquals("image/jpeg", item.mimeType);
            assertEquals(12.0, item.latitude);
            assertEquals(34.0, item.longitude);
            assertEquals(0xD000, item.dateTakenInMs);
            assertEquals(1280395646L, item.dateAddedInSec);
            assertEquals(1275934796L, item.dateModifiedInSec);
            assertEquals("/mnt/sdcard/DCIM/100CANON/IMG_0072.JPG", item.filePath);
        }
    }

    class TestMoreImages extends TestLocalImageAlbum {
        @Override
        public void prepareData(SQLiteDatabase db) {
            // Albums are sorted by names, and items are sorted by
            // dateTimeTaken (descending)
            createImageTable(db);
            // bucket 0xB000
            insertImageData(db, 1000, 0xB000, "second");  // id 1
            insertImageData(db, 2000, 0xB000, "second");  // id 2
            // bucket 0xB001
            insertImageData(db, 3000, 0xB001, "first");   // id 3
        }

        @Override
        public void verifyResult() {
            assertEquals(0, mAlbumSet.getMediaItemCount());
            assertEquals(2, mAlbumSet.getSubMediaSetCount());
            assertEquals(3, mAlbumSet.getTotalMediaItemCount());

            MediaSet first = mAlbumSet.getSubMediaSet(0);
            assertEquals(1, first.getMediaItemCount());
            LocalMediaItem item = (LocalMediaItem) first.getMediaItem(0, 1).get(0);
            assertEquals(3, item.id);
            assertEquals(3000L, item.dateTakenInMs);

            MediaSet second = mAlbumSet.getSubMediaSet(1);
            assertEquals(2, second.getMediaItemCount());
            item = (LocalMediaItem) second.getMediaItem(0, 1).get(0);
            assertEquals(2, item.id);
            assertEquals(2000L, item.dateTakenInMs);
            item = (LocalMediaItem) second.getMediaItem(1, 1).get(0);
            assertEquals(1, item.id);
            assertEquals(1000L, item.dateTakenInMs);
        }
    }

    class OnContentDirtyLatch implements ContentListener {
        private CountDownLatch mLatch = new CountDownLatch(1);

        public void onContentDirty() {
            mLatch.countDown();
        }

        public boolean isOnContentDirtyBeCalled(long timeout)
                throws InterruptedException {
            return mLatch.await(timeout, TimeUnit.MILLISECONDS);
        }
    }

    class TestDeleteOneAlbum extends TestLocalImageAlbum {
        @Override
        public void prepareData(SQLiteDatabase db) {
            // Albums are sorted by names, and items are sorted by
            // dateTimeTaken (descending)
            createImageTable(db);
            // bucket 0xB000
            insertImageData(db, 1000, 0xB000, "second");  // id 1
            insertImageData(db, 2000, 0xB000, "second");  // id 2
            // bucket 0xB001
            insertImageData(db, 3000, 0xB001, "first");   // id 3
        }

        @Override
        public void verifyResult() throws Exception {
            MediaSet sub = mAlbumSet.getSubMediaSet(1);  // "second"
            assertEquals(2, mAlbumSet.getSubMediaSetCount());
            OnContentDirtyLatch latch = new OnContentDirtyLatch();
            sub.addContentListener(latch);
            assertTrue((sub.getSupportedOperations() & MediaSet.SUPPORT_DELETE) != 0);
            sub.delete();
            mAlbumSet.fakeChange();
            latch.isOnContentDirtyBeCalled(DEFAULT_TIMEOUT);
            mAlbumSet.reload();
            assertEquals(1, mAlbumSet.getSubMediaSetCount());
        }
    }

    class TestDeleteOneImage extends TestLocalImageAlbum {

        @Override
        public void prepareData(SQLiteDatabase db) {
            createImageTable(db);
            insertImageData(db);
        }

        @Override
        public void verifyResult() {
            MediaSet sub = mAlbumSet.getSubMediaSet(0);
            LocalMediaItem item = (LocalMediaItem) sub.getMediaItem(0, 1).get(0);
            assertEquals(1, sub.getMediaItemCount());
            assertTrue((sub.getSupportedOperations() & MediaSet.SUPPORT_DELETE) != 0);
            sub.delete();
            sub.reload();
            assertEquals(0, sub.getMediaItemCount());
        }
    }

    static void createImageTable(SQLiteDatabase db) {
        // This is copied from MediaProvider
        db.execSQL("CREATE TABLE IF NOT EXISTS images (" +
                "_id INTEGER PRIMARY KEY," +
                "_data TEXT," +
                "_size INTEGER," +
                "_display_name TEXT," +
                "mime_type TEXT," +
                "title TEXT," +
                "date_added INTEGER," +
                "date_modified INTEGER," +
                "description TEXT," +
                "picasa_id TEXT," +
                "isprivate INTEGER," +
                "latitude DOUBLE," +
                "longitude DOUBLE," +
                "datetaken INTEGER," +
                "orientation INTEGER," +
                "mini_thumb_magic INTEGER," +
                "bucket_id TEXT," +
                "bucket_display_name TEXT" +
               ");");
    }

    static void insertImageData(SQLiteDatabase db) {
        insertImageData(db, 0xD000, 0xB000, "name");
    }

    static void insertImageData(SQLiteDatabase db, long dateTaken,
            int bucketId, String bucketName) {
        db.execSQL("INSERT INTO images (title, mime_type, latitude, longitude, "
                + "datetaken, date_added, date_modified, bucket_id, "
                + "bucket_display_name, _data, orientation) "
                + "VALUES ('IMG_0072', 'image/jpeg', 12, 34, "
                + dateTaken + ", 1280395646, 1275934796, '" + bucketId + "', "
                + "'" + bucketName + "', "
                + "'/mnt/sdcard/DCIM/100CANON/IMG_0072.JPG', 0)");
    }

    class TestZeroVideo extends TestLocalVideoAlbum {
        @Override
        public void prepareData(SQLiteDatabase db) {
            createVideoTable(db);
        }

        @Override
        public void verifyResult() {
            assertEquals(0, mAlbumSet.getMediaItemCount());
            assertEquals(0, mAlbumSet.getSubMediaSetCount());
            assertEquals(0, mAlbumSet.getTotalMediaItemCount());
        }
    }

    class TestOneVideo extends TestLocalVideoAlbum {
        @Override
        public void prepareData(SQLiteDatabase db) {
            createVideoTable(db);
            insertVideoData(db);
        }

        @Override
        public void verifyResult() {
            assertEquals(0, mAlbumSet.getMediaItemCount());
            assertEquals(1, mAlbumSet.getSubMediaSetCount());
            assertEquals(1, mAlbumSet.getTotalMediaItemCount());
            MediaSet sub = mAlbumSet.getSubMediaSet(0);
            assertEquals(1, sub.getMediaItemCount());
            assertEquals(0, sub.getSubMediaSetCount());
            LocalMediaItem item = (LocalMediaItem) sub.getMediaItem(0, 1).get(0);
            assertEquals(1, item.id);
            assertEquals("VID_20100811_051413", item.caption);
            assertEquals("video/mp4", item.mimeType);
            assertEquals(11.0, item.latitude);
            assertEquals(22.0, item.longitude);
            assertEquals(0xD000, item.dateTakenInMs);
            assertEquals(1281503663L, item.dateAddedInSec);
            assertEquals(1281503662L, item.dateModifiedInSec);
            assertEquals("/mnt/sdcard/DCIM/Camera/VID_20100811_051413.3gp",
                    item.filePath);
        }
    }

    class TestMoreVideos extends TestLocalVideoAlbum {
        @Override
        public void prepareData(SQLiteDatabase db) {
            // Albums are sorted by names, and items are sorted by
            // dateTimeTaken (descending)
            createVideoTable(db);
            // bucket 0xB002
            insertVideoData(db, 1000, 0xB000, "second");  // id 1
            insertVideoData(db, 2000, 0xB000, "second");  // id 2
            // bucket 0xB001
            insertVideoData(db, 3000, 0xB001, "first");   // id 3
        }

        @Override
        public void verifyResult() {
            assertEquals(0, mAlbumSet.getMediaItemCount());
            assertEquals(2, mAlbumSet.getSubMediaSetCount());
            assertEquals(3, mAlbumSet.getTotalMediaItemCount());

            MediaSet first = mAlbumSet.getSubMediaSet(0);
            assertEquals(1, first.getMediaItemCount());
            LocalMediaItem item = (LocalMediaItem) first.getMediaItem(0, 1).get(0);
            assertEquals(3, item.id);
            assertEquals(3000L, item.dateTakenInMs);

            MediaSet second = mAlbumSet.getSubMediaSet(1);
            assertEquals(2, second.getMediaItemCount());
            item = (LocalMediaItem) second.getMediaItem(0, 1).get(0);
            assertEquals(2, item.id);
            assertEquals(2000L, item.dateTakenInMs);
            item = (LocalMediaItem) second.getMediaItem(1, 1).get(0);
            assertEquals(1, item.id);
            assertEquals(1000L, item.dateTakenInMs);
        }
    }

    static void createVideoTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS video (" +
                   "_id INTEGER PRIMARY KEY," +
                   "_data TEXT NOT NULL," +
                   "_display_name TEXT," +
                   "_size INTEGER," +
                   "mime_type TEXT," +
                   "date_added INTEGER," +
                   "date_modified INTEGER," +
                   "title TEXT," +
                   "duration INTEGER," +
                   "artist TEXT," +
                   "album TEXT," +
                   "resolution TEXT," +
                   "description TEXT," +
                   "isprivate INTEGER," +   // for YouTube videos
                   "tags TEXT," +           // for YouTube videos
                   "category TEXT," +       // for YouTube videos
                   "language TEXT," +       // for YouTube videos
                   "mini_thumb_data TEXT," +
                   "latitude DOUBLE," +
                   "longitude DOUBLE," +
                   "datetaken INTEGER," +
                   "mini_thumb_magic INTEGER" +
                   ");");
        db.execSQL("ALTER TABLE video ADD COLUMN bucket_id TEXT;");
        db.execSQL("ALTER TABLE video ADD COLUMN bucket_display_name TEXT");
    }

    static void insertVideoData(SQLiteDatabase db) {
        insertVideoData(db, 0xD000, 0xB000, "name");
    }

    static void insertVideoData(SQLiteDatabase db, long dateTaken,
            int bucketId, String bucketName) {
        db.execSQL("INSERT INTO video (title, mime_type, latitude, longitude, "
                + "datetaken, date_added, date_modified, bucket_id, "
                + "bucket_display_name, _data, duration) "
                + "VALUES ('VID_20100811_051413', 'video/mp4', 11, 22, "
                + dateTaken + ", 1281503663, 1281503662, '" + bucketId + "', "
                + "'" + bucketName + "', "
                + "'/mnt/sdcard/DCIM/Camera/VID_20100811_051413.3gp', 2964)");
    }

    static GalleryAppStub newGalleryContext(SQLiteDatabase db, Looper mainLooper) {
        MockContentResolver cr = new MockContentResolver();
        ContentProvider cp = new DbContentProvider(db, cr);
        cr.addProvider("media", cp);
        return new GalleryAppMock(null, cr, mainLooper);
    }
}

class DbContentProvider extends MockContentProvider {
    private static final String TAG = "DbContentProvider";
    private SQLiteDatabase mDatabase;
    private ContentResolver mContentResolver;

    DbContentProvider(SQLiteDatabase db, ContentResolver cr) {
        mDatabase = db;
        mContentResolver = cr;
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        // This is a simplified version extracted from MediaProvider.

        String tableName = getTableName(uri);
        if (tableName == null) return null;

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(tableName);

        String groupBy = null;
        String limit = uri.getQueryParameter("limit");

        if (uri.getQueryParameter("distinct") != null) {
            qb.setDistinct(true);
        }

        Log.v(TAG, "query = " + qb.buildQuery(projection, selection,
                selectionArgs, groupBy, null, sortOrder, limit));

        if (selectionArgs != null) {
            for (String s : selectionArgs) {
                Log.v(TAG, "  selectionArgs = " + s);
            }
        }

        Cursor c = qb.query(mDatabase, projection, selection,
                selectionArgs, groupBy, null, sortOrder, limit);

        return c;
    }

    @Override
    public int delete(Uri uri, String whereClause, String[] whereArgs) {
        Log.v(TAG, "delete " + uri + "," + whereClause + "," + whereArgs[0]);
        String tableName = getTableName(uri);
        if (tableName == null) return 0;
        int count = mDatabase.delete(tableName, whereClause, whereArgs);
        mContentResolver.notifyChange(uri, null);
        return count;
    }

    private String getTableName(Uri uri) {
        String uriString = uri.toString();
        if (uriString.startsWith("content://media/external/images/media")) {
            return "images";
        } else if (uriString.startsWith("content://media/external/video/media")) {
            return "video";
        } else {
            return null;
        }
    }
}
