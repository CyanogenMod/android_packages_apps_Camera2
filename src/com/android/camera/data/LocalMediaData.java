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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.camera.ui.FilmStripView;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.PhotoSphereHelper;
import com.android.camera2.R;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A base class for all the local media files. The bitmap is loaded in
 * background thread. Subclasses should implement their own background loading
 * thread by sub-classing BitmapLoadTask and overriding doInBackground() to
 * return a bitmap.
 */
public abstract class LocalMediaData implements LocalData {
    protected long id;
    protected String title;
    protected String mimeType;
    protected long dateTakenInSeconds;
    protected long dateModifiedInSeconds;
    protected String path;
    // width and height should be adjusted according to orientation.
    protected int width;
    protected int height;
    protected long sizeInBytes;
    protected double latitude;
    protected double longitude;

    /** The panorama metadata information of this media data. */
    protected PhotoSphereHelper.PanoramaMetadata mPanoramaMetadata;

    /** Used to load photo sphere metadata from image files. */
    protected PanoramaMetadataLoader mPanoramaMetadataLoader = null;

    /**
     * Used for thumbnail loading optimization. True if this data has a
     * corresponding visible view.
     */
    protected Boolean mUsing = false;

    @Override
    public long getDateTaken() {
        return dateTakenInSeconds;
    }

    @Override
    public long getDateModified() {
        return dateModifiedInSeconds;
    }

