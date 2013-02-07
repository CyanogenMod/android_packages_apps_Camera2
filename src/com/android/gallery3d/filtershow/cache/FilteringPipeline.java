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

package com.android.gallery3d.filtershow.cache;

import android.graphics.Bitmap;
import android.os.*;
import android.os.Process;
import android.support.v8.renderscript.*;
import android.util.Log;

import com.android.gallery3d.filtershow.filters.ImageFilterRS;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class FilteringPipeline implements Handler.Callback {

    private final static FilteringPipeline gPipeline = new FilteringPipeline();
    private static final String LOGTAG = "FilteringPipeline";
    private ImagePreset mPreviousGeometryPreset = null;
    private ImagePreset mPreviousFiltersPreset = null;
    private GeometryMetadata mPreviousGeometry = null;
    private float mPreviewScaleFactor = 1.0f;

    private Bitmap mOriginalBitmap = null;
    private Bitmap mResizedOriginalBitmap = null;

    private boolean DEBUG = false;

    private HandlerThread mHandlerThread = null;
    private final static int NEW_PRESET = 0;
    private final static int NEW_RENDERING_REQUEST = 1;
    private final static int COMPUTE_PRESET = 2;
    private final static int COMPUTE_RENDERING_REQUEST = 3;

    private Handler mProcessingHandler = null;
    private final Handler mUIHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_PRESET: {
                    TripleBufferBitmap buffer = MasterImage.getImage().getDoubleBuffer();
                    buffer.swapConsumer();
                    MasterImage.getImage().notifyObservers();
                    break;
                }
                case NEW_RENDERING_REQUEST: {
                    RenderingRequest request = (RenderingRequest) msg.obj;
                    request.markAvailable();
                    break;
                }
            }
        }
    };

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case COMPUTE_PRESET: {
                ImagePreset preset = (ImagePreset) msg.obj;
                TripleBufferBitmap buffer = MasterImage.getImage().getDoubleBuffer();
                compute(buffer, preset, COMPUTE_PRESET);
                buffer.swapProducer();
                Message uimsg = mUIHandler.obtainMessage(NEW_PRESET);
                mUIHandler.sendMessage(uimsg);
                break;
            }
            case COMPUTE_RENDERING_REQUEST: {
                RenderingRequest request = (RenderingRequest) msg.obj;
                render(request);
                Message uimsg = mUIHandler.obtainMessage(NEW_RENDERING_REQUEST);
                uimsg.obj = request;
                mUIHandler.sendMessage(uimsg);
                break;
            }
        }
        return false;
    }

    private static float RESIZE_FACTOR = 0.8f;
    private static float MAX_PROCESS_TIME = 100; // in ms
    private float mResizeFactor = 1.0f;
    private long mResizeTime = 0;

    private Allocation mOriginalAllocation = null;
    private Allocation mFiltersOnlyOriginalAllocation =  null;

    private FilteringPipeline() {
        mHandlerThread = new HandlerThread("FilteringPipeline",
                Process.THREAD_PRIORITY_FOREGROUND);
        mHandlerThread.start();
        mProcessingHandler = new Handler(mHandlerThread.getLooper(), this);
    }

    public static FilteringPipeline getPipeline() {
        return gPipeline;
    }

    public synchronized void setOriginal(Bitmap bitmap) {
        mOriginalBitmap = bitmap;
        Log.v(LOGTAG,"setOriginal, size " + bitmap.getWidth() + " x " + bitmap.getHeight());
        updateOriginalAllocation(MasterImage.getImage().getPreset());
        updatePreviewBuffer();
    }

    public synchronized boolean updateOriginalAllocation(ImagePreset preset) {
        if (mOriginalBitmap == null) {
            return false;
        }
        /*
        //FIXME: turn back on the on-the-fly resize.
        int w = (int) (mOriginalBitmap.getWidth() * mResizeFactor);
        int h = (int) (mOriginalBitmap.getHeight() * mResizeFactor);
        if (!needsGeometryRepaint() && mResizedOriginalBitmap != null && w == mResizedOriginalBitmap.getWidth()) {
            return false;
        }
        mResizedOriginalBitmap = Bitmap.createScaledBitmap(mOriginalBitmap, w, h, true);
        */
        GeometryMetadata geometry = preset.getGeometry();
        if (mPreviousGeometry != null && geometry.equals(mPreviousGeometry)) {
            return false;
        }
        RenderScript RS = ImageFilterRS.getRenderScriptContext();
        if (mFiltersOnlyOriginalAllocation != null) {
            mFiltersOnlyOriginalAllocation.destroy();
        }
        mFiltersOnlyOriginalAllocation = Allocation.createFromBitmap(RS, mOriginalBitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        if (mOriginalAllocation != null) {
            mOriginalAllocation.destroy();
        }
        mResizedOriginalBitmap = preset.applyGeometry(mOriginalBitmap);
        mOriginalAllocation = Allocation.createFromBitmap(RS, mResizedOriginalBitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        mPreviousGeometry = new GeometryMetadata(geometry);
        return true;
    }

    public void postRenderingRequest(RenderingRequest request) {
        if (mOriginalAllocation == null) {
            return;
        }
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_RENDERING_REQUEST);
        msg.obj = request;
        mProcessingHandler.sendMessage(msg);
    }

    public synchronized void updatePreviewBuffer() {
        if (mOriginalAllocation == null) {
            return;
        }
        if (mProcessingHandler.hasMessages(COMPUTE_PRESET)) {
            return;
        }
        if (!needsRepaint()) {
            return;
        }
        if (MasterImage.getImage().getPreset() == null) {
            return;
        }
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_PRESET);
        msg.obj = MasterImage.getImage().getPreset();
        mProcessingHandler.sendMessage(msg);
    }

    private void setPresetParameters(ImagePreset preset) {
        preset.setScaleFactor(mPreviewScaleFactor);
        if (mPreviewScaleFactor < 1.0f) {
            preset.setIsHighQuality(false);
        } else {
            preset.setIsHighQuality(true);
        }
    }

    private void render(RenderingRequest request) {
        if (request.getBitmap() == null
                || request.getImagePreset() == null) {
            return;
        }
        Bitmap bitmap = request.getBitmap();
        ImagePreset preset = request.getImagePreset();
        updateOriginalAllocation(preset);
        if (request.getType() == RenderingRequest.FULL_RENDERING
                || request.getType() == RenderingRequest.GEOMETRY_RENDERING) {
            mOriginalAllocation.copyTo(bitmap);
        } else {
            mFiltersOnlyOriginalAllocation.copyTo(bitmap);
        }
        if (request.getType() == RenderingRequest.FULL_RENDERING
                || request.getType() == RenderingRequest.FILTERS_RENDERING) {
            Bitmap bmp = preset.apply(bitmap);
            request.setBitmap(bmp);
        }
    }

    private void compute(TripleBufferBitmap buffer, ImagePreset preset, int type) {
        if (DEBUG) {
            Log.v(LOGTAG, "compute preset " + preset);
            preset.showFilters();
        }

        String thread = Thread.currentThread().getName();
        long time = System.currentTimeMillis();
        if (updateOriginalAllocation(preset)) {
            buffer.updateBitmaps(mResizedOriginalBitmap);
        }
        Bitmap bitmap = buffer.getProducer();
        long time2 = System.currentTimeMillis();

        if (bitmap == null || (bitmap.getWidth() != mResizedOriginalBitmap.getWidth())
                || (bitmap.getHeight() != mResizedOriginalBitmap.getHeight())) {
            buffer.updateBitmaps(mResizedOriginalBitmap);
            bitmap = buffer.getProducer();
        }
        mOriginalAllocation.copyTo(bitmap);

        bitmap = preset.apply(bitmap);

        time = System.currentTimeMillis() - time;
        time2 = System.currentTimeMillis() - time2;
        if (DEBUG) {
            Log.v(LOGTAG, "Applying " + type + " filters to bitmap "
                    + bitmap + " (" + bitmap.getWidth() + " x " + bitmap.getHeight()
                    + ") took " + time + " ms, " + time2 + " ms for the filter, on thread " + thread);
        }
        if (type == COMPUTE_PRESET) {
            if (mResizeFactor > 0.6 && time > MAX_PROCESS_TIME && (System.currentTimeMillis() + 1000 > mResizeTime)) {
                mResizeTime = System.currentTimeMillis();
                mResizeFactor *= RESIZE_FACTOR;
            }
        }
    }

    private synchronized boolean needsRepaint() {
        TripleBufferBitmap buffer = MasterImage.getImage().getDoubleBuffer();
        return buffer.checkRepaintNeeded();
    }

    public void setPreviewScaleFactor(float previewScaleFactor) {
        mPreviewScaleFactor = previewScaleFactor;
    }

    public float getPreviewScaleFactor() {
        return mPreviewScaleFactor;
    }
}
