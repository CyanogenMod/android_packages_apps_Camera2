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

import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.filters.ImageFilterRS;
import com.android.gallery3d.filtershow.filters.ImageFilterVignette;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.util.HashMap;

public class FilteringPipeline implements Handler.Callback {

    private final static FilteringPipeline gPipeline = new FilteringPipeline();
    private static final String LOGTAG = "FilteringPipeline";
    private ImagePreset mPreviousPreset = null;
    private ImagePreset mPreviousGeometryPreset = null;
    private ImagePreset mPreviousFiltersPreset = null;
    private GeometryMetadata mPreviousGeometry = null;
    private float mPreviewScaleFactor = 1.0f;

    private Bitmap mOriginalBitmap = null;
    private Bitmap mResizedOriginalBitmap = null;

    private boolean DEBUG = false;

    private HandlerThread mHandlerThread = null;
    private final static int NEW_PRESET = 0;
    private final static int NEW_GEOMETRY_PRESET = 1;
    private final static int NEW_FILTERS_PRESET = 2;
    private final static int COMPUTE_PRESET = 3;
    private final static int COMPUTE_GEOMETRY_PRESET = 4;
    private final static int COMPUTE_FILTERS_PRESET = 5;

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
                case NEW_GEOMETRY_PRESET: {
                    TripleBufferBitmap buffer = MasterImage.getImage().getGeometryOnlyBuffer();
                    buffer.swapConsumer();
                    MasterImage.getImage().notifyObservers();
                    break;
                }
                case NEW_FILTERS_PRESET: {
                    TripleBufferBitmap buffer = MasterImage.getImage().getFiltersOnlyBuffer();
                    buffer.swapConsumer();
                    MasterImage.getImage().notifyObservers();
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
            case COMPUTE_GEOMETRY_PRESET: {
                ImagePreset preset = (ImagePreset) msg.obj;
                TripleBufferBitmap buffer = MasterImage.getImage().getGeometryOnlyBuffer();
                compute(buffer, preset, COMPUTE_GEOMETRY_PRESET);
                buffer.swapProducer();
                Message uimsg = mUIHandler.obtainMessage(NEW_GEOMETRY_PRESET);
                mUIHandler.sendMessage(uimsg);
                break;
            }
            case COMPUTE_FILTERS_PRESET: {
                ImagePreset preset = (ImagePreset) msg.obj;
                TripleBufferBitmap buffer = MasterImage.getImage().getFiltersOnlyBuffer();
                compute(buffer, preset, COMPUTE_FILTERS_PRESET);
                buffer.swapProducer();
                Message uimsg = mUIHandler.obtainMessage(NEW_FILTERS_PRESET);
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
        updatePreviewBuffer();
        updateFiltersOnlyPreviewBuffer();
        updateGeometryOnlyPreviewBuffer();
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
        if (!needsRepaint()) {
            return;
        }
        if (mProcessingHandler.hasMessages(COMPUTE_PRESET)) {
            mProcessingHandler.removeMessages(COMPUTE_PRESET);
        }
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_PRESET);
        ImagePreset preset = new ImagePreset(MasterImage.getImage().getPreset());
        setPresetParameters(preset);
        msg.obj = preset;
        mProcessingHandler.sendMessage(msg);
    }

    public void updateGeometryOnlyPreviewBuffer() {
        if (mOriginalAllocation == null) {
            return;
        }
        if (!needsGeometryRepaint()) {
            return;
        }
        if (mProcessingHandler.hasMessages(COMPUTE_GEOMETRY_PRESET)) {
            mProcessingHandler.removeMessages(COMPUTE_GEOMETRY_PRESET);
        }
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_GEOMETRY_PRESET);
        ImagePreset preset = new ImagePreset(MasterImage.getImage().getGeometryPreset());
        setPresetParameters(preset);
        msg.obj = preset;
        mProcessingHandler.sendMessage(msg);
    }

    public void updateFiltersOnlyPreviewBuffer() {
        if (mOriginalAllocation == null) {
            return;
        }
        if (!needsFiltersRepaint()) {
            return;
        }
        if (mProcessingHandler.hasMessages(COMPUTE_FILTERS_PRESET)) {
            mProcessingHandler.removeMessages(COMPUTE_FILTERS_PRESET);
        }
        Message msg = mProcessingHandler.obtainMessage(COMPUTE_FILTERS_PRESET);
        ImagePreset preset = new ImagePreset(MasterImage.getImage().getFiltersOnlyPreset());
        setPresetParameters(preset);

        msg.obj = preset;
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

        time = System.currentTimeMillis() - time;
        time2 = System.currentTimeMillis() - time2;
        if (DEBUG) {
            Log.v(LOGTAG, "Applying " + type + " filters to bitmap "
                    + bitmap + " (" + bitmap.getWidth() + " x " + bitmap.getHeight()
                    + ") took " + time + " ms, " + time2 + " ms for the filter, on thread " + thread);
        }
        if (type == COMPUTE_PRESET) {
            mPreviousPreset = preset;
            if (mResizeFactor > 0.6 && time > MAX_PROCESS_TIME && (System.currentTimeMillis() + 1000 > mResizeTime)) {
                mResizeTime = System.currentTimeMillis();
                mResizeFactor *= RESIZE_FACTOR;
            }
        } else if (type == COMPUTE_GEOMETRY_PRESET) {
            mPreviousGeometryPreset = preset;
        } else if (type == COMPUTE_FILTERS_PRESET) {
            mPreviousFiltersPreset = preset;
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

    public void setPreviewScaleFactor(float previewScaleFactor) {
        mPreviewScaleFactor = previewScaleFactor;
    }

    public float getPreviewScaleFactor() {
        return mPreviewScaleFactor;
    }
}
