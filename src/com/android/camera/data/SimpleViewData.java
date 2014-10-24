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
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.android.camera.debug.Log;
import com.android.camera.filmstrip.ImageData;

import java.util.UUID;

/**
 * A LocalData that does nothing but only shows a view.
 */
public class SimpleViewData implements LocalData {
    private static final Log.Tag TAG = new Log.Tag("SimpleViewData");
    private static final String SIMPLE_VIEW_URI_SCHEME = "simple_view_data";

    private final int mWidth;
    private final int mHeight;
    private final View mView;
    private final long mDateTaken;
    private final long mDateModified;
    private final Bundle mMetaData;
    private final Uri mUri;
    private final LocalDataViewType mItemViewType;

    public SimpleViewData(
            View v, LocalDataViewType viewType, int width, int height,
            int dateTaken, int dateModified) {
        mView = v;
        mItemViewType = viewType;
        mWidth = width;
        mHeight = height;
        mDateTaken = dateTaken;
        mDateModified = dateModified;
        mMetaData = new Bundle();
        Uri.Builder builder = new Uri.Builder();
        String uuid = UUID.randomUUID().toString();
        builder.scheme(SIMPLE_VIEW_URI_SCHEME).appendPath(uuid);
        mUri = builder.build();
    }

    @Override
    public long getDateTaken() {
        return mDateTaken;
    }

    @Override
    public long getDateModified() {
        return mDateModified;
    }

    @Override
    public String getTitle() {
        return "";
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
    public int getViewType() {
        return ImageData.VIEW_TYPE_REMOVABLE;
    }

    @Override
    public LocalDataViewType getItemViewType() {
        return mItemViewType;
    }

    @Override
    public String getPath() {
        return "";
    }

    @Override
    public Uri getUri() {
        return mUri;
    }

    @Override
    public int getLocalDataType() {
        return LOCAL_VIEW;
    }

    @Override
    public LocalData refresh(Context context) {
        return this;
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
    public boolean delete(Context c) {
        return false;
    }

    @Override
    public View getView(Context context, View recycled, int width, int height,
            int placeHolderResourceId, LocalDataAdapter adapter, boolean isInProgressSession,
            ActionCallback actionCallback) {
        return mView;
    }

    @Override
    public void loadFullImage(Context context, int w, int h, View view, LocalDataAdapter adapter) {
        // do nothing.
    }

    @Override
    public void prepare() {
        // do nothing.
    }

    @Override
    public void recycle(View view) {
        // Do nothing.
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
    public MediaDetails getMediaDetails(Context context) {
        return null;
    }

    @Override
    public double[] getLatLong() {
        return null;
    }

    @Override
    public String getMimeType() {
        return null;
    }

    @Override
    public long getSizeInBytes() {
        return 0;
    }

    @Override
    public long getContentId() {
        return -1;
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
}
