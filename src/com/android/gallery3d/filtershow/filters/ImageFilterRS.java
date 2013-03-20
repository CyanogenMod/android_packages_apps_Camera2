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

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v8.renderscript.*;
import android.util.Log;
import android.content.res.Resources;
import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.CachingPipeline;

public abstract class ImageFilterRS extends ImageFilter {
    private static final String LOGTAG = "ImageFilterRS";

    private static volatile RenderScript sRS = null;
    private static volatile Resources sResources = null;
    private volatile boolean mResourcesLoaded = false;

    // This must be used inside block synchronized on ImageFilterRS class object
    protected abstract void createFilter(android.content.res.Resources res,
            float scaleFactor, int quality);

    // This must be used inside block synchronized on ImageFilterRS class object
    protected abstract void runFilter();

    // This must be used inside block synchronized on ImageFilterRS class object
    protected void update(Bitmap bitmap) {
        getOutPixelsAllocation().copyTo(bitmap);
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
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return bitmap;
        }
        try {
            synchronized(ImageFilterRS.class) {
                if (sRS == null)  {
                    Log.w(LOGTAG, "Cannot apply before calling createRenderScriptContext");
                    return bitmap;
                }
                CachingPipeline pipeline = getEnvironment().getCachingPipeline();
                boolean needsUpdate = pipeline.prepareRenderscriptAllocations(bitmap);
                if (needsUpdate || !isResourcesLoaded()) {
                    // the allocations changed size
                    freeResources();
                    createFilter(sResources, scaleFactor, quality);
                    setResourcesLoaded(true);
                }
                runFilter();
                update(bitmap);
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

    public static synchronized RenderScript getRenderScriptContext() {
        return sRS;
    }

    public static synchronized void createRenderscriptContext(Activity context) {
        if( sRS != null) {
            Log.w(LOGTAG, "A prior RS context exists when calling setRenderScriptContext");
            destroyRenderScriptContext();
        }
        sRS = RenderScript.create(context);
        sResources = context.getResources();
    }

    public static synchronized void destroyRenderScriptContext() {
        sRS.destroy();
        sRS = null;
        sResources = null;
    }

    private static synchronized Allocation convertBitmap(Bitmap bitmap) {
        return Allocation.createFromBitmap(sRS, bitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
    }

    private static synchronized Allocation convertRGBAtoA(Bitmap bitmap) {
        Type.Builder tb_a8 = new Type.Builder(sRS, Element.A_8(sRS));
        ScriptC_grey greyConvert = new ScriptC_grey(sRS,
                sRS.getApplicationContext().getResources(), R.raw.grey);

        Allocation bitmapTemp = convertBitmap(bitmap);
        if (bitmapTemp.getType().getElement().isCompatible(Element.A_8(sRS))) {
            return bitmapTemp;
        }

        tb_a8.setX(bitmapTemp.getType().getX());
        tb_a8.setY(bitmapTemp.getType().getY());
        Allocation bitmapAlloc = Allocation.createTyped(sRS, tb_a8.create());
        greyConvert.forEach_RGBAtoA(bitmapTemp, bitmapAlloc);

        return bitmapAlloc;
    }

    public Allocation loadScaledResourceAlpha(int resource, int inSampleSize) {
        Resources res = null;
        synchronized(ImageFilterRS.class) {
            res = sRS.getApplicationContext().getResources();
        }
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

    public Allocation loadResourceAlpha(int resource) {
        return loadScaledResourceAlpha(resource, 1);
    }

    public Allocation loadResource(int resource) {
        Resources res = null;
        synchronized(ImageFilterRS.class) {
            res = sRS.getApplicationContext().getResources();
        }
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

    public void freeResources() {
        if (!isResourcesLoaded()) {
            return;
        }
        synchronized(ImageFilterRS.class) {
            resetAllocations();
            setResourcesLoaded(false);
        }
    }
}