    @Override
    public String getTitle() {
        return new String(title);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean isUIActionSupported(int action) {
        return false;
    }

    @Override
    public boolean isDataActionSupported(int action) {
        return false;
    }

    @Override
    public boolean delete(Context ctx) {
        File f = new File(path);
        return f.delete();
    }

    @Override
    public void viewPhotoSphere(PhotoSphereHelper.PanoramaViewHelper helper) {
        helper.showPanorama(getContentUri());
    }

    @Override
    public void isPhotoSphere(Context context, final PanoramaSupportCallback callback) {
        // If we already have metadata, use it.
        if (mPanoramaMetadata != null) {
            callback.panoramaInfoAvailable(mPanoramaMetadata.mUsePanoramaViewer,
                    mPanoramaMetadata.mIsPanorama360);
        }

        // Otherwise prepare a loader, if we don't have one already.
        if (mPanoramaMetadataLoader == null) {
            mPanoramaMetadataLoader = new PanoramaMetadataLoader(getContentUri());
        }

        // Load the metadata asynchronously.
        mPanoramaMetadataLoader.getPanoramaMetadata(context,
                new PanoramaMetadataLoader.PanoramaMetadataCallback() {
                    @Override
                    public void onPanoramaMetadataLoaded(PhotoSphereHelper.PanoramaMetadata metadata) {
                        // Store the metadata and remove the loader to free up
                        // space.
                        mPanoramaMetadata = metadata;
                        mPanoramaMetadataLoader = null;
                        callback.panoramaInfoAvailable(metadata.mUsePanoramaViewer,
                                metadata.mIsPanorama360);
                    }
                });
    }

    @Override
    public void onFullScreen(boolean fullScreen) {
        // do nothing.
    }

    @Override
    public boolean canSwipeInFullScreen() {
        return true;
    }

    protected ImageView fillImageView(Context ctx, ImageView v,
            int decodeWidth, int decodeHeight, Drawable placeHolder) {
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setImageDrawable(placeHolder);

        BitmapLoadTask task = getBitmapLoadTask(v, decodeWidth, decodeHeight);
        task.execute();
        return v;
    }

    @Override
    public View getView(Context ctx,
            int decodeWidth, int decodeHeight, Drawable placeHolder) {
        return fillImageView(ctx, new ImageView(ctx),
                decodeWidth, decodeHeight, placeHolder);
    }

    @Override
    public void prepare() {
        synchronized (mUsing) {
            mUsing = true;
        }
    }

    @Override
    public void recycle() {
        synchronized (mUsing) {
            mUsing = false;
        }
    }

    @Override
    public double[] getLatLong() {
        if (latitude == 0 && longitude == 0) {
            return null;
        }
        return new double[] {
                latitude, longitude
        };
    }

    protected boolean isUsing() {
        synchronized (mUsing) {
            return mUsing;
        }
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public MediaDetails getMediaDetails(Context context) {
        DateFormat dateFormatter = DateFormat.getDateTimeInstance();
        MediaDetails mediaDetails = new MediaDetails();
        mediaDetails.addDetail(MediaDetails.INDEX_TITLE, title);
        mediaDetails.addDetail(MediaDetails.INDEX_WIDTH, width);
        mediaDetails.addDetail(MediaDetails.INDEX_HEIGHT, height);
        mediaDetails.addDetail(MediaDetails.INDEX_PATH, path);
        mediaDetails.addDetail(MediaDetails.INDEX_DATETIME,
                dateFormatter.format(new Date(dateModifiedInSeconds * 1000)));
        if (sizeInBytes > 0) {
            mediaDetails.addDetail(MediaDetails.INDEX_SIZE, sizeInBytes);
        }
        if (latitude != 0 && longitude != 0) {
            String locationString = String.format(Locale.getDefault(), "%f, %f", latitude,
                    longitude);
            mediaDetails.addDetail(MediaDetails.INDEX_LOCATION, locationString);
        }
        return mediaDetails;
    }

    @Override
    public abstract int getViewType();

    protected abstract BitmapLoadTask getBitmapLoadTask(
            ImageView v, int decodeWidth, int decodeHeight);

    public static class PhotoData extends LocalMediaData {
        private static final String TAG = "CAM_PhotoData";

        public static final int COL_ID = 0;
        public static final int COL_TITLE = 1;
        public static final int COL_MIME_TYPE = 2;
        public static final int COL_DATE_TAKEN = 3;
        public static final int COL_DATE_MODIFIED = 4;
        public static final int COL_DATA = 5;
        public static final int COL_ORIENTATION = 6;
        public static final int COL_WIDTH = 7;
        public static final int COL_HEIGHT = 8;
        public static final int COL_SIZE = 9;
        public static final int COL_LATITUDE = 10;
        public static final int COL_LONGITUDE = 11;

        static final Uri CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        static final String QUERY_ORDER = MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC, "
                + MediaStore.Images.ImageColumns._ID + " DESC";
        /**
         * These values should be kept in sync with column IDs (COL_*) above.
         */
        static final String[] QUERY_PROJECTION = {
                MediaStore.Images.ImageColumns._ID,           // 0, int
                MediaStore.Images.ImageColumns.TITLE,         // 1, string
                MediaStore.Images.ImageColumns.MIME_TYPE,     // 2, string
                MediaStore.Images.ImageColumns.DATE_TAKEN,    // 3, int
                MediaStore.Images.ImageColumns.DATE_MODIFIED, // 4, int
                MediaStore.Images.ImageColumns.DATA,          // 5, string
                MediaStore.Images.ImageColumns.ORIENTATION,   // 6, int, 0, 90, 180, 270
                MediaStore.Images.ImageColumns.WIDTH,         // 7, int
                MediaStore.Images.ImageColumns.HEIGHT,        // 8, int
                MediaStore.Images.ImageColumns.SIZE,          // 9, long
                MediaStore.Images.ImageColumns.LATITUDE,      // 10, double
                MediaStore.Images.ImageColumns.LONGITUDE      // 11, double
        };

        private static final int mSupportedUIActions =
                FilmStripView.ImageData.ACTION_DEMOTE
                        | FilmStripView.ImageData.ACTION_PROMOTE;
        private static final int mSupportedDataActions =
                LocalData.ACTION_DELETE;

        /** 32K buffer. */
        private static final byte[] DECODE_TEMP_STORAGE = new byte[32 * 1024];

        /** from MediaStore, can only be 0, 90, 180, 270 */
        public int orientation;

        static PhotoData buildFromCursor(Cursor c) {
            PhotoData d = new PhotoData();
            d.id = c.getLong(COL_ID);
            d.title = c.getString(COL_TITLE);
            d.mimeType = c.getString(COL_MIME_TYPE);
            d.dateTakenInSeconds = c.getLong(COL_DATE_TAKEN);
            d.dateModifiedInSeconds = c.getLong(COL_DATE_MODIFIED);
            d.path = c.getString(COL_DATA);
            d.orientation = c.getInt(COL_ORIENTATION);
            d.width = c.getInt(COL_WIDTH);
            d.height = c.getInt(COL_HEIGHT);
            if (d.width <= 0 || d.height <= 0) {
                Log.w(TAG, "Warning! zero dimension for "
                        + d.path + ":" + d.width + "x" + d.height);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(d.path, opts);
                if (opts.outWidth != -1 && opts.outHeight != -1) {
                    d.width = opts.outWidth;
                    d.height = opts.outHeight;
                } else {
                    Log.w(TAG, "Warning! dimension decode failed for " + d.path);
                    Bitmap b = BitmapFactory.decodeFile(d.path);
                    if (b == null) {
                        return null;
                    }
                    d.width = b.getWidth();
                    d.height = b.getHeight();
                }
            }
            if (d.orientation == 90 || d.orientation == 270) {
                int b = d.width;
                d.width = d.height;
                d.height = b;
            }
            d.sizeInBytes = c.getLong(COL_SIZE);
            d.latitude = c.getDouble(COL_LATITUDE);
            d.longitude = c.getDouble(COL_LONGITUDE);
            return d;
        }

        @Override
        public String toString() {
            return "Photo:" + ",data=" + path + ",mimeType=" + mimeType
                    + "," + width + "x" + height + ",orientation=" + orientation
                    + ",date=" + new Date(dateTakenInSeconds);
        }

        @Override
        public int getViewType() {
            return TYPE_REMOVABLE_VIEW;
        }

        @Override
        public boolean isUIActionSupported(int action) {
            return ((action & mSupportedUIActions) == action);
        }

        @Override
        public boolean isDataActionSupported(int action) {
            return ((action & mSupportedDataActions) == action);
        }

        @Override
        public boolean delete(Context c) {
            ContentResolver cr = c.getContentResolver();
            cr.delete(CONTENT_URI, MediaStore.Images.ImageColumns._ID + "=" + id, null);
            return super.delete(c);
        }

        @Override
        public Uri getContentUri() {
            Uri baseUri = CONTENT_URI;
            return baseUri.buildUpon().appendPath(String.valueOf(id)).build();
        }

        @Override
        public MediaDetails getMediaDetails(Context context) {
            MediaDetails mediaDetails = super.getMediaDetails(context);
            MediaDetails.extractExifInfo(mediaDetails, path);
            return mediaDetails;
        }

        @Override
        public int getLocalDataType() {
            if (mPanoramaMetadata != null && mPanoramaMetadata.mUsePanoramaViewer) {
                return LOCAL_PHOTO_SPHERE;
            }
            return LOCAL_IMAGE;
        }

        @Override
        public boolean refresh(ContentResolver resolver) {
            Cursor c = resolver.query(
                    getContentUri(), QUERY_PROJECTION, null, null, null);
            if (c == null || !c.moveToFirst()) {
                return false;
            }
            PhotoData newData = buildFromCursor(c);
            id = newData.id;
            title = newData.title;
            mimeType = newData.mimeType;
            dateTakenInSeconds = newData.dateTakenInSeconds;
            dateModifiedInSeconds = newData.dateModifiedInSeconds;
            path = newData.path;
            orientation = newData.orientation;
            width = newData.width;
            height = newData.height;
            return true;
        }

        @Override
        public boolean isPhoto() {
            return true;
        }

        @Override
        protected BitmapLoadTask getBitmapLoadTask(
                ImageView v, int decodeWidth, int decodeHeight) {
            return new PhotoBitmapLoadTask(v, decodeWidth, decodeHeight);
        }

        private final class PhotoBitmapLoadTask extends BitmapLoadTask {
            private int mDecodeWidth;
            private int mDecodeHeight;

            public PhotoBitmapLoadTask(ImageView v, int decodeWidth, int decodeHeight) {
                super(v);
                mDecodeWidth = decodeWidth;
                mDecodeHeight = decodeHeight;
            }

            @Override
            protected Bitmap doInBackground(Void... v) {
                int sampleSize = 1;
                if (width > mDecodeWidth || height > mDecodeHeight) {
                    int heightRatio = Math.round((float) height / (float) mDecodeHeight);
                    int widthRatio = Math.round((float) width / (float) mDecodeWidth);
                    sampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
                }

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sampleSize;
                opts.inTempStorage = DECODE_TEMP_STORAGE;
                if (isCancelled() || !isUsing()) {
                    return null;
                }
                Bitmap b = BitmapFactory.decodeFile(path, opts);
                if (orientation != 0) {
                    if (isCancelled() || !isUsing()) {
                        return null;
                    }
                    Matrix m = new Matrix();
                    m.setRotate(orientation);
                    b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, false);
                }
                return b;
            }
        }
    }

    public static class VideoData extends LocalMediaData {
        public static final int COL_ID = 0;
        public static final int COL_TITLE = 1;
        public static final int COL_MIME_TYPE = 2;
        public static final int COL_DATE_TAKEN = 3;
        public static final int COL_DATE_MODIFIED = 4;
        public static final int COL_DATA = 5;
        public static final int COL_WIDTH = 6;
        public static final int COL_HEIGHT = 7;
        public static final int COL_RESOLUTION = 8;
        public static final int COL_SIZE = 9;
        public static final int COL_LATITUDE = 10;
        public static final int COL_LONGITUDE = 11;
        public static final int COL_DURATION = 12;

        static final Uri CONTENT_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        private static final int mSupportedUIActions =
                FilmStripView.ImageData.ACTION_DEMOTE
                        | FilmStripView.ImageData.ACTION_PROMOTE;
        private static final int mSupportedDataActions =
                LocalData.ACTION_DELETE
                        | LocalData.ACTION_PLAY;

        static final String QUERY_ORDER = MediaStore.Video.VideoColumns.DATE_TAKEN + " DESC, "
                + MediaStore.Video.VideoColumns._ID + " DESC";
        /**
         * These values should be kept in sync with column IDs (COL_*) above.
         */
        static final String[] QUERY_PROJECTION = {
                MediaStore.Video.VideoColumns._ID,           // 0, int
                MediaStore.Video.VideoColumns.TITLE,         // 1, string
                MediaStore.Video.VideoColumns.MIME_TYPE,     // 2, string
                MediaStore.Video.VideoColumns.DATE_TAKEN,    // 3, int
                MediaStore.Video.VideoColumns.DATE_MODIFIED, // 4, int
                MediaStore.Video.VideoColumns.DATA,          // 5, string
                MediaStore.Video.VideoColumns.WIDTH,         // 6, int
                MediaStore.Video.VideoColumns.HEIGHT,        // 7, int
                MediaStore.Video.VideoColumns.RESOLUTION,    // 8 string
                MediaStore.Video.VideoColumns.SIZE,          // 9 long
                MediaStore.Video.VideoColumns.LATITUDE,      // 10 double
                MediaStore.Video.VideoColumns.LONGITUDE,     // 11 double
                MediaStore.Video.VideoColumns.DURATION       // 12 long
        };

        private Uri mPlayUri;

        /** The duration in milliseconds. */
        private long durationInSeconds;

        static VideoData buildFromCursor(Cursor c) {
            VideoData d = new VideoData();
            d.id = c.getLong(COL_ID);
            d.title = c.getString(COL_TITLE);
            d.mimeType = c.getString(COL_MIME_TYPE);
            d.dateTakenInSeconds = c.getLong(COL_DATE_TAKEN);
            d.dateModifiedInSeconds = c.getLong(COL_DATE_MODIFIED);
            d.path = c.getString(COL_DATA);
            d.width = c.getInt(COL_WIDTH);
            d.height = c.getInt(COL_HEIGHT);
            d.mPlayUri = d.getContentUri();
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            String rotation = null;
            try {
                retriever.setDataSource(d.path);
            } catch (IllegalArgumentException ex) {
                retriever.release();
                Log.e(TAG, "MediaMetadataRetriever.setDataSource() fail:"
                        + ex.getMessage());
                return null;
            }
            rotation = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (d.width == 0 || d.height == 0) {
                retrieveVideoDimension(retriever, d);
            }
            retriever.release();
            if (d.width == 0 || d.height == 0) {
                // Width or height is still not available.
                Log.e(TAG, "Unable to retrieve dimension of video:" + d.path);
                return null;
            }
            if (rotation != null
                    && (rotation.equals("90") || rotation.equals("270"))) {
                int b = d.width;
                d.width = d.height;
                d.height = b;
            }

            d.sizeInBytes = c.getLong(COL_SIZE);
            d.latitude = c.getDouble(COL_LATITUDE);
            d.longitude = c.getDouble(COL_LONGITUDE);
            d.durationInSeconds = c.getLong(COL_DURATION) / 1000;
            return d;
        }

        @Override
        public String toString() {
            return "Video:" + ",data=" + path + ",mimeType=" + mimeType
                    + "," + width + "x" + height + ",date=" + new Date(dateTakenInSeconds);
        }

        @Override
        public int getViewType() {
            return TYPE_REMOVABLE_VIEW;
        }

        @Override
        public boolean isUIActionSupported(int action) {
            return ((action & mSupportedUIActions) == action);
        }

        @Override
        public boolean isDataActionSupported(int action) {
            return ((action & mSupportedDataActions) == action);
        }

        @Override
        public boolean delete(Context ctx) {
            ContentResolver cr = ctx.getContentResolver();
            cr.delete(CONTENT_URI, MediaStore.Video.VideoColumns._ID + "=" + id, null);
            return super.delete(ctx);
        }

        @Override
        public Uri getContentUri() {
            Uri baseUri = CONTENT_URI;
            return baseUri.buildUpon().appendPath(String.valueOf(id)).build();
        }

        @Override
        public MediaDetails getMediaDetails(Context context) {
            MediaDetails mediaDetails = super.getMediaDetails(context);
            String duration = MediaDetails.formatDuration(context, durationInSeconds);
            mediaDetails.addDetail(MediaDetails.INDEX_DURATION, duration);
            return mediaDetails;
        }

        @Override
        public int getLocalDataType() {
            return LOCAL_VIDEO;
        }

        @Override
        public boolean refresh(ContentResolver resolver) {
            Cursor c = resolver.query(
                    getContentUri(), QUERY_PROJECTION, null, null, null);
            if (c == null || !c.moveToFirst()) {
                return false;
            }
            VideoData newData = buildFromCursor(c);
            if (newData == null) {
                return false;
            }
            id = newData.id;
            title = newData.title;
            mimeType = newData.mimeType;
            dateTakenInSeconds = newData.dateTakenInSeconds;
            dateModifiedInSeconds = newData.dateModifiedInSeconds;
            path = newData.path;
            width = newData.width;
            height = newData.height;
            mPlayUri = newData.mPlayUri;
            return true;
        }

        @Override
        public View getView(final Context ctx,
                int decodeWidth, int decodeHeight, Drawable placeHolder) {

            // ImageView for the bitmap.
            ImageView iv = new ImageView(ctx);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            fillImageView(ctx, iv, decodeWidth, decodeHeight, placeHolder);

            // ImageView for the play icon.
            ImageView icon = new ImageView(ctx);
            icon.setImageResource(R.drawable.ic_control_play);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            icon.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            icon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CameraUtil.playVideo(ctx, mPlayUri, title);
                }
            });

