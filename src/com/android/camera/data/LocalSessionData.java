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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.Storage;

import java.util.Date;

/**
 * This is used to represent a local data item that is in progress and not
 * yet in the media store.
 */
public class LocalSessionData implements LocalData {

    private Uri mUri;
    private long mDateTaken;
    protected final Bundle mMetaData;
    private int mWidth;
    private int mHeight;

    public LocalSessionData(Uri uri) {
        mUri = uri;
        mMetaData = new Bundle();
        mDateTaken = new Date().getTime();
        Point size = Storage.getSizeForSession(uri);
        mWidth = size.x;
        mHeight = size.y;
    }

    @Override
    public View getView(Context context, int width, int height, Drawable placeHolder,
           LocalDataAdapter adapter, boolean isInProgress) {
        //TODO do this on a background thread
        byte[] jpegData = Storage.getJpegForSession(mUri);
        Bitmap bmp = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
        ImageView imageView = new ImageView(context);
        imageView.setImageBitmap(bmp);
        return imageView;
    }

    @Override
    public void resizeView(Context context, int width, int height, View view,
           LocalDataAdapter adapter) {

    }

    @Override
    public long getDateTaken() {
        return mDateTaken;
    }

    @Override
    public long getDateModified() {
        return mDateTaken;
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
    public boolean rotate90Degrees(Context context, LocalDataAdapter adapter, int currentDataId, boolean clockwise) {
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
    public void recycle() {

    }

    @Override
    public Uri getContentUri() {
        return mUri;
    }
}
