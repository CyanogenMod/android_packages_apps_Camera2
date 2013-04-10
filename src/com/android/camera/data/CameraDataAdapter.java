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
import android.graphics.drawable.Drawable;
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
    private static final String[] CAMERA_PATH = { Storage.DIRECTORY + "%" };

    private List<LocalData> mImages;

    private Listener mListener;
    private View mCameraPreviewView;
    private ColorDrawable mPlaceHolder;

    private int mSuggestedWidth = DEFAULT_DECODE_SIZE;
    private int mSuggestedHeight = DEFAULT_DECODE_SIZE;

    public CameraDataAdapter(View cameraPreviewView, int placeHolderColor) {
        mCameraPreviewView = cameraPreviewView;
        mPlaceHolder = new ColorDrawable(placeHolderColor);
    }

    public void setCameraPreviewInfo(int width, int height) {
        addOrReplaceCameraData(buildCameraImageData(width, height));
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

    public void requestLoad(ContentResolver resolver) {
        QueryTask qtask = new QueryTask();
        qtask.execute(resolver);
    }

    @Override
    public View getView(Context c, int dataID) {
        if (dataID >= mImages.size() || dataID < 0) {
            return null;
        }

        return mImages.get(dataID).getView(
                c, mSuggestedWidth, mSuggestedHeight, mPlaceHolder);
    }

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    private LocalData buildCameraImageData(int width, int height) {
        LocalData d = new CameraPreviewData(width, height);
        return d;
    }

    private void addOrReplaceCameraData(LocalData data) {
        if (mImages == null) mImages = new ArrayList<LocalData>();
        if (mImages.size() == 0) {
            mImages.add(0, data);
            return;
        }

        LocalData first = mImages.get(0);
        if (first.getType() == ImageData.TYPE_CAMERA_PREVIEW) {
            mImages.set(0, data);
        } else {
            mImages.add(0, data);
        }
    }

    private class QueryTask extends AsyncTask<ContentResolver, Void, List<LocalData>> {
        @Override
        protected List<LocalData> doInBackground(ContentResolver... resolver) {
            List<LocalData> l = null;
            Cursor c = resolver[0].query(
                    Images.Media.EXTERNAL_CONTENT_URI,
                    LocalPhotoData.QUERY_PROJECTION,
                    MediaStore.Images.Media.DATA + " like ? ", CAMERA_PATH,
                    LocalPhotoData.QUERY_ORDER);
            if (c == null) return null;

            // build up the list.
            l = new ArrayList<LocalData>();
            c.moveToFirst();
            while (!c.isLast()) {
                LocalData data = LocalPhotoData.buildFromCursor(c);
                if (data != null) {
                    l.add(data);
                } else {
                    Log.e(TAG, "Error decoding file:"
                            + c.getString(LocalPhotoData.COL_DATA));
                }
                c.moveToNext();
            }
            c.close();
            return l;
        }

        @Override
        protected void onPostExecute(List<LocalData> l) {
            boolean changed = (l != mImages);
            LocalData first = null;
            if (mImages != null && mImages.size() > 0) {
                first = mImages.get(0);
                if (first.getType() != ImageData.TYPE_CAMERA_PREVIEW) first = null;
            }
            mImages = l;
            if (first != null) addOrReplaceCameraData(first);
            // both might be null.
            if (changed) mListener.onDataLoaded();
        }
    }

    private abstract static class LocalData implements FilmStripView.ImageData {
        public int id;
        public String title;
        public String mimeType;
        public String path;
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
        public boolean isActionSupported(int action) {
            return false;
        }

        @Override
        abstract public int getType();

        abstract View getView(Context c, int width, int height, Drawable placeHolder);
    }

    private class CameraPreviewData extends LocalData {
        private int mWidth;
        private int mHeight;

        CameraPreviewData(int w, int h) {
            mWidth = w;
            mHeight = h;
        }

        @Override
        public int getWidth() {
            return mWidth;
        }

        @Override
        public int getHeight() {
            return mHeight;
        }

        @Override
        public int getType() {
            return ImageData.TYPE_CAMERA_PREVIEW;
        }

        @Override
        View getView(Context c, int width, int height, Drawable placeHolder) {
            return mCameraPreviewView;
        }
    }

    private static class LocalPhotoData extends LocalData {
        static final String QUERY_ORDER = ImageColumns.DATE_TAKEN + " DESC, "
                + ImageColumns._ID + " DESC";
        static final String[] QUERY_PROJECTION = {
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


        // 32K buffer.
        private static final byte[] DECODE_TEMP_STORAGE = new byte[32 * 1024];

        // from MediaStore, can only be 0, 90, 180, 270;
        public int orientation;

        static LocalPhotoData buildFromCursor(Cursor c) {
            LocalPhotoData d = new LocalPhotoData();
            d.id = c.getInt(COL_ID);
            d.title = c.getString(COL_TITLE);
            d.mimeType = c.getString(COL_MIME_TYPE);
            d.path = c.getString(COL_DATA);
            d.orientation = c.getInt(COL_ORIENTATION);
            d.width = c.getInt(COL_WIDTH);
            d.height = c.getInt(COL_HEIGHT);
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

        @Override
        View getView(Context c,
                int decodeWidth, int decodeHeight, Drawable placeHolder) {
            ImageView v = new ImageView(c);
            v.setImageDrawable(placeHolder);

            v.setScaleType(ImageView.ScaleType.FIT_XY);
            LoadBitmapTask task = new LoadBitmapTask(v, decodeWidth, decodeHeight);
            task.execute();
            return v;
        }


        @Override
        public String toString() {
            return "LocalPhotoData:" + ",data=" + path + ",mimeType=" + mimeType
                    + "," + width + "x" + height + ",orientation=" + orientation;
        }

        @Override
        public int getType() {
            return TYPE_PHOTO;
        }

        private static Dimension decodeDimension(String path) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            Bitmap b = BitmapFactory.decodeFile(path, opts);
            if (b == null) return null;
            Dimension d = new Dimension();
            d.width = opts.outWidth;
            d.height = opts.outHeight;
            return d;
        }

        private static class Dimension {
            public int width;
            public int height;
        }

        private class LoadBitmapTask extends AsyncTask<Void, Void, Bitmap> {
            private ImageView mView;
            private int mDecodeWidth;
            private int mDecodeHeight;

            public LoadBitmapTask(ImageView v, int decodeWidth, int decodeHeight) {
                mView = v;
                mDecodeWidth = decodeWidth;
                mDecodeHeight = decodeHeight;
            }

            @Override
            protected Bitmap doInBackground(Void... v) {
                BitmapFactory.Options opts = null;
                Bitmap b;
                int sample = 1;
                while (mDecodeWidth * sample < width
                        || mDecodeHeight * sample < height) {
                    sample *= 2;
                }
                opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inTempStorage = DECODE_TEMP_STORAGE;
                if (isCancelled()) return null;
                b = BitmapFactory.decodeFile(path, opts);
                if (orientation != 0) {
                    if (isCancelled()) return null;
                    Matrix m = new Matrix();
                    m.setRotate((float) orientation);
                    b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
                }
                return b;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap == null) {
                    Log.e(TAG, "Cannot decode bitmap file:" + path);
                    return;
                }
                mView.setImageBitmap(bitmap);
            }
        }
    }
}
