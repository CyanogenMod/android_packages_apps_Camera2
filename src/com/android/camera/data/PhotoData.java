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
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.data.LocalDataQuery.CursorToLocalDataFactory;
import com.android.camera.debug.Log;
import com.android.camera2.R;
import com.bumptech.glide.DrawableRequestBuilder;
import com.bumptech.glide.DrawableTypeRequest;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.Date;
import java.util.List;

public class PhotoData extends LocalMediaData {
    private static final Log.Tag TAG = new Log.Tag("PhotoData");

    // GL max texture size: keep bitmaps below this value.
    private static final int MAXIMUM_TEXTURE_SIZE = 2048;

    static final Uri CONTENT_URI = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    // Sort all data by ID. This must be aligned with
    // {@link CameraDataAdapter.QueryTask} which relies on the highest ID
    // being first in any data returned.
    private static final String QUERY_ORDER = MediaStore.Images.ImageColumns._ID + " DESC";


    private static final int mSupportedUIActions =
          LocalMediaData.ACTION_DEMOTE | LocalMediaData.ACTION_PROMOTE | LocalMediaData.ACTION_ZOOM;
    private static final int mSupportedDataActions =
          DATA_ACTION_DELETE | DATA_ACTION_EDIT | DATA_ACTION_SHARE;

    /** from MediaStore, can only be 0, 90, 180, 270 */
    private final int mOrientation;

    /** @see #getSignature() */
    private final String mSignature;

    public PhotoData(long id, String title, String mimeType,
          long dateTakenInMilliSeconds, long dateModifiedInSeconds,
          String path, int orientation, int width, int height,
          long sizeInBytes, double latitude, double longitude) {
        super(id, title, mimeType, dateTakenInMilliSeconds, dateModifiedInSeconds,
              path, width, height, sizeInBytes, latitude, longitude);
        mOrientation = orientation;
        mSignature = mimeType + orientation + dateModifiedInSeconds;
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
        return new PhotoDataFactory().get(context, getUri());
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
          LocalDataAdapter adapter) {
        loadImage(context, (ImageView) v, thumbWidth, thumbHeight, 0, true);
    }

    private void loadImage(Context context, ImageView imageView, int thumbWidth,
          int thumbHeight, int placeHolderResourceId, boolean full) {

        //TODO: Figure out why these can be <= 0.
        if (thumbWidth <= 0 || thumbHeight <=0) {
            return;
        }

        final int overrideWidth;
        final int overrideHeight;

        final DrawableRequestBuilder<Uri> thumbnailRequest;
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
              .diskCacheStrategy(full ? DiskCacheStrategy.NONE : DiskCacheStrategy.RESULT)
              .placeholder(placeHolderResourceId)
              .fitCenter()
              .override(overrideWidth, overrideHeight)
              .thumbnail(thumbnailRequest)
              .into(imageView);
    }

    /** Loads an image using a MediaStore Uri with our default options. */
    private DrawableTypeRequest<Uri> loadUri(Context context) {
        return Glide.with(context)
              .loadFromMediaStore(getUri());
    }

    /** Loads a thumbnail with a size targeted to use MediaStore.Images.Thumbnails. */
    private DrawableRequestBuilder<Uri> loadMediaStoreThumb(Context context) {
        return loadUri(context)
              .override(MEDIASTORE_THUMB_WIDTH, MEDIASTORE_THUMB_HEIGHT);
    }

    public static class PhotoDataFactory implements CursorToLocalDataFactory {
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
        private static final int COL_LATITUDE = 10;
        private static final int COL_LONGITUDE = 11;

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

        @Override
        public PhotoData get(Cursor c) {
            long id = c.getLong(COL_ID);
            String title = c.getString(COL_TITLE);
            String mimeType = c.getString(COL_MIME_TYPE);
            long dateTakenInMilliSeconds = c.getLong(COL_DATE_TAKEN);
            long dateModifiedInSeconds = c.getLong(COL_DATE_MODIFIED);
            String path = c.getString(COL_DATA);
            int orientation = c.getInt(COL_ORIENTATION);
            int width = c.getInt(COL_WIDTH);
            int height = c.getInt(COL_HEIGHT);

            // If the width or height is unknown, attempt to decode it from
            // the physical bitmaps.
            if (width <= 0 || height <= 0) {
                Log.w(TAG, "Zero dimension in ContentResolver for "
                      + path + ":" + width + "x" + height);

                // Ensure we only decode the dimensions, not the whole
                // file if at all possible.
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, opts);
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    width = opts.outWidth;
                    height = opts.outHeight;
                } else {
                    Log.w(TAG, "Dimension decode failed for " + path);

                    // Fall back on decoding the entire file
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
            return new PhotoData(id, title, mimeType, dateTakenInMilliSeconds,
                  dateModifiedInSeconds, path, orientation, width, height,
                  sizeInBytes, latitude, longitude);
        }

        public PhotoData get(Context context, Uri uri) {
            PhotoData newData = null;
            Cursor c = context.getContentResolver().query(uri, QUERY_PROJECTION, null,
                  null, null);
            if (c != null) {
                if (c.moveToFirst()) {
                    newData = get(c);
                }
                c.close();
            }

            return newData;
        }

        /** Query for all the photo data items */
        public static List<LocalData> queryAll(ContentResolver cr) {
            return queryAll(cr, PhotoData.CONTENT_URI, LocalMediaData.QUERY_ALL_MEDIA_ID);
        }

        /** Query for all the photo data items */
        public static List<LocalData> queryAll(ContentResolver cr, Uri uri, long lastId) {
            return LocalDataQuery.forCameraPath(cr, uri, QUERY_PROJECTION, lastId, QUERY_ORDER,
                  new PhotoDataFactory());
        }

        /** Query for a single data item */
        public static LocalData queryContentUri(ContentResolver cr, Uri uri) {
            // TODO: Consider refactoring this, this approach may be slow.
            List<LocalData> newPhotos = queryAll(cr, uri, QUERY_ALL_MEDIA_ID);
            if (newPhotos.isEmpty()) {
                return null;
            }
            return newPhotos.get(0);
        }
    }
}
