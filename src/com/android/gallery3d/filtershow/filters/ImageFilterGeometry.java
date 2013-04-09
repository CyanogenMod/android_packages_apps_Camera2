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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.filtershow.imageshow.GeometryMath;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;

public class ImageFilterGeometry extends ImageFilter {
    private final Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
    private GeometryMetadata mGeometry = null;
    private static final String LOGTAG = "ImageFilterGeometry";
    private static final boolean LOGV = false;
    private static final int BOTH = 3;
    private static final int VERTICAL = 2;
    private static final int HORIZONTAL = 1;
    private static final int NINETY = 1;
    private static final int ONE_EIGHTY = 2;
    private static final int TWO_SEVENTY = 3;

    public ImageFilterGeometry() {
        mName = "Geometry";
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        // FIXME: clone() should not be needed. Remove when we fix geometry.
        ImageFilterGeometry filter = (ImageFilterGeometry) super.clone();
        return filter;
    }

    native protected void nativeApplyFilterFlip(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, int flip);

    native protected void nativeApplyFilterRotate(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, int rotate);

    native protected void nativeApplyFilterCrop(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, int offsetWidth, int offsetHeight);

    native protected void nativeApplyFilterStraighten(Bitmap src, int srcWidth, int srcHeight,
            Bitmap dst, int dstWidth, int dstHeight, float straightenAngle);

    @Override
    public void useRepresentation(FilterRepresentation representation) {
        mGeometry = (GeometryMetadata) representation;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        // TODO: implement bilinear or bicubic here... for now, just use
        // canvas to do a simple implementation...
        // TODO: and be more memory efficient! (do it in native?)
        RectF cb = mGeometry.getPreviewCropBounds();
        RectF pb = mGeometry.getPhotoBounds();
        if (cb.width() == 0 || cb.height() == 0 || pb.width() == 0 || pb.height() == 0) {
            Log.w(LOGTAG, "Cannot apply geometry: geometry metadata has not been initialized");
            return bitmap;
        }
        CropExtras extras = mGeometry.getCropExtras();
        boolean useExtras = mGeometry.getUseCropExtrasFlag();
        int outputX = 0;
        int outputY = 0;
        boolean s = false;
        if (extras != null && useExtras){
            outputX = extras.getOutputX();
            outputY = extras.getOutputY();
            s = extras.getScaleUp();
        }


        Rect cropBounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        RectF crop = mGeometry.getCropBounds(bitmap);
        if (crop.width() > 0 && crop.height() > 0)
            cropBounds = GeometryMath.roundNearest(crop);

        int width = cropBounds.width();
        int height = cropBounds.height();

        if (mGeometry.hasSwitchedWidthHeight()){
            int temp = width;
            width = height;
            height = temp;
        }

        if(outputX <= 0 || outputY <= 0){
            outputX = width;
            outputY = height;
        }

        float scaleX = 1;
        float scaleY = 1;
        if (s){
                scaleX = (float) outputX / width;
                scaleY = (float) outputY / height;
        }

        Bitmap temp = null;
        temp = Bitmap.createBitmap(outputX, outputY, mConfig);

        float[] displayCenter = {
                temp.getWidth() / 2f, temp.getHeight() / 2f
        };

        Matrix m1 = mGeometry.buildTotalXform(bitmap.getWidth(), bitmap.getHeight(), displayCenter);

        m1.postScale(scaleX, scaleY, displayCenter[0], displayCenter[1]);

        Canvas canvas = new Canvas(temp);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        canvas.drawBitmap(bitmap, m1, paint);
        return temp;
    }

}
