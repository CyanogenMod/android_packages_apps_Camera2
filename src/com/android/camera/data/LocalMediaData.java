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
import android.media.CamcorderProfile;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.Storage;
import com.android.camera.debug.Log;
import com.android.camera2.R;
import com.bumptech.glide.BitmapRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.BitmapEncoder;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A base class for all the local media files. The bitmap is loaded in
 * background thread. Subclasses should implement their own background loading
 * thread by sub-classing BitmapLoadTask and overriding doInBackground() to
 * return a bitmap.
 */
public abstract class LocalMediaData implements LocalData {
    /** The minimum id to use to query for all media at a given media store uri */
    static final int QUERY_ALL_MEDIA_ID = -1;
    private static final String CAMERA_PATH = Storage.DIRECTORY + "%";
    private static final String SELECT_BY_PATH = MediaStore.MediaColumns.DATA + " LIKE ?";
    private static final int MEDIASTORE_THUMB_WIDTH = 512;
    private static final int MEDIASTORE_THUMB_HEIGHT = 384;

    protected final long mContentId;
    protected final String mTitle;
    protected final String mMimeType;
    protected final long mDateTakenInMilliSeconds;
    protected final long mDateModifiedInSeconds;
    protected final String mPath;
    // width and height should be adjusted according to orientation.
    protected final int mWidth;
    protected final int mHeight;
    protected final long mSizeInBytes;
    protected final double mLatitude;
    protected final double mLongitude;
    protected final Bundle mMetaData;

    private static final int JPEG_COMPRESS_QUALITY = 90;
    private static final BitmapEncoder JPEG_ENCODER =
            new BitmapEncoder(Bitmap.CompressFormat.JPEG, JPEG_COMPRESS_QUALITY);

    /**
     * Used for thumbnail loading optimization. True if this data has a
     * corresponding visible view.
     */
    protected Boolean mUsing = false;

    public LocalMediaData(long contentId, String title, String mimeType,
            long dateTakenInMilliSeconds, long dateModifiedInSeconds, String path,
            int width, int height, long sizeInBytes, double latitude,
            double longitude) {
        mContentId = contentId;
        mTitle = title;
        mMimeType = mimeType;
        mDateTakenInMilliSeconds = dateTakenInMilliSeconds;
        mDateModifiedInSeconds = dateModifiedInSeconds;
        mPath = path;
        mWidth = width;
        mHeight = height;
        mSizeInBytes = sizeInBytes;
        mLatitude = latitude;
        mLongitude = longitude;
        mMetaData = new Bundle();
    }

    private interface CursorToLocalData {
        public LocalData build(Cursor cursor);
    }

    private static List<LocalData> queryLocalMediaData(ContentResolver contentResolver,
            Uri contentUri, String[] projection, long minimumId, String orderBy,
            CursorToLocalData builder) {
        String selection = SELECT_BY_PATH + " AND " + MediaStore.MediaColumns._ID + " > ?";
        String[] selectionArgs = new String[] { CAMERA_PATH, Long.toString(minimumId) };

        Cursor cursor = contentResolver.query(contentUri, projection,
                selection, selectionArgs, orderBy);
        List<LocalData> result = new ArrayList<LocalData>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                LocalData data = builder.build(cursor);
                if (data != null) {
                    result.add(data);
                } else {
                    final int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                    Log.e(TAG, "Error loading data:" + cursor.getString(dataIndex));
                }
            }

