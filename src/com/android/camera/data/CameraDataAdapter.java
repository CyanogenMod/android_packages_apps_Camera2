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
import com.android.camera.data.LocalData.ActionCallback;
import com.android.camera.debug.Log;
import com.android.camera.filmstrip.ImageData;
import com.android.camera.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A {@link LocalDataAdapter} that provides data in the camera folder.
 */
public class CameraDataAdapter implements LocalDataAdapter {
    private static final Log.Tag TAG = new Log.Tag("CameraDataAdapter");

    private static final int DEFAULT_DECODE_SIZE = 1600;

    private final Context mContext;

    private LocalDataList mImages;

    private Listener mListener;
    private LocalDataListener mLocalDataListener;
    private final int mPlaceHolderResourceId;

    private int mSuggestedWidth = DEFAULT_DECODE_SIZE;
    private int mSuggestedHeight = DEFAULT_DECODE_SIZE;
    private long mLastPhotoId = LocalMediaData.QUERY_ALL_MEDIA_ID;

    private LocalData mLocalDataToDelete;

    public CameraDataAdapter(Context context, int placeholderResource) {
        mContext = context;
        mImages = new LocalDataList();
        mPlaceHolderResourceId = placeholderResource;
    }

    @Override
    public void setLocalDataListener(LocalDataListener listener) {
        mLocalDataListener = listener;
    }

    @Override
    public void requestLoadNewPhotos() {
        LoadNewPhotosTask ltask = new LoadNewPhotosTask(mLastPhotoId);
        ltask.execute(mContext.getContentResolver());
    }

    @Override
    public void requestLoad(Callback<Void> doneCallback) {
        QueryTask qtask = new QueryTask(doneCallback);
        qtask.execute(mContext);
    }

