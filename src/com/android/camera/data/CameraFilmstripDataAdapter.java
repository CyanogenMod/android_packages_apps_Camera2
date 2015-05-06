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

package com.android.camera.data;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;

import com.android.camera.Storage;
import com.android.camera.data.FilmstripItem.VideoClickedCallback;
import com.android.camera.debug.Log;
import com.android.camera.util.Callback;
import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * A {@link LocalFilmstripDataAdapter} that provides data in the camera folder.
 */
public class CameraFilmstripDataAdapter implements LocalFilmstripDataAdapter {
    private static final Log.Tag TAG = new Log.Tag("CameraDataAdapter");

    private static final int DEFAULT_DECODE_SIZE = 1600;

    private final Context mContext;
    private final PhotoItemFactory mPhotoItemFactory;
    private final VideoItemFactory mVideoItemFactory;

    private FilmstripItemList mFilmstripItems;


    private Listener mListener;
    private FilmstripItemListener mFilmstripItemListener;

    private int mSuggestedWidth = DEFAULT_DECODE_SIZE;
    private int mSuggestedHeight = DEFAULT_DECODE_SIZE;
    private long mLastPhotoId = FilmstripItemBase.QUERY_ALL_MEDIA_ID;

    private FilmstripItem mFilmstripItemToDelete;

    public CameraFilmstripDataAdapter(Context context,
            PhotoItemFactory photoItemFactory, VideoItemFactory videoItemFactory) {
        mContext = context;
        mFilmstripItems = new FilmstripItemList();
        mPhotoItemFactory = photoItemFactory;
        mVideoItemFactory = videoItemFactory;
    }

    @Override
    public void setLocalDataListener(FilmstripItemListener listener) {
        mFilmstripItemListener = listener;
    }

    @Override
    public void requestLoadNewPhotos() {
        LoadNewPhotosTask ltask = new LoadNewPhotosTask(mContext, mLastPhotoId);
        ltask.execute(mContext.getContentResolver());
    }

    @Override
    public void requestLoad(Callback<Void> onDone) {
        QueryTask qtask = new QueryTask(onDone);
        qtask.execute(mContext);
    }

    @Override
    public AsyncTask updateMetadataAt(int index) {
        return updateMetadataAt(index, false);
    }

