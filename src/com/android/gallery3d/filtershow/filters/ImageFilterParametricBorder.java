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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.android.gallery3d.R;

public class ImageFilterParametricBorder extends ImageFilter {
    private int mBorderColor = Color.WHITE;
    private int mBorderSize = 10;
    private int mBorderCornerRadius = 10;

    public ImageFilterParametricBorder() {
        setFilterType(TYPE_BORDER);
        mName = "Border";
    }

    @Override
    public int getTextId() {
        return R.string.borders;
    }

    @Override
    public boolean showParameterValue() {
        return false;
    }

    @Override
    public boolean showEditingControls() {
        return false;
    }

    @Override
    public boolean showUtilityPanel() {
        return false;
    }

    public ImageFilterParametricBorder(int color, int size, int radius) {
        setBorder(color, size, radius);
        setFilterType(TYPE_BORDER);
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterParametricBorder filter = (ImageFilterParametricBorder) super.clone();
        filter.setBorder(mBorderColor, mBorderSize, mBorderCornerRadius);
        return filter;
    }

    public boolean isNil() {
        return false;
    }

    @Override
    public boolean same(ImageFilter filter) {
        boolean isBorderFilter = super.same(filter);
        if (!isBorderFilter) {
            return false;
        }
        if (!(filter instanceof ImageFilterParametricBorder)) {
            return false;
        }
        ImageFilterParametricBorder borderFilter = (ImageFilterParametricBorder) filter;
        if (borderFilter.mBorderColor != mBorderColor) {
            return false;
        }
        if (borderFilter.mBorderSize != mBorderSize) {
            return false;
        }
        if (borderFilter.mBorderCornerRadius != mBorderCornerRadius) {
            return false;
        }
        return true;
    }

    public void setBorder(int color, int size, int radius) {
        mBorderColor = color;
        mBorderSize = size;
        mBorderCornerRadius = radius;
    }

    private void applyHelper(Canvas canvas, int w, int h) {
        Path border = new Path();
        border.moveTo(0, 0);
        float bs = mBorderSize / 100.0f * w;
        float r = mBorderCornerRadius / 100.0f * w;
        border.lineTo(0, h);
        border.lineTo(w, h);
        border.lineTo(w, 0);
        border.lineTo(0, 0);
        border.addRoundRect(new RectF(bs, bs, w - bs, h - bs),
                r, r, Path.Direction.CW);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(mBorderColor);
        canvas.drawPath(border, paint);
    }

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
       Canvas canvas = new Canvas(bitmap);
       applyHelper(canvas, bitmap.getWidth(), bitmap.getHeight());
       return bitmap;
    }

    @Override
    public Bitmap iconApply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        Canvas canvas = new Canvas(bitmap);
        applyHelper(canvas, bitmap.getWidth() * 4, bitmap.getHeight() * 4);
        return bitmap;
    }

}
