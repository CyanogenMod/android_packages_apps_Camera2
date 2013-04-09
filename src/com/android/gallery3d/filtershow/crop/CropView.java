/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.crop;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.R;

public class CropView extends View {
    private static final String LOGTAG = "CropView";

    Bitmap mImage = null;
    CropObject mCropObj = null;
    private final Drawable mCropIndicator;
    private final int mIndicatorSize;

    private float mPrevX = 0;
    private float mPrevY = 0;

    private int mMinSideSize = 45;
    private int mTouchTolerance = 20;
    private boolean mMovingBlock = false;

    private Matrix mDisplayMatrix = null;
    private Matrix mDisplayMatrixInverse = null;

    private enum Mode {
        NONE, MOVE
    }

    private Mode mState = Mode.NONE;

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        mCropIndicator = resources.getDrawable(R.drawable.camera_crop);
        mIndicatorSize = (int) resources.getDimension(R.dimen.crop_indicator_size);
    }

    // For unchanging parameters
    public void setup(Bitmap image, int minSideSize, int touchTolerance) {
        mImage = image;
        mMinSideSize = minSideSize;
        mTouchTolerance = touchTolerance;
        reset();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mImage == null) {
            return;
        }
        int displayWidth = getWidth();
        int displayHeight = getHeight();
        Rect imageBoundsOriginal = new Rect(0, 0, mImage.getWidth(), mImage.getHeight());
        Rect displayBoundsOriginal = new Rect(0, 0, displayWidth, displayHeight);
        if (mCropObj == null) {
            reset();
            mCropObj = new CropObject(imageBoundsOriginal, imageBoundsOriginal, 0);
        }

        RectF imageBounds = mCropObj.getInnerBounds();
        RectF displayBounds = mCropObj.getOuterBounds();

        // If display matrix doesn't exist, create it and its dependencies
        if (mDisplayMatrix == null || mDisplayMatrixInverse == null) {
            mDisplayMatrix = getBitmapToDisplayMatrix(displayBounds, new RectF(
                    displayBoundsOriginal));
            mDisplayMatrixInverse = new Matrix();
            mDisplayMatrixInverse.reset();
            if (!mDisplayMatrix.invert(mDisplayMatrixInverse)) {
                Log.w(LOGTAG, "could not invert display matrix");
            }
            // Scale min side and tolerance by display matrix scale factor
            mCropObj.setMinInnerSideSize(mDisplayMatrixInverse.mapRadius(mMinSideSize));
            mCropObj.setTouchTolerance(mDisplayMatrixInverse.mapRadius(mTouchTolerance));
        }
        canvas.drawBitmap(mImage, mDisplayMatrix, new Paint());

        if (mDisplayMatrix.mapRect(imageBounds)) {
            drawCropRect(canvas, imageBounds);
            drawRuleOfThird(canvas, imageBounds);
            drawIndicators(canvas, mCropIndicator, mIndicatorSize, imageBounds,
                    mCropObj.isFixedAspect(), mCropObj.getSelectState());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (mDisplayMatrix == null || mDisplayMatrixInverse == null) {
            return true;
        }
        float[] touchPoint = {
                x, y
        };
        mDisplayMatrixInverse.mapPoints(touchPoint);
        x = touchPoint[0];
        y = touchPoint[1];
        switch (event.getActionMasked()) {
            case (MotionEvent.ACTION_DOWN):
                if (mState == Mode.NONE) {
                    if (!mCropObj.selectEdge(x, y)) {
                        mMovingBlock = mCropObj.selectEdge(CropObject.MOVE_BLOCK);
                    }
                    mPrevX = x;
                    mPrevY = y;
                    mState = Mode.MOVE;
                } else {
                    reset();
                }
                break;
            case (MotionEvent.ACTION_UP):
                if (mState == Mode.MOVE) {
                    mCropObj.selectEdge(CropObject.MOVE_NONE);
                    mMovingBlock = false;
                    mPrevX = x;
                    mPrevY = y;
                    mState = Mode.NONE;
                } else {
                    reset();
                }
                break;
            case (MotionEvent.ACTION_MOVE):
                if (mState == Mode.MOVE) {
                    float dx = x - mPrevX;
                    float dy = y - mPrevY;
                    mCropObj.moveCurrentSelection(dx, dy);
                    mPrevX = x;
                    mPrevY = y;
                } else {
                    reset();
                }
                break;
            default:
                reset();
                break;
        }
        invalidate();
        return true;
    }

    public void reset() {
        Log.w(LOGTAG, "reset called");
        mState = Mode.NONE;
        mCropObj = null;
        mDisplayMatrix = null;
        mDisplayMatrixInverse = null;
        mMovingBlock = false;
        invalidate();
    }

    public boolean getCropBounds(RectF out_crop, RectF in_newContaining) {
        Matrix m = new Matrix();
        RectF inner = mCropObj.getInnerBounds();
        RectF outer = mCropObj.getOuterBounds();
        if (!m.setRectToRect(outer, in_newContaining, Matrix.ScaleToFit.FILL)) {
            Log.w(LOGTAG, "failed to make transform matrix");
            return false;
        }
        if (!m.mapRect(inner)) {
            Log.w(LOGTAG, "failed to transform crop bounds");
            return false;
        }
        out_crop.set(inner);
        return true;
    }

    // Helper methods

    private static void drawRuleOfThird(Canvas canvas, RectF bounds) {
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(128, 255, 255, 255));
        p.setStrokeWidth(2);
        float stepX = bounds.width() / 3.0f;
        float stepY = bounds.height() / 3.0f;
        float x = bounds.left + stepX;
        float y = bounds.top + stepY;
        for (int i = 0; i < 2; i++) {
            canvas.drawLine(x, bounds.top, x, bounds.bottom, p);
            x += stepX;
        }
        for (int j = 0; j < 2; j++) {
            canvas.drawLine(bounds.left, y, bounds.right, y, p);
            y += stepY;
        }
    }

    private static void drawCropRect(Canvas canvas, RectF bounds) {
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(3);
        canvas.drawRect(bounds, p);
    }

    private static void drawIndicator(Canvas canvas, Drawable indicator, int indicatorSize,
            float centerX, float centerY) {
        int left = (int) centerX - indicatorSize / 2;
        int top = (int) centerY - indicatorSize / 2;
        indicator.setBounds(left, top, left + indicatorSize, top + indicatorSize);
        indicator.draw(canvas);
    }

    private static void drawIndicators(Canvas canvas, Drawable cropIndicator, int indicatorSize,
            RectF bounds, boolean fixedAspect, int selection) {
        boolean notMoving = (selection == CropObject.MOVE_NONE);
        if (fixedAspect) {
            if ((selection == CropObject.TOP_LEFT) || notMoving) {
                drawIndicator(canvas, cropIndicator, indicatorSize, bounds.left, bounds.top);
            }
            if ((selection == CropObject.TOP_RIGHT) || notMoving) {
                drawIndicator(canvas, cropIndicator, indicatorSize, bounds.right, bounds.top);
            }
            if ((selection == CropObject.BOTTOM_LEFT) || notMoving) {
                drawIndicator(canvas, cropIndicator, indicatorSize, bounds.left, bounds.bottom);
            }
            if ((selection == CropObject.BOTTOM_RIGHT) || notMoving) {
                drawIndicator(canvas, cropIndicator, indicatorSize, bounds.right, bounds.bottom);
            }
        } else {
            if (((selection & CropObject.MOVE_TOP) != 0) || notMoving) {
                drawIndicator(canvas, cropIndicator, indicatorSize, bounds.centerX(), bounds.top);
            }
            if (((selection & CropObject.MOVE_BOTTOM) != 0) || notMoving) {
                drawIndicator(canvas, cropIndicator, indicatorSize, bounds.centerX(), bounds.bottom);
            }
            if (((selection & CropObject.MOVE_LEFT) != 0) || notMoving) {
                drawIndicator(canvas, cropIndicator, indicatorSize, bounds.left, bounds.centerY());
            }
            if (((selection & CropObject.MOVE_RIGHT) != 0) || notMoving) {
                drawIndicator(canvas, cropIndicator, indicatorSize, bounds.right, bounds.centerY());
            }
        }
    }

    private static Matrix getBitmapToDisplayMatrix(RectF imageBounds, RectF displayBounds) {
        Matrix m = new Matrix();
        setBitmapToDisplayMatrix(m, imageBounds, displayBounds);
        return m;
    }

    private static boolean setBitmapToDisplayMatrix(Matrix m, RectF imageBounds,
            RectF displayBounds) {
        m.reset();
        return m.setRectToRect(imageBounds, displayBounds, Matrix.ScaleToFit.CENTER);
    }

}
