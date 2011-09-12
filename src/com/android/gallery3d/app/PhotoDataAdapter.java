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

package com.android.gallery3d.app;

import com.android.gallery3d.common.BitmapUtils;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.PhotoView;
import com.android.gallery3d.ui.PhotoView.ImageData;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.ui.TileImageViewAdapter;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class PhotoDataAdapter implements PhotoPage.Model {
    @SuppressWarnings("unused")
    private static final String TAG = "PhotoDataAdapter";

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_FINISH = 2;
    private static final int MSG_RUN_OBJECT = 3;

    private static final int MIN_LOAD_COUNT = 8;
    private static final int DATA_CACHE_SIZE = 32;
    private static final int IMAGE_CACHE_SIZE = 5;

    private static final int BIT_SCREEN_NAIL = 1;
    private static final int BIT_FULL_IMAGE = 2;

    private static final long VERSION_OUT_OF_RANGE = MediaObject.nextVersionNumber();

    // sImageFetchSeq is the fetching sequence for images.
    // We want to fetch the current screennail first (offset = 0), the next
    // screennail (offset = +1), then the previous screennail (offset = -1) etc.
    // After all the screennail are fetched, we fetch the full images (only some
    // of them because of we don't want to use too much memory).
    private static ImageFetch[] sImageFetchSeq;

    private static class ImageFetch {
        int indexOffset;
        int imageBit;
        public ImageFetch(int offset, int bit) {
            indexOffset = offset;
            imageBit = bit;
        }
    }

    static {
        int k = 0;
        sImageFetchSeq = new ImageFetch[1 + (IMAGE_CACHE_SIZE - 1) * 2 + 3];
        sImageFetchSeq[k++] = new ImageFetch(0, BIT_SCREEN_NAIL);

        for (int i = 1; i < IMAGE_CACHE_SIZE; ++i) {
            sImageFetchSeq[k++] = new ImageFetch(i, BIT_SCREEN_NAIL);
            sImageFetchSeq[k++] = new ImageFetch(-i, BIT_SCREEN_NAIL);
        }

        sImageFetchSeq[k++] = new ImageFetch(0, BIT_FULL_IMAGE);
        sImageFetchSeq[k++] = new ImageFetch(1, BIT_FULL_IMAGE);
        sImageFetchSeq[k++] = new ImageFetch(-1, BIT_FULL_IMAGE);
    }

    private final TileImageViewAdapter mTileProvider = new TileImageViewAdapter();

    // PhotoDataAdapter caches MediaItems (data) and ImageEntries (image).
    //
    // The MediaItems are stored in the mData array, which has DATA_CACHE_SIZE
    // entries. The valid index range are [mContentStart, mContentEnd). We keep
    // mContentEnd - mContentStart <= DATA_CACHE_SIZE, so we can use
    // (i % DATA_CACHE_SIZE) as index to the array.
    //
    // The valid MediaItem window size (mContentEnd - mContentStart) may be
    // smaller than DATA_CACHE_SIZE because we only update the window and reload
    // the MediaItems when there are significant changes to the window position
    // (>= MIN_LOAD_COUNT).
    private final MediaItem mData[] = new MediaItem[DATA_CACHE_SIZE];
    private int mContentStart = 0;
    private int mContentEnd = 0;

    /*
     * The ImageCache is a version-to-ImageEntry map. It only holds
     * the ImageEntries in the range of [mActiveStart, mActiveEnd).
     * We also keep mActiveEnd - mActiveStart <= IMAGE_CACHE_SIZE.
     * Besides, the [mActiveStart, mActiveEnd) range must be contained
     * within the[mContentStart, mContentEnd) range.
     */
    private HashMap<Long, ImageEntry> mImageCache = new HashMap<Long, ImageEntry>();
    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    // mCurrentIndex is the "center" image the user is viewing. The change of
    // mCurrentIndex triggers the data loading and image loading.
    private int mCurrentIndex;

    // mChanges keeps the version number (of MediaItem) about the previous,
    // current, and next image. If the version number changes, we invalidate
    // the model. This is used after a database reload or mCurrentIndex changes.
    private final long mChanges[] = new long[3];

    private final Handler mMainHandler;
    private final ThreadPool mThreadPool;

    private final PhotoView mPhotoView;
    private final MediaSet mSource;
    private ReloadTask mReloadTask;

    private long mSourceVersion = MediaObject.INVALID_DATA_VERSION;
    private int mSize = 0;
    private Path mItemPath;
    private boolean mIsActive;

    public interface DataListener extends LoadingListener {
        public void onPhotoAvailable(long version, boolean fullImage);
        public void onPhotoChanged(int index, Path item);
    }

    private DataListener mDataListener;

    private final SourceListener mSourceListener = new SourceListener();

    // The path of the current viewing item will be stored in mItemPath.
    // If mItemPath is not null, mCurrentIndex is only a hint for where we
    // can find the item. If mItemPath is null, then we use the mCurrentIndex to
    // find the image being viewed.
    public PhotoDataAdapter(GalleryActivity activity,
            PhotoView view, MediaSet mediaSet, Path itemPath, int indexHint) {
        mSource = Utils.checkNotNull(mediaSet);
        mPhotoView = Utils.checkNotNull(view);
        mItemPath = Utils.checkNotNull(itemPath);
        mCurrentIndex = indexHint;
        mThreadPool = activity.getThreadPool();

        Arrays.fill(mChanges, MediaObject.INVALID_DATA_VERSION);

        mMainHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @SuppressWarnings("unchecked")
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_RUN_OBJECT:
                        ((Runnable) message.obj).run();
                        return;
                    case MSG_LOAD_START: {
                        if (mDataListener != null) mDataListener.onLoadingStarted();
                        return;
                    }
                    case MSG_LOAD_FINISH: {
                        if (mDataListener != null) mDataListener.onLoadingFinished();
                        return;
                    }
                    default: throw new AssertionError();
                }
            }
        };

        updateSlidingWindow();
    }

    private long getVersion(int index) {
        if (index < 0 || index >= mSize) return VERSION_OUT_OF_RANGE;
        if (index >= mContentStart && index < mContentEnd) {
            MediaItem item = mData[index % DATA_CACHE_SIZE];
            if (item != null) return item.getDataVersion();
        }
        return MediaObject.INVALID_DATA_VERSION;
    }

    private void fireModelInvalidated() {
        for (int i = -1; i <= 1; ++i) {
            long current = getVersion(mCurrentIndex + i);
            long change = mChanges[i + 1];
            if (current != change) {
                mPhotoView.notifyImageInvalidated(i);
                mChanges[i + 1] = current;
            }
        }
    }

    public void setDataListener(DataListener listener) {
        mDataListener = listener;
    }

    private void updateScreenNail(long version, Future<Bitmap> future) {
        ImageEntry entry = mImageCache.get(version);
        if (entry == null || entry.screenNailTask != future) {
            Bitmap screenNail = future.get();
            if (screenNail != null) screenNail.recycle();
            return;
        }

        entry.screenNailTask = null;
        entry.screenNail = future.get();

        if (entry.screenNail == null) {
            entry.failToLoad = true;
        } else {
            if (mDataListener != null) {
                mDataListener.onPhotoAvailable(version, false);
            }
            for (int i = -1; i <=1; ++i) {
                if (version == getVersion(mCurrentIndex + i)) {
                    if (i == 0) updateTileProvider(entry);
                    mPhotoView.notifyImageInvalidated(i);
                }
            }
        }
        updateImageRequests();
    }

    private void updateFullImage(long version, Future<BitmapRegionDecoder> future) {
        ImageEntry entry = mImageCache.get(version);
        if (entry == null || entry.fullImageTask != future) {
            BitmapRegionDecoder fullImage = future.get();
            if (fullImage != null) fullImage.recycle();
            return;
        }

        entry.fullImageTask = null;
        entry.fullImage = future.get();
        if (entry.fullImage != null) {
            if (mDataListener != null) {
                mDataListener.onPhotoAvailable(version, true);
            }
            if (version == getVersion(mCurrentIndex)) {
                updateTileProvider(entry);
                mPhotoView.notifyImageInvalidated(0);
            }
        }
        updateImageRequests();
    }

    public void resume() {
        mIsActive = true;
        mSource.addContentListener(mSourceListener);
        updateImageCache();
        updateImageRequests();

        mReloadTask = new ReloadTask();
        mReloadTask.start();

        mPhotoView.notifyModelInvalidated();
    }

    public void pause() {
        mIsActive = false;

        mReloadTask.terminate();
        mReloadTask = null;

        mSource.removeContentListener(mSourceListener);

        for (ImageEntry entry : mImageCache.values()) {
            if (entry.fullImageTask != null) entry.fullImageTask.cancel();
            if (entry.screenNailTask != null) entry.screenNailTask.cancel();
        }
        mImageCache.clear();
        mTileProvider.clear();
    }

    private ImageData getImage(int index) {
        if (index < 0 || index >= mSize || !mIsActive) return null;
        Utils.assertTrue(index >= mActiveStart && index < mActiveEnd);

        ImageEntry entry = mImageCache.get(getVersion(index));
        Bitmap screennail = entry == null ? null : entry.screenNail;
        if (screennail != null) {
            return new ImageData(screennail, entry.rotation);
        } else {
            return new ImageData(null, 0);
        }
    }

    public ImageData getPreviousImage() {
        return getImage(mCurrentIndex - 1);
    }

    public ImageData getNextImage() {
        return getImage(mCurrentIndex + 1);
    }

    private void updateCurrentIndex(int index) {
        mCurrentIndex = index;
        updateSlidingWindow();

        MediaItem item = mData[index % DATA_CACHE_SIZE];
        mItemPath = item == null ? null : item.getPath();

        updateImageCache();
        updateImageRequests();
        updateTileProvider();
        mPhotoView.notifyOnNewImage();

        if (mDataListener != null) {
            mDataListener.onPhotoChanged(index, mItemPath);
        }
        fireModelInvalidated();
    }

    public void next() {
        updateCurrentIndex(mCurrentIndex + 1);
    }

    public void previous() {
        updateCurrentIndex(mCurrentIndex - 1);
    }

    public void jumpTo(int index) {
        if (mCurrentIndex == index) return;
        updateCurrentIndex(index);
    }

    public Bitmap getBackupImage() {
        return mTileProvider.getBackupImage();
    }

    public int getImageHeight() {
        return mTileProvider.getImageHeight();
    }

    public int getImageWidth() {
        return mTileProvider.getImageWidth();
    }

    public int getImageRotation() {
        ImageEntry entry = mImageCache.get(getVersion(mCurrentIndex));
        return entry == null ? 0 : entry.rotation;
    }

    public int getLevelCount() {
        return mTileProvider.getLevelCount();
    }

    public Bitmap getTile(int level, int x, int y, int tileSize) {
        return mTileProvider.getTile(level, x, y, tileSize);
    }

    public boolean isFailedToLoad() {
        return mTileProvider.isFailedToLoad();
    }

    public boolean isEmpty() {
        return mSize == 0;
    }

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public MediaItem getCurrentMediaItem() {
        return mData[mCurrentIndex % DATA_CACHE_SIZE];
    }

    public void setCurrentPhoto(Path path, int indexHint) {
        if (mItemPath == path) return;
        mItemPath = path;
        mCurrentIndex = indexHint;
        updateSlidingWindow();
        updateImageCache();
        fireModelInvalidated();

        // We need to reload content if the path doesn't match.
        MediaItem item = getCurrentMediaItem();
        if (item != null && item.getPath() != path) {
            if (mReloadTask != null) mReloadTask.notifyDirty();
        }
    }

    private void updateTileProvider() {
        ImageEntry entry = mImageCache.get(getVersion(mCurrentIndex));
        if (entry == null) { // in loading
            mTileProvider.clear();
        } else {
            updateTileProvider(entry);
        }
    }

    private void updateTileProvider(ImageEntry entry) {
        Bitmap screenNail = entry.screenNail;
        BitmapRegionDecoder fullImage = entry.fullImage;
        if (screenNail != null) {
            if (fullImage != null) {
                mTileProvider.setBackupImage(screenNail,
                        fullImage.getWidth(), fullImage.getHeight());
                mTileProvider.setRegionDecoder(fullImage);
            } else {
                int width = screenNail.getWidth();
                int height = screenNail.getHeight();
                mTileProvider.setBackupImage(screenNail, width, height);
            }
        } else {
            mTileProvider.clear();
            if (entry.failToLoad) mTileProvider.setFailedToLoad();
        }
    }

    private void updateSlidingWindow() {
        // 1. Update the image window
        int start = Utils.clamp(mCurrentIndex - IMAGE_CACHE_SIZE / 2,
                0, Math.max(0, mSize - IMAGE_CACHE_SIZE));
        int end = Math.min(mSize, start + IMAGE_CACHE_SIZE);

        if (mActiveStart == start && mActiveEnd == end) return;

        mActiveStart = start;
        mActiveEnd = end;

        // 2. Update the data window
        start = Utils.clamp(mCurrentIndex - DATA_CACHE_SIZE / 2,
                0, Math.max(0, mSize - DATA_CACHE_SIZE));
        end = Math.min(mSize, start + DATA_CACHE_SIZE);
        if (mContentStart > mActiveStart || mContentEnd < mActiveEnd
                || Math.abs(start - mContentStart) > MIN_LOAD_COUNT) {
            for (int i = mContentStart; i < mContentEnd; ++i) {
                if (i < start || i >= end) {
                    mData[i % DATA_CACHE_SIZE] = null;
                }
            }
            mContentStart = start;
            mContentEnd = end;
            if (mReloadTask != null) mReloadTask.notifyDirty();
        }
    }

    private void updateImageRequests() {
        if (!mIsActive) return;

        int currentIndex = mCurrentIndex;
        MediaItem item = mData[currentIndex % DATA_CACHE_SIZE];
        if (item == null || item.getPath() != mItemPath) {
            // current item mismatch - don't request image
            return;
        }

        // 1. Find the most wanted request and start it (if not already started).
        Future<?> task = null;
        for (int i = 0; i < sImageFetchSeq.length; i++) {
            int offset = sImageFetchSeq[i].indexOffset;
            int bit = sImageFetchSeq[i].imageBit;
            task = startTaskIfNeeded(currentIndex + offset, bit);
            if (task != null) break;
        }

        // 2. Cancel everything else.
        for (ImageEntry entry : mImageCache.values()) {
            if (entry.screenNailTask != null && entry.screenNailTask != task) {
                entry.screenNailTask.cancel();
                entry.screenNailTask = null;
                entry.requestedBits &= ~BIT_SCREEN_NAIL;
            }
            if (entry.fullImageTask != null && entry.fullImageTask != task) {
                entry.fullImageTask.cancel();
                entry.fullImageTask = null;
                entry.requestedBits &= ~BIT_FULL_IMAGE;
            }
        }
    }

    private static class ScreenNailJob implements Job<Bitmap> {
        private MediaItem mItem;

        public ScreenNailJob(MediaItem item) {
            mItem = item;
        }

        @Override
        public Bitmap run(JobContext jc) {
            Bitmap bitmap = mItem.requestImage(MediaItem.TYPE_THUMBNAIL).run(jc);
            if (jc.isCancelled()) return null;
            if (bitmap != null) {
                bitmap = BitmapUtils.rotateBitmap(bitmap,
                    mItem.getRotation() - mItem.getFullImageRotation(), true);
            }
            return bitmap;
        }
    }

    // Returns the task if we started the task or the task is already started.
    private Future<?> startTaskIfNeeded(int index, int which) {
        if (index < mActiveStart || index >= mActiveEnd) return null;

        ImageEntry entry = mImageCache.get(getVersion(index));
        if (entry == null) return null;

        if (which == BIT_SCREEN_NAIL && entry.screenNailTask != null) {
            return entry.screenNailTask;
        } else if (which == BIT_FULL_IMAGE && entry.fullImageTask != null) {
            return entry.fullImageTask;
        }

        MediaItem item = mData[index % DATA_CACHE_SIZE];
        Utils.assertTrue(item != null);

        if (which == BIT_SCREEN_NAIL
                && (entry.requestedBits & BIT_SCREEN_NAIL) == 0) {
            entry.requestedBits |= BIT_SCREEN_NAIL;
            entry.screenNailTask = mThreadPool.submit(
                    new ScreenNailJob(item),
                    new ScreenNailListener(item.getDataVersion()));
            // request screen nail
            return entry.screenNailTask;
        }
        if (which == BIT_FULL_IMAGE
                && (entry.requestedBits & BIT_FULL_IMAGE) == 0
                && (item.getSupportedOperations()
                & MediaItem.SUPPORT_FULL_IMAGE) != 0) {
            entry.requestedBits |= BIT_FULL_IMAGE;
            entry.fullImageTask = mThreadPool.submit(
                    item.requestLargeImage(),
                    new FullImageListener(item.getDataVersion()));
            // request full image
            return entry.fullImageTask;
        }
        return null;
    }

    private void updateImageCache() {
        HashSet<Long> toBeRemoved = new HashSet<Long>(mImageCache.keySet());
        for (int i = mActiveStart; i < mActiveEnd; ++i) {
            MediaItem item = mData[i % DATA_CACHE_SIZE];
            long version = item == null
                    ? MediaObject.INVALID_DATA_VERSION
                    : item.getDataVersion();
            if (version == MediaObject.INVALID_DATA_VERSION) continue;
            ImageEntry entry = mImageCache.get(version);
            toBeRemoved.remove(version);
            if (entry != null) {
                if (Math.abs(i - mCurrentIndex) > 1) {
                    if (entry.fullImageTask != null) {
                        entry.fullImageTask.cancel();
                        entry.fullImageTask = null;
                    }
                    entry.fullImage = null;
                    entry.requestedBits &= ~BIT_FULL_IMAGE;
                }
            } else {
                entry = new ImageEntry();
                entry.rotation = item.getFullImageRotation();
                mImageCache.put(version, entry);
            }
        }

        // Clear the data and requests for ImageEntries outside the new window.
        for (Long version : toBeRemoved) {
            ImageEntry entry = mImageCache.remove(version);
            if (entry.fullImageTask != null) entry.fullImageTask.cancel();
            if (entry.screenNailTask != null) entry.screenNailTask.cancel();
        }
    }

    private class FullImageListener
            implements Runnable, FutureListener<BitmapRegionDecoder> {
        private final long mVersion;
        private Future<BitmapRegionDecoder> mFuture;

        public FullImageListener(long version) {
            mVersion = version;
        }

        @Override
        public void onFutureDone(Future<BitmapRegionDecoder> future) {
            mFuture = future;
            mMainHandler.sendMessage(
                    mMainHandler.obtainMessage(MSG_RUN_OBJECT, this));
        }

        @Override
        public void run() {
            updateFullImage(mVersion, mFuture);
        }
    }

    private class ScreenNailListener
            implements Runnable, FutureListener<Bitmap> {
        private final long mVersion;
        private Future<Bitmap> mFuture;

        public ScreenNailListener(long version) {
            mVersion = version;
        }

        @Override
        public void onFutureDone(Future<Bitmap> future) {
            mFuture = future;
            mMainHandler.sendMessage(
                    mMainHandler.obtainMessage(MSG_RUN_OBJECT, this));
        }

        @Override
        public void run() {
            updateScreenNail(mVersion, mFuture);
        }
    }

    private static class ImageEntry {
        public int requestedBits = 0;
        public int rotation;
        public BitmapRegionDecoder fullImage;
        public Bitmap screenNail;
        public Future<Bitmap> screenNailTask;
        public Future<BitmapRegionDecoder> fullImageTask;
        public boolean failToLoad = false;
    }

    private class SourceListener implements ContentListener {
        public void onContentDirty() {
            if (mReloadTask != null) mReloadTask.notifyDirty();
        }
    }

    private <T> T executeAndWait(Callable<T> callable) {
        FutureTask<T> task = new FutureTask<T>(callable);
        mMainHandler.sendMessage(
                mMainHandler.obtainMessage(MSG_RUN_OBJECT, task));
        try {
            return task.get();
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static class UpdateInfo {
        public long version;
        public boolean reloadContent;
        public Path target;
        public int indexHint;
        public int contentStart;
        public int contentEnd;

        public int size;
        public ArrayList<MediaItem> items;
    }

    private class GetUpdateInfo implements Callable<UpdateInfo> {

        private boolean needContentReload() {
            for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
                if (mData[i % DATA_CACHE_SIZE] == null) return true;
            }
            MediaItem current = mData[mCurrentIndex % DATA_CACHE_SIZE];
            return current == null || current.getPath() != mItemPath;
        }

        @Override
        public UpdateInfo call() throws Exception {
            // TODO: Try to load some data in first update
            UpdateInfo info = new UpdateInfo();
            info.version = mSourceVersion;
            info.reloadContent = needContentReload();
            info.target = mItemPath;
            info.indexHint = mCurrentIndex;
            info.contentStart = mContentStart;
            info.contentEnd = mContentEnd;
            info.size = mSize;
            return info;
        }
    }

    private class UpdateContent implements Callable<Void> {
        UpdateInfo mUpdateInfo;

        public UpdateContent(UpdateInfo updateInfo) {
            mUpdateInfo = updateInfo;
        }

        @Override
        public Void call() throws Exception {
            UpdateInfo info = mUpdateInfo;
            mSourceVersion = info.version;

            if (info.size != mSize) {
                mSize = info.size;
                if (mContentEnd > mSize) mContentEnd = mSize;
                if (mActiveEnd > mSize) mActiveEnd = mSize;
            }

            if (info.indexHint == MediaSet.INDEX_NOT_FOUND) {
                // The image has been deleted, clear mItemPath, the
                // mCurrentIndex will be updated in the updateCurrentItem().
                mItemPath = null;
                updateCurrentItem();
            } else {
                mCurrentIndex = info.indexHint;
            }

            updateSlidingWindow();

            if (info.items != null) {
                int start = Math.max(info.contentStart, mContentStart);
                int end = Math.min(info.contentStart + info.items.size(), mContentEnd);
                int dataIndex = start % DATA_CACHE_SIZE;
                for (int i = start; i < end; ++i) {
                    mData[dataIndex] = info.items.get(i - info.contentStart);
                    if (++dataIndex == DATA_CACHE_SIZE) dataIndex = 0;
                }
            }
            if (mItemPath == null) {
                MediaItem current = mData[mCurrentIndex % DATA_CACHE_SIZE];
                mItemPath = current == null ? null : current.getPath();
            }
            updateImageCache();
            updateTileProvider();
            updateImageRequests();
            fireModelInvalidated();
            return null;
        }

        private void updateCurrentItem() {
            if (mSize == 0) return;
            if (mCurrentIndex >= mSize) {
                mCurrentIndex = mSize - 1;
                mPhotoView.notifyOnNewImage();
                mPhotoView.startSlideInAnimation(PhotoView.TRANS_SLIDE_IN_LEFT);
            } else {
                mPhotoView.notifyOnNewImage();
                mPhotoView.startSlideInAnimation(PhotoView.TRANS_SLIDE_IN_RIGHT);
            }
        }
    }

    private class ReloadTask extends Thread {
        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;

        private boolean mIsLoading = false;

        private void updateLoading(boolean loading) {
            if (mIsLoading == loading) return;
            mIsLoading = loading;
            mMainHandler.sendEmptyMessage(loading ? MSG_LOAD_START : MSG_LOAD_FINISH);
        }

        @Override
        public void run() {
            while (mActive) {
                synchronized (this) {
                    if (!mDirty && mActive) {
                        updateLoading(false);
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                mDirty = false;
                UpdateInfo info = executeAndWait(new GetUpdateInfo());
                synchronized (DataManager.LOCK) {
                    updateLoading(true);
                    long version = mSource.reload();
                    if (info.version != version) {
                        info.reloadContent = true;
                        info.size = mSource.getMediaItemCount();
                    }
                    if (!info.reloadContent) continue;
                    info.items =  mSource.getMediaItem(info.contentStart, info.contentEnd);
                    MediaItem item = findCurrentMediaItem(info);
                    if (item == null || item.getPath() != info.target) {
                        info.indexHint = findIndexOfTarget(info);
                    }
                }
                executeAndWait(new UpdateContent(info));
            }
        }

        public synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        public synchronized void terminate() {
            mActive = false;
            notifyAll();
        }

        private MediaItem findCurrentMediaItem(UpdateInfo info) {
            ArrayList<MediaItem> items = info.items;
            int index = info.indexHint - info.contentStart;
            return index < 0 || index >= items.size() ? null : items.get(index);
        }

        private int findIndexOfTarget(UpdateInfo info) {
            if (info.target == null) return info.indexHint;
            ArrayList<MediaItem> items = info.items;

            // First, try to find the item in the data just loaded
            if (items != null) {
                for (int i = 0, n = items.size(); i < n; ++i) {
                    if (items.get(i).getPath() == info.target) return i + info.contentStart;
                }
            }

            // Not found, find it in mSource.
            return mSource.getIndexOfItem(info.target, info.indexHint);
        }
    }
}
