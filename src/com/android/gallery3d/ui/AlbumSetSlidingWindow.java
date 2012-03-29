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

package com.android.gallery3d.ui;

import android.graphics.Bitmap;
import android.os.Message;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.ThreadPool;

public class AlbumSetSlidingWindow implements AlbumSetView.ModelListener {
    private static final String TAG = "AlbumSetSlidingWindow";
    private static final int MSG_UPDATE_ALBUM_ENTRY = 1;

    public static interface Listener {
        public void onSizeChanged(int size);
        public void onContentChanged();
    }

    private final AlbumSetView.Model mSource;
    private int mSize;

    private int mContentStart = 0;
    private int mContentEnd = 0;

    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    private Listener mListener;

    private final AlbumSetEntry mData[];
    private final SynchronizedHandler mHandler;
    private final ThreadPool mThreadPool;
    private final AlbumLabelMaker mLabelMaker;
    private final String mLoadingText;
    private final TextureUploader mTextureUploader;

    private int mActiveRequestCount = 0;
    private boolean mIsActive = false;
    private BitmapTexture mLoadingLabel;

    private int mSlotWidth;

    public static class AlbumSetEntry {
        public MediaSet album;
        public MediaItem coverItem;
        public Texture content;
        public Texture label;
        public Path setPath;
        public int sourceType;
        public int cacheFlag;
        public int cacheStatus;
        public int rotation;
        public int mediaType;
        public boolean isPanorama;
        public boolean isWaitLoadingDisplayed;
        public long setDataVersion;
        public long coverDataVersion;
        private BitmapLoader labelLoader;
        private BitmapLoader coverLoader;
    }

