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
    private boolean DEBUG = false;

    private static long HIRES_DELAY = 300; // in ms

    private volatile boolean mPipelineIsOn = false;

    private CachingPipeline mAccessoryPipeline = null;
    private CachingPipeline mPreviewPipeline = null;
    private CachingPipeline mHighresPreviewPipeline = null;

    private HandlerThread mHandlerThread = null;
    private final static int NEW_PRESET = 0;
    private final static int NEW_RENDERING_REQUEST = 1;
    private final static int COMPUTE_PRESET = 2;
    private final static int COMPUTE_RENDERING_REQUEST = 3;
    private final static int COMPUTE_PARTIAL_RENDERING_REQUEST = 4;
    private final static int COMPUTE_HIGHRES_RENDERING_REQUEST = 5;

    private volatile boolean mHasUnhandledPreviewRequest = false;

    private String getType(int value) {
        if (value == COMPUTE_RENDERING_REQUEST) {
            return "COMPUTE_RENDERING_REQUEST";
        }
        if (value == COMPUTE_PARTIAL_RENDERING_REQUEST) {
            return "COMPUTE_PARTIAL_RENDERING_REQUEST";
        }
        if (value == COMPUTE_HIGHRES_RENDERING_REQUEST) {
            return "COMPUTE_HIGHRES_RENDERING_REQUEST";
        }
        return "UNKNOWN TYPE";
    }

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
                mPreviewPipeline.compute(buffer, preset, COMPUTE_PRESET);
                buffer.swapProducer();
                Message uimsg = mUIHandler.obtainMessage(NEW_PRESET);
                mUIHandler.sendMessage(uimsg);
                break;
            }
            case COMPUTE_RENDERING_REQUEST:
            case COMPUTE_PARTIAL_RENDERING_REQUEST:
            case COMPUTE_HIGHRES_RENDERING_REQUEST: {

                if (DEBUG) {
                    Log.v(LOGTAG, "Compute Request: " + getType(msg.what));
                }

                RenderingRequest request = (RenderingRequest) msg.obj;
                if (msg.what == COMPUTE_HIGHRES_RENDERING_REQUEST) {
                    mHighresPreviewPipeline.render(request);
                } else {
                    mAccessoryPipeline.render(request);
                }
                if (request.getBitmap() != null) {
                    Message uimsg = mUIHandler.obtainMessage(NEW_RENDERING_REQUEST);
                    uimsg.obj = request;
                    mUIHandler.sendMessage(uimsg);
                }
                break;
            }
        }
        return false;
    }

    private FilteringPipeline() {
        mHandlerThread = new HandlerThread("FilteringPipeline",
                Process.THREAD_PRIORITY_FOREGROUND);
        mHandlerThread.start();
        mProcessingHandler = new Handler(mHandlerThread.getLooper(), this);
        mAccessoryPipeline = new CachingPipeline(
                FiltersManager.getManager(), "Accessory");
        mPreviewPipeline = new CachingPipeline(
                FiltersManager.getPreviewManager(), "Preview");
        mHighresPreviewPipeline = new CachingPipeline(
                FiltersManager.getHighresManager(), "Highres");
    }

    public synchronized static FilteringPipeline getPipeline() {
        if (sPipeline == null) {
            sPipeline = new FilteringPipeline();
        }
        return sPipeline;
    }

    public void setOriginal(Bitmap bitmap) {
        if (mPipelineIsOn) {
            Log.e(LOGTAG, "setOriginal called after pipeline initialization!");
            return;
        }
        mAccessoryPipeline.setOriginal(bitmap);
        mPreviewPipeline.setOriginal(bitmap);
        mHighresPreviewPipeline.setOriginal(bitmap);
    }

    public void postRenderingRequest(RenderingRequest request) {
        if (!mPipelineIsOn) {
            return;
        }
        int type = COMPUTE_RENDERING_REQUEST;
        if (request.getType() == RenderingRequest.PARTIAL_RENDERING) {
            type = COMPUTE_PARTIAL_RENDERING_REQUEST;
        }
        if (request.getType() == RenderingRequest.HIGHRES_RENDERING) {
            type = COMPUTE_HIGHRES_RENDERING_REQUEST;
        }
        Message msg = mProcessingHandler.obtainMessage(type);
        msg.obj = request;
        if (type == COMPUTE_PARTIAL_RENDERING_REQUEST
                || type == COMPUTE_HIGHRES_RENDERING_REQUEST) {
            if (mProcessingHandler.hasMessages(msg.what)) {
                mProcessingHandler.removeMessages(msg.what);
            }
            mProcessingHandler.sendMessageDelayed(msg, HIRES_DELAY);
        } else {
            mProcessingHandler.sendMessage(msg);
        }
    }

    public void updatePreviewBuffer() {
        if (!mPipelineIsOn) {
            return;
        }
        mHasUnhandledPreviewRequest = true;
        mHighresPreviewPipeline.stop();
        if (mProcessingHandler.hasMessages(COMPUTE_PRESET)) {
            return;
        }
        if (!mPreviewPipeline.needsRepaint()) {
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

    public void setPreviewScaleFactor(float previewScaleFactor) {
        mAccessoryPipeline.setPreviewScaleFactor(previewScaleFactor);
        mPreviewPipeline.setPreviewScaleFactor(previewScaleFactor);
        mHighresPreviewPipeline.setPreviewScaleFactor(previewScaleFactor);
    }

    public void setHighResPreviewScaleFactor(float highResPreviewScaleFactor) {
        mAccessoryPipeline.setHighResPreviewScaleFactor(highResPreviewScaleFactor);
        mPreviewPipeline.setHighResPreviewScaleFactor(highResPreviewScaleFactor);
        mHighresPreviewPipeline.setHighResPreviewScaleFactor(highResPreviewScaleFactor);
    }

    public static synchronized void reset() {
        sPipeline.mAccessoryPipeline.reset();
        sPipeline.mPreviewPipeline.reset();
        sPipeline.mHighresPreviewPipeline.reset();
        sPipeline.mHandlerThread.quit();
        sPipeline = null;
    }

    public void turnOnPipeline(boolean t) {
        mPipelineIsOn = t;
        if (mPipelineIsOn) {
            assert(mPreviewPipeline.isInitialized());
            assert(mAccessoryPipeline.isInitialized());
            assert(mHighresPreviewPipeline.isInitialized());
            updatePreviewBuffer();
        }
    }
}
