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

import com.android.gallery3d.filtershow.imageshow.GeometryMetadata.FLIP;

public class ImageFlip extends ImageGeometry {

    private static final Paint gPaint = new Paint();
    private static final float MIN_FLICK_DIST_FOR_FLIP = 0.2f;
    private static final String LOGTAG = "ImageFlip";
    private FLIP mNextFlip = FLIP.NONE;

    public ImageFlip(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageFlip(Context context) {
        super(context);
    }

    @Override
    protected void setActionDown(float x, float y) {
        super.setActionDown(x, y);
    }

    @Override
    protected void setActionMove(float x, float y) {
        super.setActionMove(x, y);

        float diffx = mTouchCenterX - x;
        float diffy = mTouchCenterY - y;
        float flick = getScaledMinFlick();
        if (Math.abs(diffx) >= flick) {
            // flick moving left/right
            FLIP flip = getLocalFlip();
            switch (flip) {
                case NONE:
                    flip = FLIP.HORIZONTAL;
                    break;
                case HORIZONTAL:
                    flip = FLIP.NONE;
                    break;
                case VERTICAL:
                    flip = FLIP.BOTH;
                    break;
                case BOTH:
                    flip = FLIP.VERTICAL;
                    break;
                default:
                    flip = FLIP.NONE;
                    break;
            }
            mNextFlip = flip;
        }
        if (Math.abs(diffy) >= flick) {
            // flick moving up/down
            FLIP flip = getLocalFlip();
            switch (flip) {
                case NONE:
                    flip = FLIP.VERTICAL;
                    break;
                case VERTICAL:
                    flip = FLIP.NONE;
                    break;
                case HORIZONTAL:
                    flip = FLIP.BOTH;
                    break;
                case BOTH:
                    flip = FLIP.HORIZONTAL;
                    break;
                default:
                    flip = FLIP.NONE;
                    break;
            }
            mNextFlip = flip;
        }
    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        setLocalFlip(mNextFlip);
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
        mNextFlip = FLIP.NONE;
    }

    private float getScaledMinFlick() {
        RectF disp = getLocalDisplayBounds();
        float scaled = Math.min(disp.width(), disp.height()) * MIN_FLICK_DIST_FOR_FLIP
                / getLocalScale();
        return scaled;
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        gPaint.setAntiAlias(true);
        gPaint.setFilterBitmap(true);
        gPaint.setDither(true);
        gPaint.setARGB(255, 255, 255, 255);

        FLIP flip = mNextFlip;
        canvas.save();
        float zoom = getLocalScale();
        canvas.rotate(getTotalLocalRotation(), mCenterX, mCenterY);
        canvas.scale(zoom, zoom, mCenterX, mCenterY);
        canvas.translate(mXOffset, mYOffset);
        if (flip == FLIP.HORIZONTAL) {
            Matrix flipper = getHorizontalMatrix(image.getWidth());
            canvas.drawBitmap(image, flipper, gPaint);
        } else if (flip == FLIP.VERTICAL) {
            Matrix flipper = getVerticalMatrix(image.getHeight());
            canvas.drawBitmap(image, flipper, gPaint);
        } else if (flip == FLIP.BOTH) {
            Matrix flipper = getVerticalMatrix(image.getHeight());
            flipper.postConcat(getHorizontalMatrix(image.getWidth()));
            canvas.drawBitmap(image, flipper, gPaint);
        } else {
            canvas.drawBitmap(image, 0, 0, gPaint);
        }
        canvas.restore();

        RectF cropBounds = getCropBoundsDisplayed(getLocalCropBounds());

        Matrix m0 = new Matrix();
        m0.setRotate(getLocalRotation(), mCenterX, mCenterY);
        float[] corners = getCornersFromRect(cropBounds);
        m0.mapPoints(corners);
        gPaint.setARGB(255, 255, 255, 255);
        // TODO: pull out style to xml
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.STROKE);
        drawClosedPath(canvas, gPaint, corners);

        canvas.save();
        canvas.rotate(getLocalRotation(), mCenterX, mCenterY);
        RectF displayRect = getLocalDisplayBounds();
        float dWidth = displayRect.width();
        float dHeight = displayRect.height();
        RectF boundsRect = cropBounds;
        gPaint.setARGB(128, 0, 0, 0);
        gPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, dWidth, boundsRect.top, gPaint);
        canvas.drawRect(0, boundsRect.bottom, dWidth, dHeight, gPaint);
        canvas.drawRect(0, boundsRect.top, boundsRect.left, boundsRect.bottom,
                gPaint);
        canvas.drawRect(boundsRect.right, boundsRect.top, dWidth,
                boundsRect.bottom, gPaint);
        canvas.rotate(-getLocalRotation(), mCenterX, mCenterY);
        canvas.restore();
    }
}
