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
            mDisplayMatrix = CropDrawingUtils.getBitmapToDisplayMatrix(displayBounds, new RectF(
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
            CropDrawingUtils.drawCropRect(canvas, imageBounds);
            CropDrawingUtils.drawRuleOfThird(canvas, imageBounds);
            CropDrawingUtils.drawIndicators(canvas, mCropIndicator, mIndicatorSize, imageBounds,
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
}
