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

public class ImageFilterDownsample extends ImageFilter {

    public ImageFilterDownsample() {
        mName = "Downsample";
        mMaxParameter = 100;
        mMinParameter = 5;
        mPreviewParameter = 10;
        mDefaultParameter = 50;
        mParameter = 50;
    }

    @Override
    public int getButtonId() {
        return R.id.downsampleButton;
    }

    @Override
    public int getTextId() {
        return R.string.downsample;
    }

    @Override
    public boolean isNil() {
        return false;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int p = mParameter;
        if (p > 0 && p < 100) {
            int newWidth =  w * p / 100;
            int newHeight = h * p / 100;
            if (newWidth <= 0 || newHeight <= 0) {
                return bitmap;
            }
            Bitmap ret = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            if (ret != bitmap) {
                bitmap.recycle();
            }
            return ret;
        }
        return bitmap;
    }
}
