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
    private ImagePreset mPreviousPreset = null;
    private ImagePreset mPreviousGeometryPreset = null;
    private ImagePreset mPreviousFiltersPreset = null;
    private GeometryMetadata mPreviousGeometry = null;

    private Bitmap mOriginalBitmap = null;
    private Bitmap mResizedOriginalBitmap = null;

    private boolean DEBUG = false;

    private HandlerThread mHandlerThread = null;
    private final static int NEW_PRESET = 0;
    private final static int COMPUTE_PRESET = 1;
    private final static int COMPUTE_GEOMETRY_PRESET = 2;
    private final static int COMPUTE_FILTERS_PRESET = 3;

    private boolean mProcessing = false;

    private Handler mProcessingHandler = null;
    private final Handler mUIHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_PRESET: {
                    MasterImage.getImage().notifyObservers();
                    mProcessing = false;
                    break;
                }
            }
        }
    };

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case COMPUTE_PRESET: {
                ImagePreset preset = MasterImage.getImage().getPreset();
                TripleBufferBitmap buffer = MasterImage.getImage().getDoubleBuffer();
                compute(buffer, preset, COMPUTE_PRESET);
                Message uimsg = mUIHandler.obtainMessage(NEW_PRESET);
                mUIHandler.sendMessage(uimsg);
                break;
            }
            case COMPUTE_GEOMETRY_PRESET: {
                ImagePreset preset = MasterImage.getImage().getGeometryPreset();
                TripleBufferBitmap buffer = MasterImage.getImage().getGeometryOnlyBuffer();
                compute(buffer, preset, COMPUTE_GEOMETRY_PRESET);
                Message uimsg = mUIHandler.obtainMessage(NEW_PRESET);
                mUIHandler.sendMessage(uimsg);
                break;
            }
            case COMPUTE_FILTERS_PRESET: {
                ImagePreset preset = MasterImage.getImage().getFiltersOnlyPreset();
                TripleBufferBitmap buffer = MasterImage.getImage().getFiltersOnlyBuffer();
                compute(buffer, preset, COMPUTE_FILTERS_PRESET);
                Message uimsg = mUIHandler.obtainMessage(NEW_PRESET);
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

    private Allocation mOriginalBitmapAllocation = null;
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

    public synchronized void updatePreviewBuffer() {
        if (mOriginalAllocation == null) {
            return;
        }
        if (mProcessing) {
            return;
        }
        if (mProcessingHandler.hasMessages(COMPUTE_PRESET)) {
            return;
        }
        if (!needsRepaint()) {
            return;
        }
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_PRESET);
        mProcessingHandler.sendMessage(msg);
        mProcessing = true;
    }

    public void updateGeometryOnlyPreviewBuffer() {
        if (mOriginalAllocation == null) {
            return;
        }
        if (mProcessing) {
            return;
        }
        if (mProcessingHandler.hasMessages(COMPUTE_GEOMETRY_PRESET)) {
            return;
        }
        if (!needsGeometryRepaint()) {
            return;
        }
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_GEOMETRY_PRESET);
        mProcessingHandler.sendMessage(msg);
        mProcessing = true;
    }

    public void updateFiltersOnlyPreviewBuffer() {
        if (mOriginalAllocation == null) {
            return;
        }
        if (mProcessing) {
            return;
        }
        if (mProcessingHandler.hasMessages(COMPUTE_FILTERS_PRESET)) {
            return;
        }
        if (!needsFiltersRepaint()) {
            return;
        }
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_FILTERS_PRESET);
        mProcessingHandler.sendMessage(msg);
        mProcessing = true;
    }

    private void compute(TripleBufferBitmap buffer, ImagePreset preset, int type) {
        String thread = Thread.currentThread().getName();
        long time = System.currentTimeMillis();
        if (updateOriginalAllocation(preset)) {
            buffer.updateBitmaps(mResizedOriginalBitmap);
        }
        Bitmap bitmap = buffer.getProducer();
        long time2 = System.currentTimeMillis();

        if (type != COMPUTE_FILTERS_PRESET) {
            if (bitmap == null || (bitmap.getWidth() != mResizedOriginalBitmap.getWidth())
                    || (bitmap.getHeight() != mResizedOriginalBitmap.getHeight())) {
                buffer.updateBitmaps(mResizedOriginalBitmap);
                bitmap = buffer.getProducer();
            }
            mOriginalAllocation.copyTo(bitmap);
        } else {
            if (bitmap == null || (bitmap.getWidth() != mOriginalBitmap.getWidth())
                    || (bitmap.getHeight() != mOriginalBitmap.getHeight())) {
                buffer.updateBitmaps(mOriginalBitmap);
                bitmap = buffer.getProducer();
            }
            mFiltersOnlyOriginalAllocation.copyTo(bitmap);
        }

        if (mOriginalAllocation == null || bitmap == null) {
            Log.v(LOGTAG, "exiting compute because mOriginalAllocation: " + mOriginalAllocation + " or bitmap: " + bitmap);
            return;
        }

        if (type != COMPUTE_GEOMETRY_PRESET) {
            bitmap = preset.apply(bitmap);
        }

        buffer.swapProducer();
        time = System.currentTimeMillis() - time;
        time2 = System.currentTimeMillis() - time2;
        if (DEBUG) {
            Log.v(LOGTAG, "Applying " + type + " filters to bitmap " + bitmap + " took " + time + " ms, " + time2 + " ms for the filter, on thread " + thread);
        }
        if (type == COMPUTE_PRESET) {
            mPreviousPreset = new ImagePreset(preset);
            if (mResizeFactor > 0.6 && time > MAX_PROCESS_TIME && (System.currentTimeMillis() + 1000 > mResizeTime)) {
                mResizeTime = System.currentTimeMillis();
                mResizeFactor *= RESIZE_FACTOR;
            }
        } else if (type == COMPUTE_GEOMETRY_PRESET) {
            mPreviousGeometryPreset = new ImagePreset(preset);
        } else if (type == COMPUTE_FILTERS_PRESET) {
            mPreviousFiltersPreset = new ImagePreset(preset);
        }
    }

    private synchronized boolean needsRepaint() {
        ImagePreset preset = MasterImage.getImage().getPreset();
        if (preset == null || mPreviousPreset == null) {
            return true;
        }
        if (preset.equals(mPreviousPreset)) {
            return false;
        }
        return true;
    }

    private synchronized boolean needsGeometryRepaint() {
        ImagePreset preset = MasterImage.getImage().getPreset();
        if (preset == null || mPreviousGeometry == null || mPreviousGeometryPreset == null) {
            return true;
        }
        GeometryMetadata geometry = preset.getGeometry();
        if (geometry.equals(mPreviousGeometryPreset.getGeometry())) {
            return false;
        }
        return true;
    }

    private synchronized boolean needsFiltersRepaint() {
        ImagePreset preset = MasterImage.getImage().getPreset();
        if (preset == null || mPreviousFiltersPreset == null) {
            return true;
        }
        if (preset.equals(mPreviousFiltersPreset)) {
            return false;
        }
        return true;
    }
}
