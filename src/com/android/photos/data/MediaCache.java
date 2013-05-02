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
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.android.photos.data.MediaCacheDatabase.Action;
import com.android.photos.data.MediaRetriever.MediaSize;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * MediaCache keeps a cache of images, videos, thumbnails and previews. Calls to
 * retrieve a specific media item are executed asynchronously. The caller has an
 * option to receive a notification for lower resolution images that happen to
 * be available prior to the one requested.
 * <p>
 * When an media item has been retrieved, the notification for it is called on a
 * separate notifier thread. This thread should not be held for a long time so
 * that other notifications may happen.
 * </p>
 * <p>
 * Media items are uniquely identified by their content URIs. Each
 * scheme/authority can offer its own MediaRetriever, running in its own thread.
 * </p>
 * <p>
 * The MediaCache is an LRU cache, but does not allow the thumbnail cache to
 * drop below a minimum size. This prevents browsing through original images to
 * wipe out the thumbnails.
 * </p>
 */
public class MediaCache {
    static final String TAG = MediaCache.class.getSimpleName();
    /** Subdirectory containing the image cache. */
    static final String IMAGE_CACHE_SUBDIR = "image_cache";
    /** File name extension to use for cached images. */
    static final String IMAGE_EXTENSION = ".cache";
    /** File name extension to use for temporary cached images while retrieving. */
    static final String TEMP_IMAGE_EXTENSION = ".temp";

    public static interface ImageReady {
        void imageReady(InputStream bitmapInputStream);
    }

    public static interface OriginalReady {
        void originalReady(File originalFile);
    }

    /** A Thread for each MediaRetriever */
    private class ProcessQueue extends Thread {
        private Queue<ProcessingJob> mQueue;

        public ProcessQueue(Queue<ProcessingJob> queue) {
            mQueue = queue;
        }

        @Override
        public void run() {
            while (mRunning) {
                ProcessingJob status;
                synchronized (mQueue) {
                    while (mQueue.isEmpty()) {
                        try {
                            mQueue.wait();
                        } catch (InterruptedException e) {
                            if (!mRunning) {
                                return;
                            }
                            Log.w(TAG, "Unexpected interruption", e);
                        }
                    }
                    status = mQueue.remove();
                }
                processTask(status);
            }
        }
    };

    private interface NotifyReady {
        void notifyReady();

        void setFile(File file) throws FileNotFoundException;

        boolean isPrefetch();
    }

    private static class NotifyOriginalReady implements NotifyReady {
        private final OriginalReady mCallback;
        private File mFile;

        public NotifyOriginalReady(OriginalReady callback) {
            mCallback = callback;
        }

        @Override
        public void notifyReady() {
            if (mCallback != null) {
                mCallback.originalReady(mFile);
            }
        }

        @Override
        public void setFile(File file) {
            mFile = file;
        }

        @Override
        public boolean isPrefetch() {
            return mCallback == null;
        }
    }

    private static class NotifyImageReady implements NotifyReady {
        private final ImageReady mCallback;
        private InputStream mInputStream;

        public NotifyImageReady(ImageReady callback) {
            mCallback = callback;
        }

        @Override
        public void notifyReady() {
            if (mCallback != null) {
                mCallback.imageReady(mInputStream);
            }
        }

        @Override
        public void setFile(File file) throws FileNotFoundException {
            mInputStream = new FileInputStream(file);
        }

        public void setBytes(byte[] bytes) {
            mInputStream = new ByteArrayInputStream(bytes);
        }

        @Override
        public boolean isPrefetch() {
            return mCallback == null;
        }
    }

    /** A media item to be retrieved and its notifications. */
    private static class ProcessingJob {
        public ProcessingJob(Uri uri, MediaSize size, NotifyReady complete,
                NotifyImageReady lowResolution) {
            this.contentUri = uri;
            this.size = size;
            this.complete = complete;
            this.lowResolution = lowResolution;
        }
        public Uri contentUri;
        public MediaSize size;
        public NotifyImageReady lowResolution;
        public NotifyReady complete;
    }

