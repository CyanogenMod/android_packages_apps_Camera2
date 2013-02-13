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

package com.android.gallery3d.ingest.ui;

import android.content.Context;
import android.mtp.MtpDevice;
import android.mtp.MtpObjectInfo;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.gallery3d.ingest.data.BitmapWithMetadata;
import com.android.gallery3d.ingest.data.MtpBitmapFetch;

public class MtpImageView extends ImageView {
    private static final int FADE_IN_TIME_MS = 80;

    private int mObjectHandle;
    private int mGeneration;

    private void init() {
         showPlaceholder();
    }

    public MtpImageView(Context context) {
        super(context);
        init();
    }

    public MtpImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MtpImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void showPlaceholder() {
        setImageResource(android.R.color.transparent);
    }

    private LoadMtpImageTask mTask;

    public void setMtpDeviceAndObjectInfo(MtpDevice device, MtpObjectInfo object, int gen) {
        int handle = object.getObjectHandle();
        if (handle == mObjectHandle && gen == mGeneration) {
            return;
        }
        cancelLoadingAndClear();
        showPlaceholder();
        mGeneration = gen;
        mObjectHandle = handle;
        mTask = new LoadMtpImageTask(device);
        mTask.execute(object);
    }

    protected Object fetchMtpImageDataFromDevice(MtpDevice device, MtpObjectInfo info) {
        return MtpBitmapFetch.getFullsize(device, info);
    }

    protected void onMtpImageDataFetchedFromDevice(Object result) {
        BitmapWithMetadata bitmapWithMetadata = (BitmapWithMetadata)result;
        setImageBitmap(bitmapWithMetadata.bitmap);
        setRotation(bitmapWithMetadata.rotationDegrees);
    }

    private class LoadMtpImageTask extends AsyncTask<MtpObjectInfo, Void, Object> {
        private MtpDevice mDevice;

        public LoadMtpImageTask(MtpDevice device) {
            mDevice = device;
        }

        @Override
        protected Object doInBackground(MtpObjectInfo... args) {
            Object result = null;
            if (!isCancelled()) {
                result = fetchMtpImageDataFromDevice(mDevice, args[0]);
            }
            mDevice = null;
            return result;
        }

        @Override
        protected void onPostExecute(Object result) {
            if (isCancelled() || result == null) {
                return;
            }
            setAlpha(0f);
            onMtpImageDataFetchedFromDevice(result);
            animate().alpha(1f).setDuration(FADE_IN_TIME_MS);
        }

        @Override
        protected void onCancelled() {
        }
    }

    protected void cancelLoadingAndClear() {
        if (mTask != null) {
            mTask.cancel(true);
        }
        mTask = null;
        animate().cancel();
        setImageResource(android.R.color.transparent);
        setRotation(0);
    }

    @Override
    public void onDetachedFromWindow() {
        cancelLoadingAndClear();
        super.onDetachedFromWindow();
    }
}
