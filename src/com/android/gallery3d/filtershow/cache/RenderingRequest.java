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
import com.android.gallery3d.app.Log;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class RenderingRequest {
    private static final String LOGTAG = "RenderingRequest";
    private boolean mIsDirect = false;
    private Bitmap mBitmap = null;
    private ImagePreset mImagePreset = null;
    private RenderingRequestCaller mCaller = null;
    private int mType = FULL_RENDERING;
    public static int FULL_RENDERING = 0;
    public static int FILTERS_RENDERING = 1;
    public static int GEOMETRY_RENDERING = 2;
    private static final Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;

    public static void post(Bitmap source, ImagePreset preset, int type,
                            RenderingRequestCaller caller) {
        if (source == null || preset == null || caller == null) {
            Log.v(LOGTAG, "something null: source: " + source + " or preset: " + preset + " or caller: " + caller);
            return;
        }
        RenderingRequest request = new RenderingRequest();
        Bitmap bitmap = null;
        if (type == FULL_RENDERING || type == GEOMETRY_RENDERING) {
            bitmap = preset.applyGeometry(source);
        } else {
            bitmap = Bitmap.createBitmap(source.getWidth(), source.getHeight(), mConfig);
        }
        request.setBitmap(bitmap);
        request.setImagePreset(new ImagePreset(preset));
        request.setType(type);
        request.setCaller(caller);
        request.post();
    }

    public void post() {
        FilteringPipeline.getPipeline().postRenderingRequest(this);
    }

    public void markAvailable() {
        if (mBitmap == null || mImagePreset == null
                || mCaller == null) {
            return;
        }
        mCaller.available(this);
    }

    public boolean isDirect() {
        return mIsDirect;
    }

    public void setDirect(boolean isDirect) {
        mIsDirect = isDirect;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    public ImagePreset getImagePreset() {
        return mImagePreset;
    }

    public void setImagePreset(ImagePreset imagePreset) {
        mImagePreset = imagePreset;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    public void setCaller(RenderingRequestCaller caller) {
        mCaller = caller;
    }
}
