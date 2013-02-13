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

public class ImageFilterStraighten extends ImageFilter {
    private final Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
    private float mRotation;
    private float mZoomFactor;

    public ImageFilterStraighten() {
        mName = "Straighten";
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        // FIXME: clone() should not be needed. Remove when we fix geometry.
        ImageFilterStraighten filter = (ImageFilterStraighten) super.clone();
        filter.mRotation = mRotation;
        filter.mZoomFactor = mZoomFactor;
        return filter;
    }

    public ImageFilterStraighten(float rotation, float zoomFactor) {
        mRotation = rotation;
        mZoomFactor = zoomFactor;
    }

    public void setRotation(float rotation) {
        mRotation = rotation;
    }

    public void setRotationZoomFactor(float zoomFactor) {
        mZoomFactor = zoomFactor;
    }

    @Override
    public void useRepresentation(FilterRepresentation representation) {

    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        // TODO: implement bilinear or bicubic here... for now, just use
        // canvas to do a simple implementation...
        // TODO: and be more memory efficient! (do it in native?)

        Bitmap temp = bitmap.copy(mConfig, true);
        Canvas canvas = new Canvas(temp);
        canvas.save();
        Rect bounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        float w = temp.getWidth();
        float h = temp.getHeight();
        float mw = temp.getWidth() / 2.0f;
        float mh = temp.getHeight() / 2.0f;

        canvas.scale(mZoomFactor, mZoomFactor, mw, mh);
        canvas.rotate(mRotation, mw, mh);
        canvas.drawBitmap(bitmap, bounds, bounds, new Paint());
        canvas.restore();

        int[] pixels = new int[(int) (w * h)];
        temp.getPixels(pixels, 0, (int) w, 0, 0, (int) w, (int) h);
        bitmap.setPixels(pixels, 0, (int) w, 0, 0, (int) w, (int) h);
        temp.recycle();
        temp = null;
        pixels = null;
        return bitmap;
    }

}
