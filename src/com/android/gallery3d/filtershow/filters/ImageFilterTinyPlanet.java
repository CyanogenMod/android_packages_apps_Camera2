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

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPMeta;
import com.android.gallery3d.filtershow.presets.ImagePreset;

/**
 * An image filter which creates a tiny planet projection.
 */
public class ImageFilterTinyPlanet extends ImageFilter {
    private static final String TAG = ImageFilterTinyPlanet.class.getSimpleName();
    public static final String GOOGLE_PANO_NAMESPACE = "http://ns.google.com/photos/1.0/panorama/";

    public static final String CROPPED_AREA_IMAGE_WIDTH_PIXELS =
            "CroppedAreaImageWidthPixels";
    public static final String CROPPED_AREA_IMAGE_HEIGHT_PIXELS =
            "CroppedAreaImageHeightPixels";
    public static final String CROPPED_AREA_FULL_PANO_WIDTH_PIXELS =
            "FullPanoWidthPixels";
    public static final String CROPPED_AREA_FULL_PANO_HEIGHT_PIXELS =
            "FullPanoHeightPixels";
    public static final String CROPPED_AREA_LEFT =
            "CroppedAreaLeftPixels";
    public static final String CROPPED_AREA_TOP =
            "CroppedAreaTopPixels";

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
        int w = bitmapIn.getWidth();
        int h = bitmapIn.getHeight();
        int outputSize = Math.min(w, h);

        ImagePreset preset = getImagePreset();
        if (preset != null) {
            if (preset.isPanoramaSafe()) {
                try {
                    XMPMeta xmp = preset.getImageLoader().getXmpObject();
                    int croppedAreaWidth =
                            getInt(xmp, CROPPED_AREA_IMAGE_WIDTH_PIXELS);
                    int croppedAreaHeight =
                            getInt(xmp, CROPPED_AREA_IMAGE_HEIGHT_PIXELS);
                    int fullPanoWidth =
                            getInt(xmp, CROPPED_AREA_FULL_PANO_WIDTH_PIXELS);
                    int fullPanoHeight =
                            getInt(xmp, CROPPED_AREA_FULL_PANO_HEIGHT_PIXELS);
                    int left = getInt(xmp, CROPPED_AREA_LEFT);
                    int top = getInt(xmp, CROPPED_AREA_TOP);

                    Bitmap paddedBitmap = Bitmap.createBitmap(
                            fullPanoWidth, fullPanoHeight, Bitmap.Config.ARGB_8888);
                    Canvas paddedCanvas = new Canvas(paddedBitmap);

                    int right = left + croppedAreaWidth;
                    int bottom = top + croppedAreaHeight;
                    Rect destRect = new Rect(left, top, right, bottom);
                    paddedCanvas.drawBitmap(bitmapIn, null, destRect, null);
                    bitmapIn = paddedBitmap;
                } catch (XMPException ex) {
                    // Do nothing, just use bitmapIn as is.
                }
            } else {
                // Do nothing, just use bitmapIn as is, there is nothing else we
                // can do.
            }
        }

        Bitmap mBitmapOut = Bitmap.createBitmap(
                outputSize, outputSize, Bitmap.Config.ARGB_8888);
        nativeApplyFilter(bitmapIn, bitmapIn.getWidth(), bitmapIn.getHeight(), mBitmapOut,
                outputSize, mParameter / 100f, 0f);
        return mBitmapOut;
    }

    private static int getInt(XMPMeta xmp, String key) throws XMPException {
        if (xmp.doesPropertyExist(GOOGLE_PANO_NAMESPACE, key)) {
            return xmp.getPropertyInteger(GOOGLE_PANO_NAMESPACE, key);
        } else {
            return 0;
        }
    }
}
