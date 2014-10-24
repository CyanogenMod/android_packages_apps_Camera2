/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.Storage;
import com.android.camera2.R;
import com.bumptech.glide.Glide;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * This is used to represent a local data item that is in progress and not
 * yet in the media store.
 */
public class LocalSessionData implements LocalData {

    private final Uri mUri;
    // Units are GMT epoch milliseconds.
    private final long mDateTaken;
    protected final Bundle mMetaData;
    private int mWidth;
    private int mHeight;

    public LocalSessionData(Uri uri) {
        mUri = uri;
        mMetaData = new Bundle();
        mDateTaken = new Date().getTime();
        refreshSize(uri);
    }

    private void refreshSize(Uri uri) {
        Point size = Storage.getSizeForSession(uri);
        mWidth = size.x;
        mHeight = size.y;
    }

    @Override
    public View getView(Context context, View recycled, int thumbWidth, int thumbHeight,
            int placeholderResourcedId, LocalDataAdapter adapter, boolean isInProgress,
            ActionCallback actionCallback) {
        final ImageView imageView;
        if (recycled != null) {
            imageView = (ImageView) recycled;
        } else {
            imageView = new ImageView(context);
            imageView.setTag(R.id.mediadata_tag_viewtype, getItemViewType().ordinal());
        }

        byte[] jpegData = Storage.getJpegForSession(mUri);
        int currentVersion = Storage.getJpegVersionForSession(mUri);
        Glide.with(context)
            .loadFromImage(jpegData, mUri.toString() + currentVersion)
            .skipDiskCache(true)
            .fitCenter()
            .into(imageView);

        imageView.setContentDescription(context.getResources().getString(
                R.string.media_processing_content_description));
        return imageView;
    }

    @Override
    public LocalDataViewType getItemViewType() {
        return LocalDataViewType.SESSION;
    }

    @Override
    public void loadFullImage(Context context, int width, int height, View view,
            LocalDataAdapter adapter) {

    }

    @Override
    public long getDateTaken() {
        return mDateTaken;
    }

    @Override
    public long getDateModified() {
        // Convert to seconds because LocalData interface specifies that this
        // method should return seconds and mDateTaken is in milliseconds.
        return TimeUnit.MILLISECONDS.toSeconds(mDateTaken);
    }

    @Override
    public String getTitle() {
        return mUri.toString();
    }

    @Override
    public boolean isDataActionSupported(int actions) {
        return false;
    }

    @Override
    public boolean delete(Context c) {
        return false;
    }

    @Override
    public void onFullScreen(boolean fullScreen) {

    }

    @Override
    public boolean canSwipeInFullScreen() {
        return true;
    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public String getMimeType() {
        return null;
    }

    @Override
    public MediaDetails getMediaDetails(Context context) {
        return null;
    }

    @Override
    public int getLocalDataType() {
        return LOCAL_IN_PROGRESS_DATA;
    }

    @Override
    public long getSizeInBytes() {
        return 0;
    }

    @Override
    public LocalData refresh(Context context) {
        refreshSize(mUri);
        return this;
    }

    @Override
    public long getContentId() {
        return 0;
    }

    @Override
    public Bundle getMetadata() {
        return mMetaData;
    }

    @Override
    public String getSignature() {
        return "";
    }

    @Override
    public boolean isMetadataUpdated() {
        return true;
    }

    @Override
    public int getRotation() {
        return 0;
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
    public int getViewType() {
        return VIEW_TYPE_REMOVABLE;
    }

    @Override
    public double[] getLatLong() {
        return null;
    }

    @Override
    public boolean isUIActionSupported(int action) {
        return false;
    }

    @Override
    public void prepare() {

    }

    @Override
    public void recycle(View view) {
        Glide.clear(view);
    }

    @Override
    public Uri getUri() {
        return mUri;
    }
}
