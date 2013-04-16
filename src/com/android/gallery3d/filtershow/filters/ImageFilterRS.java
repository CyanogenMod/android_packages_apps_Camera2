/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.filtershow.filters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v8.renderscript.*;
import android.util.Log;
import android.content.res.Resources;
import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.CachingPipeline;

public abstract class ImageFilterRS extends ImageFilter {
    private static final String LOGTAG = "ImageFilterRS";
    private boolean DEBUG = false;
    private int mLastInputWidth = 0;
    private int mLastInputHeight = 0;

    public static boolean PERF_LOGGING = false;

    private static ScriptC_grey mGreyConvert = null;
    private static RenderScript mRScache = null;

    private volatile boolean mResourcesLoaded = false;

    protected abstract void createFilter(android.content.res.Resources res,
            float scaleFactor, int quality);

    protected void createFilter(android.content.res.Resources res,
    float scaleFactor, int quality, Allocation in) {}
    protected void bindScriptValues(Allocation in) {}

    protected abstract void runFilter();

    protected void update(Bitmap bitmap) {
        getOutPixelsAllocation().copyTo(bitmap);
    }

    protected RenderScript getRenderScriptContext() {
        return CachingPipeline.getRenderScriptContext();
    }

    protected Allocation getInPixelsAllocation() {
        CachingPipeline pipeline = getEnvironment().getCachingPipeline();
        return pipeline.getInPixelsAllocation();
    }

    protected Allocation getOutPixelsAllocation() {
        CachingPipeline pipeline = getEnvironment().getCachingPipeline();
        return pipeline.getOutPixelsAllocation();
    }

    @Override
    public void apply(Allocation in, Allocation out) {
        long startOverAll = System.nanoTime();
        long startFilter = 0;
        long endFilter = 0;
        if (!mResourcesLoaded) {
            CachingPipeline pipeline = getEnvironment().getCachingPipeline();
            createFilter(pipeline.getResources(), getEnvironment().getScaleFactor(),
                    getEnvironment().getQuality(), in);
            mResourcesLoaded = true;
        }
        startFilter = System.nanoTime();
        bindScriptValues(in);
        run(in, out);
        if (PERF_LOGGING) {
            getRenderScriptContext().finish();
            endFilter = System.nanoTime();
            long endOverAll = System.nanoTime();
            String msg = String.format("%s; image size %dx%d; ", getName(),
                    in.getType().getX(), in.getType().getY());
            long timeOverAll = (endOverAll - startOverAll) / 1000;
            long timeFilter = (endFilter - startFilter) / 1000;
            msg += String.format("over all %.2f ms (%.2f FPS); ",
                    timeOverAll / 1000.f, 1000000.f / timeOverAll);
            msg += String.format("run filter %.2f ms (%.2f FPS)",
                    timeFilter / 1000.f, 1000000.f / timeFilter);
            Log.i(LOGTAG, msg);
        }
    }