    private boolean mRunning = true;
    private static MediaCache sInstance;
    private File mCacheDir;
    private Context mContext;
    private Queue<NotifyReady> mCallbacks = new LinkedList<NotifyReady>();
    private Map<String, MediaRetriever> mRetrievers = new HashMap<String, MediaRetriever>();
    private Map<String, List<ProcessingJob>> mTasks = new HashMap<String, List<ProcessingJob>>();
    private List<ProcessQueue> mProcessingThreads = new ArrayList<ProcessQueue>();
    private MediaCacheDatabase mDatabaseHelper;
    private long mTempImageNumber = 1;
    private Object mTempImageNumberLock = new Object();

    private long mMaxCacheSize = 40 * 1024 * 1024; // 40 MB
    private long mMinThumbCacheSize = 4 * 1024 * 1024; // 4 MB
    private long mCacheSize = -1;
    private long mThumbCacheSize = -1;
    private Object mCacheSizeLock = new Object();

    private Action mNotifyCachedLowResolution = new Action() {
        @Override
        public void execute(Uri uri, long id, MediaSize size, Object parameter) {
            ProcessingJob job = (ProcessingJob) parameter;
            File file = createCacheImagePath(id);
            addNotification(job.lowResolution, file);
        }
    };

    private Action mMoveTempToCache = new Action() {
        @Override
        public void execute(Uri uri, long id, MediaSize size, Object parameter) {
            File tempFile = (File) parameter;
            File cacheFile = createCacheImagePath(id);
            tempFile.renameTo(cacheFile);
        }
    };

    private Action mDeleteFile = new Action() {
        @Override
        public void execute(Uri uri, long id, MediaSize size, Object parameter) {
            File file = createCacheImagePath(id);
            file.delete();
            synchronized (mCacheSizeLock) {
                if (mCacheSize != -1) {
                    long length = (Long) parameter;
                    mCacheSize -= length;
                    if (size == MediaSize.Thumbnail) {
                        mThumbCacheSize -= length;
                    }
                }
            }
        }
    };

    /** The thread used to make ImageReady and OriginalReady callbacks. */
    private Thread mProcessNotifications = new Thread() {
        @Override
        public void run() {
            while (mRunning) {
                NotifyReady notifyImage;
                synchronized (mCallbacks) {
                    while (mCallbacks.isEmpty()) {
                        try {
                            mCallbacks.wait();
                        } catch (InterruptedException e) {
                            if (!mRunning) {
                                return;
                            }
                            Log.w(TAG, "Unexpected Interruption, continuing");
                        }
                    }
                    notifyImage = mCallbacks.remove();
                }

                notifyImage.notifyReady();
            }
        }
    };

    public static synchronized void initialize(Context context) {
        if (sInstance == null) {
            sInstance = new MediaCache(context);
            MediaCacheUtils.initialize(context);
        }
    }

    public static MediaCache getInstance() {
        return sInstance;
    }

    public static synchronized void shutdown() {
        sInstance.mRunning = false;
        sInstance.mProcessNotifications.interrupt();
        for (ProcessQueue processingThread : sInstance.mProcessingThreads) {
            processingThread.interrupt();
        }
        sInstance = null;
    }

    private MediaCache(Context context) {
        mDatabaseHelper = new MediaCacheDatabase(context);
        mProcessNotifications.start();
        mContext = context;
    }

    // This is used for testing.
    public void setCacheDir(File cacheDir) {
        cacheDir.mkdirs();
        mCacheDir = cacheDir;
    }

    public File getCacheDir() {
        synchronized (mContext) {
            if (mCacheDir == null) {
                String state = Environment.getExternalStorageState();
                File baseDir;
                if (Environment.MEDIA_MOUNTED.equals(state)) {
                    baseDir = mContext.getExternalCacheDir();
                } else {
                    // Stored in internal cache
                    baseDir = mContext.getCacheDir();
                }
                mCacheDir = new File(baseDir, IMAGE_CACHE_SUBDIR);
                mCacheDir.mkdirs();
            }
            return mCacheDir;
        }
    }

    /**
     * Invalidates all cached images related to a given contentUri. This call
     * doesn't complete until the images have been removed from the cache.
     */
    public void invalidate(Uri contentUri) {
        mDatabaseHelper.delete(contentUri, mDeleteFile);
    }