    private AsyncTask updateMetadataAt(int index, boolean forceItemUpdate) {
        MetadataUpdateTask result = new MetadataUpdateTask(forceItemUpdate);
        result.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, index);
        return result;
    }

    @Override
    public boolean isMetadataUpdatedAt(int index) {
        if (index < 0 || index >= mFilmstripItems.size()) {
            return true;
        }
        return mFilmstripItems.get(index).getMetadata().isLoaded();
    }

    @Override
    public int getItemViewType(int index) {
        if (index < 0 || index >= mFilmstripItems.size()) {
            return -1;
        }

        return mFilmstripItems.get(index).getItemViewType().ordinal();
    }

    @Override
    public FilmstripItem getItemAt(int index) {
        if (index < 0 || index >= mFilmstripItems.size()) {
            return null;
        }
        return mFilmstripItems.get(index);
    }

    @Override
    public int getTotalNumber() {
        return mFilmstripItems.size();
    }

    @Override
    public FilmstripItem getFilmstripItemAt(int index) {
        return getItemAt(index);
    }

    @Override
    public void suggestViewSizeBound(int w, int h) {
        mSuggestedWidth = w;
        mSuggestedHeight = h;
    }

    @Override
    public View getView(View recycled, int index,
            VideoClickedCallback videoClickedCallback) {
        if (index >= mFilmstripItems.size() || index < 0) {
            return null;
        }

        FilmstripItem item = mFilmstripItems.get(index);
        item.setSuggestedSize(mSuggestedWidth, mSuggestedHeight);

        return item.getView(Optional.fromNullable(recycled), this, /* inProgress */ false,
              videoClickedCallback);
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
        if (mFilmstripItems.size() != 0) {
            mListener.onFilmstripItemLoaded();
        }
    }

    @Override
    public void removeAt(int index) {
        FilmstripItem d = mFilmstripItems.remove(index);
        if (d == null) {
            return;
        }

        // Delete previously removed data first.
        executeDeletion();
        mFilmstripItemToDelete = d;
        mListener.onFilmstripItemRemoved(index, d);
    }

    @Override
    public boolean addOrUpdate(FilmstripItem item) {
        final Uri uri = item.getData().getUri();
        int pos = findByContentUri(uri);
        if (pos != -1) {
            // a duplicate one, just do a substitute.
            Log.v(TAG, "found duplicate data: " + uri);
            updateItemAt(pos, item);
            return false;
        } else {
            // a new data.
            insertItem(item);
            return true;
        }
    }

    @Override
    public int findByContentUri(Uri uri) {
        // LocalDataList will return in O(1) if the uri is not contained.
        // Otherwise the performance is O(n), but this is acceptable as we will
        // most often call this to find an element at the beginning of the list.
        return mFilmstripItems.indexOf(uri);
    }

    @Override
    public boolean undoDeletion() {
        if (mFilmstripItemToDelete == null) {
            return false;
        }
        FilmstripItem d = mFilmstripItemToDelete;
        mFilmstripItemToDelete = null;
        insertItem(d);
        return true;
    }

    @Override
    public boolean executeDeletion() {
        if (mFilmstripItemToDelete == null) {
            return false;
        }

        DeletionTask task = new DeletionTask();
        task.execute(mFilmstripItemToDelete);
        mFilmstripItemToDelete = null;
        return true;
    }

    @Override
    public void clear() {
        replaceItemList(new FilmstripItemList());
    }

    @Override
    public void refresh(Uri uri) {
        final int pos = findByContentUri(uri);
        if (pos == -1) {
            return;
        }

        FilmstripItem data = mFilmstripItems.get(pos);
        FilmstripItem refreshedData = data.refresh();

        // Refresh failed. Probably removed already.
        if (refreshedData == null && mListener != null) {
            mListener.onFilmstripItemRemoved(pos, data);
            return;
        }
        updateItemAt(pos, refreshedData);
    }

    @Override
    public void updateItemAt(final int pos, FilmstripItem item) {
        mFilmstripItems.set(pos, item);
        updateMetadataAt(pos, true /* forceItemUpdate */);
    }

    private void insertItem(FilmstripItem item) {
        // Since this function is mostly for adding the newest data,
        // a simple linear search should yield the best performance over a
        // binary search.
        int pos = 0;
        Comparator<FilmstripItem> comp = new NewestFirstComparator(
                new Date());
        for (; pos < mFilmstripItems.size()
                && comp.compare(item, mFilmstripItems.get(pos)) > 0; pos++) {
        }
        mFilmstripItems.add(pos, item);
        if (mListener != null) {
            mListener.onFilmstripItemInserted(pos, item);
        }
    }

    /** Update all the data */
    private void replaceItemList(FilmstripItemList list) {
        if (list.size() == 0 && mFilmstripItems.size() == 0) {
            return;
        }
        mFilmstripItems = list;
        if (mListener != null) {
            mListener.onFilmstripItemLoaded();
        }
    }

    @Override
    public List<AsyncTask> preloadItems(List<Integer> items) {
        List<AsyncTask> result = new ArrayList<>();
        for (Integer id : items) {
            if (!isMetadataUpdatedAt(id)) {
                result.add(updateMetadataAt(id));
            }
        }
        return result;
    }

    @Override
    public void cancelItems(List<AsyncTask> loadTokens) {
        for (AsyncTask asyncTask : loadTokens) {
            if (asyncTask != null) {
                asyncTask.cancel(false);
            }
        }
    }

    @Override
    public List<Integer> getItemsInRange(int startPosition, int endPosition) {
        List<Integer> result = new ArrayList<>();
        for (int i = Math.max(0, startPosition); i < endPosition; i++) {
            result.add(i);
        }
        return result;
    }

    @Override
    public int getCount() {
        return getTotalNumber();
    }

    private class LoadNewPhotosTask extends AsyncTask<ContentResolver, Void, List<PhotoItem>> {

        private final long mMinPhotoId;
        private final Context mContext;

        public LoadNewPhotosTask(Context context, long lastPhotoId) {
            mContext = context;
            mMinPhotoId = lastPhotoId;
        }

        /**
         * Loads any new photos added to our storage directory since our last query.
         * @param contentResolvers {@link android.content.ContentResolver} to load data.
         * @return An {@link java.util.ArrayList} containing any new data.
         */
        @Override
        protected List<PhotoItem> doInBackground(ContentResolver... contentResolvers) {
            if (mMinPhotoId != FilmstripItemBase.QUERY_ALL_MEDIA_ID) {
                Log.v(TAG, "updating media metadata with photos newer than id: " + mMinPhotoId);
                final ContentResolver cr = contentResolvers[0];
                return mPhotoItemFactory.queryAll(PhotoDataQuery.CONTENT_URI, mMinPhotoId);
            }
            return new ArrayList<>(0);
        }

        @Override
        protected void onPostExecute(List<PhotoItem> newPhotoData) {
            if (newPhotoData == null) {
                Log.w(TAG, "null data returned from new photos query");
                return;
            }
            Log.v(TAG, "new photos query return num items: " + newPhotoData.size());
            if (!newPhotoData.isEmpty()) {
                FilmstripItem newestPhoto = newPhotoData.get(0);
                // We may overlap with another load task or a query task, in which case we want
                // to be sure we never decrement the oldest seen id.
                long newLastPhotoId = newestPhoto.getData().getContentId();
                Log.v(TAG, "updating last photo id (old:new) " +
                        mLastPhotoId + ":" + newLastPhotoId);
                mLastPhotoId = Math.max(mLastPhotoId, newLastPhotoId);
            }
            // We may add data that is already present, but if we do, it will be deduped in addOrUpdate.
            // addOrUpdate does not dedupe session items, so we ignore them here
            for (FilmstripItem filmstripItem : newPhotoData) {
                Uri sessionUri = Storage.getSessionUriFromContentUri(
                      filmstripItem.getData().getUri());
                if (sessionUri == null) {
                    addOrUpdate(filmstripItem);
                }
            }
        }
    }

    private class QueryTaskResult {
        public FilmstripItemList mFilmstripItemList;
        public long mLastPhotoId;

        public QueryTaskResult(FilmstripItemList filmstripItemList, long lastPhotoId) {
            mFilmstripItemList = filmstripItemList;
            mLastPhotoId = lastPhotoId;
        }
    }

    private class QueryTask extends AsyncTask<Context, Void, QueryTaskResult> {
        // The maximum number of data to load metadata for in a single task.
        private static final int MAX_METADATA = 5;

        private final Callback<Void> mDoneCallback;

        public QueryTask(Callback<Void> doneCallback) {
            mDoneCallback = doneCallback;
        }

        /**
         * Loads all the photo and video data in the camera folder in background
         * and combine them into one single list.
         *
         * @param contexts {@link Context} to load all the data.
         * @return An {@link CameraFilmstripDataAdapter.QueryTaskResult} containing
         *  all loaded data and the highest photo id in the dataset.
         */
        @Override
        protected QueryTaskResult doInBackground(Context... contexts) {
            final Context context = contexts[0];
            FilmstripItemList l = new FilmstripItemList();
            // Photos and videos
            List<PhotoItem> photoData = mPhotoItemFactory.queryAll();
            List<VideoItem> videoData = mVideoItemFactory.queryAll();

            long lastPhotoId = FilmstripItemBase.QUERY_ALL_MEDIA_ID;
            if (photoData != null && !photoData.isEmpty()) {
                // This relies on {@link LocalMediaData.QUERY_ORDER} returning
                // items sorted descending by ID, as such we can just pull the
                // ID from the first item in the result to establish the last
                // (max) photo ID.
                FilmstripItemData firstPhotoData = photoData.get(0).getData();

                if(firstPhotoData != null) {
                    lastPhotoId = firstPhotoData.getContentId();
                }
            }

            if (photoData != null) {
                Log.v(TAG, "retrieved photo metadata, number of items: " + photoData.size());
                l.addAll(photoData);
            }
            if (videoData != null) {
                Log.v(TAG, "retrieved video metadata, number of items: " + videoData.size());
                l.addAll(videoData);
            }
            Log.v(TAG, "sorting video/photo metadata");
            // Photos should be sorted within photo/video by ID, which in most
            // cases should correlate well to the date taken/modified. This sort
            // operation makes all photos/videos sorted by date in one list.
            l.sort(new NewestFirstComparator(new Date()));
            Log.v(TAG, "sorted video/photo metadata");

            // Load enough metadata so it's already loaded when we open the filmstrip.
            for (int i = 0; i < MAX_METADATA && i < l.size(); i++) {
                FilmstripItem data = l.get(i);
                MetadataLoader.loadMetadata(context, data);
            }
            return new QueryTaskResult(l, lastPhotoId);
        }

        @Override
        protected void onPostExecute(QueryTaskResult result) {
            // Since we're wiping away all of our data, we should always replace any existing last
            // photo id with the new one we just obtained so it matches the data we're showing.
            mLastPhotoId = result.mLastPhotoId;
            replaceItemList(result.mFilmstripItemList);
            if (mDoneCallback != null) {
                mDoneCallback.onCallback(null);
            }
            // Now check for any photos added since this task was kicked off
            LoadNewPhotosTask ltask = new LoadNewPhotosTask(mContext, mLastPhotoId);
            ltask.execute(mContext.getContentResolver());
        }
    }

    private class DeletionTask extends AsyncTask<FilmstripItem, Void, Void> {
        @Override
        protected Void doInBackground(FilmstripItem... items) {
            for (FilmstripItem item : items) {
                if (!item.getAttributes().canDelete()) {
                    Log.v(TAG, "Deletion is not supported:" + item);
                    continue;
                }
                item.delete();
            }
            return null;
        }
    }

    private class MetadataUpdateTask extends AsyncTask<Integer, Void, List<Integer> > {
        private final boolean mForceUpdate;

        MetadataUpdateTask(boolean forceUpdate) {
            super();
            mForceUpdate = forceUpdate;
        }

        MetadataUpdateTask() {
            this(false);
        }

        @Override
        protected List<Integer> doInBackground(Integer... dataId) {
            List<Integer> updatedList = new ArrayList<>();
            for (Integer id : dataId) {
                if (id < 0 || id >= mFilmstripItems.size()) {
                    continue;
                }
                final FilmstripItem data = mFilmstripItems.get(id);
                if (MetadataLoader.loadMetadata(mContext, data) || mForceUpdate) {
                    updatedList.add(id);
                }
            }
            return updatedList;
        }

        @Override
        protected void onPostExecute(final List<Integer> updatedData) {
            // Since the metadata will affect the width and height of the data
            // if it's a video, we need to notify the DataAdapter listener
            // because ImageData.getWidth() and ImageData.getHeight() now may
            // return different values due to the metadata.
            if (mListener != null) {
                mListener.onFilmstripItemUpdated(new UpdateReporter() {
                    @Override
                    public boolean isDataRemoved(int index) {
                        return false;
                    }

                    @Override
                    public boolean isDataUpdated(int index) {
                        return updatedData.contains(index);
                    }
                });
            }
            if (mFilmstripItemListener == null) {
                return;
            }
            mFilmstripItemListener.onMetadataUpdated(updatedData);
        }
    }
}