    protected void run(Allocation in, Allocation out) {}

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return bitmap;
        }
        try {
            CachingPipeline pipeline = getEnvironment().getCachingPipeline();
            if (DEBUG) {
                Log.v(LOGTAG, "apply filter " + getName() + " in pipeline " + pipeline.getName());
            }
            Resources rsc = pipeline.getResources();
            boolean sizeChanged = false;
            if (getInPixelsAllocation() != null
                    && ((getInPixelsAllocation().getType().getX() != mLastInputWidth)
                    || (getInPixelsAllocation().getType().getY() != mLastInputHeight))) {
                sizeChanged = true;
            }
            if (pipeline.prepareRenderscriptAllocations(bitmap)
                    || !isResourcesLoaded() || sizeChanged) {
                freeResources();
                createFilter(rsc, scaleFactor, quality);
                setResourcesLoaded(true);
                mLastInputWidth = getInPixelsAllocation().getType().getX();
                mLastInputHeight = getInPixelsAllocation().getType().getY();
            }
            bindScriptValues();
            runFilter();
            update(bitmap);
            if (DEBUG) {
                Log.v(LOGTAG, "DONE apply filter " + getName() + " in pipeline " + pipeline.getName());
            }
        } catch (android.renderscript.RSIllegalArgumentException e) {
            Log.e(LOGTAG, "Illegal argument? " + e);
        } catch (android.renderscript.RSRuntimeException e) {
            Log.e(LOGTAG, "RS runtime exception ? " + e);
        } catch (java.lang.OutOfMemoryError e) {
            // Many of the renderscript filters allocated large (>16Mb resources) in order to apply.
            System.gc();
            displayLowMemoryToast();
            Log.e(LOGTAG, "not enough memory for filter " + getName(), e);
        }

        return bitmap;
    }

    protected static Allocation convertBitmap(Bitmap bitmap) {
        return Allocation.createFromBitmap(CachingPipeline.getRenderScriptContext(), bitmap,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT | Allocation.USAGE_GRAPHICS_TEXTURE);
    }

    private static Allocation convertRGBAtoA(Bitmap bitmap) {
        RenderScript RS = CachingPipeline.getRenderScriptContext();
        if (RS != mRScache || mGreyConvert == null) {
            mGreyConvert = new ScriptC_grey(RS, RS.getApplicationContext().getResources(),
                                            R.raw.grey);
            mRScache = RS;
        }

        Type.Builder tb_a8 = new Type.Builder(RS, Element.A_8(RS));

        Allocation bitmapTemp = convertBitmap(bitmap);
        if (bitmapTemp.getType().getElement().isCompatible(Element.A_8(RS))) {
            return bitmapTemp;
        }

        tb_a8.setX(bitmapTemp.getType().getX());
        tb_a8.setY(bitmapTemp.getType().getY());
        Allocation bitmapAlloc = Allocation.createTyped(RS, tb_a8.create(),
                                                        Allocation.MipmapControl.MIPMAP_NONE,
                                                        Allocation.USAGE_SCRIPT | Allocation.USAGE_GRAPHICS_TEXTURE);
        mGreyConvert.forEach_RGBAtoA(bitmapTemp, bitmapAlloc);
        bitmapTemp.destroy();
        return bitmapAlloc;
    }

    public Allocation loadScaledResourceAlpha(int resource, int inSampleSize) {
        Resources res = CachingPipeline.getResources();
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ALPHA_8;
        options.inSampleSize      = inSampleSize;
        Bitmap bitmap = BitmapFactory.decodeResource(
                res,
                resource, options);
        Allocation ret = convertRGBAtoA(bitmap);
        bitmap.recycle();
        return ret;
    }

    public Allocation loadScaledResourceAlpha(int resource, int w, int h, int inSampleSize) {
        Resources res = CachingPipeline.getResources();
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ALPHA_8;
        options.inSampleSize      = inSampleSize;
        Bitmap bitmap = BitmapFactory.decodeResource(
                res,
                resource, options);
        Bitmap resizeBitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        Allocation ret = convertRGBAtoA(resizeBitmap);
        resizeBitmap.recycle();
        bitmap.recycle();
        return ret;
    }

    public Allocation loadResourceAlpha(int resource) {
        return loadScaledResourceAlpha(resource, 1);
    }

    public Allocation loadResource(int resource) {
        Resources res = CachingPipeline.getResources();
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(
                res,
                resource, options);
        Allocation ret = convertBitmap(bitmap);
        bitmap.recycle();
        return ret;
    }

    private boolean isResourcesLoaded() {
        return mResourcesLoaded;
    }

    private void setResourcesLoaded(boolean resourcesLoaded) {
        mResourcesLoaded = resourcesLoaded;
    }

    /**
     *  Bitmaps and RS Allocations should be cleared here
     */
    abstract protected void resetAllocations();

    /**
     * RS Script objects (and all other RS objects) should be cleared here
     */
    abstract protected void resetScripts();

    /**
     * Scripts values should be bound here
     */
    abstract protected void bindScriptValues();

    public void freeResources() {
        if (!isResourcesLoaded()) {
            return;
        }
        resetAllocations();
        setResourcesLoaded(false);
    }
}
