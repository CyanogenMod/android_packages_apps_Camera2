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
import android.graphics.Path;
import android.graphics.RectF;

public class ImageFilterParametricBorder extends ImageFilter {
    private FilterColorBorderRepresentation mParameters = null;

    public ImageFilterParametricBorder() {
        mName = "Border";
    }

    public void useRepresentation(FilterRepresentation representation) {
        FilterColorBorderRepresentation parameters = (FilterColorBorderRepresentation) representation;
        mParameters = parameters;
    }

    public FilterColorBorderRepresentation getParameters() {
        return mParameters;
    }

    private void applyHelper(Canvas canvas, int w, int h) {
        if (getParameters() == null) {
            return;
        }
        Path border = new Path();
        border.moveTo(0, 0);
        float bs = getParameters().getBorderSize() / 100.0f * w;
        float r = getParameters().getBorderRadius() / 100.0f * w;
        border.lineTo(0, h);
        border.lineTo(w, h);
        border.lineTo(w, 0);
        border.lineTo(0, 0);
        border.addRoundRect(new RectF(bs, bs, w - bs, h - bs),
                r, r, Path.Direction.CW);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(getParameters().getColor());
        canvas.drawPath(border, paint);
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, int quality) {
       Canvas canvas = new Canvas(bitmap);
       applyHelper(canvas, bitmap.getWidth(), bitmap.getHeight());
       return bitmap;
    }

}
