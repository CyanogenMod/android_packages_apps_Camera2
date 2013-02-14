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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.android.gallery3d.ingest.data.BitmapWithMetadata;
import com.android.gallery3d.ingest.data.MtpBitmapFetch;

import java.lang.ref.WeakReference;

public class MtpImageView extends ImageView {
    private int mObjectHandle;
    private int mGeneration;

    private WeakReference<MtpImageView> mWeakReference = new WeakReference<MtpImageView>(this);
    private Object mFetchLock = new Object();
    private boolean mFetchPending = false;
    private MtpObjectInfo mFetchObjectInfo;
    private MtpDevice mFetchDevice;
    private Object mFetchResult;

    private static final FetchImageHandler sFetchHandler = FetchImageHandler.createOnNewThread();
    private static final ShowImageHandler sFetchCompleteHandler = new ShowImageHandler();

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

    public void setMtpDeviceAndObjectInfo(MtpDevice device, MtpObjectInfo object, int gen) {
        int handle = object.getObjectHandle();
        if (handle == mObjectHandle && gen == mGeneration) {
            return;
        }
        cancelLoadingAndClear();
        showPlaceholder();
        mGeneration = gen;
        mObjectHandle = handle;
        synchronized (mFetchLock) {
            mFetchObjectInfo = object;
            mFetchDevice = device;
            if (mFetchPending) return;
            mFetchPending = true;
            sFetchHandler.sendMessage(
                    sFetchHandler.obtainMessage(0, mWeakReference));
        }
    }

    protected Object fetchMtpImageDataFromDevice(MtpDevice device, MtpObjectInfo info) {
        return MtpBitmapFetch.getFullsize(device, info);
    }

    protected void onMtpImageDataFetchedFromDevice(Object result) {
        BitmapWithMetadata bitmapWithMetadata = (BitmapWithMetadata)result;
        setImageBitmap(bitmapWithMetadata.bitmap);
        setRotation(bitmapWithMetadata.rotationDegrees);
    }

    protected void cancelLoadingAndClear() {
        synchronized (mFetchLock) {
            mFetchDevice = null;
            mFetchObjectInfo = null;
            mFetchResult = null;
        }
        setImageResource(android.R.color.transparent);
        setRotation(0);
    }

    @Override
    public void onDetachedFromWindow() {
        cancelLoadingAndClear();
        super.onDetachedFromWindow();
    }

    private static class FetchImageHandler extends Handler {
        public FetchImageHandler(Looper l) {
            super(l);
        }

        public static FetchImageHandler createOnNewThread() {
            HandlerThread t = new HandlerThread("MtpImageView Fetch");
            t.start();
            return new FetchImageHandler(t.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            @SuppressWarnings("unchecked")
            MtpImageView parent = ((WeakReference<MtpImageView>) msg.obj).get();
            if (parent == null) return;
            MtpObjectInfo objectInfo;
            MtpDevice device;
            synchronized (parent.mFetchLock) {
                parent.mFetchPending = false;
                device = parent.mFetchDevice;
                objectInfo = parent.mFetchObjectInfo;
            }
            if (device == null) return;
            Object result = parent.fetchMtpImageDataFromDevice(device, objectInfo);
            if (result == null) return;
            synchronized (parent.mFetchLock) {
                if (parent.mFetchObjectInfo != objectInfo) return;
                parent.mFetchResult = result;
                parent.mFetchDevice = null;
                parent.mFetchObjectInfo = null;
                sFetchCompleteHandler.sendMessage(
                        sFetchCompleteHandler.obtainMessage(0, parent.mWeakReference));
            }
        }
    }

    private static class ShowImageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            @SuppressWarnings("unchecked")
            MtpImageView parent = ((WeakReference<MtpImageView>) msg.obj).get();
            if (parent == null) return;
            Object result;
            synchronized (parent.mFetchLock) {
                result = parent.mFetchResult;
            }
            if (result == null) return;
            parent.onMtpImageDataFetchedFromDevice(result);
        }
    }
}
