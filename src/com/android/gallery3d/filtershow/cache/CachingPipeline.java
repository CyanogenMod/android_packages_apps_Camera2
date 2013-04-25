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

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.util.Log;

import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilterGeometry;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.presets.FilterEnvironment;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class CachingPipeline {
    private static final String LOGTAG = "CachingPipeline";
    private boolean DEBUG = false;

    private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.ARGB_8888;

    private static volatile RenderScript sRS = null;
    private static volatile Resources sResources = null;

    private FiltersManager mFiltersManager = null;
    private volatile Bitmap mOriginalBitmap = null;
    private volatile Bitmap mResizedOriginalBitmap = null;

    private FilterEnvironment mEnvironment = new FilterEnvironment();

    private volatile Allocation mOriginalAllocation = null;
    private volatile Allocation mFiltersOnlyOriginalAllocation =  null;

    protected volatile Allocation mInPixelsAllocation;
    protected volatile Allocation mOutPixelsAllocation;
    private volatile int mWidth = 0;
    private volatile int mHeight = 0;

    private volatile GeometryMetadata mPreviousGeometry = null;
    private volatile float mPreviewScaleFactor = 1.0f;
    private volatile float mHighResPreviewScaleFactor = 1.0f;
    private volatile String mName = "";

    private ImageFilterGeometry mGeometry = null;

    public CachingPipeline(FiltersManager filtersManager, String name) {
        mFiltersManager = filtersManager;
        mName = name;
    }

    public static synchronized Resources getResources() {
        return sResources;
    }

    public static synchronized void setResources(Resources resources) {
        sResources = resources;
    }

    public static synchronized RenderScript getRenderScriptContext() {
        return sRS;
    }

    public static synchronized void setRenderScriptContext(RenderScript RS) {
        sRS = RS;
    }

    public static synchronized void createRenderscriptContext(Activity context) {
        if (sRS != null) {
            Log.w(LOGTAG, "A prior RS context exists when calling setRenderScriptContext");
            destroyRenderScriptContext();
        }
        sRS = RenderScript.create(context);
        sResources = context.getResources();
    }

    public static synchronized void destroyRenderScriptContext() {
        if (sRS != null) {
            sRS.destroy();
        }
        sRS = null;
        sResources = null;
    }

    public void stop() {
        mEnvironment.setStop(true);
    }

    public synchronized void reset() {
        synchronized (CachingPipeline.class) {
            if (getRenderScriptContext() == null) {
                return;
            }
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
            mHighResPreviewScaleFactor = 1.0f;

            destroyPixelAllocations();
        }
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
        if (request.getType() == RenderingRequest.HIGHRES_RENDERING) {
            return "HIGHRES_RENDERING";
        }
        return "UNKNOWN TYPE!";
    }

    private void setupEnvironment(ImagePreset preset, boolean highResPreview) {
        mEnvironment.setCachingPipeline(this);
        mEnvironment.setFiltersManager(mFiltersManager);
        if (highResPreview) {
            mEnvironment.setScaleFactor(mHighResPreviewScaleFactor);
        } else {
            mEnvironment.setScaleFactor(mPreviewScaleFactor);
        }
        mEnvironment.setQuality(ImagePreset.QUALITY_PREVIEW);
        mEnvironment.setImagePreset(preset);
        mEnvironment.setStop(false);
    }

    public void setOriginal(Bitmap bitmap) {
        mOriginalBitmap = bitmap;
        Log.v(LOGTAG,"setOriginal, size " + bitmap.getWidth() + " x " + bitmap.getHeight());
        ImagePreset preset = MasterImage.getImage().getPreset();
        setupEnvironment(preset, false);
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

        RenderScript RS = getRenderScriptContext();

        Allocation filtersOnlyOriginalAllocation = mFiltersOnlyOriginalAllocation;
        mFiltersOnlyOriginalAllocation = Allocation.createFromBitmap(RS, originalBitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        if (filtersOnlyOriginalAllocation != null) {
            filtersOnlyOriginalAllocation.destroy();
        }

        Allocation originalAllocation = mOriginalAllocation;
        mResizedOriginalBitmap = preset.applyGeometry(originalBitmap, mEnvironment);
        mOriginalAllocation = Allocation.createFromBitmap(RS, mResizedOriginalBitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        if (originalAllocation != null) {
            originalAllocation.destroy();
        }

        mPreviousGeometry = new GeometryMetadata(geometry);
        return true;
    }

    public synchronized void render(RenderingRequest request) {
        synchronized (CachingPipeline.class) {
            if (getRenderScriptContext() == null) {
                return;
            }
            if (((request.getType() != RenderingRequest.PARTIAL_RENDERING
                    && request.getType() != RenderingRequest.HIGHRES_RENDERING)
                    && request.getBitmap() == null)
                    || request.getImagePreset() == null) {
                return;
            }

            if (DEBUG) {
                Log.v(LOGTAG, "render image of type " + getType(request));
            }

            Bitmap bitmap = request.getBitmap();
            ImagePreset preset = request.getImagePreset();
            setupEnvironment(preset,
                    request.getType() != RenderingRequest.HIGHRES_RENDERING);
            mFiltersManager.freeFilterResources(preset);

            if (request.getType() == RenderingRequest.PARTIAL_RENDERING) {
                ImageLoader loader = MasterImage.getImage().getImageLoader();
                if (loader == null) {
                    Log.w(LOGTAG, "loader not yet setup, cannot handle: " + getType(request));
                    return;
                }
                bitmap = loader.getScaleOneImageForPreset(request.getBounds(),
                        request.getDestination());
                if (bitmap == null) {
                    Log.w(LOGTAG, "could not get bitmap for: " + getType(request));
                    return;
                }
            }

            if (request.getType() == RenderingRequest.HIGHRES_RENDERING) {
                ImageLoader loader = MasterImage.getImage().getImageLoader();
                bitmap = loader.getOriginalBitmapHighres();
                bitmap = preset.applyGeometry(bitmap, mEnvironment);
            }

            if (request.getType() == RenderingRequest.FULL_RENDERING
                    || request.getType() == RenderingRequest.GEOMETRY_RENDERING
                    || request.getType() == RenderingRequest.FILTERS_RENDERING) {
                updateOriginalAllocation(preset);
            }

            if (DEBUG) {
                Log.v(LOGTAG, "after update, req bitmap (" + bitmap.getWidth() + "x" + bitmap.getHeight()
                        + " ? resizeOriginal (" + mResizedOriginalBitmap.getWidth() + "x"
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
                    || request.getType() == RenderingRequest.PARTIAL_RENDERING
                    || request.getType() == RenderingRequest.HIGHRES_RENDERING
                    || request.getType() == RenderingRequest.STYLE_ICON_RENDERING) {

                if (request.getType() == RenderingRequest.ICON_RENDERING) {
                    mEnvironment.setQuality(ImagePreset.QUALITY_ICON);
                } else  if (request.getType() == RenderingRequest.STYLE_ICON_RENDERING) {
                    mEnvironment.setQuality(ImagePreset.STYLE_ICON);
                } else {
                    mEnvironment.setQuality(ImagePreset.QUALITY_PREVIEW);
                }

                Bitmap bmp = preset.apply(bitmap, mEnvironment);
                if (!mEnvironment.needsStop()) {
                    request.setBitmap(bmp);
                }
                mFiltersManager.freeFilterResources(preset);
            }
        }
    }

    public synchronized void renderImage(ImagePreset preset, Allocation in, Allocation out) {
        synchronized (CachingPipeline.class) {
            if (getRenderScriptContext() == null) {
                return;
            }
            setupEnvironment(preset, false);
            mFiltersManager.freeFilterResources(preset);
            preset.applyFilters(-1, -1, in, out, mEnvironment);
            // TODO: we should render the border onto a different bitmap instead
            preset.applyBorder(in, out, mEnvironment);
        }
    }

    public synchronized Bitmap renderFinalImage(Bitmap bitmap, ImagePreset preset) {
        synchronized (CachingPipeline.class) {
            if (getRenderScriptContext() == null) {
                return bitmap;
            }
            setupEnvironment(preset, false);
            mEnvironment.setQuality(ImagePreset.QUALITY_FINAL);
            mEnvironment.setScaleFactor(1.0f);
            mFiltersManager.freeFilterResources(preset);
            bitmap = preset.applyGeometry(bitmap, mEnvironment);
            bitmap = preset.apply(bitmap, mEnvironment);
            return bitmap;
        }
    }

    public Bitmap renderGeometryIcon(Bitmap bitmap, ImagePreset preset) {
        // Called by RenderRequest on the main thread
        // TODO: change this -- we should reuse a pool of bitmaps instead...
        if (mGeometry == null) {
            mGeometry = new ImageFilterGeometry();
        }
        mGeometry.useRepresentation(preset.getGeometry());
        return mGeometry.apply(bitmap, mPreviewScaleFactor,
                ImagePreset.QUALITY_PREVIEW);
    }

    public synchronized void compute(TripleBufferBitmap buffer, ImagePreset preset, int type) {
        synchronized (CachingPipeline.class) {
            if (getRenderScriptContext() == null) {
                return;
            }
            if (DEBUG) {
                Log.v(LOGTAG, "compute preset " + preset);
                preset.showFilters();
            }

            String thread = Thread.currentThread().getName();
            long time = System.currentTimeMillis();
            setupEnvironment(preset, false);
            mFiltersManager.freeFilterResources(preset);

            Bitmap resizedOriginalBitmap = mResizedOriginalBitmap;
            if (updateOriginalAllocation(preset)) {
                resizedOriginalBitmap = mResizedOriginalBitmap;
                mEnvironment.cache(buffer.getProducer());
                buffer.updateProducerBitmap(resizedOriginalBitmap);
            }
            Bitmap bitmap = buffer.getProducer();
            long time2 = System.currentTimeMillis();

            if (bitmap == null || (bitmap.getWidth() != resizedOriginalBitmap.getWidth())
                    || (bitmap.getHeight() != resizedOriginalBitmap.getHeight())) {
                mEnvironment.cache(buffer.getProducer());
                buffer.updateProducerBitmap(resizedOriginalBitmap);
                bitmap = buffer.getProducer();
            }
            mOriginalAllocation.copyTo(bitmap);

            Bitmap tmpbitmap = preset.apply(bitmap, mEnvironment);
            if (tmpbitmap != bitmap) {
                mEnvironment.cache(buffer.getProducer());
                buffer.setProducer(tmpbitmap);
            }

            mFiltersManager.freeFilterResources(preset);

            time = System.currentTimeMillis() - time;
            time2 = System.currentTimeMillis() - time2;
            if (DEBUG) {
                Log.v(LOGTAG, "Applying type " + type + " filters to bitmap "
                        + bitmap + " (" + bitmap.getWidth() + " x " + bitmap.getHeight()
                        + ") took " + time + " ms, " + time2 + " ms for the filter, on thread " + thread);
            }
        }
    }

    public boolean needsRepaint() {
        TripleBufferBitmap buffer = MasterImage.getImage().getDoubleBuffer();
        return buffer.checkRepaintNeeded();
    }

    public void setPreviewScaleFactor(float previewScaleFactor) {
        mPreviewScaleFactor = previewScaleFactor;
    }

    public void setHighResPreviewScaleFactor(float highResPreviewScaleFactor) {
        mHighResPreviewScaleFactor = highResPreviewScaleFactor;
    }

    public synchronized boolean isInitialized() {
        return getRenderScriptContext() != null && mOriginalBitmap != null;
    }

    public boolean prepareRenderscriptAllocations(Bitmap bitmap) {
        RenderScript RS = getRenderScriptContext();
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
        if (RS != null) {
            mInPixelsAllocation.copyFrom(bitmap);
        }
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
