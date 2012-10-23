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

import com.android.gallery3d.filtershow.ui.Spline;

public class ImageFilterCurves extends ImageFilter {

    private static final String LOGTAG = "ImageFilterCurves";
    private final Spline[] mSplines = new Spline[4];

    public ImageFilterCurves() {
        mName = "Curves";
        reset();
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterCurves filter = (ImageFilterCurves) super.clone();
        for (int i = 0; i < 4; i++) {
            if (mSplines[i] != null) {
                filter.setSpline(mSplines[i], i);
            }
        }
        return filter;
    }

    @Override
    public boolean isNil() {
        for (int i = 0; i < 4; i++) {
            if (mSplines[i] != null && !mSplines[i].isOriginal()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean same(ImageFilter filter) {
        boolean isCurveFilter = super.same(filter);
        if (!isCurveFilter) {
            return false;
        }
        ImageFilterCurves curve = (ImageFilterCurves) filter;
        for (int i = 0; i < 4; i++) {
            if (mSplines[i] != curve.mSplines[i]) {
                return false;
            }
        }
        return true;
    }

    public void populateArray(int[] array, int curveIndex) {
        Spline spline = mSplines[curveIndex];
        if (spline == null) {
            return;
        }
        float[] curve = spline.getAppliedCurve();
        for (int i = 0; i < 256; i++) {
            array[i] = (int) (curve[i] * 255);
        }
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (!mSplines[Spline.RGB].isOriginal()) {
            int[] rgbGradient = new int[256];
            populateArray(rgbGradient, Spline.RGB);
            nativeApplyGradientFilter(bitmap, bitmap.getWidth(), bitmap.getHeight(),
                    rgbGradient, rgbGradient, rgbGradient);
        }

        int[] redGradient = null;
        if (!mSplines[Spline.RED].isOriginal()) {
            redGradient = new int[256];
            populateArray(redGradient, Spline.RED);
        }
        int[] greenGradient = null;
        if (!mSplines[Spline.GREEN].isOriginal()) {
            greenGradient = new int[256];
            populateArray(greenGradient, Spline.GREEN);
        }
        int[] blueGradient = null;
        if (!mSplines[Spline.BLUE].isOriginal()) {
            blueGradient = new int[256];
            populateArray(blueGradient, Spline.BLUE);
        }

        nativeApplyGradientFilter(bitmap, bitmap.getWidth(), bitmap.getHeight(),
                redGradient, greenGradient, blueGradient);
        return bitmap;
    }

    public void setSpline(Spline spline, int splineIndex) {
        mSplines[splineIndex] = new Spline(spline);
    }

    public Spline getSpline(int splineIndex) {
        return mSplines[splineIndex];
    }

    public void reset() {
        Spline spline = new Spline();

        spline.addPoint(0.0f, 1.0f);
        spline.addPoint(1.0f, 0.0f);

        for (int i = 0; i < 4; i++) {
            mSplines[i] = new Spline(spline);
        }
    }
}
