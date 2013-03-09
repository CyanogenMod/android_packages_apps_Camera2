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
import android.support.v8.renderscript.*;
import android.util.Log;
import com.android.gallery3d.R;

public abstract class ImageFilterRS extends ImageFilter {
    private final String LOGTAG = "ImageFilterRS";

    private static RenderScript mRS = null;
    protected static Allocation mInPixelsAllocation;
    protected static Allocation mOutPixelsAllocation;
    private static android.content.res.Resources mResources = null;
    private static Bitmap sOldBitmap = null;
    private Bitmap mOldBitmap = null;

    private boolean mResourcesLoaded = false;

    private final Bitmap.Config mBitmapConfig = Bitmap.Config.ARGB_8888;

    public void resetBitmap() {
        mOldBitmap = null;
    }

    public void prepare(Bitmap bitmap, float scaleFactor, int quality) {
        if (sOldBitmap == null
                || (bitmap.getWidth() != sOldBitmap.getWidth())
                || (bitmap.getHeight() != sOldBitmap.getHeight())) {
            if (mInPixelsAllocation != null) {
                mInPixelsAllocation.destroy();
                mInPixelsAllocation = null;
            }
            if (mOutPixelsAllocation != null) {
                mOutPixelsAllocation.destroy();
                mOutPixelsAllocation = null;
            }
            Bitmap bitmapBuffer = bitmap.copy(mBitmapConfig, true);
            mOutPixelsAllocation = Allocation.createFromBitmap(mRS, bitmapBuffer,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            mInPixelsAllocation = Allocation.createTyped(mRS,
                    mOutPixelsAllocation.getType());
            sOldBitmap = bitmap;
        }
        mInPixelsAllocation.copyFrom(bitmap);
        if (mOldBitmap != sOldBitmap || !isResourcesLoaded()) {
            freeResources();
            createFilter(mResources, scaleFactor, quality);
            mOldBitmap = sOldBitmap;
            setResourcesLoaded(true);
        }
    }

    abstract public void createFilter(android.content.res.Resources res,
            float scaleFactor, int quality);

    abstract public void runFilter();

    public void update(Bitmap bitmap) {
        mOutPixelsAllocation.copyTo(bitmap);
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        if (bitmap == null) {
            return bitmap;
        }
        try {
            prepare(bitmap, scaleFactor, quality);
            runFilter();
            update(bitmap);
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

    public static RenderScript getRenderScriptContext() {
        return mRS;
    }

    public static void setRenderScriptContext(Activity context) {
        if (mRS == null) {
            mRS = RenderScript.create(context);
        }
        mResources = context.getResources();
        if (mInPixelsAllocation != null) {
            mInPixelsAllocation.destroy();
            mInPixelsAllocation = null;
        }
        if (mOutPixelsAllocation != null) {
            mOutPixelsAllocation.destroy();
            mOutPixelsAllocation = null;
        }
        sOldBitmap = null;
    }

    public Allocation convertRGBAtoA(Bitmap bitmap) {
        Type.Builder tb_a8 = new Type.Builder(mRS, Element.U8(mRS));
        ScriptC_grey greyConvert = new ScriptC_grey(mRS, mResources, R.raw.grey);

        Allocation bitmapTemp = Allocation.createFromBitmap(mRS, bitmap);

        if (bitmapTemp.getType().getElement().isCompatible(Element.U8(mRS))) {
            return bitmapTemp;
        }

        tb_a8.setX(bitmapTemp.getType().getX());
        tb_a8.setY(bitmapTemp.getType().getY());
        Allocation bitmapAlloc = Allocation.createTyped(mRS, tb_a8.create());
        greyConvert.forEach_RGBAtoA(bitmapTemp, bitmapAlloc);

        return bitmapAlloc;
    }

    public boolean isResourcesLoaded() {
        return mResourcesLoaded;
    }

    public void setResourcesLoaded(boolean resourcesLoaded) {
        mResourcesLoaded = resourcesLoaded;
    }

    abstract protected void resetAllocations();

    public void freeResources() {
        if (!isResourcesLoaded()) {
            return;
        }
        resetAllocations();
        setResourcesLoaded(false);
    }
}
