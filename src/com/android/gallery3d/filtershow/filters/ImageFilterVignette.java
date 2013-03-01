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
import android.graphics.Matrix;

public class ImageFilterVignette extends SimpleImageFilter {
    private static final String LOGTAG = "ImageFilterVignette";

    public ImageFilterVignette() {
        mName = "Vignette";
    }

    @Override
    public FilterRepresentation getDefaultRepresentation() {
        FilterVignetteRepresentation representation = new FilterVignetteRepresentation();
        return representation;
    }

    native protected void nativeApplyFilter(
            Bitmap bitmap, int w, int h, int cx, int cy, float radx, float rady, float strength);

    private float calcRadius(float cx, float cy, int w, int h) {
        float d = cx;
        if (d < (w - cx)) {
            d = w - cx;
        }
        if (d < cy) {
            d = cy;
        }
        if (d < (h - cy)) {
            d = h - cy;
        }
        return d * d * 2.0f;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
        FilterVignetteRepresentation rep = (FilterVignetteRepresentation) getParameters();
        if (rep == null) {
            return bitmap;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float value = rep.getValue() / 100.0f;
        float cx = w / 2;
        float cy = h / 2;
        float r = calcRadius(cx, cy, w, h);
        float rx = r;
        float ry = r;
        if (rep.isCenterSet()) {
            Matrix m = getOriginalToScreenMatrix(w, h);
            cx = rep.getCenterX();
            cy = rep.getCenterY();
            float[] center = new float[] { cx, cy };
            m.mapPoints(center);
            cx = center[0];
            cy = center[1];
            rx = m.mapRadius(rep.getRadiusX());
            ry = m.mapRadius(rep.getRadiusY());
         }
        nativeApplyFilter(bitmap, w, h, (int) cx, (int) cy, rx, ry, value);
        return bitmap;
    }
}