            FrameLayout f = new FrameLayout(ctx);
            f.addView(iv);
            f.addView(icon);
            return f;
        }

        @Override
        public boolean isPhoto() {
            return false;
        }

        @Override
        protected BitmapLoadTask getBitmapLoadTask(
                ImageView v, int decodeWidth, int decodeHeight) {
            return new VideoBitmapLoadTask(v);
        }

        private final class VideoBitmapLoadTask extends BitmapLoadTask {

            public VideoBitmapLoadTask(ImageView v) {
                super(v);
            }

            @Override
            protected Bitmap doInBackground(Void... v) {
                if (isCancelled() || !isUsing()) {
                    return null;
                }
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(path);
                byte[] data = retriever.getEmbeddedPicture();
                Bitmap bitmap = null;
                if (isCancelled() || !isUsing()) {
                    retriever.release();
                    return null;
                }
                if (data != null) {
                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                }
                if (bitmap == null) {
                    bitmap = retriever.getFrameAtTime();
                }
                retriever.release();
                return bitmap;
            }
        }

        /**
         * Extracts video height/width if available. If unavailable, set to 0.
         *
         * @param retriever An initialized metadata retriever.
         * @param d The {@link VideoData} whose width/height are to update.
         */
        private static void retrieveVideoDimension(
                MediaMetadataRetriever retriever, VideoData d) {
            String val = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            d.width = (val == null) ? 0 : Integer.parseInt(val);
            val = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            d.height = (val == null) ? 0 : Integer.parseInt(val);
        }
    }

    /**
     * An {@link AsyncTask} class that loads the bitmap in the background
     * thread. Sub-classes should implement their own
     * {@code BitmapLoadTask#doInBackground(Void...)}."
     */
    protected abstract class BitmapLoadTask extends AsyncTask<Void, Void, Bitmap> {
        protected ImageView mView;

        protected BitmapLoadTask(ImageView v) {
            mView = v;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (!isUsing()) {
                return;
            }
            if (bitmap == null) {
                Log.e(TAG, "Failed decoding bitmap for file:" + path);
                return;
            }
            BitmapDrawable d = new BitmapDrawable(bitmap);
            mView.setScaleType(ImageView.ScaleType.FIT_XY);
            mView.setImageDrawable(d);
        }
    }
}
