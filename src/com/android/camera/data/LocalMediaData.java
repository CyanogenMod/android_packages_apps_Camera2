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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.Storage;
import com.android.camera.debug.Log;
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
    /** The minimum id to use to query for all media at a given media store uri */
    public static final int QUERY_ALL_MEDIA_ID = -1;

    protected static final int MEDIASTORE_THUMB_WIDTH = 512;
    protected static final int MEDIASTORE_THUMB_HEIGHT = 384;

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

    /**
     * Used for thumbnail loading optimization. True if this data has a
     * corresponding visible view.
     */
    protected boolean mUsing = false;
    protected final Object mUsingLock = new Object();

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
        File fileToDelete = new File(mPath);
        boolean deletionSucceeded = fileToDelete.delete();
        deleteIfEmptyCameraSubDir(fileToDelete.getParentFile());
        return deletionSucceeded;
    }

    @Override
    public void onFullScreen(boolean fullScreen) {
        // do nothing.
    }

    @Override
    public boolean canSwipeInFullScreen() {
        return true;
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
        synchronized (mUsingLock) {
            mUsing = true;
        }
    }

    @Override
    public void recycle(View view) {
        synchronized (mUsingLock) {
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

    protected abstract ImageView fillImageView(Context context, ImageView v,
          int thumbWidth, int thumbHeight, int placeHolderResourceId,
          LocalDataAdapter adapter, boolean isInProgress);

    protected boolean isUsing() {
        synchronized (mUsingLock) {
            return mUsing;
        }
    }

    protected static String getReadableDate(long dateInSeconds) {
        DateFormat dateFormatter = DateFormat.getDateTimeInstance();
        return dateFormatter.format(new Date(dateInSeconds * 1000));
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
            directory.delete();
        }
    }
}