            cursor.close();
        }
        return result;
    }

    @Override
    public long getDateTaken() {
        return mDateTakenInMilliSeconds;
    }

    @Override
    public long getDateModified() {
        return mDateModifiedInSeconds;
    }

    @Override
    public long getContentId() {
        return mContentId;
    }

    @Override
    public String getTitle() {
        return mTitle;
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
    public int getRotation() {
        return 0;
    }

    @Override
    public String getPath() {
        return mPath;
    }

    @Override
    public long getSizeInBytes() {
        return mSizeInBytes;
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
    public boolean delete(Context context) {
        File f = new File(mPath);
        return f.delete();
    }

    @Override
    public void onFullScreen(boolean fullScreen) {
        // do nothing.
    }

    @Override
    public boolean canSwipeInFullScreen() {
        return true;
    }

    protected ImageView fillImageView(Context context, ImageView v,
            int thumbWidth, int thumbHeight, int placeHolderResourceId,
            LocalDataAdapter adapter, boolean isInProgress) {
        Glide.with(context)
            .loadFromMediaStore(getUri(), mMimeType, mDateModifiedInSeconds, 0)
            .fitCenter()
            .placeholder(placeHolderResourceId)
            .into(v);

        v.setContentDescription(context.getResources().getString(
                R.string.media_date_content_description,
                getReadableDate(mDateModifiedInSeconds)));

        return v;
    }

    @Override
    public View getView(Context context, View recycled, int thumbWidth, int thumbHeight,
            int placeHolderResourceId, LocalDataAdapter adapter, boolean isInProgress,
            ActionCallback actionCallback) {
        final ImageView imageView;
        if (recycled != null) {
            imageView = (ImageView) recycled;
        } else {
            imageView = (ImageView) LayoutInflater.from(context)
                .inflate(R.layout.filmstrip_image, null);
            imageView.setTag(R.id.mediadata_tag_viewtype, getItemViewType().ordinal());
        }

        return fillImageView(context, imageView, thumbWidth, thumbHeight,
                placeHolderResourceId, adapter, isInProgress);
    }

    @Override
    public void loadFullImage(Context context, int thumbWidth, int thumbHeight, View view,
            LocalDataAdapter adapter) {
        // Default is do nothing.
        // Can be implemented by sub-classes.
    }

    @Override
    public void prepare() {
        synchronized (mUsing) {
            mUsing = true;
        }
    }

    @Override
    public void recycle(View view) {
        synchronized (mUsing) {
            mUsing = false;
        }
    }

    @Override
    public double[] getLatLong() {
        if (mLatitude == 0 && mLongitude == 0) {
            return null;
        }
        return new double[] {
                mLatitude, mLongitude
        };
    }

    protected boolean isUsing() {
        synchronized (mUsing) {
            return mUsing;
        }
    }

    @Override
    public String getMimeType() {
        return mMimeType;
    }

    @Override
    public MediaDetails getMediaDetails(Context context) {
        MediaDetails mediaDetails = new MediaDetails();
        mediaDetails.addDetail(MediaDetails.INDEX_TITLE, mTitle);
        mediaDetails.addDetail(MediaDetails.INDEX_WIDTH, mWidth);
        mediaDetails.addDetail(MediaDetails.INDEX_HEIGHT, mHeight);
        mediaDetails.addDetail(MediaDetails.INDEX_PATH, mPath);
        mediaDetails.addDetail(MediaDetails.INDEX_DATETIME,
                getReadableDate(mDateModifiedInSeconds));
        if (mSizeInBytes > 0) {
            mediaDetails.addDetail(MediaDetails.INDEX_SIZE, mSizeInBytes);
        }
        if (mLatitude != 0 && mLongitude != 0) {
            String locationString = String.format(Locale.getDefault(), "%f, %f", mLatitude,
                    mLongitude);
            mediaDetails.addDetail(MediaDetails.INDEX_LOCATION, locationString);
        }
        return mediaDetails;
    }

    private static String getReadableDate(long dateInSeconds) {
        DateFormat dateFormatter = DateFormat.getDateTimeInstance();
        return dateFormatter.format(new Date(dateInSeconds * 1000));
    }

    @Override
    public abstract int getViewType();

    @Override
    public Bundle getMetadata() {
        return mMetaData;
    }

    @Override
    public boolean isMetadataUpdated() {
        return MetadataLoader.isMetadataCached(this);
    }

    public static final class PhotoData extends LocalMediaData {
        private static final Log.Tag TAG = new Log.Tag("PhotoData");

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

        // GL max texture size: keep bitmaps below this value.
        private static final int MAXIMUM_TEXTURE_SIZE = 2048;

        static final Uri CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        // Sort all data by ID. This must be aligned with
        // {@link CameraDataAdapter.QueryTask} which relies on the highest ID
        // being first in any data returned.
        private static final String QUERY_ORDER = MediaStore.Images.ImageColumns._ID + " DESC";
        /**
         * These values should be kept in sync with column IDs (COL_*) above.
         */
        private static final String[] QUERY_PROJECTION = {
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

        private static final int mSupportedUIActions = ACTION_DEMOTE | ACTION_PROMOTE | ACTION_ZOOM;
        private static final int mSupportedDataActions =
                DATA_ACTION_DELETE | DATA_ACTION_EDIT | DATA_ACTION_SHARE;

        /** from MediaStore, can only be 0, 90, 180, 270 */
        private final int mOrientation;
        /** @see #getSignature() */
        private final String mSignature;

        public static LocalData fromContentUri(ContentResolver cr, Uri contentUri) {
            List<LocalData> newPhotos = query(cr, contentUri, QUERY_ALL_MEDIA_ID);
            if (newPhotos.isEmpty()) {
                return null;
            }
            return newPhotos.get(0);
        }

        public PhotoData(long id, String title, String mimeType,
                long dateTakenInMilliSeconds, long dateModifiedInSeconds,
                String path, int orientation, int width, int height,
                long sizeInBytes, double latitude, double longitude) {
            super(id, title, mimeType, dateTakenInMilliSeconds, dateModifiedInSeconds,
                    path, width, height, sizeInBytes, latitude, longitude);
            mOrientation = orientation;
            mSignature = mimeType + orientation + dateModifiedInSeconds;
        }

        static List<LocalData> query(ContentResolver cr, Uri uri, long lastId) {
            return queryLocalMediaData(cr, uri, QUERY_PROJECTION, lastId, QUERY_ORDER,
                    new PhotoDataBuilder());
        }

        private static PhotoData buildFromCursor(Cursor c) {
            long id = c.getLong(COL_ID);
            String title = c.getString(COL_TITLE);
            String mimeType = c.getString(COL_MIME_TYPE);
            long dateTakenInMilliSeconds = c.getLong(COL_DATE_TAKEN);
            long dateModifiedInSeconds = c.getLong(COL_DATE_MODIFIED);
            String path = c.getString(COL_DATA);
            int orientation = c.getInt(COL_ORIENTATION);
            int width = c.getInt(COL_WIDTH);
            int height = c.getInt(COL_HEIGHT);
            if (width <= 0 || height <= 0) {
                Log.w(TAG, "Zero dimension in ContentResolver for "
                        + path + ":" + width + "x" + height);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, opts);
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    width = opts.outWidth;
                    height = opts.outHeight;
                } else {
                    Log.w(TAG, "Dimension decode failed for " + path);
                    Bitmap b = BitmapFactory.decodeFile(path);
                    if (b == null) {
                        Log.w(TAG, "PhotoData skipped."
                                + " Decoding " + path + "failed.");
                        return null;
                    }
                    width = b.getWidth();
                    height = b.getHeight();
                    if (width == 0 || height == 0) {
                        Log.w(TAG, "PhotoData skipped. Bitmap size 0 for " + path);
                        return null;
                    }
                }
            }

            long sizeInBytes = c.getLong(COL_SIZE);
            double latitude = c.getDouble(COL_LATITUDE);
            double longitude = c.getDouble(COL_LONGITUDE);
            PhotoData result = new PhotoData(id, title, mimeType, dateTakenInMilliSeconds,
                    dateModifiedInSeconds, path, orientation, width, height,
                    sizeInBytes, latitude, longitude);
            return result;
        }

        @Override
        public int getRotation() {
            return mOrientation;
        }

        @Override
        public String toString() {
            return "Photo:" + ",data=" + mPath + ",mimeType=" + mMimeType
                    + "," + mWidth + "x" + mHeight + ",orientation=" + mOrientation
                    + ",date=" + new Date(mDateTakenInMilliSeconds);
        }

        @Override
        public int getViewType() {
            return VIEW_TYPE_REMOVABLE;
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
        public boolean delete(Context context) {
            ContentResolver cr = context.getContentResolver();
            cr.delete(CONTENT_URI, MediaStore.Images.ImageColumns._ID + "=" + mContentId, null);
            return super.delete(context);
        }

        @Override
        public Uri getUri() {
            Uri baseUri = CONTENT_URI;
            return baseUri.buildUpon().appendPath(String.valueOf(mContentId)).build();
        }

        @Override
        public MediaDetails getMediaDetails(Context context) {
            MediaDetails mediaDetails = super.getMediaDetails(context);
            MediaDetails.extractExifInfo(mediaDetails, mPath);
            mediaDetails.addDetail(MediaDetails.INDEX_ORIENTATION, mOrientation);
            return mediaDetails;
        }

        @Override
        public int getLocalDataType() {
            return LOCAL_IMAGE;
        }

        @Override
        public LocalData refresh(Context context) {
            PhotoData newData = null;
            Cursor c = context.getContentResolver().query(getUri(), QUERY_PROJECTION, null,
                    null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    newData = buildFromCursor(c);
                }
                c.close();
            }

            return newData;
        }

        @Override
        public String getSignature() {
            return mSignature;
        }

        @Override
        protected ImageView fillImageView(Context context, final ImageView v, final int thumbWidth,
                final int thumbHeight, int placeHolderResourceId, LocalDataAdapter adapter,
                boolean isInProgress) {
            loadImage(context, v, thumbWidth, thumbHeight, placeHolderResourceId, false);

            int stringId = R.string.photo_date_content_description;
            if (PanoramaMetadataLoader.isPanorama(this) ||
                PanoramaMetadataLoader.isPanorama360(this)) {
                stringId = R.string.panorama_date_content_description;
            } else if (PanoramaMetadataLoader.isPanoramaAndUseViewer(this)) {
                // assume it's a PhotoSphere
                stringId = R.string.photosphere_date_content_description;
            } else if (RgbzMetadataLoader.hasRGBZData(this)) {
                stringId = R.string.refocus_date_content_description;
            }

            v.setContentDescription(context.getResources().getString(
                    stringId,
                    getReadableDate(mDateModifiedInSeconds)));

            return v;
        }

        private void loadImage(Context context, ImageView imageView, int thumbWidth,
                int thumbHeight, int placeHolderResourceId, boolean full) {

            //TODO: Figure out why these can be <= 0.
            if (thumbWidth <= 0 || thumbHeight <=0) {
                return;
            }

            final int overrideWidth;
            final int overrideHeight;
            final BitmapRequestBuilder<Uri, Bitmap> thumbnailRequest;
            if (full) {
                // Load up to the maximum size Bitmap we can render.
                overrideWidth = Math.min(getWidth(), MAXIMUM_TEXTURE_SIZE);
                overrideHeight = Math.min(getHeight(), MAXIMUM_TEXTURE_SIZE);

                // Load two thumbnails, first the small low quality thumb from the media store,
                // then a medium quality thumbWidth/thumbHeight image. Using two thumbnails ensures
                // we don't flicker to grey while we load the maximum size image.
                thumbnailRequest = loadUri(context)
                    .override(thumbWidth, thumbHeight)
                    .fitCenter()
                    .thumbnail(loadMediaStoreThumb(context));
            } else {
                // Load a medium quality thumbWidth/thumbHeight image.
                overrideWidth = thumbWidth;
                overrideHeight = thumbHeight;

                // Load a single small low quality thumbnail from the media store.
                thumbnailRequest = loadMediaStoreThumb(context);
            }

            loadUri(context)
                .placeholder(placeHolderResourceId)
                .fitCenter()
                .override(overrideWidth, overrideHeight)
                .thumbnail(thumbnailRequest)
                .into(imageView);
        }

        /** Loads a thumbnail with a size targeted to use MediaStore.Images.Thumbnails. */
        private BitmapRequestBuilder<Uri, Bitmap> loadMediaStoreThumb(Context context) {
            return loadUri(context)
                .override(MEDIASTORE_THUMB_WIDTH, MEDIASTORE_THUMB_HEIGHT);
        }

        /** Loads an image using a MediaStore Uri with our default options. */
        private BitmapRequestBuilder<Uri, Bitmap> loadUri(Context context) {
            return Glide.with(context)
                .loadFromMediaStore(getUri(), mMimeType, mDateModifiedInSeconds, mOrientation)
                .asBitmap()
                .encoder(JPEG_ENCODER);
        }

        @Override
        public void recycle(View view) {
            super.recycle(view);
            if (view != null) {
                Glide.clear(view);
            }
        }

        @Override
        public LocalDataViewType getItemViewType() {
            return LocalDataViewType.PHOTO;
        }

        @Override
        public void loadFullImage(Context context, int thumbWidth, int thumbHeight, View v,
            LocalDataAdapter adapter)
        {
            loadImage(context, (ImageView) v, thumbWidth, thumbHeight, 0, true);
        }

        private static class PhotoDataBuilder implements CursorToLocalData {
            @Override
            public PhotoData build(Cursor cursor) {
                return LocalMediaData.PhotoData.buildFromCursor(cursor);
            }
        }
    }

    public static final class VideoData extends LocalMediaData {
        public static final int COL_ID = 0;
        public static final int COL_TITLE = 1;
        public static final int COL_MIME_TYPE = 2;
        public static final int COL_DATE_TAKEN = 3;
        public static final int COL_DATE_MODIFIED = 4;
        public static final int COL_DATA = 5;
        public static final int COL_WIDTH = 6;
        public static final int COL_HEIGHT = 7;
        public static final int COL_SIZE = 8;
        public static final int COL_LATITUDE = 9;
        public static final int COL_LONGITUDE = 10;
        public static final int COL_DURATION = 11;

        static final Uri CONTENT_URI = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

        private static final int mSupportedUIActions = ACTION_DEMOTE | ACTION_PROMOTE;
        private static final int mSupportedDataActions =
                DATA_ACTION_DELETE | DATA_ACTION_PLAY | DATA_ACTION_SHARE;

        private static final String QUERY_ORDER = MediaStore.Video.VideoColumns.DATE_TAKEN
                + " DESC, " + MediaStore.Video.VideoColumns._ID + " DESC";
        /**
         * These values should be kept in sync with column IDs (COL_*) above.
         */
        private static final String[] QUERY_PROJECTION = {
                MediaStore.Video.VideoColumns._ID,           // 0, int
                MediaStore.Video.VideoColumns.TITLE,         // 1, string
                MediaStore.Video.VideoColumns.MIME_TYPE,     // 2, string
                MediaStore.Video.VideoColumns.DATE_TAKEN,    // 3, int
                MediaStore.Video.VideoColumns.DATE_MODIFIED, // 4, int
                MediaStore.Video.VideoColumns.DATA,          // 5, string
                MediaStore.Video.VideoColumns.WIDTH,         // 6, int
                MediaStore.Video.VideoColumns.HEIGHT,        // 7, int
                MediaStore.Video.VideoColumns.SIZE,          // 8 long
                MediaStore.Video.VideoColumns.LATITUDE,      // 9 double
                MediaStore.Video.VideoColumns.LONGITUDE,     // 10 double
                MediaStore.Video.VideoColumns.DURATION       // 11 long
        };

        /** The duration in milliseconds. */
        private final long mDurationInSeconds;
        private final String mSignature;

        public VideoData(long id, String title, String mimeType,
                long dateTakenInMilliSeconds, long dateModifiedInSeconds,
                String path, int width, int height, long sizeInBytes,
                double latitude, double longitude, long durationInSeconds) {
            super(id, title, mimeType, dateTakenInMilliSeconds, dateModifiedInSeconds,
                    path, width, height, sizeInBytes, latitude, longitude);
            mDurationInSeconds = durationInSeconds;
            mSignature = mimeType + dateModifiedInSeconds;
        }

        public static LocalData fromContentUri(ContentResolver cr, Uri contentUri) {
            List<LocalData> newVideos = query(cr, contentUri, QUERY_ALL_MEDIA_ID);
            if (newVideos.isEmpty()) {
                return null;
            }
            return newVideos.get(0);
        }

        static List<LocalData> query(ContentResolver cr, Uri uri, long lastId) {
            return queryLocalMediaData(cr, uri, QUERY_PROJECTION, lastId, QUERY_ORDER,
                    new VideoDataBuilder());
        }

        /**
         * We can't trust the media store and we can't afford the performance overhead of
         * synchronously decoding the video header for every item when loading our data set
         * from the media store, so we instead run the metadata loader in the background
         * to decode the video header for each item and prefer whatever values it obtains.
         */
        private int getBestWidth() {
            int metadataWidth = VideoRotationMetadataLoader.getWidth(this);
            if (metadataWidth > 0) {
                return metadataWidth;
            } else {
                return mWidth;
            }
        }

        private int getBestHeight() {
            int metadataHeight = VideoRotationMetadataLoader.getHeight(this);
            if (metadataHeight > 0) {
                return metadataHeight;
            } else {
                return mHeight;
            }
        }

        /**
         * If the metadata loader has determined from the video header that we need to rotate the video
         * 90 or 270 degrees, then we swap the width and height.
         */
        @Override
        public int getWidth() {
            return VideoRotationMetadataLoader.isRotated(this) ? getBestHeight() : getBestWidth();
        }

        @Override
        public int getHeight() {
            return VideoRotationMetadataLoader.isRotated(this) ?  getBestWidth() : getBestHeight();
        }

        private static VideoData buildFromCursor(Cursor c) {
            long id = c.getLong(COL_ID);
            String title = c.getString(COL_TITLE);
            String mimeType = c.getString(COL_MIME_TYPE);
            long dateTakenInMilliSeconds = c.getLong(COL_DATE_TAKEN);
            long dateModifiedInSeconds = c.getLong(COL_DATE_MODIFIED);
            String path = c.getString(COL_DATA);
            int width = c.getInt(COL_WIDTH);
            int height = c.getInt(COL_HEIGHT);

            // If the media store doesn't contain a width and a height, use the width and height
            // of the default camera mode instead. When the metadata loader runs, it will set the
            // correct values.
            if (width == 0 || height == 0) {
                Log.w(TAG, "failed to retrieve width and height from the media store, defaulting " +
                        " to camera profile");
                CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
                width = profile.videoFrameWidth;
                height = profile.videoFrameHeight;
            }

            long sizeInBytes = c.getLong(COL_SIZE);
            double latitude = c.getDouble(COL_LATITUDE);
            double longitude = c.getDouble(COL_LONGITUDE);
            long durationInSeconds = c.getLong(COL_DURATION) / 1000;
            VideoData d = new VideoData(id, title, mimeType, dateTakenInMilliSeconds,
                    dateModifiedInSeconds, path, width, height, sizeInBytes,
                    latitude, longitude, durationInSeconds);
            return d;
        }

        @Override
        public String toString() {
            return "Video:" + ",data=" + mPath + ",mimeType=" + mMimeType
                    + "," + mWidth + "x" + mHeight + ",date=" + new Date(mDateTakenInMilliSeconds);
        }

        @Override
        public int getViewType() {
            return VIEW_TYPE_REMOVABLE;
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
        public boolean delete(Context context) {
            ContentResolver cr = context.getContentResolver();
            cr.delete(CONTENT_URI, MediaStore.Video.VideoColumns._ID + "=" + mContentId, null);
            return super.delete(context);
        }

        @Override
        public Uri getUri() {
            Uri baseUri = CONTENT_URI;
            return baseUri.buildUpon().appendPath(String.valueOf(mContentId)).build();
        }

        @Override
        public MediaDetails getMediaDetails(Context context) {
            MediaDetails mediaDetails = super.getMediaDetails(context);
            String duration = MediaDetails.formatDuration(context, mDurationInSeconds);
            mediaDetails.addDetail(MediaDetails.INDEX_DURATION, duration);
            return mediaDetails;
        }

        @Override
        public int getLocalDataType() {
            return LOCAL_VIDEO;
        }

        @Override
        public LocalData refresh(Context context) {
            Cursor c = context.getContentResolver().query(getUri(), QUERY_PROJECTION, null,
                    null, null);
            if (c == null || !c.moveToFirst()) {
                return null;
            }
            VideoData newData = buildFromCursor(c);
            return newData;
        }

        @Override
        public String getSignature() {
            return mSignature;
        }

        @Override
        protected ImageView fillImageView(Context context, final ImageView v, final int thumbWidth,
                final int thumbHeight, int placeHolderResourceId, LocalDataAdapter adapter,
                boolean isInProgress) {

            //TODO: Figure out why these can be <= 0.
            if (thumbWidth <= 0 || thumbHeight <=0) {
                return v;
            }

            Glide.with(context)
                .loadFromMediaStore(getUri(), mMimeType, mDateModifiedInSeconds, 0)
                .asBitmap()
                .encoder(JPEG_ENCODER)
                .thumbnail(Glide.with(context)
                    .loadFromMediaStore(getUri(), mMimeType, mDateModifiedInSeconds, 0)
                    .asBitmap()
                    .encoder(JPEG_ENCODER)
                    .override(MEDIASTORE_THUMB_WIDTH, MEDIASTORE_THUMB_HEIGHT))
                .placeholder(placeHolderResourceId)
                .fitCenter()
                .override(thumbWidth, thumbHeight)
                .into(v);

            // Content descriptions applied to parent FrameView
            // see getView

            return v;
        }

        @Override
        public View getView(final Context context, View recycled,
                int thumbWidth, int thumbHeight, int placeHolderResourceId,
                LocalDataAdapter adapter, boolean isInProgress,
                final ActionCallback actionCallback) {

            final VideoViewHolder viewHolder;
            final View result;
            if (recycled != null) {
                result = recycled;
                viewHolder = (VideoViewHolder) recycled.getTag(R.id.mediadata_tag_target);
            } else {
                result = LayoutInflater.from(context).inflate(R.layout.filmstrip_video, null);
                result.setTag(R.id.mediadata_tag_viewtype, getItemViewType().ordinal());
                ImageView videoView = (ImageView) result.findViewById(R.id.video_view);
                ImageView playButton = (ImageView) result.findViewById(R.id.play_button);
                viewHolder = new VideoViewHolder(videoView, playButton);
                result.setTag(R.id.mediadata_tag_target, viewHolder);
            }

            fillImageView(context, viewHolder.mVideoView, thumbWidth, thumbHeight,
                    placeHolderResourceId, adapter, isInProgress);

            // ImageView for the play icon.
            viewHolder.mPlayButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    actionCallback.playVideo(getUri(), mTitle);
                }
            });

            result.setContentDescription(context.getResources().getString(
                    R.string.video_date_content_description,
                    getReadableDate(mDateModifiedInSeconds)));

            return result;
        }

        @Override
        public void recycle(View view) {
            super.recycle(view);
            VideoViewHolder videoViewHolder =
                    (VideoViewHolder) view.getTag(R.id.mediadata_tag_target);
            Glide.clear(videoViewHolder.mVideoView);
        }

        @Override
        public LocalDataViewType getItemViewType() {
            return LocalDataViewType.VIDEO;
        }
    }

    private static class VideoDataBuilder implements CursorToLocalData {

        @Override
        public VideoData build(Cursor cursor) {
            return LocalMediaData.VideoData.buildFromCursor(cursor);
        }
    }

     private static class VideoViewHolder {
        private final ImageView mVideoView;
        private final ImageView mPlayButton;

        public VideoViewHolder(ImageView videoView, ImageView playButton) {
            mVideoView = videoView;
            mPlayButton = playButton;
        }
    }
}
