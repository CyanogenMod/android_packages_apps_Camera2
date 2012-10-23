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
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.util.Log;

public class ImageFilterRS extends ImageFilter {
    private final String LOGTAG = "ImageFilterRS";

    private static RenderScript mRS = null;
    protected static Allocation mInPixelsAllocation;
    protected static Allocation mOutPixelsAllocation;
    private static android.content.res.Resources mResources = null;

    public void prepare(Bitmap bitmap) {
        mInPixelsAllocation = Allocation.createFromBitmap(mRS, bitmap,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT);
        mOutPixelsAllocation = Allocation.createTyped(mRS, mInPixelsAllocation.getType());
    }

    public void createFilter(android.content.res.Resources res, float scaleFactor,
            boolean highQuality) {
    }

    public void runFilter() {
    }

    public void update(Bitmap bitmap) {
        mOutPixelsAllocation.copyTo(bitmap);
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (bitmap == null) {
            return bitmap;
        }
        try {
            prepare(bitmap);
            createFilter(mResources, scaleFactor, highQuality);
            runFilter();
            update(bitmap);
        } catch (android.renderscript.RSIllegalArgumentException e) {
            Log.e(LOGTAG, "Illegal argument? " + e);
        } catch (android.renderscript.RSRuntimeException e) {
            Log.e(LOGTAG, "RS runtime exception ? " + e);
        }
        return bitmap;
    }

    public static RenderScript getRenderScriptContext() {
        return mRS;
    }

    public static void setRenderScriptContext(Activity context) {
        mRS = RenderScript.create(context);
        mResources = context.getResources();
    }

}
