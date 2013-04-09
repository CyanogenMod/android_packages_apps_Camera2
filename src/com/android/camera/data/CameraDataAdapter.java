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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.Storage;
import com.android.camera.ui.FilmStripView;
import com.android.camera.ui.FilmStripView.ImageData;

import java.util.ArrayList;
import java.util.List;

/**
 * A FilmStripDataProvider that provide data in the camera folder.
 *
 * The given view for camera preview won't be added until the preview info
 * has been set by setPreviewInfo(int, int, int)
 */
public class CameraDataAdapter implements FilmStripView.DataAdapter {
    private static final String TAG = "CamreaFilmStripDataProvider";

    private static final int DEFAULT_DECODE_SIZE = 3000;
    private static final String ORDER_CLAUSE = ImageColumns.DATE_TAKEN + " DESC, "
            + ImageColumns._ID + " DESC";
    private static final String[] CAMERA_PATH = { Storage.DIRECTORY + "%" };
    private static final int COL_ID = 0;
    private static final int COL_TITLE = 1;
    private static final int COL_MIME_TYPE = 2;
    private static final int COL_DATE_TAKEN = 3;
    private static final int COL_DATE_MODIFIED = 4;
    private static final int COL_DATA = 5;
    private static final int COL_ORIENTATION = 6;
    private static final int COL_WIDTH = 7;
    private static final int COL_HEIGHT = 8;
    private static final int COL_SIZE = 9;

    private static final String[] PROJECTION = {
        ImageColumns._ID,           // 0, int
        ImageColumns.TITLE,         // 1, string
        ImageColumns.MIME_TYPE,     // 2, tring
        ImageColumns.DATE_TAKEN,    // 3, int
        ImageColumns.DATE_MODIFIED, // 4, int
        ImageColumns.DATA,          // 5, string
        ImageColumns.ORIENTATION,   // 6, int, 0, 90, 180, 270
        ImageColumns.WIDTH,         // 7, int
        ImageColumns.HEIGHT,        // 8, int
        ImageColumns.SIZE           // 9, int
    };

    // 32K buffer.
    private static final byte[] DECODE_TEMP_STORAGE = new byte[32 * 1024];

    private List<LocalImageData> mImages;

    private Listener mListener;
    private View mCameraPreviewView;
    private ColorDrawable mPlaceHolder;

    private int mSuggestedWidth = DEFAULT_DECODE_SIZE;
    private int mSuggestedHeight = DEFAULT_DECODE_SIZE;

    public CameraDataAdapter(View cameraPreviewView, int placeHolderColor) {
        mCameraPreviewView = cameraPreviewView;
        mPlaceHolder = new ColorDrawable(placeHolderColor);
    }

    public void setCameraPreviewInfo(int width, int height, int orientation) {
        addOrReplaceCameraData(buildCameraImageData(width, height, orientation));
    }

    @Override
    public int getTotalNumber() {
        return mImages.size();
    }

    @Override
    public ImageData getImageData(int id) {
        if (id >= mImages.size()) return null;
        return mImages.get(id);
    }

    @Override
    public void suggestSize(int w, int h) {
        if (w <= 0 || h <= 0) {
            mSuggestedWidth  = mSuggestedHeight = DEFAULT_DECODE_SIZE;
        } else {
            mSuggestedWidth = (w < DEFAULT_DECODE_SIZE ? w : DEFAULT_DECODE_SIZE);
            mSuggestedHeight = (h < DEFAULT_DECODE_SIZE ? h : DEFAULT_DECODE_SIZE);
        }
    }

    @Override
    public void requestLoad(ContentResolver resolver) {
        QueryTask qtask = new QueryTask();
        qtask.execute(resolver);
    }

