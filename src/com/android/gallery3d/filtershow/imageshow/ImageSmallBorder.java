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

package com.android.gallery3d.filtershow.imageshow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.util.AttributeSet;

public class ImageSmallBorder extends ImageSmallFilter {

    // TODO: move this to xml.
    protected final int mSelectedBackgroundColor = Color.WHITE;
    protected final int mInnerBorderColor = Color.BLACK;
    protected final int mInnerBorderWidth = 2;
    protected final float mImageScaleFactor = 3.5f;

    public ImageSmallBorder(Context context) {
        super(context);
    }

    public ImageSmallBorder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onDraw(Canvas canvas) {
        requestFilteredImages();
        canvas.drawColor(mBackgroundColor);
        // TODO: simplify & make faster...
        RectF border = new RectF(mMargin, 2*mMargin, getWidth() - mMargin - 1, getWidth());

        if (mIsSelected) {
            mPaint.setColor(mSelectedBackgroundColor);
            canvas.drawRect(0, mMargin, getWidth(), getWidth() + mMargin, mPaint);
        }

        mPaint.setColor(mInnerBorderColor);
        mPaint.setStrokeWidth(mInnerBorderWidth);
        Path path = new Path();
        path.addRect(border, Path.Direction.CCW);
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, mPaint);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.save();
        canvas.clipRect(mMargin + 1, 2*mMargin, getWidth() - mMargin - 2, getWidth() - 1,
                Region.Op.INTERSECT);
        canvas.translate(mMargin + 1, 2*mMargin + 1);
        canvas.scale(mImageScaleFactor, mImageScaleFactor);
        Rect d = new Rect(0, 0, getWidth(), getWidth());
        drawImage(canvas, getFilteredImage(), d);
        canvas.restore();
    }

    @Override
    public void drawImage(Canvas canvas, Bitmap image, Rect d) {
        if (image != null) {
            int iw = image.getWidth();
            int ih = image.getHeight();
            Rect s = new Rect(0, 0, iw, iw);
            canvas.drawBitmap(image, s, d, mPaint);
        }
    }
}
