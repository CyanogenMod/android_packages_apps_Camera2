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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import com.android.camera.Storage;
import com.android.camera.ui.FilmStripView.ImageData;

/**
 * A {@link LocalDataAdapter} that provides data in the camera folder.
 */
public class CameraDataAdapter implements LocalDataAdapter {
    private static final String TAG = CameraDataAdapter.class.getSimpleName();

    private static final int DEFAULT_DECODE_SIZE = 3000;
    private static final String[] CAMERA_PATH = { Storage.DIRECTORY + "%" };

    private List<LocalData> mImages;

    private Listener mListener;
    private Drawable mPlaceHolder;

    private int mSuggestedWidth = DEFAULT_DECODE_SIZE;
    private int mSuggestedHeight = DEFAULT_DECODE_SIZE;

    private LocalData mLocalDataToDelete;

    public CameraDataAdapter(Drawable placeHolder) {
        mPlaceHolder = placeHolder;
    }

    @Override
    public void requestLoad(ContentResolver resolver) {
        QueryTask qtask = new QueryTask();
        qtask.execute(resolver);
    }

    @Override
    public int getTotalNumber() {
        if (mImages == null) {
            return 0;
        }
        return mImages.size();
    }

    @Override
    public ImageData getImageData(int id) {
        return getData(id);
    }

    @Override
    public void suggestViewSizeBound(int w, int h) {
        if (w <= 0 || h <= 0) {
            mSuggestedWidth  = mSuggestedHeight = DEFAULT_DECODE_SIZE;
        } else {
            mSuggestedWidth = (w < DEFAULT_DECODE_SIZE ? w : DEFAULT_DECODE_SIZE);
            mSuggestedHeight = (h < DEFAULT_DECODE_SIZE ? h : DEFAULT_DECODE_SIZE);
        }
    }

    @Override
    public View getView(Context c, int dataID) {
        if (mImages == null) {
            return null;
        }
        if (dataID >= mImages.size() || dataID < 0) {
            return null;
        }

        return mImages.get(dataID).getView(
                c, mSuggestedWidth, mSuggestedHeight,
                mPlaceHolder.getConstantState().newDrawable());
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
        if (mImages != null) {
            mListener.onDataLoaded();
        }
    }

    @Override
    public void onDataFullScreen(int dataID, boolean fullScreen) {
        if (dataID < mImages.size() && dataID >= 0) {
            mImages.get(dataID).onFullScreen(fullScreen);
        }
    }

    @Override
    public void onDataCentered(int dataID, boolean centered) {
        // do nothing.
    }

    @Override
    public boolean canSwipeInFullScreen(int dataID) {
        if (dataID < mImages.size() && dataID > 0) {
            return mImages.get(dataID).canSwipeInFullScreen();
        }
        return true;
    }

    @Override
    public void removeData(Context c, int dataID) {
        if (dataID >= mImages.size()) return;
        LocalData d = mImages.remove(dataID);
        // Delete previously removed data first.
        executeDeletion(c);
        mLocalDataToDelete = d;
        mListener.onDataRemoved(dataID, d);
    }

    private void insertData(LocalData data) {
        if (mImages == null) {
            mImages = new ArrayList<LocalData>();
        }

        // Since this function is mostly for adding the newest data,
        // a simple linear search should yield the best performance over a
        // binary search.
        int pos = 0;
        Comparator<LocalData> comp = new LocalData.NewestFirstComparator();
        for (; pos < mImages.size()
                && comp.compare(data, mImages.get(pos)) > 0; pos++);
        mImages.add(pos, data);
        if (mListener != null) {
            mListener.onDataInserted(pos, data);
        }
    }

    @Override
    public void addNewVideo(ContentResolver cr, Uri uri) {
        Cursor c = cr.query(uri,
                LocalData.Video.QUERY_PROJECTION,
                MediaStore.Images.Media.DATA + " like ? ", CAMERA_PATH,
                LocalData.Video.QUERY_ORDER);
        if (c != null && c.moveToFirst()) {
            insertData(LocalData.Video.buildFromCursor(c));
        }
    }

    @Override
    public void addNewPhoto(ContentResolver cr, Uri uri) {
        Cursor c = cr.query(uri,
                LocalData.Photo.QUERY_PROJECTION,
                MediaStore.Images.Media.DATA + " like ? ", CAMERA_PATH,
                LocalData.Photo.QUERY_ORDER);
        if (c != null && c.moveToFirst()) {
            insertData(LocalData.Photo.buildFromCursor(c));
        }
    }