    @Override
    public View getView(Context c, int dataID) {
        if (dataID >= mImages.size() || dataID < 0) {
            return null;
        }

        LocalImageData data = mImages.get(dataID);

        if (data.isCameraData) return mCameraPreviewView;

        ImageView v = new ImageView(c);
        v.setImageDrawable(mPlaceHolder);

        v.setScaleType(ImageView.ScaleType.FIT_XY);
        LoadBitmapTask task = new LoadBitmapTask(data, v);
        task.execute();
        return v;
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    private LocalImageData buildCameraImageData(int width, int height, int orientation) {
        LocalImageData d = new LocalImageData();
        d.width = width;
        d.height = height;
        d.orientation = orientation;
        d.isCameraData = true;
        d.supportedAction = ImageData.ACTION_NONE;
        return d;
    }

    private void addOrReplaceCameraData(LocalImageData data) {
        if (mImages == null) mImages = new ArrayList<LocalImageData>();
        if (mImages.size() == 0) {
            mImages.add(0, data);
            return;
        }

        LocalImageData first = mImages.get(0);
        if (first.isCameraData) {
            mImages.set(0, data);
        } else {
            mImages.add(0, data);
        }
    }

    private LocalImageData buildCursorImageData(Cursor c) {
        LocalImageData d = new LocalImageData();
        d.id = c.getInt(COL_ID);
        d.title = c.getString(COL_TITLE);
        d.mimeType = c.getString(COL_MIME_TYPE);
        d.path = c.getString(COL_DATA);
        d.orientation = c.getInt(COL_ORIENTATION);
        d.width = c.getInt(COL_WIDTH);
        d.height = c.getInt(COL_HEIGHT);
        d.supportedAction = ImageData.ACTION_PROMOTE | ImageData.ACTION_DEMOTE;
        if (d.width <= 0 || d.height <= 0) {
            Log.v(TAG, "warning! zero dimension for "
                    + d.path + ":" + d.width + "x" + d.height);
            Dimension dim = decodeDimension(d.path);
            if (dim != null) {
                d.width = dim.width;
                d.height = dim.height;
            } else {
                Log.v(TAG, "warning! dimension decode failed for " + d.path);
                Bitmap b = BitmapFactory.decodeFile(d.path);
                if (b == null) return null;
                d.width = b.getWidth();
                d.height = b.getHeight();
            }
        }
        if (d.orientation == 90 || d.orientation == 270) {
            int b = d.width;
            d.width = d.height;
            d.height = b;
        }
        return d;
    }

    private Dimension decodeDimension(String path) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        Bitmap b = BitmapFactory.decodeFile(path, opts);
        if (b == null) return null;
        Dimension d = new Dimension();
        d.width = opts.outWidth;
        d.height = opts.outHeight;
        return d;
    }

    private class Dimension {
        public int width;
        public int height;
    }

    private class LocalImageData implements FilmStripView.ImageData {
        public boolean isCameraData;
        public int id;
        public String title;
        public String mimeType;
        public String path;
        // from MediaStore, can only be 0, 90, 180, 270;
        public int orientation;
        // width and height should be adjusted according to orientation.
        public int width;
        public int height;
        public int supportedAction;

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getType() {
            if (isCameraData) return ImageData.TYPE_CAMERA_PREVIEW;
            return ImageData.TYPE_PHOTO;
        }

        @Override
        public boolean isActionSupported(int action) {
            return ((action & supportedAction) != 0);
        }

        @Override
        public String toString() {
            return "LocalImageData:" + ",data=" + path + ",mimeType=" + mimeType
                    + "," + width + "x" + height + ",orientation=" + orientation;
        }
    }

    private class QueryTask extends AsyncTask<ContentResolver, Void, List<LocalImageData>> {
        private ContentResolver mResolver;
        private LocalImageData mCameraImageData;

        @Override
        protected List<LocalImageData> doInBackground(ContentResolver... resolver) {
            List<LocalImageData> l = null;
            Cursor c = resolver[0].query(Images.Media.EXTERNAL_CONTENT_URI, PROJECTION,
                    MediaStore.Images.Media.DATA + " like ? ", CAMERA_PATH,
                    ORDER_CLAUSE);
            if (c == null) return null;
            l = new ArrayList<LocalImageData>();
            c.moveToFirst();
            while (!c.isLast()) {
                LocalImageData data = buildCursorImageData(c);
                if (data != null) l.add(data);
                else Log.e(TAG, "Error decoding file:" + c.getString(COL_DATA));
                c.moveToNext();
            }
            c.close();
            return l;
        }

        @Override
        protected void onPostExecute(List<LocalImageData> l) {
            boolean changed = (l != mImages);
            LocalImageData first = null;
            if (mImages != null && mImages.size() > 0) {
                first = mImages.get(0);
                if (!first.isCameraData) first = null;
            }
            mImages = l;
            if (first != null) addOrReplaceCameraData(first);
            // both might be null.
            if (changed && mListener != null) mListener.onDataLoaded();
        }
    }

    private class LoadBitmapTask extends AsyncTask<Void, Void, Bitmap> {
        private LocalImageData mData;
        private ImageView mView;

        public LoadBitmapTask(
                LocalImageData d, ImageView v) {
            mData = d;
            mView = v;
        }

        @Override
        protected Bitmap doInBackground(Void... v) {
            BitmapFactory.Options opts = null;
            Bitmap b;
            int sample = 1;
            while (mSuggestedWidth * sample < mData.width
                    || mSuggestedHeight * sample < mData.height) {
                sample *= 2;
            }
            opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inTempStorage = DECODE_TEMP_STORAGE;
            if (isCancelled()) return null;
            b = BitmapFactory.decodeFile(mData.path, opts);
            if (mData.orientation != 0) {
                if (isCancelled()) return null;
                Matrix m = new Matrix();
                m.setRotate((float) mData.orientation);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
            }
            return b;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap == null) {
                Log.e(TAG, "Cannot decode bitmap file:" + mData.path);
                return;
            }
            mView.setImageBitmap(bitmap);
        }
    }
}
