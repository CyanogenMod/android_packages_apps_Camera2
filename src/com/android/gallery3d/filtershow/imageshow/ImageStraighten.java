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
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;

public class ImageStraighten extends ImageGeometry {

    private float mBaseAngle = 0;
    private float mAngle = 0;

    private static final String LOGTAG = "ImageStraighten";
    private static final Paint gPaint = new Paint();

    public ImageStraighten(Context context) {
        super(context);
    }

    public ImageStraighten(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setActionDown(float x, float y) {
        super.setActionDown(x, y);
        mBaseAngle = mAngle = getLocalStraighten();
    }

    @Override
    protected void setActionMove(float x, float y) {
        super.setActionMove(x, y);
        computeValue();
        setLocalStraighten(mAngle);
    }

    private float angleFor(float dx, float dy) {
        return (float) (Math.atan2(dx, dy) * 180 / Math.PI);
    }

    private void computeValue() {
        if (mCurrentX == mTouchCenterX && mCurrentY == mTouchCenterY) {
            return;
        }
        float dX1 = mTouchCenterX - mCenterX;
        float dY1 = mTouchCenterY - mCenterY;
        float dX2 = mCurrentX - mCenterX;
        float dY2 = mCurrentY - mCenterY;

        float angleA = angleFor(dX1, dY1);
        float angleB = angleFor(dX2, dY2);
        float angle = (angleB - angleA) % 360;
        mAngle = (mBaseAngle - angle) % 360;
        mAngle = Math.max(MIN_STRAIGHTEN_ANGLE, mAngle);
        mAngle = Math.min(MAX_STRAIGHTEN_ANGLE, mAngle);
    }

    @Override
    protected void lostVisibility() {
        saveAndSetPreset();
    }

    @Override
    public void onNewValue(int value) {
        setLocalStraighten(clamp(value, MIN_STRAIGHTEN_ANGLE, MAX_STRAIGHTEN_ANGLE));
        if (getPanelController() != null) {
            getPanelController().onNewValue((int) getLocalStraighten());
        }
        invalidate();
    }

    @Override
    protected int getLocalValue() {
        return (int) getLocalStraighten();
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        drawTransformedBitmap(canvas, image, gPaint, false);

        // Draw the grid
        RectF bounds = cropBounds(image);
        Path path = new Path();
        path.addRect(bounds, Path.Direction.CCW);
        gPaint.setARGB(255, 255, 255, 255);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        RectF display = getLocalDisplayBounds();
        float dWidth = display.width();
        float dHeight = display.height();

        if (mMode == MODES.MOVE) {
            canvas.save();
            canvas.clipPath(path);

            int n = 16;
            float step = dWidth / n;
            float p = 0;
            for (int i = 1; i < n; i++) {
                p = i * step;
                gPaint.setARGB(60, 255, 255, 255);
                canvas.drawLine(p, 0, p, dHeight, gPaint);
                canvas.drawLine(0, p, dWidth, p, gPaint);
            }
            canvas.restore();
        }
    }

}
