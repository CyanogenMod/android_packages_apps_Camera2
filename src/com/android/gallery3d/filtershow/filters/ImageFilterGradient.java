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
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Shader.TileMode;

public class ImageFilterGradient extends ImageFilter {

    private Bitmap mGradientBitmap = null;
    private int[] mColors = null;
    private float[] mPositions = null;

    public ImageFilterGradient() {
        mName = "Gradient";
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterGradient filter = (ImageFilterGradient) super.clone();
        System.arraycopy(mColors, 0, filter.mColors, 0, mColors.length);
        System.arraycopy(mPositions, 0, filter.mPositions, 0, mPositions.length);
        return filter;
    }

    public void addColor(int color, float position) {
        int length = 0;
        if (mColors != null) {
            length = mColors.length;
        }
        int[] colors = new int[length + 1];
        float[] positions = new float[length + 1];

        for (int i = 0; i < length; i++) {
            colors[i] = mColors[i];
            positions[i] = mPositions[i];
        }

        colors[length] = color;
        positions[length] = position;

        mColors = colors;
        mPositions = positions;
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        createGradient();
        int[] gradient = new int[256];
        int[] redGradient = new int[256];
        int[] greenGradient = new int[256];
        int[] blueGradient = new int[256];
        mGradientBitmap.getPixels(gradient, 0, 256, 0, 0, 256, 1);

        for (int i = 0; i < 256; i++) {
            redGradient[i] = Color.red(gradient[i]);
            greenGradient[i] = Color.green(gradient[i]);
            blueGradient[i] = Color.blue(gradient[i]);
        }
        nativeApplyGradientFilter(bitmap, bitmap.getWidth(), bitmap.getHeight(),
                redGradient, greenGradient, blueGradient);
        return bitmap;
    }

    public void createGradient() {
        if (mGradientBitmap != null) {
            return;
        }

        /* Create a 200 x 200 bitmap and fill it with black. */
        Bitmap b = Bitmap.createBitmap(256, 1, Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);

        /* Create your gradient. */

        /*
         * int[] colors = new int[2]; colors[0] = Color.argb(255, 20, 20, 10);
         * colors[0] = Color.BLACK; colors[1] = Color.argb(255, 228, 231, 193);
         * float[] positions = new float[2]; positions[0] = 0; positions[1] = 1;
         */

        LinearGradient grad = new LinearGradient(0, 0, 255, 1, mColors,
                mPositions, TileMode.CLAMP);

        /* Draw your gradient to the top of your bitmap. */
        Paint p = new Paint();
        p.setStyle(Style.FILL);
        p.setShader(grad);
        c.drawRect(0, 0, 256, 1, p);
        mGradientBitmap = b;
    }

}
