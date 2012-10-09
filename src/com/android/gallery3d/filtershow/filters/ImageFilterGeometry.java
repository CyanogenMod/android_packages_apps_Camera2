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
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata.FLIP;
import com.android.gallery3d.filtershow.imageshow.ImageGeometry;

public class ImageFilterGeometry extends ImageFilter {
    private final Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
    private GeometryMetadata mGeometry = null;
    private static final String LOGTAG = "ImageFilterGeometry";
    private static final boolean LOGV = false;
    private static final int BOTH = 3;
    private static final int VERTICAL = 2;
    private static final int HORIZONTAL = 1;

    public ImageFilterGeometry() {
        mName = "Geometry";
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterGeometry filter = (ImageFilterGeometry) super.clone();
        return filter;
    }

    public void setGeometryMetadata(GeometryMetadata m) {
        mGeometry = m;
    }

    native protected void nativeApplyFilterFlip(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, int flip);

    native protected void nativeApplyFilterRotate(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, float rotate);

    native protected void nativeApplyFilterCrop(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, int offsetWidth, int offsetHeight);

    native protected void nativeApplyFilterStraighten(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, float straightenAngle);

    @Override
    public Bitmap apply(Bitmap originalBitmap, float scaleFactor, boolean highQuality) {
        Rect cropBounds = new Rect();
        Rect originalBounds = new Rect();
        FLIP flipType = mGeometry.getFlipType();
        float rAngle = mGeometry.getRotation();
        float sAngle = mGeometry.getStraightenRotation();
        mGeometry.getCropBounds().roundOut(cropBounds);
        mGeometry.getPhotoBounds().roundOut(originalBounds);
        boolean flip = flipType != FLIP.NONE;
        boolean rotate = rAngle != 0;
        boolean crop = !cropBounds.equals(originalBounds);
        boolean straighten = sAngle != 0;

        int jniFlipType = 0;
        switch (flipType) {
            case BOTH:
                jniFlipType = BOTH;
                break;
            case VERTICAL:
                jniFlipType = VERTICAL;
                break;
            case HORIZONTAL:
                jniFlipType = HORIZONTAL;
                break;
            default:
                jniFlipType = 0;
                break;
        }
        int bmWidth = originalBitmap.getWidth();
        int bmHeight = originalBitmap.getHeight();
        if (!(flip || rotate || crop || straighten)) {
            return originalBitmap;
        }
        if (originalBounds.width() != bmWidth || originalBounds.height() != bmHeight) {
            if (LOGV)
                Log.v(LOGTAG, "PHOTOBOUNDS WIDTH/HEIGHT NOT SAME AS BITMAP WIDTH/HEIGHT");
            return originalBitmap;
        }
        Bitmap modBitmap = originalBitmap;
        Rect modifiedBounds = new Rect(originalBounds);
        if (flip) {
            modBitmap = originalBitmap.copy(mConfig, true);
            nativeApplyFilterFlip(originalBitmap, bmWidth, bmHeight, modBitmap,
                    bmWidth, bmHeight, jniFlipType);
        }
        if (rotate) {
            // Fails for non-90 degree rotations
            Bitmap modBitmapRotate = null;
            if (((int) (sAngle / 90)) % 2 == 0) {
                modBitmapRotate = Bitmap.createBitmap(bmWidth, bmHeight, mConfig);
                nativeApplyFilterRotate(modBitmap, bmWidth, bmHeight, modBitmapRotate,
                        bmWidth, bmHeight, mGeometry.getRotation());
                modifiedBounds = new Rect(0, 0, bmWidth, bmHeight);
            } else {
                modBitmapRotate = Bitmap.createBitmap(bmHeight, bmWidth, mConfig);
                nativeApplyFilterRotate(modBitmap, bmWidth, bmHeight, modBitmapRotate,
                        bmHeight, bmWidth, mGeometry.getRotation());
                modifiedBounds = new Rect(0, 0, bmHeight, bmWidth);
            }
            modBitmap = modBitmapRotate;
        }
        if (straighten) {
            Rect straightenBounds = new Rect();
            ImageGeometry.getUntranslatedStraightenCropBounds(new RectF(modifiedBounds), sAngle)
                    .roundOut(straightenBounds);
            Bitmap modBitmapStraighten = Bitmap.createBitmap(straightenBounds.width(),
                    straightenBounds.height(), mConfig);
            nativeApplyFilterStraighten(modBitmap, modifiedBounds.width(), modifiedBounds.height(),
                    modBitmapStraighten,
                    straightenBounds.width(), straightenBounds.height(),
                    mGeometry.getStraightenRotation());
            modifiedBounds = straightenBounds;
            modBitmap = modBitmapStraighten;
        }
        if (crop) {
            Bitmap modBitmapCrop = Bitmap.createBitmap(cropBounds.width(), cropBounds.height(),
                    mConfig);
            // Force crop bounds to be within straighten bounds.
            if (!modifiedBounds.intersect(cropBounds)) {
                return modBitmap;
            }
            nativeApplyFilterCrop(modBitmap, bmWidth, bmHeight, modBitmapCrop,
                    cropBounds.width(), cropBounds.height(), cropBounds.left, cropBounds.top);
            modBitmap = modBitmapCrop;
        }
        return modBitmap;
    }

}
