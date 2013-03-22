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
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.util.Log;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilterRS;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class CachingPipeline {
    private static final String LOGTAG = "CachingPipeline";
    private boolean DEBUG = false;

    private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.ARGB_8888;

    private FiltersManager mFiltersManager = null;
    private volatile Bitmap mOriginalBitmap = null;
    private volatile Bitmap mResizedOriginalBitmap = null;

    private volatile Allocation mOriginalAllocation = null;
    private volatile Allocation mFiltersOnlyOriginalAllocation =  null;

    protected volatile Allocation mInPixelsAllocation;
    protected volatile Allocation mOutPixelsAllocation;
    private volatile int mWidth = 0;
    private volatile int mHeight = 0;

    private volatile GeometryMetadata mPreviousGeometry = null;
    private volatile float mPreviewScaleFactor = 1.0f;
    private volatile String mName = "";

    public CachingPipeline(FiltersManager filtersManager, String name) {
        mFiltersManager = filtersManager;
        mName = name;
    }

    public synchronized void reset() {
        mOriginalBitmap = null; // just a reference to the bitmap in ImageLoader
        if (mResizedOriginalBitmap != null) {
            mResizedOriginalBitmap.recycle();
            mResizedOriginalBitmap = null;
        }
        if (mOriginalAllocation != null) {
            mOriginalAllocation.destroy();
            mOriginalAllocation = null;
        }
        if (mFiltersOnlyOriginalAllocation != null) {
            mFiltersOnlyOriginalAllocation.destroy();
            mFiltersOnlyOriginalAllocation = null;
        }
        mPreviousGeometry = null;
        mPreviewScaleFactor = 1.0f;

        destroyPixelAllocations();
    }

    private synchronized void destroyPixelAllocations() {
        if (DEBUG) {
            Log.v(LOGTAG, "destroyPixelAllocations in " + getName());
        }
        if (mInPixelsAllocation != null) {
            mInPixelsAllocation.destroy();
            mInPixelsAllocation = null;
        }
        if (mOutPixelsAllocation != null) {
            mOutPixelsAllocation.destroy();
            mOutPixelsAllocation = null;
        }
        mWidth = 0;
        mHeight = 0;
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

    private void setPresetParameters(ImagePreset preset) {
        preset.setScaleFactor(mPreviewScaleFactor);
        preset.setQuality(ImagePreset.QUALITY_PREVIEW);
        preset.setupEnvironment(mFiltersManager);
        preset.getEnvironment().setCachingPipeline(this);
    }

    public void setOriginal(Bitmap bitmap) {
        mOriginalBitmap = bitmap;
        Log.v(LOGTAG,"setOriginal, size " + bitmap.getWidth() + " x " + bitmap.getHeight());
        ImagePreset preset = MasterImage.getImage().getPreset();
        preset.setupEnvironment(mFiltersManager);
        updateOriginalAllocation(preset);
    }

    private synchronized boolean updateOriginalAllocation(ImagePreset preset) {
        Bitmap originalBitmap = mOriginalBitmap;

        if (originalBitmap == null) {
            return false;
        }

        GeometryMetadata geometry = preset.getGeometry();
        if (mPreviousGeometry != null && geometry.equals(mPreviousGeometry)) {
            return false;
        }

        if (DEBUG) {
            Log.v(LOGTAG, "geometry has changed");
        }

        RenderScript RS = ImageFilterRS.getRenderScriptContext();

        Allocation filtersOnlyOriginalAllocation = mFiltersOnlyOriginalAllocation;
        mFiltersOnlyOriginalAllocation = Allocation.createFromBitmap(RS, originalBitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        if (filtersOnlyOriginalAllocation != null) {
            filtersOnlyOriginalAllocation.destroy();
        }

        Allocation originalAllocation = mOriginalAllocation;
        mResizedOriginalBitmap = preset.applyGeometry(originalBitmap);
        mOriginalAllocation = Allocation.createFromBitmap(RS, mResizedOriginalBitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        if (originalAllocation != null) {
            originalAllocation.destroy();
        }

        mPreviousGeometry = new GeometryMetadata(geometry);
        return true;
    }

    public synchronized void render(RenderingRequest request) {
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
        mFiltersManager.freeFilterResources(preset);

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

        if (request.getType() == RenderingRequest.FULL_RENDERING
                || request.getType() == RenderingRequest.GEOMETRY_RENDERING
                || request.getType() == RenderingRequest.FILTERS_RENDERING) {
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
            mFiltersManager.freeFilterResources(preset);
        }

    }

    public synchronized Bitmap renderFinalImage(Bitmap bitmap, ImagePreset preset) {
        setPresetParameters(preset);
        mFiltersManager.freeFilterResources(preset);
        bitmap = preset.applyGeometry(bitmap);
        bitmap = preset.apply(bitmap);
        return bitmap;
    }

    public synchronized void compute(TripleBufferBitmap buffer, ImagePreset preset, int type) {
        if (DEBUG) {
            Log.v(LOGTAG, "compute preset " + preset);
            preset.showFilters();
        }

        String thread = Thread.currentThread().getName();
        long time = System.currentTimeMillis();
        setPresetParameters(preset);
        mFiltersManager.freeFilterResources(preset);

        Bitmap resizedOriginalBitmap = mResizedOriginalBitmap;
        if (updateOriginalAllocation(preset)) {
            resizedOriginalBitmap = mResizedOriginalBitmap;
            buffer.updateBitmaps(resizedOriginalBitmap);
        }
        Bitmap bitmap = buffer.getProducer();
        long time2 = System.currentTimeMillis();

        if (bitmap == null || (bitmap.getWidth() != resizedOriginalBitmap.getWidth())
                || (bitmap.getHeight() != resizedOriginalBitmap.getHeight())) {
            buffer.updateBitmaps(resizedOriginalBitmap);
            bitmap = buffer.getProducer();
        }
        mOriginalAllocation.copyTo(bitmap);

        bitmap = preset.apply(bitmap);

        mFiltersManager.freeFilterResources(preset);

        time = System.currentTimeMillis() - time;
        time2 = System.currentTimeMillis() - time2;
        if (DEBUG) {
            Log.v(LOGTAG, "Applying type " + type + " filters to bitmap "
                    + bitmap + " (" + bitmap.getWidth() + " x " + bitmap.getHeight()
                    + ") took " + time + " ms, " + time2 + " ms for the filter, on thread " + thread);
        }
    }

    public boolean needsRepaint() {
        TripleBufferBitmap buffer = MasterImage.getImage().getDoubleBuffer();
        return buffer.checkRepaintNeeded();
    }


    public void setPreviewScaleFactor(float previewScaleFactor) {
        mPreviewScaleFactor = previewScaleFactor;
    }

    public synchronized boolean isInitialized() {
        return mOriginalBitmap != null;
    }

    public boolean prepareRenderscriptAllocations(Bitmap bitmap) {
        RenderScript RS = ImageFilterRS.getRenderScriptContext();
        boolean needsUpdate = false;
        if (mOutPixelsAllocation == null || mInPixelsAllocation == null ||
                bitmap.getWidth() != mWidth || bitmap.getHeight() != mHeight) {
            destroyPixelAllocations();
            Bitmap bitmapBuffer = bitmap;
            if (bitmap.getConfig() == null || bitmap.getConfig() != BITMAP_CONFIG) {
                bitmapBuffer = bitmap.copy(BITMAP_CONFIG, true);
            }
            mOutPixelsAllocation = Allocation.createFromBitmap(RS, bitmapBuffer,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            mInPixelsAllocation = Allocation.createTyped(RS,
                    mOutPixelsAllocation.getType());
            needsUpdate = true;
        }
        mInPixelsAllocation.copyFrom(bitmap);
        if (bitmap.getWidth() != mWidth
                || bitmap.getHeight() != mHeight) {
            mWidth = bitmap.getWidth();
            mHeight = bitmap.getHeight();
            needsUpdate = true;
        }
        if (DEBUG) {
            Log.v(LOGTAG, "prepareRenderscriptAllocations: " + needsUpdate + " in " + getName());
        }
        return needsUpdate;
    }

    public synchronized Allocation getInPixelsAllocation() {
        return mInPixelsAllocation;
    }

    public synchronized Allocation getOutPixelsAllocation() {
        return mOutPixelsAllocation;
    }

    public String getName() {
        return mName;
    }
}
