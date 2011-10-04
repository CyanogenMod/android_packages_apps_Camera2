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

import android.os.Handler;
import android.os.Message;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.ui.AlbumSetView;
import com.android.gallery3d.ui.SynchronizedHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class AlbumSetDataAdapter implements AlbumSetView.Model {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumSetDataAdapter";

    private static final int INDEX_NONE = -1;

    private static final int MIN_LOAD_COUNT = 4;
    private static final int MAX_COVER_COUNT = 1;

    private static final int MSG_LOAD_START = 1;
    private static final int MSG_LOAD_FINISH = 2;
    private static final int MSG_RUN_OBJECT = 3;

    private static final MediaItem[] EMPTY_MEDIA_ITEMS = new MediaItem[0];

    private final MediaSet[] mData;
    private final MediaItem[][] mCoverData;
    private final long[] mItemVersion;
    private final long[] mSetVersion;

    private int mActiveStart = 0;
    private int mActiveEnd = 0;

    private int mContentStart = 0;
    private int mContentEnd = 0;

    private final MediaSet mSource;
    private long mSourceVersion = MediaObject.INVALID_DATA_VERSION;
    private int mSize;

    private AlbumSetView.ModelListener mModelListener;
    private LoadingListener mLoadingListener;
    private ReloadTask mReloadTask;

    private final Handler mMainHandler;

    private final MySourceListener mSourceListener = new MySourceListener();

    public AlbumSetDataAdapter(GalleryActivity activity, MediaSet albumSet, int cacheSize) {
        mSource = Utils.checkNotNull(albumSet);
        mCoverData = new MediaItem[cacheSize][];
        mData = new MediaSet[cacheSize];
        mItemVersion = new long[cacheSize];
        mSetVersion = new long[cacheSize];
        Arrays.fill(mItemVersion, MediaObject.INVALID_DATA_VERSION);
        Arrays.fill(mSetVersion, MediaObject.INVALID_DATA_VERSION);

        mMainHandler = new SynchronizedHandler(activity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_RUN_OBJECT:
                        ((Runnable) message.obj).run();
                        return;
                    case MSG_LOAD_START:
                        if (mLoadingListener != null) mLoadingListener.onLoadingStarted();
                        return;
                    case MSG_LOAD_FINISH:
                        if (mLoadingListener != null) mLoadingListener.onLoadingFinished();
                        return;
                }
            }
        };
    }

    public void pause() {
        mReloadTask.terminate();
        mReloadTask = null;
        mSource.removeContentListener(mSourceListener);
    }

    public void resume() {
        mSource.addContentListener(mSourceListener);
        mReloadTask = new ReloadTask();
        mReloadTask.start();
    }

    public MediaSet getMediaSet(int index) {
        if (index < mActiveStart && index >= mActiveEnd) {
            throw new IllegalArgumentException(String.format(
                    "%s not in (%s, %s)", index, mActiveStart, mActiveEnd));
        }
        return mData[index % mData.length];
    }

    public MediaItem[] getCoverItems(int index) {
        if (index < mActiveStart && index >= mActiveEnd) {
            throw new IllegalArgumentException(String.format(
                    "%s not in (%s, %s)", index, mActiveStart, mActiveEnd));
        }
        MediaItem[] result = mCoverData[index % mCoverData.length];

        // If the result is not ready yet, return an empty array
        return result == null ? EMPTY_MEDIA_ITEMS : result;
    }

    public int getActiveStart() {
        return mActiveStart;
    }

    public int getActiveEnd() {
        return mActiveEnd;
    }

    public boolean isActive(int index) {
        return index >= mActiveStart && index < mActiveEnd;
    }

    public int size() {
        return mSize;
    }

    private void clearSlot(int slotIndex) {
        mData[slotIndex] = null;
        mCoverData[slotIndex] = null;
        mItemVersion[slotIndex] = MediaObject.INVALID_DATA_VERSION;
        mSetVersion[slotIndex] = MediaObject.INVALID_DATA_VERSION;
    }

    private void setContentWindow(int contentStart, int contentEnd) {
        if (contentStart == mContentStart && contentEnd == mContentEnd) return;
        MediaItem[][] data = mCoverData;
        int length = data.length;

        int start = this.mContentStart;
        int end = this.mContentEnd;

        mContentStart = contentStart;
        mContentEnd = contentEnd;

        if (contentStart >= end || start >= contentEnd) {
            for (int i = start, n = end; i < n; ++i) {
                clearSlot(i % length);
            }
        } else {
            for (int i = start; i < contentStart; ++i) {
                clearSlot(i % length);
            }
            for (int i = contentEnd, n = end; i < n; ++i) {
                clearSlot(i % length);
            }
        }
        mReloadTask.notifyDirty();
    }

    public void setActiveWindow(int start, int end) {
        if (start == mActiveStart && end == mActiveEnd) return;

        Utils.assertTrue(start <= end
                && end - start <= mCoverData.length && end <= mSize);

        mActiveStart = start;
        mActiveEnd = end;

        int length = mCoverData.length;
        // If no data is visible, keep the cache content
        if (start == end) return;

        int contentStart = Utils.clamp((start + end) / 2 - length / 2,
                0, Math.max(0, mSize - length));
        int contentEnd = Math.min(contentStart + length, mSize);
        if (mContentStart > start || mContentEnd < end
                || Math.abs(contentStart - mContentStart) > MIN_LOAD_COUNT) {
            setContentWindow(contentStart, contentEnd);
        }
    }

    private class MySourceListener implements ContentListener {
        public void onContentDirty() {
            mReloadTask.notifyDirty();
        }
    }

    public void setModelListener(AlbumSetView.ModelListener listener) {
        mModelListener = listener;
    }

    public void setLoadingListener(LoadingListener listener) {
        mLoadingListener = listener;
    }

    private static void getRepresentativeItems(MediaSet set, int wanted,
            ArrayList<MediaItem> result) {
        if (set.getMediaItemCount() > 0) {
            result.addAll(set.getMediaItem(0, wanted));
        }

        int n = set.getSubMediaSetCount();
        for (int i = 0; i < n && wanted > result.size(); i++) {
            MediaSet subset = set.getSubMediaSet(i);
            double perSet = (double) (wanted - result.size()) / (n - i);
            int m = (int) Math.ceil(perSet);
            getRepresentativeItems(subset, m, result);
        }
    }

    private static class UpdateInfo {
        public long version;
        public int index;

        public int size;
        public MediaSet item;
        public MediaItem covers[];
    }

    private class GetUpdateInfo implements Callable<UpdateInfo> {

        private final long mVersion;

        public GetUpdateInfo(long version) {
            mVersion = version;
        }

        private int getInvalidIndex(long version) {
            long setVersion[] = mSetVersion;
            int length = setVersion.length;
            for (int i = mContentStart, n = mContentEnd; i < n; ++i) {
                int index = i % length;
                if (setVersion[i % length] != version) return i;
            }
            return INDEX_NONE;
        }

        @Override
        public UpdateInfo call() throws Exception {
            int index = getInvalidIndex(mVersion);
            if (index == INDEX_NONE && mSourceVersion == mVersion) return null;
            UpdateInfo info = new UpdateInfo();
            info.version = mSourceVersion;
            info.index = index;
            info.size = mSize;
            return info;
        }
    }

    private class UpdateContent implements Callable<Void> {
        private final UpdateInfo mUpdateInfo;

        public UpdateContent(UpdateInfo info) {
            mUpdateInfo = info;
        }

        public Void call() {
            // Avoid notifying listeners of status change after pause
            // Otherwise gallery will be in inconsistent state after resume.
            if (mReloadTask == null) return null;
            UpdateInfo info = mUpdateInfo;
            mSourceVersion = info.version;
            if (mSize != info.size) {
                mSize = info.size;
                if (mModelListener != null) mModelListener.onSizeChanged(mSize);
                if (mContentEnd > mSize) mContentEnd = mSize;
                if (mActiveEnd > mSize) mActiveEnd = mSize;
            }
            // Note: info.index could be INDEX_NONE, i.e., -1
            if (info.index >= mContentStart && info.index < mContentEnd) {
                int pos = info.index % mCoverData.length;
                mSetVersion[pos] = info.version;
                long itemVersion = info.item.getDataVersion();
                if (mItemVersion[pos] == itemVersion) return null;
                mItemVersion[pos] = itemVersion;
                mData[pos] = info.item;
                mCoverData[pos] = info.covers;
                if (mModelListener != null
                        && info.index >= mActiveStart && info.index < mActiveEnd) {
                    mModelListener.onWindowContentChanged(info.index);
                }
            }
            return null;
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

    // TODO: load active range first
    private class ReloadTask extends Thread {
        private volatile boolean mActive = true;
        private volatile boolean mDirty = true;
        private volatile boolean mIsLoading = false;

        private void updateLoading(boolean loading) {
            if (mIsLoading == loading) return;
            mIsLoading = loading;
            mMainHandler.sendEmptyMessage(loading ? MSG_LOAD_START : MSG_LOAD_FINISH);
        }

        @Override
        public void run() {
            boolean updateComplete = false;
            while (mActive) {
                synchronized (this) {
                    if (mActive && !mDirty && updateComplete) {
                        updateLoading(false);
                        Utils.waitWithoutInterrupt(this);
                        continue;
                    }
                }
                mDirty = false;
                updateLoading(true);

                long version;
                synchronized (DataManager.LOCK) {
                    version = mSource.reload();
                }
                UpdateInfo info = executeAndWait(new GetUpdateInfo(version));
                updateComplete = info == null;
                if (updateComplete) continue;

                synchronized (DataManager.LOCK) {
                    if (info.version != version) {
                        info.version = version;
                        info.size = mSource.getSubMediaSetCount();

                        // If the size becomes smaller after reload(), we may
                        // receive from GetUpdateInfo an index which is too
                        // big. Because the main thread is not aware of the size
                        // change until we call UpdateContent.
                        if (info.index >= info.size) {
                            info.index = INDEX_NONE;
                        }
                    }
                    if (info.index != INDEX_NONE) {
                        info.item = mSource.getSubMediaSet(info.index);
                        if (info.item == null) continue;
                        ArrayList<MediaItem> covers = new ArrayList<MediaItem>();
                        getRepresentativeItems(info.item, MAX_COVER_COUNT, covers);
                        info.covers = covers.toArray(new MediaItem[covers.size()]);
                    }
                }
                executeAndWait(new UpdateContent(info));
            }
            updateLoading(false);
        }

        public synchronized void notifyDirty() {
            mDirty = true;
            notifyAll();
        }

        public synchronized void terminate() {
            mActive = false;
            notifyAll();
        }
    }
}


