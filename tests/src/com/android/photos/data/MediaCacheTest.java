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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.test.ProviderTestCase2;

import com.android.gallery3d.tests.R;
import com.android.photos.data.MediaCache.ImageReady;
import com.android.photos.data.MediaCache.OriginalReady;
import com.android.photos.data.MediaRetriever.MediaSize;
import com.android.photos.data.PhotoProvider.Photos;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MediaCacheTest extends ProviderTestCase2<PhotoProvider> {
    @SuppressWarnings("unused")
    private static final String TAG = MediaCacheTest.class.getSimpleName();

    private File mDir;
    private File mImage;
    private File mCacheDir;
    private Resources mResources;
    private MediaCache mMediaCache;
    private ReadyCollector mReady;

    public static final long MAX_WAIT = 2000;

    private static class ReadyCollector implements ImageReady, OriginalReady {
        public File mOriginalFile;
        public InputStream mInputStream;

        @Override
        public synchronized void originalReady(File originalFile) {
            mOriginalFile = originalFile;
            notifyAll();
        }

        @Override
        public synchronized void imageReady(InputStream bitmapInputStream) {
            mInputStream = bitmapInputStream;
            notifyAll();
        }

        public synchronized boolean waitForNotification() {
            long endWait = SystemClock.uptimeMillis() + MAX_WAIT;

            try {
                while (mInputStream == null && mOriginalFile == null
                        && SystemClock.uptimeMillis() < endWait) {
                    wait(endWait - SystemClock.uptimeMillis());
                }
            } catch (InterruptedException e) {
            }
            return mInputStream != null || mOriginalFile != null;
        }
    }

    private static class DummyMediaRetriever implements MediaRetriever {
        private boolean mNullUri = false;
        @Override
        public File getLocalFile(Uri contentUri) {
            return null;
        }

        @Override
        public MediaSize getFastImageSize(Uri contentUri, MediaSize size) {
            return null;
        }

        @Override
        public byte[] getTemporaryImage(Uri contentUri, MediaSize temporarySize) {
            return null;
        }

        @Override
        public boolean getMedia(Uri contentUri, MediaSize imageSize, File tempFile) {
            return false;
        }

        @Override
        public Uri normalizeUri(Uri contentUri, MediaSize size) {
            if (mNullUri) {
                return null;
            } else {
                return contentUri;
            }
        }

        @Override
        public MediaSize normalizeMediaSize(Uri contentUri, MediaSize size) {
            return size;
        }

        public void setNullUri() {
            mNullUri = true;
        }
    };

    public MediaCacheTest() {
        super(PhotoProvider.class, PhotoProvider.AUTHORITY);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mReady = new ReadyCollector();
        File externalDir = Environment.getExternalStorageDirectory();
        mDir = new File(externalDir, "test");
        mDir.mkdirs();
        mCacheDir = new File(externalDir, "test_cache");
        mImage = new File(mDir, "original.jpg");
        MediaCache.initialize(getMockContext());
        MediaCache.getInstance().setCacheDir(mCacheDir);
        mMediaCache = MediaCache.getInstance();
        mMediaCache.addRetriever("file", "", new FileRetriever());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mMediaCache.clearCacheDir();
        MediaCache.shutdown();
        mMediaCache = null;
        mImage.delete();
        mDir.delete();
        mCacheDir.delete();
    }

    public void setLocalContext(Context context) {
        mResources = context.getResources();
    }

    public void testRetrieveOriginal() throws IOException {
        copyResourceToFile(R.raw.galaxy_nexus, mImage.getPath());
        Uri uri = Uri.fromFile(mImage);
        mMediaCache.retrieveOriginal(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        assertNull(mReady.mInputStream);
        assertEquals(mImage, mReady.mOriginalFile);
    }

    public void testRetrievePreview() throws IOException {
        copyResourceToFile(R.raw.galaxy_nexus, mImage.getPath());
        Uri uri = Uri.fromFile(mImage);
        mMediaCache.retrievePreview(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        assertNotNull(mReady.mInputStream);
        assertNull(mReady.mOriginalFile);
        Bitmap bitmap = BitmapFactory.decodeStream(mReady.mInputStream);
        mReady.mInputStream.close();
        assertNotNull(bitmap);
        Bitmap original = BitmapFactory.decodeFile(mImage.getPath());
        assertTrue(bitmap.getWidth() < original.getWidth());
        assertTrue(bitmap.getHeight() < original.getHeight());
        int maxDimension = Math.max(bitmap.getWidth(), bitmap.getHeight());
        int targetSize = MediaCacheUtils.getTargetSize(MediaSize.Preview);
        assertTrue(maxDimension >= targetSize);
        assertTrue(maxDimension < (targetSize * 2));
    }

    public void testRetrieveExifThumb() throws IOException {
        copyResourceToFile(R.raw.galaxy_nexus, mImage.getPath());
        Uri uri = Uri.fromFile(mImage);
        ReadyCollector done = new ReadyCollector();
        mMediaCache.retrieveThumbnail(uri, done, mReady);
        assertTrue(mReady.waitForNotification());
        assertNotNull(mReady.mInputStream);
        assertNull(mReady.mOriginalFile);
        Bitmap bitmap = BitmapFactory.decodeStream(mReady.mInputStream);
        mReady.mInputStream.close();
        assertTrue(done.waitForNotification());
        assertNotNull(done.mInputStream);
        done.mInputStream.close();
        assertNotNull(bitmap);
        assertEquals(320, bitmap.getWidth());
        assertEquals(240, bitmap.getHeight());
    }

    public void testRetrieveThumb() throws IOException {
        copyResourceToFile(R.raw.galaxy_nexus, mImage.getPath());
        Uri uri = Uri.fromFile(mImage);
        long downsampleStart = SystemClock.uptimeMillis();
        mMediaCache.retrieveThumbnail(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        long downsampleEnd = SystemClock.uptimeMillis();
        assertNotNull(mReady.mInputStream);
        assertNull(mReady.mOriginalFile);
        Bitmap bitmap = BitmapFactory.decodeStream(mReady.mInputStream);
        mReady.mInputStream.close();
        assertNotNull(bitmap);
        Bitmap original = BitmapFactory.decodeFile(mImage.getPath());
        assertTrue(bitmap.getWidth() < original.getWidth());
        assertTrue(bitmap.getHeight() < original.getHeight());
        int maxDimension = Math.max(bitmap.getWidth(), bitmap.getHeight());
        int targetSize = MediaCacheUtils.getTargetSize(MediaSize.Thumbnail);
        assertTrue(maxDimension >= targetSize);
        assertTrue(maxDimension < (targetSize * 2));

        // Retrieve cached thumb.
        mReady = new ReadyCollector();
        long start = SystemClock.uptimeMillis();
        mMediaCache.retrieveThumbnail(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        mReady.mInputStream.close();
        long end = SystemClock.uptimeMillis();
        // Already cached. Wait shorter time.
        assertTrue((end - start) < (downsampleEnd - downsampleStart) / 2);
    }

    public void testGetVideo() throws IOException {
        mImage = new File(mDir, "original.mp4");
        copyResourceToFile(R.raw.android_lawn, mImage.getPath());
        Uri uri = Uri.fromFile(mImage);

        mMediaCache.retrieveOriginal(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        assertNull(mReady.mInputStream);
        assertNotNull(mReady.mOriginalFile);

        mReady = new ReadyCollector();
        mMediaCache.retrievePreview(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        assertNotNull(mReady.mInputStream);
        assertNull(mReady.mOriginalFile);
        Bitmap bitmap = BitmapFactory.decodeStream(mReady.mInputStream);
        mReady.mInputStream.close();
        int maxDimension = Math.max(bitmap.getWidth(), bitmap.getHeight());
        int targetSize = MediaCacheUtils.getTargetSize(MediaSize.Preview);
        assertTrue(maxDimension >= targetSize);
        assertTrue(maxDimension < (targetSize * 2));

        mReady = new ReadyCollector();
        mMediaCache.retrieveThumbnail(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        assertNotNull(mReady.mInputStream);
        assertNull(mReady.mOriginalFile);
        bitmap = BitmapFactory.decodeStream(mReady.mInputStream);
        mReady.mInputStream.close();
        maxDimension = Math.max(bitmap.getWidth(), bitmap.getHeight());
        targetSize = MediaCacheUtils.getTargetSize(MediaSize.Thumbnail);
        assertTrue(maxDimension >= targetSize);
        assertTrue(maxDimension < (targetSize * 2));
    }

    public void testFastImage() throws IOException {
        copyResourceToFile(R.raw.galaxy_nexus, mImage.getPath());
        Uri uri = Uri.fromFile(mImage);
        mMediaCache.retrieveThumbnail(uri, mReady, null);
        mReady.waitForNotification();
        mReady.mInputStream.close();

        mMediaCache.retrieveOriginal(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        assertNotNull(mReady.mInputStream);
        mReady.mInputStream.close();
    }

    public void testBadRetriever() {
        Uri uri = Photos.CONTENT_URI;
        try {
            mMediaCache.retrieveOriginal(uri, mReady, null);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testInsertIntoCache() throws IOException {
        // FileRetriever inserts into the cache opportunistically with Videos
        mImage = new File(mDir, "original.mp4");
        copyResourceToFile(R.raw.android_lawn, mImage.getPath());
        Uri uri = Uri.fromFile(mImage);

        mMediaCache.retrieveThumbnail(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        mReady.mInputStream.close();
        assertNotNull(mMediaCache.getCachedFile(uri, MediaSize.Preview));
    }

    public void testBadNormalizedUri() {
        DummyMediaRetriever retriever = new DummyMediaRetriever();
        Uri uri = Uri.fromParts("http", "world", "morestuff");
        mMediaCache.addRetriever(uri.getScheme(), uri.getAuthority(), retriever);
        retriever.setNullUri();
        try {
            mMediaCache.retrieveOriginal(uri, mReady, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    public void testClearOldCache() throws IOException {
        copyResourceToFile(R.raw.galaxy_nexus, mImage.getPath());
        Uri uri = Uri.fromFile(mImage);
        mMediaCache.retrievePreview(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        mReady.mInputStream.close();
        mMediaCache.setMaxCacheSize(mMediaCache.getCachedFile(uri, MediaSize.Preview).length());
        assertNotNull(mMediaCache.getCachedFile(uri, MediaSize.Preview));

        mReady = new ReadyCollector();
        // This should kick the preview image out of the cache.
        mMediaCache.retrieveThumbnail(uri, mReady, null);
        assertTrue(mReady.waitForNotification());
        mReady.mInputStream.close();
        assertNull(mMediaCache.getCachedFile(uri, MediaSize.Preview));
        assertNotNull(mMediaCache.getCachedFile(uri, MediaSize.Thumbnail));
    }

    public void testClearLargeInCache() throws IOException {
        copyResourceToFile(R.raw.galaxy_nexus, mImage.getPath());
        Uri imageUri = Uri.fromFile(mImage);
        mMediaCache.retrieveThumbnail(imageUri, mReady, null);
        assertTrue(mReady.waitForNotification());
            mReady.mInputStream.close();
        assertNotNull(mMediaCache.getCachedFile(imageUri, MediaSize.Thumbnail));
        long thumbSize = mMediaCache.getCachedFile(imageUri, MediaSize.Thumbnail).length();
        mMediaCache.setMaxCacheSize(thumbSize * 10);

        for (int i = 0; i < 9; i++) {
            File tempImage = new File(mDir, "image" + i + ".jpg");
            mImage.renameTo(tempImage);
            Uri tempImageUri = Uri.fromFile(tempImage);
            mReady = new ReadyCollector();
            mMediaCache.retrieveThumbnail(tempImageUri, mReady, null);
            assertTrue(mReady.waitForNotification());
                mReady.mInputStream.close();
            tempImage.renameTo(mImage);
        }
        assertNotNull(mMediaCache.getCachedFile(imageUri, MediaSize.Thumbnail));

        for (int i = 0; i < 9; i++) {
            File tempImage = new File(mDir, "image" + i + ".jpg");
            mImage.renameTo(tempImage);
            Uri tempImageUri = Uri.fromFile(tempImage);
            mReady = new ReadyCollector();
            mMediaCache.retrievePreview(tempImageUri, mReady, null);
            assertTrue(mReady.waitForNotification());
                mReady.mInputStream.close();
            tempImage.renameTo(mImage);
        }
        assertNotNull(mMediaCache.getCachedFile(imageUri, MediaSize.Thumbnail));
        Uri oldestUri = Uri.fromFile(new File(mDir, "image0.jpg"));
        assertNull(mMediaCache.getCachedFile(oldestUri, MediaSize.Thumbnail));
    }

    private void copyResourceToFile(int resourceId, String path) throws IOException {
        File outputDir = new File(path).getParentFile();
        outputDir.mkdirs();

        InputStream in = mResources.openRawResource(resourceId);
        FileOutputStream out = new FileOutputStream(path);
        byte[] buffer = new byte[1000];
        int bytesRead;

        while ((bytesRead = in.read(buffer)) >= 0) {
            out.write(buffer, 0, bytesRead);
        }

        in.close();
        out.close();
    }
}
