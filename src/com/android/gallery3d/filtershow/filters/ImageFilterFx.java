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

public class ImageFilterFx extends ImageFilter {
    private static final String TAG = "ImageFilterFx";
    Bitmap fxBitmap;
    public ImageFilterFx(Bitmap fxBitmap,String name) {
        setFilterType(TYPE_FX);
        mName = name;
        this.fxBitmap = fxBitmap;
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterFx filter = (ImageFilterFx) super.clone();
        filter.fxBitmap = this.fxBitmap;
        return filter;
    }

    @Override
    public boolean isNil() {
        if (fxBitmap != null) {
            return false;
        }
        return true;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h,Bitmap  fxBitmap, int fxw, int fxh);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (fxBitmap==null)
            return bitmap;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int fxw = fxBitmap.getWidth();
        int fxh = fxBitmap.getHeight();

        nativeApplyFilter(bitmap, w, h,   fxBitmap,  fxw,  fxh);
        return bitmap;
    }
}