    public void clearCacheDir() {
        File[] cachedFiles = getCacheDir().listFiles();
        if (cachedFiles != null) {
            for (File cachedFile : cachedFiles) {
                cachedFile.delete();
            }
        }
    }

    /**
     * Add a MediaRetriever for a Uri scheme and authority. This MediaRetriever
     * will be granted its own thread for retrieving images.
     */
    public void addRetriever(String scheme, String authority, MediaRetriever retriever) {
        String differentiator = getDifferentiator(scheme, authority);
        synchronized (mRetrievers) {
            mRetrievers.put(differentiator, retriever);
        }
        synchronized (mTasks) {
            LinkedList<ProcessingJob> queue = new LinkedList<ProcessingJob>();
            mTasks.put(differentiator, queue);
            new ProcessQueue(queue).start();
        }
    }

    /**
     * Retrieves a thumbnail. complete will be called when the thumbnail is
     * available. If lowResolution is not null and a lower resolution thumbnail
     * is available before the thumbnail, lowResolution will be called prior to
     * complete. All callbacks will be made on a thread other than the calling
     * thread.
     *
     * @param contentUri The URI for the full resolution image to search for.
     * @param complete Callback for when the image has been retrieved.
     * @param lowResolution If not null and a lower resolution image is
     *            available prior to retrieving the thumbnail, this will be
     *            called with the low resolution bitmap.
     */
    public void retrieveThumbnail(Uri contentUri, ImageReady complete, ImageReady lowResolution) {
        addTask(contentUri, complete, lowResolution, MediaSize.Thumbnail);
    }

    /**
     * Retrieves a preview. complete will be called when the preview is
     * available. If lowResolution is not null and a lower resolution preview is
     * available before the preview, lowResolution will be called prior to
     * complete. All callbacks will be made on a thread other than the calling
     * thread.
     *
     * @param contentUri The URI for the full resolution image to search for.
     * @param complete Callback for when the image has been retrieved.
     * @param lowResolution If not null and a lower resolution image is
     *            available prior to retrieving the preview, this will be called
     *            with the low resolution bitmap.
     */
    public void retrievePreview(Uri contentUri, ImageReady complete, ImageReady lowResolution) {
        addTask(contentUri, complete, lowResolution, MediaSize.Preview);
    }

    /**
     * Retrieves the original image or video. complete will be called when the
     * media is available on the local file system. If lowResolution is not null
     * and a lower resolution preview is available before the original,
     * lowResolution will be called prior to complete. All callbacks will be
     * made on a thread other than the calling thread.
     *
     * @param contentUri The URI for the full resolution image to search for.
     * @param complete Callback for when the image has been retrieved.
     * @param lowResolution If not null and a lower resolution image is
     *            available prior to retrieving the preview, this will be called
     *            with the low resolution bitmap.
     */
    public void retrieveOriginal(Uri contentUri, OriginalReady complete, ImageReady lowResolution) {
        File localFile = getLocalFile(contentUri);
        if (localFile != null) {
            addNotification(new NotifyOriginalReady(complete), localFile);
        } else {
            NotifyImageReady notifyLowResolution = (lowResolution == null) ? null
                    : new NotifyImageReady(lowResolution);
            addTask(contentUri, new NotifyOriginalReady(complete), notifyLowResolution,
                    MediaSize.Original);
        }
    }

    /**
     * Looks for an already cached media at a specific size.
     *
     * @param contentUri The original media item content URI
     * @param size The target size to search for in the cache
     * @return The cached file location or null if it is not cached.
     */
    public File getCachedFile(Uri contentUri, MediaSize size) {
        Long cachedId = mDatabaseHelper.getCached(contentUri, size);
        File file = null;
        if (cachedId != null) {
            file = createCacheImagePath(cachedId);
            if (!file.exists()) {
                mDatabaseHelper.delete(contentUri, size, mDeleteFile);
                file = null;
            }
        }
        return file;
    }

