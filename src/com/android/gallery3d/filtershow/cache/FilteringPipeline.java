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

import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilterRS;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class FilteringPipeline implements Handler.Callback {

    private static volatile FilteringPipeline sPipeline = null;
    private static final String LOGTAG = "FilteringPipeline";
    private volatile GeometryMetadata mPreviousGeometry = null;
    private volatile float mPreviewScaleFactor = 1.0f;
    private volatile boolean mPipelineIsOn = false;

    private volatile Bitmap mOriginalBitmap = null;
    private volatile Bitmap mResizedOriginalBitmap = null;

    private boolean DEBUG = false;

    private HandlerThread mHandlerThread = null;
    private final static int NEW_PRESET = 0;
    private final static int NEW_RENDERING_REQUEST = 1;
    private final static int COMPUTE_PRESET = 2;
    private final static int COMPUTE_RENDERING_REQUEST = 3;
    private final static int COMPUTE_PARTIAL_RENDERING_REQUEST = 4;

    private volatile boolean mHasUnhandledPreviewRequest = false;

    private Handler mProcessingHandler = null;
    private final Handler mUIHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_PRESET: {
                    TripleBufferBitmap buffer = MasterImage.getImage().getDoubleBuffer();
                    buffer.swapConsumer();
                    MasterImage.getImage().notifyObservers();
                    if (mHasUnhandledPreviewRequest) {
                        updatePreviewBuffer();
                    }
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
        if (!mPipelineIsOn) {
            return false;
        }
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
            case COMPUTE_RENDERING_REQUEST:
            case COMPUTE_PARTIAL_RENDERING_REQUEST: {
                if (msg.what == COMPUTE_PARTIAL_RENDERING_REQUEST) {
                    if (mProcessingHandler.hasMessages(COMPUTE_PARTIAL_RENDERING_REQUEST)) {
                        return false;
                    }
                }
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
    private static long HIRES_DELAY = 100; // in ms

    private volatile Allocation mOriginalAllocation = null;
    private volatile Allocation mFiltersOnlyOriginalAllocation =  null;

    private FilteringPipeline() {
        mHandlerThread = new HandlerThread("FilteringPipeline",
                Process.THREAD_PRIORITY_FOREGROUND);
        mHandlerThread.start();
        mProcessingHandler = new Handler(mHandlerThread.getLooper(), this);
    }

    public synchronized static FilteringPipeline getPipeline() {
        if (sPipeline == null) {
            sPipeline = new FilteringPipeline();
        }
        return sPipeline;
    }

    public synchronized void setOriginal(Bitmap bitmap) {
        mOriginalBitmap = bitmap;
        Log.v(LOGTAG,"setOriginal, size " + bitmap.getWidth() + " x " + bitmap.getHeight());
        ImagePreset preset = MasterImage.getImage().getPreset();
        preset.setupEnvironment();
        updateOriginalAllocation(preset);
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

        if (DEBUG) {
            Log.v(LOGTAG, "geometry has changed");
        }

        RenderScript RS = ImageFilterRS.getRenderScriptContext();
        if (mFiltersOnlyOriginalAllocation != null) {
            mFiltersOnlyOriginalAllocation.destroy();
            mFiltersOnlyOriginalAllocation = null;
        }
        mFiltersOnlyOriginalAllocation = Allocation.createFromBitmap(RS, mOriginalBitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        if (mOriginalAllocation != null) {
            mOriginalAllocation.destroy();
            mOriginalAllocation = null;
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
        if (!mPipelineIsOn) {
            return;
        }
        int type = COMPUTE_RENDERING_REQUEST;
        if (request.getType() == RenderingRequest.PARTIAL_RENDERING) {
            type = COMPUTE_PARTIAL_RENDERING_REQUEST;
        }
        Message msg = mProcessingHandler.obtainMessage(type);
        msg.obj = request;
        if (type == COMPUTE_PARTIAL_RENDERING_REQUEST) {
            mProcessingHandler.sendMessageDelayed(msg, HIRES_DELAY);
        } else {
            mProcessingHandler.sendMessage(msg);
        }
    }

    public synchronized void updatePreviewBuffer() {
        if (mOriginalAllocation == null) {
            return;
        }
        mHasUnhandledPreviewRequest = true;
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
        mHasUnhandledPreviewRequest = false;
        mProcessingHandler.sendMessageAtFrontOfQueue(msg);
    }

    private void setPresetParameters(ImagePreset preset) {
        float scale = mPreviewScaleFactor;
        preset.setScaleFactor(scale);
        if (scale < 1.0f) {
            preset.setQuality(ImagePreset.QUALITY_PREVIEW);
        } else {
            preset.setQuality(ImagePreset.QUALITY_PREVIEW);
        }
    }

    private String getType(RenderingRequest request) {
        if (request.getType() == RenderingRequest.ICON_RENDERING) {
            return "ICON_RENDERING";
        }
        if (request.getType() == RenderingRequest.FILTERS_RENDERING) {
            return "FILTERS_RENDERING";
        }
        if (request.getType() == RenderingRequest.FULL_RENDERING) {
            return "FULL_RENDERING";
        }
        if (request.getType() == RenderingRequest.GEOMETRY_RENDERING) {
            return "GEOMETRY_RENDERING";
        }
        if (request.getType() == RenderingRequest.PARTIAL_RENDERING) {
            return "PARTIAL_RENDERING";
        }
        return "UNKNOWN TYPE!";
    }

    private synchronized void render(RenderingRequest request) {
        if ((request.getType() != RenderingRequest.PARTIAL_RENDERING
                && request.getBitmap() == null)
                || request.getImagePreset() == null) {
            return;
        }
        if (DEBUG) {
            Log.v(LOGTAG, "render image of type " + getType(request));
        }

        Bitmap bitmap = request.getBitmap();
        ImagePreset preset = request.getImagePreset();
        setPresetParameters(preset);
        preset.setupEnvironment();

        if (request.getType() == RenderingRequest.PARTIAL_RENDERING) {
            ImageLoader loader = MasterImage.getImage().getImageLoader();
            if (loader == null) {
                Log.w(LOGTAG, "loader not yet setup, cannot handle: " + getType(request));
                return;
            }
            bitmap = loader.getScaleOneImageForPreset(null, preset,
                    request.getBounds(), request.getDestination(), false);
            if (bitmap == null) {
                Log.w(LOGTAG, "could not get bitmap for: " + getType(request));
                return;
            }
        }

        if (request.getType() != RenderingRequest.ICON_RENDERING
                && request.getType() != RenderingRequest.PARTIAL_RENDERING) {
            updateOriginalAllocation(preset);
        }
        if (DEBUG) {
            Log.v(LOGTAG, "after update, req bitmap (" + bitmap.getWidth() + "x" + bitmap.getHeight()
                    +" ? resizeOriginal (" + mResizedOriginalBitmap.getWidth() + "x"
                    + mResizedOriginalBitmap.getHeight());
        }
        if (request.getType() == RenderingRequest.FULL_RENDERING
                || request.getType() == RenderingRequest.GEOMETRY_RENDERING) {
            mOriginalAllocation.copyTo(bitmap);
        } else if (request.getType() == RenderingRequest.FILTERS_RENDERING) {
            mFiltersOnlyOriginalAllocation.copyTo(bitmap);
        }

        if (request.getType() == RenderingRequest.FULL_RENDERING
                || request.getType() == RenderingRequest.FILTERS_RENDERING
                || request.getType() == RenderingRequest.ICON_RENDERING
                || request.getType() == RenderingRequest.PARTIAL_RENDERING) {
            Bitmap bmp = preset.apply(bitmap);
            request.setBitmap(bmp);

            FiltersManager.getManager().freeFilterResources(preset);
        }

    }

    private synchronized void compute(TripleBufferBitmap buffer, ImagePreset preset, int type) {
        if (DEBUG) {
            Log.v(LOGTAG, "compute preset " + preset);
            preset.showFilters();
        }

        String thread = Thread.currentThread().getName();
        long time = System.currentTimeMillis();
        setPresetParameters(preset);
        preset.setupEnvironment(FiltersManager.getPreviewManager());

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
        FiltersManager.getPreviewManager().freeFilterResources(preset);

        time = System.currentTimeMillis() - time;
        time2 = System.currentTimeMillis() - time2;
        if (DEBUG) {
            Log.v(LOGTAG, "Applying type " + type + " filters to bitmap "
                    + bitmap + " (" + bitmap.getWidth() + " x " + bitmap.getHeight()
                    + ") took " + time + " ms, " + time2 + " ms for the filter, on thread " + thread);
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

    public static synchronized void reset() {
        sPipeline.mHandlerThread.quit();
        sPipeline = null;
    }

    public void turnOnPipeline(boolean t) {
        mPipelineIsOn = t;
    }
}
