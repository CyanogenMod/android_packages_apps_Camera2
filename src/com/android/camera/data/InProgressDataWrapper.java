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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera2.R;

/**
 * A wrapper class for in-progress data. Data that's still being processed
 * should not supporting any actions. Only methods related to actions like
 * {@link #isDataActionSupported(int)} and
 * {@link #isUIActionSupported(int)} are implemented by this class.
 */
public class InProgressDataWrapper implements LocalData {

    final LocalData mLocalData;

    public InProgressDataWrapper(LocalData wrappedData) {
        mLocalData = wrappedData;
    }

    @Override
    public View getView(
            Context context, int width, int height,
            Drawable placeHolder, LocalDataAdapter adapter, boolean isInProgress) {

        return mLocalData.getView(context, width, height, placeHolder, adapter, true);
    }

    @Override
    public void resizeView(Context context, int w, int h, View v, LocalDataAdapter adapter) {
        // do nothing.
    }

    @Override
    public long getDateTaken() {
        return mLocalData.getDateTaken();
    }

    @Override
    public long getDateModified() {
        return mLocalData.getDateModified();
    }

    @Override
    public String getTitle() {
        return mLocalData.getTitle();
    }

    @Override
    public boolean isDataActionSupported(int actions) {
        return false;
    }

    @Override
    public boolean delete(Context c) {
        // No actions are allowed to modify the wrapped data.
        return false;
    }

    @Override
    public boolean rotate90Degrees(
            Context context, LocalDataAdapter adapter,
            int currentDataId, boolean clockwise) {
        // No actions are allowed to modify the wrapped data.
        return false;
    }

    @Override
    public void onFullScreen(boolean fullScreen) {
        mLocalData.onFullScreen(fullScreen);
    }

    @Override
    public boolean canSwipeInFullScreen() {
        return mLocalData.canSwipeInFullScreen();
    }

    @Override
    public String getPath() {
        return mLocalData.getPath();
    }

    @Override
    public String getMimeType() {
        return mLocalData.getMimeType();
    }

    @Override
    public MediaDetails getMediaDetails(Context context) {
        return mLocalData.getMediaDetails(context);
    }

    @Override
    public int getLocalDataType() {
        // Force the data type to be in-progress data.
        return LOCAL_IN_PROGRESS_DATA;
    }

    @Override
    public long getSizeInBytes() {
        return mLocalData.getSizeInBytes();
    }

    @Override
    public LocalData refresh(Context context) {
        return mLocalData.refresh(context);
    }

    @Override
    public long getContentId() {
        return mLocalData.getContentId();
    }

    @Override
    public Bundle getMetadata() {
        return mLocalData.getMetadata();
    }

    @Override
    public boolean isMetadataUpdated() {
        return mLocalData.isMetadataUpdated();
    }

    @Override
    public int getWidth() {
        return mLocalData.getWidth();
    }

    @Override
    public int getHeight() {
        return mLocalData.getHeight();
    }

    @Override
    public int getRotation() {
        return mLocalData.getRotation();
    }

    @Override
    public int getViewType() {
        return mLocalData.getViewType();
    }

    @Override
    public double[] getLatLong() {
        return mLocalData.getLatLong();
    }

    @Override
    public boolean isUIActionSupported(int action) {
        return false;
    }

    @Override
    public void prepare() {
        mLocalData.prepare();
    }

    @Override
    public void recycle() {
        mLocalData.recycle();
    }

    @Override
    public Uri getContentUri() {
        return mLocalData.getContentUri();
    }
}
