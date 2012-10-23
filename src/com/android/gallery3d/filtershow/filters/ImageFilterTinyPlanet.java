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

import com.android.gallery3d.filtershow.presets.ImagePreset;

/**
 * An image filter which creates a tiny planet projection.
 */
public class ImageFilterTinyPlanet extends ImageFilter {
    private static final String TAG = ImageFilterTinyPlanet.class.getSimpleName();

    public ImageFilterTinyPlanet() {
        setFilterType(TYPE_TINYPLANET);
        mName = "TinyPlanet";

        mMinParameter = 10;
        mMaxParameter = 60;
        mDefaultParameter = 20;
        mPreviewParameter = 20;
        mParameter = 20;
    }

    native protected void nativeApplyFilter(
            Bitmap bitmapIn, int width, int height, Bitmap bitmapOut, int outSize, float scale,
            float angle);

    @Override
    public Bitmap apply(Bitmap bitmapIn, float scaleFactor, boolean highQuality) {
        ImagePreset preset = getImagePreset();
        if (preset != null) {
            if (preset.isPanoramaSafe()) {
                // TODO(haeberling): Get XMPMeta object.
                Object xmp = preset.getImageLoader().getXmpObject();
            } else {
                // TODO(haeberling): What should we do for:
                // !preset.isPanoramaSafe()?
            }
        }

        int w = bitmapIn.getWidth();
        int h = bitmapIn.getHeight();
        int outputSize = Math.min(w, h);

        Bitmap mBitmapOut = Bitmap.createBitmap(
                outputSize, outputSize, Bitmap.Config.ARGB_8888);

        // TODO(haeberling): Add the padding back in based on the meta-data.
        nativeApplyFilter(bitmapIn, w, h, mBitmapOut, outputSize, mParameter / 100f, 0f);
        return mBitmapOut;
    }
}