    @Override
    public AsyncTask updateMetadata(int dataId) {
        MetadataUpdateTask result = new MetadataUpdateTask();
        result.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, dataId);
        return result;
    }

    @Override
    public boolean isMetadataUpdated(int dataId) {
        if (dataId < 0 || dataId >= mImages.size()) {
            return true;
        }
        return mImages.get(dataId).isMetadataUpdated();
    }

    @Override
    public int getItemViewType(int dataId) {
        if (dataId < 0 || dataId >= mImages.size()) {
            return -1;
        }

        return mImages.get(dataId).getItemViewType().ordinal();
    }

    @Override
    public LocalData getLocalData(int dataID) {
        if (dataID < 0 || dataID >= mImages.size()) {
            return null;
        }
        return mImages.get(dataID);
    }

    @Override
    public int getTotalNumber() {
        return mImages.size();
    }

    @Override
    public ImageData getImageData(int id) {
        return getLocalData(id);
    }

    @Override
    public void suggestViewSizeBound(int w, int h) {
        mSuggestedWidth = w;
        mSuggestedHeight = h;
    }

    @Override
    public View getView(Context context, View recycled, int dataID,
            ActionCallback actionCallback) {
        if (dataID >= mImages.size() || dataID < 0) {
            return null;
        }

        return mImages.get(dataID).getView(
                context, recycled, mSuggestedWidth, mSuggestedHeight,
                mPlaceHolderResourceId, this, /* inProgress */ false, actionCallback);
    }

    @Override
    public void resizeView(Context context, int dataID, View view, int w, int h) {
        if (dataID >= mImages.size() || dataID < 0) {
            return;
        }
        mImages.get(dataID).loadFullImage(context, mSuggestedWidth, mSuggestedHeight, view, this);
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
        if (mImages.size() != 0) {
            mListener.onDataLoaded();
        }
    }

    @Override
    public boolean canSwipeInFullScreen(int dataID) {
        if (dataID < mImages.size() && dataID > 0) {
            return mImages.get(dataID).canSwipeInFullScreen();
        }
        return true;
    }

    @Override
    public void removeData(int dataID) {
        LocalData d = mImages.remove(dataID);
        if (d == null) {
            return;
        }

        // Delete previously removed data first.
        executeDeletion();
        mLocalDataToDelete = d;
        mListener.onDataRemoved(dataID, d);
    }

    @Override
    public boolean addData(LocalData newData) {
        final Uri uri = newData.getUri();
        int pos = findDataByContentUri(uri);
        if (pos != -1) {
            // a duplicate one, just do a substitute.
            Log.v(TAG, "found duplicate data: " + uri);
            updateData(pos, newData);
            return false;
        } else {
            // a new data.
            insertData(newData);
            return true;
        }
    }

    @Override
    public int findDataByContentUri(Uri uri) {
        // LocalDataList will return in O(1) if the uri is not contained.
        // Otherwise the performance is O(n), but this is acceptable as we will
        // most often call this to find an element at the beginning of the list.
        return mImages.indexOf(uri);
    }

    @Override
    public boolean undoDataRemoval() {
        if (mLocalDataToDelete == null) {
            return false;
        }
        LocalData d = mLocalDataToDelete;
        mLocalDataToDelete = null;
        insertData(d);
        return true;
    }

    @Override
    public boolean executeDeletion() {
        if (mLocalDataToDelete == null) {
            return false;
        }

        DeletionTask task = new DeletionTask();
        task.execute(mLocalDataToDelete);
        mLocalDataToDelete = null;
        return true;
    }

    @Override
    public void flush() {
        replaceData(new LocalDataList());
    }

    @Override
    public void refresh(Uri uri) {
        final int pos = findDataByContentUri(uri);
        if (pos == -1) {
            return;
        }

        LocalData data = mImages.get(pos);
        LocalData refreshedData = data.refresh(mContext);

        // Refresh failed. Probably removed already.
        if (refreshedData == null && mListener != null) {
            mListener.onDataRemoved(pos, data);
            return;
        }
        updateData(pos, refreshedData);
    }

    @Override
    public void updateData(final int pos, LocalData data) {
        mImages.set(pos, data);
        if (mListener != null) {
            mListener.onDataUpdated(new UpdateReporter() {
                @Override
                public boolean isDataRemoved(int dataID) {
                    return false;
                }

                @Override
                public boolean isDataUpdated(int dataID) {
                    return (dataID == pos);
                }
            });
        }
    }

    private void insertData(LocalData data) {
        // Since this function is mostly for adding the newest data,
        // a simple linear search should yield the best performance over a
        // binary search.
        int pos = 0;
        Comparator<LocalData> comp = new LocalData.NewestFirstComparator();
        for (; pos < mImages.size()
                && comp.compare(data, mImages.get(pos)) > 0; pos++) {
            ;
        }
        mImages.add(pos, data);
        if (mListener != null) {
            mListener.onDataInserted(pos, data);
        }
    }

    /** Update all the data */
    private void replaceData(LocalDataList list) {
        if (list.size() == 0 && mImages.size() == 0) {
            return;
        }
        mImages = list;
        if (mListener != null) {
            mListener.onDataLoaded();
        }
    }

    @Override
    public List<AsyncTask> preloadItems(List<Integer> items) {
        List<AsyncTask> result = new ArrayList<AsyncTask>();
        for (Integer id : items) {
            if (!isMetadataUpdated(id)) {
                result.add(updateMetadata(id));
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
        List<Integer> result = new ArrayList<Integer>();
        for (int i = Math.max(0, startPosition); i < endPosition; i++) {
            result.add(i);
        }
        return result;
    }

    @Override
    public int getCount() {
        return getTotalNumber();
    }

    private class LoadNewPhotosTask extends AsyncTask<ContentResolver, Void, List<LocalData>> {

        private final long mMinPhotoId;

        public LoadNewPhotosTask(long lastPhotoId) {
            mMinPhotoId = lastPhotoId;
        }

        /**
         * Loads any new photos added to our storage directory since our last query.
         * @param contentResolvers {@link android.content.ContentResolver} to load data.
         * @return An {@link java.util.ArrayList} containing any new data.
         */
        @Override
        protected List<LocalData> doInBackground(ContentResolver... contentResolvers) {
            if (mMinPhotoId != LocalMediaData.QUERY_ALL_MEDIA_ID) {
                Log.v(TAG, "updating media metadata with photos newer than id: " + mMinPhotoId);
                final ContentResolver cr = contentResolvers[0];
                return LocalMediaData.PhotoData.query(cr, LocalMediaData.PhotoData.CONTENT_URI,
                        mMinPhotoId);
            }
            return new ArrayList<LocalData>(0);
        }

        @Override
        protected void onPostExecute(List<LocalData> newPhotoData) {
            if (newPhotoData == null) {
                Log.w(TAG, "null data returned from new photos query");
                return;
            }
            Log.v(TAG, "new photos query return num items: " + newPhotoData.size());
            if (!newPhotoData.isEmpty()) {
                LocalData newestPhoto = newPhotoData.get(0);
                // We may overlap with another load task or a query task, in which case we want
                // to be sure we never decrement the oldest seen id.
                long newLastPhotoId = newestPhoto.getContentId();
                Log.v(TAG, "updating last photo id (old:new) " +
                        mLastPhotoId + ":" + newLastPhotoId);
                mLastPhotoId = Math.max(mLastPhotoId, newLastPhotoId);
            }
            // We may add data that is already present, but if we do, it will be deduped in addData.
            // addData does not dedupe session items, so we ignore them here
            for (LocalData localData : newPhotoData) {
                Uri sessionUri = Storage.getSessionUriFromContentUri(localData.getUri());
                if (sessionUri == null) {
                    addData(localData);
                }
            }
        }
    }

    private class QueryTaskResult {
        public LocalDataList mLocalDataList;
        public long mLastPhotoId;

        public QueryTaskResult(LocalDataList localDataList, long lastPhotoId) {
            mLocalDataList = localDataList;
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
         * @return An {@link com.android.camera.data.CameraDataAdapter.QueryTaskResult} containing
         *  all loaded data and the highest photo id in the dataset.
         */
        @Override
        protected QueryTaskResult doInBackground(Context... contexts) {
            final Context context = contexts[0];
            final ContentResolver cr = context.getContentResolver();
            LocalDataList l = new LocalDataList();
            // Photos
            List<LocalData> photoData = LocalMediaData.PhotoData.query(cr,
                    LocalMediaData.PhotoData.CONTENT_URI, LocalMediaData.QUERY_ALL_MEDIA_ID);
            List<LocalData> videoData = LocalMediaData.VideoData.query(cr,
                    LocalMediaData.VideoData.CONTENT_URI, LocalMediaData.QUERY_ALL_MEDIA_ID);

            long lastPhotoId = LocalMediaData.QUERY_ALL_MEDIA_ID;
            if (!photoData.isEmpty()) {
                // This relies on {@link LocalMediaData.QUERY_ORDER} returning
                // items sorted descending by ID, as such we can just pull the
                // ID from the first item in the result to establish the last
                // (max) photo ID.
                lastPhotoId = photoData.get(0).getContentId();
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
            l.sort(new LocalData.NewestFirstComparator());
            Log.v(TAG, "sorted video/photo metadata");

            // Load enough metadata so it's already loaded when we open the filmstrip.
            for (int i = 0; i < MAX_METADATA && i < l.size(); i++) {
                LocalData data = l.get(i);
                MetadataLoader.loadMetadata(context, data);
            }
            return new QueryTaskResult(l, lastPhotoId);
        }

        @Override
        protected void onPostExecute(QueryTaskResult result) {
            // Since we're wiping away all of our data, we should always replace any existing last
            // photo id with the new one we just obtained so it matches the data we're showing.
            mLastPhotoId = result.mLastPhotoId;
            replaceData(result.mLocalDataList);
            if (mDoneCallback != null) {
                mDoneCallback.onCallback(null);
            }
            // Now check for any photos added since this task was kicked off
            LoadNewPhotosTask ltask = new LoadNewPhotosTask(mLastPhotoId);
            ltask.execute(mContext.getContentResolver());
        }
    }

    private class DeletionTask extends AsyncTask<LocalData, Void, Void> {
        @Override
        protected Void doInBackground(LocalData... data) {
            for (int i = 0; i < data.length; i++) {
                if (!data[i].isDataActionSupported(LocalData.DATA_ACTION_DELETE)) {
                    Log.v(TAG, "Deletion is not supported:" + data[i]);
                    continue;
                }
                data[i].delete(mContext);
            }
            return null;
        }
    }

    private class MetadataUpdateTask extends AsyncTask<Integer, Void, List<Integer> > {
        @Override
        protected List<Integer> doInBackground(Integer... dataId) {
            List<Integer> updatedList = new ArrayList<Integer>();
            for (Integer id : dataId) {
                if (id < 0 || id >= mImages.size()) {
                    continue;
                }
                final LocalData data = mImages.get(id);
                if (MetadataLoader.loadMetadata(mContext, data)) {
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
                mListener.onDataUpdated(new UpdateReporter() {
                    @Override
                    public boolean isDataRemoved(int dataID) {
                        return false;
                    }

                    @Override
                    public boolean isDataUpdated(int dataID) {
                        return updatedData.contains(dataID);
                    }
                });
            }
            if (mLocalDataListener == null) {
                return;
            }
            mLocalDataListener.onMetadataUpdated(updatedData);
        }
    }
}