    /**
     * Inserts a media item into the cache.
     *
     * @param contentUri The original media item URI.
     * @param size The size of the media item to store in the cache.
     * @param tempFile The temporary file where the image is stored. This file
     *            will no longer exist after executing this method.
     * @return The new location, in the cache, of the media item or null if it
     *         wasn't possible to move into the cache.
     */
    public File insertIntoCache(Uri contentUri, MediaSize size, File tempFile) {
        long fileSize = tempFile.length();
        if (fileSize == 0) {
            return null;
        }
        File cacheFile = null;
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        // Ensure that this step is atomic
        db.beginTransaction();
        try {
            Long id = mDatabaseHelper.getCached(contentUri, size);
            if (id != null) {
                cacheFile = createCacheImagePath(id);
                if (tempFile.renameTo(cacheFile)) {
                    mDatabaseHelper.updateLength(id, fileSize);
                } else {
                    Log.w(TAG, "Could not update cached file with " + tempFile);
                    tempFile.delete();
                    cacheFile = null;
                }
            } else {
                ensureFreeCacheSpace(tempFile.length(), size);
                id = mDatabaseHelper.insert(contentUri, size, mMoveTempToCache, tempFile);
                cacheFile = createCacheImagePath(id);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return cacheFile;
    }

    /**
     * For testing purposes.
     */
    public void setMaxCacheSize(long maxCacheSize) {
        synchronized (mCacheSizeLock) {
            mMaxCacheSize = maxCacheSize;
            mMinThumbCacheSize = mMaxCacheSize / 10;
            mCacheSize = -1;
            mThumbCacheSize = -1;
        }
    }

    private File createCacheImagePath(long id) {
        return new File(getCacheDir(), String.valueOf(id) + IMAGE_EXTENSION);
    }

    private void addTask(Uri contentUri, ImageReady complete, ImageReady lowResolution,
            MediaSize size) {
        NotifyReady notifyComplete = new NotifyImageReady(complete);
        NotifyImageReady notifyLowResolution = null;
        if (lowResolution != null) {
            notifyLowResolution = new NotifyImageReady(lowResolution);
        }
        addTask(contentUri, notifyComplete, notifyLowResolution, size);
    }

    private void addTask(Uri contentUri, NotifyReady complete, NotifyImageReady lowResolution,
            MediaSize size) {
        MediaRetriever retriever = getMediaRetriever(contentUri);
        Uri uri = retriever.normalizeUri(contentUri, size);
        if (uri == null) {
            throw new IllegalArgumentException("No MediaRetriever for " + contentUri);
        }
        size = retriever.normalizeMediaSize(uri, size);

        File cachedFile = getCachedFile(uri, size);
        if (cachedFile != null) {
            addNotification(complete, cachedFile);
            return;
        }
        String differentiator = getDifferentiator(uri.getScheme(), uri.getAuthority());
        synchronized (mTasks) {
            List<ProcessingJob> tasks = mTasks.get(differentiator);
            if (tasks == null) {
                throw new IllegalArgumentException("Cannot find retriever for: " + uri);
            }
            synchronized (tasks) {
                ProcessingJob job = new ProcessingJob(uri, size, complete, lowResolution);
                if (complete.isPrefetch()) {
                    tasks.add(job);
                } else {
                    int index = tasks.size() - 1;
                    while (index >= 0 && tasks.get(index).complete.isPrefetch()) {
                        index--;
                    }
                    tasks.add(index + 1, job);
                }
                tasks.notifyAll();
            }
        }
    }

    private MediaRetriever getMediaRetriever(Uri uri) {
        String differentiator = getDifferentiator(uri.getScheme(), uri.getAuthority());
        MediaRetriever retriever;
        synchronized (mRetrievers) {
            retriever = mRetrievers.get(differentiator);
        }
        if (retriever == null) {
            throw new IllegalArgumentException("No MediaRetriever for " + uri);
        }
        return retriever;
    }

    private File getLocalFile(Uri uri) {
        MediaRetriever retriever = getMediaRetriever(uri);
        File localFile = null;
        if (retriever != null) {
            localFile = retriever.getLocalFile(uri);
        }
        return localFile;
    }

    private MediaSize getFastImageSize(Uri uri, MediaSize size) {
        MediaRetriever retriever = getMediaRetriever(uri);
        return retriever.getFastImageSize(uri, size);
    }

    private boolean isFastImageBetter(MediaSize fastImageType, MediaSize size) {
        if (fastImageType == null) {
            return false;
        }
        if (size == null) {
            return true;
        }
        return fastImageType.isBetterThan(size);
    }

    private byte[] getTemporaryImage(Uri uri, MediaSize fastImageType) {
        MediaRetriever retriever = getMediaRetriever(uri);
        return retriever.getTemporaryImage(uri, fastImageType);
    }

    private void processTask(ProcessingJob job) {
        File cachedFile = getCachedFile(job.contentUri, job.size);
        if (cachedFile != null) {
            addNotification(job.complete, cachedFile);
            return;
        }

        boolean hasLowResolution = job.lowResolution != null;
        if (hasLowResolution) {
            MediaSize cachedSize = mDatabaseHelper.executeOnBestCached(job.contentUri, job.size,
                    mNotifyCachedLowResolution);
            MediaSize fastImageSize = getFastImageSize(job.contentUri, job.size);
            if (isFastImageBetter(fastImageSize, cachedSize)) {
                if (fastImageSize.isTemporary()) {
                    byte[] bytes = getTemporaryImage(job.contentUri, fastImageSize);
                    if (bytes != null) {
                        addNotification(job.lowResolution, bytes);
                    }
                } else {
                    File lowFile = getMedia(job.contentUri, fastImageSize);
                    if (lowFile != null) {
                        addNotification(job.lowResolution, lowFile);
                    }
                }
            }
        }

        // Now get the full size desired
        File fullSizeFile = getMedia(job.contentUri, job.size);
        if (fullSizeFile != null) {
            addNotification(job.complete, fullSizeFile);
        }
    }

    private void addNotification(NotifyReady callback, File file) {
        try {
            callback.setFile(file);
            synchronized (mCallbacks) {
                mCallbacks.add(callback);
                mCallbacks.notifyAll();
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to read file " + file, e);
        }
    }

    private void addNotification(NotifyImageReady callback, byte[] bytes) {
        callback.setBytes(bytes);
        synchronized (mCallbacks) {
            mCallbacks.add(callback);
            mCallbacks.notifyAll();
        }
    }

    private File getMedia(Uri uri, MediaSize size) {
        long imageNumber;
        synchronized (mTempImageNumberLock) {
            imageNumber = mTempImageNumber++;
        }
        File tempFile = new File(getCacheDir(), String.valueOf(imageNumber) + TEMP_IMAGE_EXTENSION);
        MediaRetriever retriever = getMediaRetriever(uri);
        boolean retrieved = retriever.getMedia(uri, size, tempFile);
        File cachedFile = null;
        if (retrieved) {
            ensureFreeCacheSpace(tempFile.length(), size);
            long id = mDatabaseHelper.insert(uri, size, mMoveTempToCache, tempFile);
            cachedFile = createCacheImagePath(id);
        }
        return cachedFile;
    }

    private static String getDifferentiator(String scheme, String authority) {
        if (authority == null) {
            return scheme;
        }
        StringBuilder differentiator = new StringBuilder(scheme);
        differentiator.append(':');
        differentiator.append(authority);
        return differentiator.toString();
    }

    private void ensureFreeCacheSpace(long size, MediaSize mediaSize) {
        synchronized (mCacheSizeLock) {
            if (mCacheSize == -1 || mThumbCacheSize == -1) {
                mCacheSize = mDatabaseHelper.getCacheSize();
                mThumbCacheSize = mDatabaseHelper.getThumbnailCacheSize();
                if (mCacheSize == -1 || mThumbCacheSize == -1) {
                    Log.e(TAG, "Can't determine size of the image cache");
                    return;
                }
            }
            mCacheSize += size;
            if (mediaSize == MediaSize.Thumbnail) {
                mThumbCacheSize += size;
            }
            if (mCacheSize > mMaxCacheSize) {
                shrinkCacheLocked();
            }
        }
    }

    private void shrinkCacheLocked() {
        long deleteSize = mMinThumbCacheSize;
        boolean includeThumbnails = (mThumbCacheSize - deleteSize) > mMinThumbCacheSize;
        mDatabaseHelper.deleteOldCached(includeThumbnails, deleteSize, mDeleteFile);
    }
}