    @Override
    public int findDataByContentUri(Uri uri) {
        // TODO: find the data.
        return -1;
    }

    @Override
    public boolean undoDataRemoval() {
        if (mLocalDataToDelete == null) return false;
        LocalData d = mLocalDataToDelete;
        mLocalDataToDelete = null;
        insertData(d);
        return true;
    }

    @Override
    public boolean executeDeletion(Context c) {
        if (mLocalDataToDelete == null) return false;

        DeletionTask task = new DeletionTask(c);
        task.execute(mLocalDataToDelete);
        mLocalDataToDelete = null;
        return true;
    }

    @Override
    public void flush() {
        replaceData(null);
    }

    private LocalData getData(int id) {
        if (mImages == null || id >= mImages.size() || id < 0) {
            return null;
        }
        return mImages.get(id);
    }

    // Update all the data but keep the camera data if already set.
    private void replaceData(List<LocalData> list) {
        boolean changed = (list != mImages);
        LocalData cameraData = null;
        if (mImages != null && mImages.size() > 0) {
            cameraData = mImages.get(0);
            if (cameraData.getType() != ImageData.TYPE_CAMERA_PREVIEW) {
                cameraData = null;
            }
        }

        mImages = list;
        if (cameraData != null) {
            // camera view exists, so we make sure at least 1 data is in the list.
            if (mImages == null) {
                mImages = new ArrayList<LocalData>();
            }
            mImages.add(0, cameraData);
            if (mListener != null) {
                // Only the camera data is not changed, everything else is changed.
                mListener.onDataUpdated(new UpdateReporter() {
                    @Override
                    public boolean isDataRemoved(int id) {
                        return false;
                    }

                    @Override
                    public boolean isDataUpdated(int id) {
                        if (id == 0) return false;
                        return true;
                    }
                });
            }
        } else {
            // both might be null.
            if (changed) {
                mListener.onDataLoaded();
            }
        }
    }

    private class QueryTask extends AsyncTask<ContentResolver, Void, List<LocalData>> {
        @Override
        protected List<LocalData> doInBackground(ContentResolver... resolver) {
            List<LocalData> l = new ArrayList<LocalData>();
            // Photos
            Cursor c = resolver[0].query(
                    LocalData.Photo.CONTENT_URI,
                    LocalData.Photo.QUERY_PROJECTION,
                    MediaStore.Images.Media.DATA + " like ? ", CAMERA_PATH,
                    LocalData.Photo.QUERY_ORDER);
            if (c != null && c.moveToFirst()) {
                // build up the list.
                while (true) {
                    LocalData data = LocalData.Photo.buildFromCursor(c);
                    if (data != null) {
                        l.add(data);
                    } else {
                        Log.e(TAG, "Error loading data:"
                                + c.getString(LocalData.Photo.COL_DATA));
                    }
                    if (c.isLast()) {
                        break;
                    }
                    c.moveToNext();
                }
            }
            if (c != null) {
                c.close();
            }

            c = resolver[0].query(
                    LocalData.Video.CONTENT_URI,
                    LocalData.Video.QUERY_PROJECTION,
                    MediaStore.Video.Media.DATA + " like ? ", CAMERA_PATH,
                    LocalData.Video.QUERY_ORDER);
            if (c != null && c.moveToFirst()) {
                // build up the list.
                c.moveToFirst();
                while (true) {
                    LocalData data = LocalData.Video.buildFromCursor(c);
                    if (data != null) {
                        l.add(data);
                    } else {
                        Log.e(TAG, "Error loading data:"
                                + c.getString(LocalData.Video.COL_DATA));
                    }
                    if (!c.isLast()) {
                        c.moveToNext();
                    } else {
                        break;
                    }
                }
            }
            if (c != null) {
                c.close();
            }

            if (l.size() == 0) return null;

            Collections.sort(l, new LocalData.NewestFirstComparator());
            return l;
        }

        @Override
        protected void onPostExecute(List<LocalData> l) {
            replaceData(l);
        }
    }

    private class DeletionTask extends AsyncTask<LocalData, Void, Void> {
        Context mContext;

        DeletionTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(LocalData... data) {
            for (int i = 0; i < data.length; i++) {
                if (!data[i].isDataActionSupported(LocalData.ACTION_DELETE)) {
                    Log.v(TAG, "Deletion is not supported:" + data[i]);
                    continue;
                }
                data[i].delete(mContext);
            }
            return null;
        }
    }
}
