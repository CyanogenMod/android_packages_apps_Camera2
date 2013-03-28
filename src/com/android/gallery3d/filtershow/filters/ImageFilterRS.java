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
    private boolean DEBUG = false;

    private volatile boolean mResourcesLoaded = false;

    protected abstract void createFilter(android.content.res.Resources res,
            float scaleFactor, int quality);

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
            if (pipeline.prepareRenderscriptAllocations(bitmap)
                    || !isResourcesLoaded()) {
                freeResources();
                createFilter(rsc, scaleFactor, quality);
                setResourcesLoaded(true);
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

    private static Allocation convertBitmap(Bitmap bitmap) {
        return Allocation.createFromBitmap(CachingPipeline.getRenderScriptContext(), bitmap,
                Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
    }

    private static Allocation convertRGBAtoA(Bitmap bitmap) {
        RenderScript RS = CachingPipeline.getRenderScriptContext();
        Type.Builder tb_a8 = new Type.Builder(RS, Element.A_8(RS));
        ScriptC_grey greyConvert = new ScriptC_grey(RS,
                RS.getApplicationContext().getResources(), R.raw.grey);

        Allocation bitmapTemp = convertBitmap(bitmap);
        if (bitmapTemp.getType().getElement().isCompatible(Element.A_8(RS))) {
            return bitmapTemp;
        }

        tb_a8.setX(bitmapTemp.getType().getX());
        tb_a8.setY(bitmapTemp.getType().getY());
        Allocation bitmapAlloc = Allocation.createTyped(RS, tb_a8.create());
        greyConvert.forEach_RGBAtoA(bitmapTemp, bitmapAlloc);

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