    public AlbumSetSlidingWindow(GalleryActivity activity,
            AlbumSetView.LabelSpec labelSpec, SelectionDrawer drawer,
            AlbumSetView.Model source, int cacheSize) {
        source.setModelListener(this);
        mSource = source;
        mData = new AlbumSetEntry[cacheSize];
        mSize = source.size();
        mThreadPool = activity.getThreadPool();

        mLabelMaker = new AlbumLabelMaker(activity.getAndroidContext(), labelSpec);
        mLoadingText = activity.getAndroidContext().getString(R.string.loading);
        mTextureUploader = new TextureUploader(activity.getGLRoot());

        mHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                Utils.assertTrue(message.what == MSG_UPDATE_ALBUM_ENTRY);
                ((EntryUpdater) message.obj).updateEntry();
            }
        };
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public AlbumSetEntry get(int slotIndex) {
        if (!isActiveSlot(slotIndex)) {
            Utils.fail("invalid slot: %s outsides (%s, %s)",
                    slotIndex, mActiveStart, mActiveEnd);
        }
        return mData[slotIndex % mData.length];
    }

    public int size() {
        return mSize;
    }

    public boolean isActiveSlot(int slotIndex) {
        return slotIndex >= mActiveStart && slotIndex < mActiveEnd;
    }

    private void setContentWindow(int contentStart, int contentEnd) {
        if (contentStart == mContentStart && contentEnd == mContentEnd) return;

        if (contentStart >= mContentEnd || mContentStart >= contentEnd) {
            for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
                freeSlotContent(i);
            }
            mSource.setActiveWindow(contentStart, contentEnd);
            for (int i = contentStart; i < contentEnd; ++i) {
                prepareSlotContent(i);
            }
        } else {
            for (int i = mContentStart; i < contentStart; ++i) {
                freeSlotContent(i);
            }
            for (int i = contentEnd, n = mContentEnd; i < n; ++i) {
                freeSlotContent(i);
            }
            mSource.setActiveWindow(contentStart, contentEnd);
            for (int i = contentStart, n = mContentStart; i < n; ++i) {
                prepareSlotContent(i);
            }
            for (int i = mContentEnd; i < contentEnd; ++i) {
                prepareSlotContent(i);
            }
        }

        mContentStart = contentStart;
        mContentEnd = contentEnd;
    }

    public void setActiveWindow(int start, int end) {
        if (!(start <= end && end - start <= mData.length && end <= mSize)) {
            Utils.fail("start = %s, end = %s, length = %s, size = %s",
                    start, end, mData.length, mSize);
        }

        AlbumSetEntry data[] = mData;
        mActiveStart = start;
        mActiveEnd = end;
        int contentStart = Utils.clamp((start + end) / 2 - data.length / 2,
                0, Math.max(0, mSize - data.length));
        int contentEnd = Math.min(contentStart + data.length, mSize);
        setContentWindow(contentStart, contentEnd);

        if (mIsActive) {
            updateTextureUploadQueue();
            updateAllImageRequests();
        }
    }

    // We would like to request non active slots in the following order:
    // Order:    8 6 4 2                   1 3 5 7
    //         |---------|---------------|---------|
    //                   |<-  active  ->|
    //         |<-------- cached range ----------->|
    private void requestNonactiveImages() {
        int range = Math.max(
                mContentEnd - mActiveEnd, mActiveStart - mContentStart);
        for (int i = 0 ;i < range; ++i) {
            requestImagesInSlot(mActiveEnd + i);
            requestImagesInSlot(mActiveStart - 1 - i);
        }
    }

    private void cancelNonactiveImages() {
        int range = Math.max(
                mContentEnd - mActiveEnd, mActiveStart - mContentStart);
        for (int i = 0 ;i < range; ++i) {
            cancelImagesInSlot(mActiveEnd + i);
            cancelImagesInSlot(mActiveStart - 1 - i);
        }
    }

    private void requestImagesInSlot(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd) return;
        AlbumSetEntry entry = mData[slotIndex % mData.length];
        if (entry.coverLoader != null) entry.coverLoader.startLoad();
        if (entry.labelLoader != null) entry.labelLoader.startLoad();
    }

    private void cancelImagesInSlot(int slotIndex) {
        if (slotIndex < mContentStart || slotIndex >= mContentEnd) return;
        AlbumSetEntry entry = mData[slotIndex % mData.length];
        if (entry.coverLoader != null) entry.coverLoader.cancelLoad();
        if (entry.labelLoader != null) entry.labelLoader.cancelLoad();
    }

    private static long getDataVersion(MediaObject object) {
        return object == null
                ? MediaSet.INVALID_DATA_VERSION
                : object.getDataVersion();
    }

    private void freeSlotContent(int slotIndex) {
        AlbumSetEntry entry = mData[slotIndex % mData.length];
        if (entry.coverLoader != null) entry.coverLoader.recycle();
        if (entry.labelLoader != null) entry.labelLoader.recycle();
        mData[slotIndex % mData.length] = null;
    }

    private void updateAlbumSetEntry(AlbumSetEntry entry,
            int slotIndex, MediaSet album, MediaItem cover) {
        entry.album = album;
        entry.setDataVersion = getDataVersion(album);
        entry.sourceType = identifySourceType(album);
        entry.cacheFlag = identifyCacheFlag(album);
        entry.cacheStatus = identifyCacheStatus(album);
        entry.setPath = (album == null) ? null : album.getPath();

        if (entry.labelLoader != null) {
            entry.labelLoader.recycle();
            entry.labelLoader = null;
            entry.label = null;
        }
        if (album != null) {
            entry.labelLoader =
                    new AlbumLabelLoader(slotIndex, album, entry.sourceType);
        }

        entry.coverItem = cover;
        if (getDataVersion(cover) != entry.coverDataVersion) {
            entry.coverDataVersion = getDataVersion(cover);
            entry.isPanorama = GalleryUtils.isPanorama(cover);
            entry.rotation = (cover == null) ? 0 : cover.getRotation();
            entry.mediaType = (cover == null) ? 0 : cover.getMediaType();
            if (entry.coverLoader != null) {
                entry.coverLoader.recycle();
                entry.coverLoader = null;
                entry.content = null;
            }
            if (cover != null) {
                entry.coverLoader = new AlbumCoverLoader(slotIndex, cover);
            }
        }
    }

    private void prepareSlotContent(int slotIndex) {
        MediaSet set = mSource.getMediaSet(slotIndex);
        MediaItem coverItem = mSource.getCoverItem(slotIndex);
        AlbumSetEntry entry = new AlbumSetEntry();
        updateAlbumSetEntry(entry, slotIndex, set, coverItem);
        mData[slotIndex % mData.length] = entry;
    }

    private static boolean startLoadBitmap(BitmapLoader loader) {
        if (loader == null) return false;
        loader.startLoad();
        return loader.isRequestInProgress();
    }

    private void queueTextureForUpload(boolean isActive, Texture texture) {
        if ((texture == null) || !(texture instanceof BitmapTexture)) return;
        if (isActive) {
            mTextureUploader.addFgTexture((BitmapTexture) texture);
        } else {
            mTextureUploader.addBgTexture((BitmapTexture) texture);
        }
    }

    private void updateTextureUploadQueue() {
        if (!mIsActive) return;
        mTextureUploader.clear();
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            AlbumSetEntry entry = mData[i % mData.length];
            boolean isActive = isActiveSlot(i);
            queueTextureForUpload(isActive, entry.label);
            queueTextureForUpload(isActive, entry.content);
        }
    }

    private void updateAllImageRequests() {
        mActiveRequestCount = 0;
        for (int i = mActiveStart, n = mActiveEnd; i < n; ++i) {
            AlbumSetEntry entry = mData[i % mData.length];
            if (startLoadBitmap(entry.coverLoader)) ++mActiveRequestCount;
            if (startLoadBitmap(entry.labelLoader)) ++mActiveRequestCount;
        }
        if (mActiveRequestCount == 0) {
            requestNonactiveImages();
        } else {
            cancelNonactiveImages();
        }
    }

    @Override
    public void onSizeChanged(int size) {
        if (mIsActive && mSize != size) {
            mSize = size;
            if (mListener != null) mListener.onSizeChanged(mSize);
        }
    }

    @Override
    public void onWindowContentChanged(int index) {
        if (!mIsActive) {
            // paused, ignore slot changed event
            return;
        }

        // If the updated content is not cached, ignore it
        if (index < mContentStart || index >= mContentEnd) {
            Log.w(TAG, String.format(
                    "invalid update: %s is outside (%s, %s)",
                    index, mContentStart, mContentEnd) );
            return;
        }

        AlbumSetEntry entry = mData[index % mData.length];
        MediaSet set = mSource.getMediaSet(index);
        MediaItem coverItem = mSource.getCoverItem(index);
        updateAlbumSetEntry(entry, index, set, coverItem);
        updateAllImageRequests();
        updateTextureUploadQueue();
        if (mListener != null && isActiveSlot(index)) {
            mListener.onContentChanged();
        }
    }

    public BitmapTexture getLoadingTexture() {
        if (mLoadingLabel == null) {
            Bitmap bitmap = mLabelMaker.requestLabel(mLoadingText, null,
                    SelectionDrawer.DATASOURCE_TYPE_NOT_CATEGORIZED)
                    .run(ThreadPool.JOB_CONTEXT_STUB);
            mLoadingLabel = new BitmapTexture(bitmap);
            mLoadingLabel.setOpaque(false);
        }
        return mLoadingLabel;
    }

    public void pause() {
        mIsActive = false;
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            freeSlotContent(i);
        }
        mLabelMaker.clearRecycledLabels();
    }

    public void resume() {
        mIsActive = true;
        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            prepareSlotContent(i);
        }
        updateAllImageRequests();
    }

    private static interface EntryUpdater {
        public void updateEntry();
    }

    private class AlbumCoverLoader extends BitmapLoader implements EntryUpdater {
        private MediaItem mMediaItem;
        private final int mSlotIndex;

        public AlbumCoverLoader(int slotIndex, MediaItem item) {
            mSlotIndex = slotIndex;
            mMediaItem = item;
        }

        @Override
        protected void recycleBitmap(Bitmap bitmap) {
            MediaItem.getMicroThumbPool().recycle(bitmap);
        }

        @Override
        protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
            return mThreadPool.submit(mMediaItem.requestImage(
                    MediaItem.TYPE_MICROTHUMBNAIL), l);
        }

        @Override
        protected void onLoadComplete(Bitmap bitmap) {
            mHandler.obtainMessage(MSG_UPDATE_ALBUM_ENTRY, this).sendToTarget();
        }

        @Override
        public void updateEntry() {
            Bitmap bitmap = getBitmap();
            if (bitmap == null) return; // error or recycled

            AlbumSetEntry entry = mData[mSlotIndex % mData.length];
            BitmapTexture texture = new BitmapTexture(bitmap);
            entry.content = texture;

            if (isActiveSlot(mSlotIndex)) {
                mTextureUploader.addFgTexture(texture);
                --mActiveRequestCount;
                if (mActiveRequestCount == 0) requestNonactiveImages();
                if (mListener != null) mListener.onContentChanged();
            } else {
                mTextureUploader.addBgTexture(texture);
            }
        }
    }

    private static int identifySourceType(MediaSet set) {
        if (set == null) {
            return SelectionDrawer.DATASOURCE_TYPE_NOT_CATEGORIZED;
        }

        Path path = set.getPath();
        if (MediaSetUtils.isCameraSource(path)) {
            return SelectionDrawer.DATASOURCE_TYPE_CAMERA;
        }

        int type = SelectionDrawer.DATASOURCE_TYPE_NOT_CATEGORIZED;
        String prefix = path.getPrefix();

        if (prefix.equals("picasa")) {
            type = SelectionDrawer.DATASOURCE_TYPE_PICASA;
        } else if (prefix.equals("local") || prefix.equals("merge")) {
            type = SelectionDrawer.DATASOURCE_TYPE_LOCAL;
        } else if (prefix.equals("mtp")) {
            type = SelectionDrawer.DATASOURCE_TYPE_MTP;
        }

        return type;
    }

    private static int identifyCacheFlag(MediaSet set) {
        if (set == null || (set.getSupportedOperations()
                & MediaSet.SUPPORT_CACHE) == 0) {
            return MediaSet.CACHE_FLAG_NO;
        }

        return set.getCacheFlag();
    }

    private static int identifyCacheStatus(MediaSet set) {
        if (set == null || (set.getSupportedOperations()
                & MediaSet.SUPPORT_CACHE) == 0) {
            return MediaSet.CACHE_STATUS_NOT_CACHED;
        }

        return set.getCacheStatus();
    }

    private class AlbumLabelLoader extends BitmapLoader implements EntryUpdater {
        private final MediaSet mMediaSet;
        private final int mSlotIndex;
        private final int mSourceType;

        public AlbumLabelLoader(
                int slotIndex, MediaSet mediaSet, int sourceType) {
            mSlotIndex = slotIndex;
            mMediaSet = mediaSet;
            mSourceType = sourceType;
        }

        @Override
        protected Future<Bitmap> submitBitmapTask(FutureListener<Bitmap> l) {
            return mThreadPool.submit(mLabelMaker.requestLabel(
                    mMediaSet, mSourceType), l);
        }

        @Override
        protected void recycleBitmap(Bitmap bitmap) {
            mLabelMaker.reycleLabel(bitmap);
        }

        @Override
        protected void onLoadComplete(Bitmap bitmap) {
            mHandler.obtainMessage(MSG_UPDATE_ALBUM_ENTRY, this).sendToTarget();
        }

        @Override
        public void updateEntry() {
            Bitmap bitmap = getBitmap();
            if (bitmap == null) return; // Error or recycled

            AlbumSetEntry entry = mData[mSlotIndex % mData.length];
            BitmapTexture texture = new BitmapTexture(bitmap);
            texture.setOpaque(false);
            entry.label = texture;

            if (isActiveSlot(mSlotIndex)) {
                mTextureUploader.addFgTexture(texture);
                --mActiveRequestCount;
                if (mActiveRequestCount == 0) requestNonactiveImages();
                if (mListener != null) mListener.onContentChanged();
            } else {
                mTextureUploader.addBgTexture(texture);
            }
        }
    }

    public void onSlotSizeChanged(int width, int height) {
        if (mSlotWidth == width) return;

        mSlotWidth = width;
        mLoadingLabel = null;
        mLabelMaker.setLabelWidth(mSlotWidth);

        if (!mIsActive) return;

        for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
            AlbumSetEntry entry = mData[i % mData.length];
            if (entry.labelLoader != null) {
                entry.labelLoader.recycle();
                entry.labelLoader = null;
                entry.label = null;
            }
            entry.labelLoader = (entry.album == null)
                    ? null
                    : new AlbumLabelLoader(i, entry.album, entry.sourceType);
        }
        updateAllImageRequests();
        updateTextureUploadQueue();
    }
}
