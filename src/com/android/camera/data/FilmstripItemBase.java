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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;

import com.android.camera.Storage;
import com.android.camera.debug.Log;
import com.android.camera.util.Size;
import com.bumptech.glide.BitmapRequestBuilder;
import com.bumptech.glide.Glide;
import com.google.common.base.Optional;

import java.io.File;
import java.text.DateFormat;

/**
 * A base class for all the local media files. The bitmap is loaded in
 * background thread. Subclasses should implement their own background loading
 * thread by sub-classing BitmapLoadTask and overriding doInBackground() to
 * return a bitmap.
 */
public abstract class FilmstripItemBase<T extends FilmstripItemData> implements FilmstripItem {
    /** The minimum id to use to query for all media at a given media store uri */
    public static final int QUERY_ALL_MEDIA_ID = -1;

    protected final Context mContext;
    protected final T mData;
    protected final Metadata mMetaData;
    protected final FilmstripItemAttributes mAttributes;
    protected final DateFormat mDateFormatter = DateFormat.getDateTimeInstance();

    public FilmstripItemBase(Context context, T data, FilmstripItemAttributes attributes) {
        mContext = context;
        mData = data;
        mAttributes = attributes;
        mMetaData = new Metadata();
    }

    @Override
    public FilmstripItemData getData() {
        return mData;
    }

    @Override
    public boolean delete() {
        File fileToDelete = new File(mData.getFilePath());
        boolean deletionSucceeded = fileToDelete.delete();
        deleteIfEmptyCameraSubDir(fileToDelete.getParentFile());
        return deletionSucceeded;
    }

    @Override
    public void loadFullImage(int thumbWidth, int thumbHeight, View view) {
        // Default is do nothing.
        // Can be implemented by sub-classes.
    }

    @Override
    public void recycle(View view) { }

    @Override
    public Optional<MediaDetails> getMediaDetails() {
        MediaDetails mediaDetails = new MediaDetails();
        mediaDetails.addDetail(MediaDetails.INDEX_TITLE, mData.getTitle());
        mediaDetails.addDetail(MediaDetails.INDEX_WIDTH, getDimensions().getWidth());
        mediaDetails.addDetail(MediaDetails.INDEX_HEIGHT, getDimensions().getHeight());
        mediaDetails.addDetail(MediaDetails.INDEX_PATH, mData.getFilePath());
        mediaDetails.addDetail(MediaDetails.INDEX_DATETIME,
              mDateFormatter.format(mData.getLastModifiedDate()));
        long mSizeInBytes = mData.getSizeInBytes();
        if (mSizeInBytes > 0) {
            mediaDetails.addDetail(MediaDetails.INDEX_SIZE, mSizeInBytes);
        }

        Location location = mData.getLocation();
        if (location != Location.UNKNOWN) {
            mediaDetails.addDetail(MediaDetails.INDEX_LOCATION, location.getLocationString());
        }
        return Optional.of(mediaDetails);
    }

    @Override
    public FilmstripItemAttributes getAttributes() {
        return mAttributes;
    }

    @Override
    public Metadata getMetadata() {
        return mMetaData;
    }

    @Override
    public Size getDimensions() {
        return mData.getDimensions();
    }

    @Override
    public int getOrientation() {
        return mData.getOrientation();
    }

    // TODO: Move the glide classes to a specific rendering class.
    protected BitmapRequestBuilder<Uri, Bitmap> glideFullResBitmap(Uri uri,
          int width, int height) {
        // compute a ratio such that viewWidth and viewHeight are less than
        // MAXIMUM_SMOOTH_TEXTURE_SIZE but maintain their aspect ratio.
        float downscaleRatio = downscaleRatioToFit(width, height,
              MAXIMUM_TEXTURE_SIZE);

        return Glide.with(mContext)
              .load(uri)
              .asBitmap()
                  .atMost()
                  .fitCenter()
              .override(
                    Math.round(width * downscaleRatio),
                    Math.round(height * downscaleRatio));
    }

    protected BitmapRequestBuilder<Uri, Bitmap> glideFilmstripThumb(Uri uri,
          int viewWidth, int viewHeight) {
        // compute a ratio such that viewWidth and viewHeight are less than
        // MAXIMUM_SMOOTH_TEXTURE_SIZE but maintain their aspect ratio.
        float downscaleRatio = downscaleRatioToFit(viewWidth, viewHeight,
              MAXIMUM_SMOOTH_TEXTURE_SIZE);

        return Glide.with(mContext)
              .load(uri)
              .asBitmap()
                  .atMost()
                  .fitCenter()
              .override(
                    Math.round(viewWidth * downscaleRatio),
                    Math.round(viewHeight * downscaleRatio));
    }

    protected BitmapRequestBuilder<Uri, Bitmap>glideMediaStoreThumb(Uri uri) {
        return Glide.with(mContext)
              .loadFromMediaStore(uri)
              .asBitmap()
                  .atMost()
                  .fitCenter()
              // This attempts to ensure we load the cached media store version.
              .override(MEDIASTORE_THUMB_WIDTH, MEDIASTORE_THUMB_HEIGHT);
    }

    protected BitmapRequestBuilder<Uri, Bitmap> glideTinyThumb(Uri uri) {
        return Glide.with(mContext)
              .loadFromMediaStore(uri)
              .asBitmap()
                  .atMost()
                  .fitCenter()
              .override(256, 265);
    }

    private float downscaleRatioToFit(int width, int height, int fitWithinSize) {
        // Find the longest dimension
        int longest = Math.max(width, height);

        if (longest > fitWithinSize) {
            return (float)fitWithinSize / (float)longest;
        }

        return 1.0f;
    }

    private void deleteIfEmptyCameraSubDir(File directory) {
        // Make sure 'directory' refers to a valid existing empty directory.
        if (!directory.exists() || !directory.isDirectory() || directory.list().length != 0) {
            return;
        }

        // Check if this is a 'Camera' sub-directory.
        String cameraPathStr = Storage.DIRECTORY_FILE.getAbsolutePath();
        String fileParentPathStr = directory.getParentFile().getAbsolutePath();
        Log.d(TAG, "CameraPathStr: " + cameraPathStr + "  fileParentPathStr: " + fileParentPathStr);

        // Delete the directory if it's an empty sub-directory of the Camera
        // directory.
        if (fileParentPathStr.equals(cameraPathStr)) {
            if(!directory.delete()) {
                Log.d(TAG, "Failed to delete: " + directory);
            }
        }
    }
}
