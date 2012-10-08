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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import com.android.gallery3d.R;

import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ImageCrop extends ImageGeometry {
    private static final boolean LOGV = false;
    private static final int MOVE_LEFT = 1;
    private static final int MOVE_TOP = 2;
    private static final int MOVE_RIGHT = 4;
    private static final int MOVE_BOTTOM = 8;
    private static final int MOVE_BLOCK = 16;

    private static final float MIN_CROP_WIDTH_HEIGHT = 0.1f;
    private static final int TOUCH_TOLERANCE = 30;
    private static final int SHADOW_ALPHA = 160;

    private float mAspectWidth = 4;
    private float mAspectHeight = 3;
    private boolean mFixAspectRatio = false; // not working yet

    private final Paint borderPaint;

    private float mCropOffsetX = 0;
    private float mCropOffsetY = 0;
    private float mPrevOffsetX = 0;
    private float mPrevOffsetY = 0;

    private int movingEdges;
    private final Drawable cropIndicator;
    private final int indicatorSize;

    private static final String LOGTAG = "ImageCrop";

    private static final Paint gPaint = new Paint();

    public ImageCrop(Context context) {
        super(context);
        Resources resources = context.getResources();
        cropIndicator = resources.getDrawable(R.drawable.camera_crop_holo);
        indicatorSize = (int) resources.getDimension(R.dimen.crop_indicator_size);
        int borderColor = resources.getColor(R.color.opaque_cyan);
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(borderColor);
        borderPaint.setStrokeWidth(2f);
    }

    public ImageCrop(Context context, AttributeSet attrs) {
        super(context, attrs);
        Resources resources = context.getResources();
        cropIndicator = resources.getDrawable(R.drawable.camera_crop_holo);
        indicatorSize = (int) resources.getDimension(R.dimen.crop_indicator_size);
        int borderColor = resources.getColor(R.color.opaque_cyan);
        borderPaint = new Paint();
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(borderColor);
        borderPaint.setStrokeWidth(2f);
    }

    private float getScaledMinWidthHeight() {
        RectF disp = getLocalDisplayBounds();
        float scaled = Math.min(disp.width(), disp.height()) * MIN_CROP_WIDTH_HEIGHT
                / getLocalScale();
        return scaled;
    }

    protected static Matrix getCropRotationMatrix(float rotation, RectF localImage) {
        Matrix m = new Matrix();
        m.setRotate(rotation, localImage.centerX(), localImage.centerY());
        if (!m.rectStaysRect()) {
            return null;
        }
        return m;
    }

    protected RectF getCropBoundsDisplayed() {
        RectF bounds = getLocalCropBounds();
        RectF crop = new RectF(bounds);
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());

        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO MAP CROP BOUNDS TO RECTANGLE");
            m = new Matrix();
        } else {
            m.mapRect(crop);
        }
        m = new Matrix();
        float zoom = getLocalScale();
        m.setScale(zoom, zoom, mCenterX, mCenterY);
        m.preTranslate(mXOffset, mYOffset);
        m.mapRect(crop);
        return crop;
    }

    private RectF getRotatedCropBounds() {
        RectF bounds = getLocalCropBounds();
        RectF crop = new RectF(bounds);
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());

        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO MAP CROP BOUNDS TO RECTANGLE");
            return null;
        } else {
            m.mapRect(crop);
        }
        return crop;
    }

    /**
     * Sets cropped bounds; modifies the bounds if it's smaller than the allowed
     * dimensions.
     */
    public void setCropBounds(RectF bounds) {
        // Avoid cropping smaller than minimum width or height.
        RectF cbounds = new RectF(bounds);
        float minWidthHeight = getScaledMinWidthHeight();

        float newWidth = cbounds.width();
        float newHeight = cbounds.height();
        if (newWidth < minWidthHeight) {
            newWidth = minWidthHeight;
        }
        if (newHeight < minWidthHeight) {
            newHeight = minWidthHeight;
        }

        RectF pbounds = getLocalPhotoBounds();
        if (pbounds.width() < minWidthHeight) {
            newWidth = pbounds.width();
        }
        if (pbounds.height() < minWidthHeight) {
            newHeight = pbounds.height();
        }

        cbounds.set(cbounds.left, cbounds.top, cbounds.left + newWidth, cbounds.top + newHeight);
        RectF snappedCrop = findCropBoundForRotatedImg(cbounds, pbounds, getLocalStraighten(),
                mCenterX - mXOffset, mCenterY - mYOffset);
        if (mFixAspectRatio) {
            // TODO: add aspect ratio stuff
            fixAspectRatio(snappedCrop, mAspectWidth, mAspectHeight);
        }
        setLocalCropBounds(snappedCrop);
        invalidate();
    }

    private void detectMovingEdges(float x, float y) {
        RectF cropped = getCropBoundsDisplayed();
        movingEdges = 0;

        // Check left or right.
        float left = Math.abs(x - cropped.left);
        float right = Math.abs(x - cropped.right);
        if ((left <= TOUCH_TOLERANCE) && (left < right)) {
            movingEdges |= MOVE_LEFT;
        }
        else if (right <= TOUCH_TOLERANCE) {
            movingEdges |= MOVE_RIGHT;
        }

        // Check top or bottom.
        float top = Math.abs(y - cropped.top);
        float bottom = Math.abs(y - cropped.bottom);
        if ((top <= TOUCH_TOLERANCE) & (top < bottom)) {
            movingEdges |= MOVE_TOP;
        }
        else if (bottom <= TOUCH_TOLERANCE) {
            movingEdges |= MOVE_BOTTOM;
        }
        invalidate();
    }

    private void moveEdges(float dX, float dY) {
        RectF cropped = getRotatedCropBounds();
        float minWidthHeight = getScaledMinWidthHeight();
        float scale = getLocalScale();
        float deltaX = dX / scale;
        float deltaY = dY / scale;
        if (movingEdges == MOVE_BLOCK) {
            // TODO
        } else {
            if ((movingEdges & MOVE_LEFT) != 0) {
                cropped.left = Math.min(cropped.left + deltaX, cropped.right - minWidthHeight);
                fixRectAspectW(cropped);
            }
            if ((movingEdges & MOVE_TOP) != 0) {
                cropped.top = Math.min(cropped.top + deltaY, cropped.bottom - minWidthHeight);
                fixRectAspectH(cropped);
            }
            if ((movingEdges & MOVE_RIGHT) != 0) {
                cropped.right = Math.max(cropped.right + deltaX, cropped.left + minWidthHeight);
                fixRectAspectW(cropped);
            }
            if ((movingEdges & MOVE_BOTTOM) != 0) {
                cropped.bottom = Math.max(cropped.bottom + deltaY, cropped.top + minWidthHeight);
                fixRectAspectH(cropped);
            }
        }
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());
        Matrix m0 = new Matrix();
        if (!m.invert(m0)) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO INVERT ROTATION MATRIX");
        }
        if (!m0.mapRect(cropped)) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO UNROTATE CROPPING BOUNDS");
        }
        setCropBounds(cropped);
    }

    private void fixRectAspectH(RectF cropped) {
        if (mFixAspectRatio) {
            float half = getNewWidthForHeightAspect(cropped.height(), mAspectWidth, mAspectHeight) / 2;
            float mid = (cropped.right - cropped.left) / 2;
            cropped.left = mid - half;
            cropped.right = mid + half;
        }
    }

    private void fixRectAspectW(RectF cropped) {
        if (mFixAspectRatio) {
            float half = getNewHeightForWidthAspect(cropped.width(), mAspectWidth, mAspectHeight) / 2;
            float mid = (cropped.bottom - cropped.top) / 2;
            cropped.top = mid - half;
            cropped.bottom = mid + half;
        }
    }

    private void drawShadow(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.drawARGB(SHADOW_ALPHA, 0, 0, 0);
        canvas.restore();
    }

    private void drawIndicator(Canvas canvas, Drawable indicator, float centerX, float centerY) {
        int left = (int) centerX - indicatorSize / 2;
        int top = (int) centerY - indicatorSize / 2;
        indicator.setBounds(left, top, left + indicatorSize, top + indicatorSize);
        indicator.draw(canvas);
    }

    @Override
    protected void setActionDown(float x, float y) {
        super.setActionDown(x, y);
        detectMovingEdges(x, y);
        if (movingEdges == 0) {
            mPrevOffsetX = mCropOffsetX;
            mPrevOffsetY = mCropOffsetY;
        }
    }

    @Override
    protected void setActionMove(float x, float y) {
        if (movingEdges != 0) {
            moveEdges(x - mCurrentX, y - mCurrentY);
        } else {
            float dx = x - mTouchCenterX;
            float dy = y - mTouchCenterY;
            mCropOffsetX = dx + mPrevOffsetX;
            mCropOffsetY = dy + mPrevOffsetY;
        }
        super.setActionMove(x, y);
    }

    @Override
    protected void gainedVisibility() {
        setCropBounds(getLocalCropBounds());
    }

    @Override
    protected void lostVisibility() {
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        gPaint.setAntiAlias(true);
        gPaint.setFilterBitmap(true);
        gPaint.setDither(true);
        gPaint.setARGB(255, 255, 255, 255);

        drawRegularFlippedBitmap(canvas, image, gPaint);

        RectF displayRect = getLocalDisplayBounds();
        float dWidth = displayRect.width();
        float dHeight = displayRect.height();
        RectF boundsRect = getCropBoundsDisplayed();
        gPaint.setARGB(128, 0, 0, 0);
        gPaint.setStyle(Paint.Style.FILL);
        // TODO: move this to style when refactoring
        canvas.drawRect(0, 0, dWidth, boundsRect.top, gPaint);
        canvas.drawRect(0, boundsRect.bottom, dWidth, dHeight, gPaint);
        canvas.drawRect(0, boundsRect.top, boundsRect.left, boundsRect.bottom,
                gPaint);
        canvas.drawRect(boundsRect.right, boundsRect.top, dWidth,
                boundsRect.bottom, gPaint);

        Path path = new Path();
        path.addRect(boundsRect, Path.Direction.CCW);
        gPaint.setARGB(255, 255, 255, 255);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.STROKE);

        canvas.drawPath(path, gPaint);

        boolean notMoving = movingEdges == 0;
        if (((movingEdges & MOVE_TOP) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, boundsRect.centerX(), boundsRect.top);
        }
        if (((movingEdges & MOVE_BOTTOM) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, boundsRect.centerX(), boundsRect.bottom);
        }
        if (((movingEdges & MOVE_LEFT) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, boundsRect.left, boundsRect.centerY());
        }
        if (((movingEdges & MOVE_RIGHT) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, boundsRect.right, boundsRect.centerY());
        }
    }

}
