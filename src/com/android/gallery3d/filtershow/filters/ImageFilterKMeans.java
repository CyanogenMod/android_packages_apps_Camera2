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

import com.android.gallery3d.R;

public class ImageFilterKMeans extends ImageFilter {
    public ImageFilterKMeans() {
        mName = "KMeans";
        mMaxParameter = 20;
        mMinParameter = 2;
        mPreviewParameter = 4;
        mDefaultParameter = 4;
        mParameter = 4;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, int p);

    @Override
    public int getButtonId() {
        return R.id.kmeansButton;
    }

    @Override
    public int getTextId() {
        return R.string.kmeans;
    }

    @Override
    public boolean isNil() {
        return false;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int p = Math.max(mParameter, mMinParameter) % (mMaxParameter + 1);
        nativeApplyFilter(bitmap, w, h, p);
        return bitmap;
    }
}
