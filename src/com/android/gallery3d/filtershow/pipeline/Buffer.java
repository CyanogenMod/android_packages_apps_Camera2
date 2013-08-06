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

package com.android.gallery3d.filtershow.pipeline;

import android.graphics.Bitmap;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;

public class Buffer {
    private static final String LOGTAG = "Buffer";
    private Bitmap mBitmap;
    private Allocation mAllocation;
    private boolean mUseAllocation = false;
    private static final Bitmap.Config BITMAP_CONFIG = Bitmap.Config.ARGB_8888;
    private ImagePreset mPreset;

    public Buffer(Bitmap bitmap) {
        RenderScript rs = CachingPipeline.getRenderScriptContext();
        if (bitmap != null) {
            mBitmap = bitmap.copy(BITMAP_CONFIG, true);
        }
        if (mUseAllocation) {
            // TODO: recreate the allocation when the RS context changes
            mAllocation = Allocation.createFromBitmap(rs, mBitmap,
                    Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        }
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap.copy(BITMAP_CONFIG, true);
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public Allocation getAllocation() {
        return mAllocation;
    }

    public void sync() {
        if (mUseAllocation) {
            mAllocation.copyTo(mBitmap);
        }
    }

    public ImagePreset getPreset() {
        return mPreset;
    }

    public void setPreset(ImagePreset preset) {
        if ((mPreset == null) || (!mPreset.same(preset))) {
            mPreset = new ImagePreset(preset);
        } else {
            mPreset.updateWith(preset);
        }
    }
}

