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
    int mNameResource = 0;
    private FilterFxRepresentation mParameters = null;

    public ImageFilterFx() {

    }

    public ImageFilterFx(Bitmap fxBitmap, String name, int nameResource) {
        setFilterType(TYPE_FX);
        mName = name;
        this.fxBitmap = fxBitmap;
        mNameResource = nameResource;
    }

    public void useRepresentation(FilterRepresentation representation) {
        FilterFxRepresentation parameters = (FilterFxRepresentation) representation;
        mParameters = parameters;
    }

    public FilterFxRepresentation getParameters() {
        return mParameters;
    }

    @Override
    public int getTextId() {
        return mNameResource;
    }

    public boolean isNil() {
        if (fxBitmap != null) {
            return false;
        }
        return true;
    }

    @Override
    public boolean showParameterValue() {
        return false;
    }

    @Override
    public boolean showEditingControls() {
        return false;
    }

    @Override
    public boolean showUtilityPanel() {
        return false;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h,Bitmap  fxBitmap, int fxw, int fxh);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (getParameters() == null || getParameters().getFxBitmap() ==null) {
            return bitmap;
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int fxw = getParameters().getFxBitmap().getWidth();
        int fxh = getParameters().getFxBitmap().getHeight();

        nativeApplyFilter(bitmap, w, h,   getParameters().getFxBitmap(),  fxw,  fxh);
        return bitmap;
    }
}
