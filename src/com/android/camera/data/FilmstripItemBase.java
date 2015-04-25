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
import android.view.View;

import com.android.camera.Storage;
import com.android.camera.debug.Log;
import com.android.camera.util.Size;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.signature.MediaStoreSignature;
import com.google.common.base.Optional;

import java.io.File;
import java.text.DateFormat;

import javax.annotation.Nonnull;

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
    protected final GlideFilmstripManager mGlideManager;
    protected final T mData;
    protected final Metadata mMetaData;
    protected final FilmstripItemAttributes mAttributes;
    protected final DateFormat mDateFormatter = DateFormat.getDateTimeInstance();

    protected Size mSuggestedSize;

    public FilmstripItemBase(Context context, GlideFilmstripManager glideManager, T data,
          FilmstripItemAttributes attributes) {
        mContext = context;
        mGlideManager = glideManager;
        mData = data;
        mAttributes = attributes;

        mMetaData = new Metadata();

        mSuggestedSize = GlideFilmstripManager.TINY_THUMB_SIZE;
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
    public void setSuggestedSize(int widthPx, int heightPx) {
        if (widthPx > 0 && heightPx > 0) {
            mSuggestedSize = new Size(widthPx, heightPx);
        } else {
            Log.w(TAG, "Suggested size was set to a zero area value!");
        }
    }

    @Override
    public void recycle(@Nonnull View view) {
        Glide.clear(view);
    }

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

    protected final Key generateSignature(FilmstripItemData data) {
        // Per Glide docs, make default mime type be the empty String
        String mimeType = (data.getMimeType() == null) ? "" : data.getMimeType();
        long modTimeSeconds = (data.getLastModifiedDate() == null) ? 0 :
              data.getLastModifiedDate().getTime() / 1000;
        return new MediaStoreSignature(mimeType, modTimeSeconds, data.getOrientation());
    }

    private void deleteIfEmptyCameraSubDir(File directory) {
        // Make sure 'directory' refers to a valid existing empty directory.
        if (!directory.exists() || !directory.isDirectory() || directory.list().length != 0) {
            return;
        }

        // Check if this is a 'Camera' sub-directory.
        String cameraPathStr = new File(Storage.generateDirectory()).getAbsolutePath();
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
