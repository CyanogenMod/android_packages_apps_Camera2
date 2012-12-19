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
import android.graphics.Canvas;
import android.graphics.Rect;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.ImageLoader;

public class ImageFilterDownsample extends ImageFilter {
    private static final int ICON_DOWNSAMPLE_FRACTION = 8;
    private ImageLoader mImageLoader;

    public ImageFilterDownsample(ImageLoader loader) {
        mName = "Downsample";
        mMaxParameter = 100;
        mMinParameter = 1;
        mPreviewParameter = 3;
        mDefaultParameter = 50;
        mParameter = 50;
        mImageLoader = loader;
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

        // size of original precached image
        Rect size = mImageLoader.getOriginalBounds();
        int orig_w = size.width();
        int orig_h = size.height();

        if (p > 0 && p < 100) {
            // scale preview to same size as the resulting bitmap from a "save"
            int newWidth = orig_w * p / 100;
            int newHeight = orig_h * p / 100;

            // only scale preview if preview isn't already scaled enough
            if (newWidth <= 0 || newHeight <= 0 || newWidth >= w || newHeight >= h) {
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

    @Override
    public Bitmap iconApply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Bitmap ret = Bitmap.createScaledBitmap(bitmap, w / ICON_DOWNSAMPLE_FRACTION, h
                / ICON_DOWNSAMPLE_FRACTION, false);
        Rect dst = new Rect(0, 0, w, h);
        Rect src = new Rect(0, 0, w / ICON_DOWNSAMPLE_FRACTION, h / ICON_DOWNSAMPLE_FRACTION);
        Canvas c = new Canvas(bitmap);
        c.drawBitmap(ret, src, dst, null);
        return bitmap;
    }
}
