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
import android.util.AttributeSet;

public class ImageRotate extends ImageGeometry {
    private static final float MATH_PI = (float) Math.PI;

    private float mBaseAngle = 0;
    private float mAngle = 0;

    private final boolean mSnapToNinety = true;
    private static final String LOGTAG = "ImageRotate";

    public ImageRotate(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageRotate(Context context) {
        super(context);
    }

    private float angleFor(float dx, float dy) {
        return (float) (Math.atan2(dx, dy) * 180 / MATH_PI);
    }

    private int snappedAngle(float angle) {
        float remainder = angle % 90;
        int current = (int) (angle / 90); // truncates
        if (remainder < -45) {
            --current;
        } else if (remainder > 45) {
            ++current;
        }
        return current * 90;
    }

    private static final Paint gPaint = new Paint();

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
    }

    @Override
    protected void setActionDown(float x, float y) {
        super.setActionDown(x, y);
        mBaseAngle = mAngle = getLocalRotation();
    }

    @Override
    protected void setActionMove(float x, float y) {
        super.setActionMove(x, y);
        computeValue();
        setLocalRotation(mAngle % 360);
    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        if (mSnapToNinety) {
            setLocalRotation(snappedAngle(mAngle % 360));
        }
    }

    @Override
    protected int getLocalValue() {
        return (int) getLocalRotation();
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        gPaint.setAntiAlias(true);
        gPaint.setFilterBitmap(true);
        gPaint.setDither(true);
        gPaint.setARGB(255, 255, 255, 255);
        drawTransformedBitmap(canvas, image, gPaint, true);
    }
}
