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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.android.gallery3d.filtershow.presets.ImagePreset;

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
    protected void gainedVisibility() {
        correctStraightenRotateAngles();
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
        gPaint.setAntiAlias(true);
        gPaint.setFilterBitmap(true);
        gPaint.setDither(true);
        gPaint.setARGB(255, 255, 255, 255);

        // Draw fully rotated image.
        drawRegularFlippedBitmap(canvas, image, gPaint);

        // Get cropping frame
        RectF boundsRect = getStraightenCropBounds();

        Matrix m1 = new Matrix();
        float zoom = getLocalScale();
        // Center and scale
        m1.setScale(zoom, zoom, mCenterX, mCenterY);
        m1.preTranslate(mCenterX - boundsRect.centerX(), mCenterY - boundsRect.centerY());
        m1.mapRect(boundsRect);
        RectF displayRect = getLocalDisplayBounds();
        float dWidth = displayRect.width();
        float dHeight = displayRect.height();

        // Draw shadows
        gPaint.setARGB(128, 0, 0, 0);
        gPaint.setStyle(Paint.Style.FILL);

        // TODO: move to xml file
        canvas.drawRect(0, 0, dWidth, boundsRect.top, gPaint);
        canvas.drawRect(0, boundsRect.bottom, dWidth, dHeight, gPaint);
        canvas.drawRect(0, boundsRect.top, boundsRect.left, boundsRect.bottom,
                gPaint);
        canvas.drawRect(boundsRect.right, boundsRect.top, dWidth,
                boundsRect.bottom, gPaint);

        // Draw crop frame
        Path path = new Path();
        path.addRect(boundsRect, Path.Direction.CCW);
        gPaint.setARGB(255, 255, 255, 255);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(path, gPaint);

        gPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        // Draw grid
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
