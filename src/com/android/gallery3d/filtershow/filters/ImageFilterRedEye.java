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

public class ImageFilterRedEye extends ImageFilter {
    private static final String TAG = "ImageFilterRedEye";


    public ImageFilterRedEye() {
        mName = "Redeye";

    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterRedEye filter = (ImageFilterRedEye) super.clone();

        return filter;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, short []matrix);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float p = mParameter;
        float value = p;
        int box = Math.min(w, h);
        int sizex = Math.min((int)((p+100)*box/400),w/2);
        int sizey = Math.min((int)((p+100)*box/800),h/2);

        short [] rect = new short[]{
                (short) (w/2-sizex),(short) (w/2-sizey),
                (short) (2*sizex),(short) (2*sizey)};

        nativeApplyFilter(bitmap, w, h, rect);
        return bitmap;
    }
}
