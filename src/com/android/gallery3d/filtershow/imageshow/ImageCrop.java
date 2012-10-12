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
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import com.android.gallery3d.R;

public class ImageCrop extends ImageGeometry {
    private static final boolean LOGV = false;
    private static final int MOVE_LEFT = 1;
    private static final int MOVE_TOP = 2;
    private static final int MOVE_RIGHT = 4;
    private static final int MOVE_BOTTOM = 8;
    private static final int MOVE_BLOCK = 16;

    private static final float MIN_CROP_WIDTH_HEIGHT = 0.1f;
    private static final int TOUCH_TOLERANCE = 30;

    private boolean mFirstDraw = true;
    private float mAspectWidth = 1;
    private float mAspectHeight = 1;
    private boolean mFixAspectRatio = false;

    private final Paint borderPaint;

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
        RectF disp = new RectF(0, 0, getWidth(), getHeight());
        float scaled = Math.min(disp.width(), disp.height()) * MIN_CROP_WIDTH_HEIGHT
                / computeScale(getWidth(), getHeight());
        return scaled;
    }

    protected Matrix getCropRotationMatrix(float rotation, RectF localImage) {
        Matrix m = getLocalGeoFlipMatrix(localImage.width(), localImage.height());
        m.postRotate(rotation, localImage.centerX(), localImage.centerY());
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
        float zoom = computeScale(getWidth(), getHeight());
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

    private RectF getUnrotatedCropBounds(RectF cropBounds) {
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());

        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO GET ROTATION MATRIX");
            return null;
        }
        Matrix m0 = new Matrix();
        if (!m.invert(m0)) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO INVERT ROTATION MATRIX");
            return null;
        }
        RectF crop = new RectF(cropBounds);
        if (!m0.mapRect(crop)) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO UNROTATE CROPPING BOUNDS");
            return null;
        }
        return crop;
    }

    private RectF getRotatedStraightenBounds() {
        RectF straightenBounds = getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten());
        Matrix m = getCropRotationMatrix(getLocalRotation(), getLocalPhotoBounds());

        if (m == null) {
            if (LOGV)
                Log.v(LOGTAG, "FAILED TO MAP STRAIGHTEN BOUNDS TO RECTANGLE");
            return null;
        } else {
            m.mapRect(straightenBounds);
        }
        return straightenBounds;
    }

    /**
     * Sets cropped bounds; modifies the bounds if it's smaller than the allowed
     * dimensions.
     */
    public void setCropBounds(RectF bounds) {
        // Avoid cropping smaller than minimum width or height.
        RectF cbounds = new RectF(bounds);
        float minWidthHeight = getScaledMinWidthHeight();
        float aw = mAspectWidth;
        float ah = mAspectHeight;
        if (mFixAspectRatio) {
            minWidthHeight /= aw * ah;
            int r = (int) (getLocalRotation() / 90);
            if (r % 2 != 0) {
                float temp = aw;
                aw = ah;
                ah = temp;
            }
        }

        float newWidth = cbounds.width();
        float newHeight = cbounds.height();
        if (mFixAspectRatio) {
            if (newWidth < (minWidthHeight * aw) || newHeight < (minWidthHeight * ah)) {
                newWidth = minWidthHeight * aw;
                newHeight = minWidthHeight * ah;
            }
        } else {
            if (newWidth < minWidthHeight) {
                newWidth = minWidthHeight;
            }
            if (newHeight < minWidthHeight) {
                newHeight = minWidthHeight;
            }
        }
        RectF pbounds = getLocalPhotoBounds();
        if (pbounds.width() < minWidthHeight) {
            newWidth = pbounds.width();
        }
        if (pbounds.height() < minWidthHeight) {
            newHeight = pbounds.height();
        }

        cbounds.set(cbounds.left, cbounds.top, cbounds.left + newWidth, cbounds.top + newHeight);
        RectF straightenBounds = getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten());
        cbounds.intersect(straightenBounds);

        if (mFixAspectRatio) {
            fixAspectRatio(cbounds, aw, ah);
        }
        setLocalCropBounds(cbounds);
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
        // Check inside block.
        if (cropped.contains(x, y) && (movingEdges == 0)) {
            movingEdges = MOVE_BLOCK;
        }
        invalidate();
    }

    private void moveEdges(float dX, float dY) {
        RectF cropped = getRotatedCropBounds();
        float minWidthHeight = getScaledMinWidthHeight();
        float scale = computeScale(getWidth(), getHeight());
        float deltaX = dX / scale;
        float deltaY = dY / scale;
        int select = movingEdges;
        if (mFixAspectRatio && (select != MOVE_BLOCK)) {
            if ((select & MOVE_LEFT) != 0) {
                select &= ~MOVE_BOTTOM;
                select |= MOVE_TOP;
                deltaY = getNewHeightForWidthAspect(deltaX, mAspectWidth, mAspectHeight);
            }
            if ((select & MOVE_TOP) != 0) {
                select &= ~MOVE_RIGHT;
                select |= MOVE_LEFT;
                deltaX = getNewWidthForHeightAspect(deltaY, mAspectWidth, mAspectHeight);
            }
            if ((select & MOVE_RIGHT) != 0) {
                select &= ~MOVE_TOP;
                select |= MOVE_BOTTOM;
                deltaY = getNewHeightForWidthAspect(deltaX, mAspectWidth, mAspectHeight);
            }
            if ((select & MOVE_BOTTOM) != 0) {
                select &= ~MOVE_LEFT;
                select |= MOVE_RIGHT;
                deltaX = getNewWidthForHeightAspect(deltaY, mAspectWidth, mAspectHeight);
            }
        }

        if (select == MOVE_BLOCK) {
            RectF straight = getRotatedStraightenBounds();
            // Move the whole cropped bounds within the photo display bounds.
            deltaX = (deltaX > 0) ? Math.min(straight.right - cropped.right, deltaX)
                    : Math.max(straight.left - cropped.left, deltaX);
            deltaY = (deltaY > 0) ? Math.min(straight.bottom - cropped.bottom, deltaY)
                    : Math.max(straight.top - cropped.top, deltaY);
            cropped.offset(deltaX, deltaY);
        } else {
            float dx = 0;
            float dy = 0;
            if ((select & MOVE_LEFT) != 0) {
                dx = Math.min(cropped.left + deltaX, cropped.right - minWidthHeight) - cropped.left;
            }
            if ((select & MOVE_TOP) != 0) {
                dy = Math.min(cropped.top + deltaY, cropped.bottom - minWidthHeight) - cropped.top;
            }
            if ((select & MOVE_RIGHT) != 0) {
                dx = Math.max(cropped.right + deltaX, cropped.left + minWidthHeight)
                        - cropped.right;
            }
            if ((select & MOVE_BOTTOM) != 0) {
                dy = Math.max(cropped.bottom + deltaY, cropped.top + minWidthHeight)
                        - cropped.bottom;
            }

            if (mFixAspectRatio) {
                if (dx < dy) {
                    dy = getNewHeightForWidthAspect(dx, mAspectWidth, mAspectHeight);
                } else {
                    dx = getNewWidthForHeightAspect(dy, mAspectWidth, mAspectHeight);
                }
            }

            if ((select & MOVE_LEFT) != 0) {
                cropped.left += dx;
            }
            if ((select & MOVE_TOP) != 0) {
                cropped.top += dy;
            }
            if ((select & MOVE_RIGHT) != 0) {
                cropped.right += dx;
            }
            if ((select & MOVE_BOTTOM) != 0) {
                cropped.bottom += dy;
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
    }

    @Override
    protected void setActionUp() {
        super.setActionUp();
        movingEdges = 0;
    }

    @Override
    protected void setActionMove(float x, float y) {
        if (movingEdges != 0)
            moveEdges(x - mCurrentX, y - mCurrentY);

        super.setActionMove(x, y);
    }

    private void cropSetup() {
        if (mFixAspectRatio) {
            RectF cb = getRotatedCropBounds();
            fixAspectRatio(cb, mAspectWidth, mAspectHeight);
            RectF cb0 = getUnrotatedCropBounds(cb);
            setCropBounds(cb0);
        } else {
            setCropBounds(getLocalCropBounds());
        }
    }

    @Override
    protected void gainedVisibility() {
        cropSetup();
        mFirstDraw = true;
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
        cropSetup();
    }

    @Override
    protected void lostVisibility() {
    }

    @Override
    protected void drawShape(Canvas canvas, Bitmap image) {
        // TODO: move style to xml
        gPaint.setAntiAlias(true);
        gPaint.setFilterBitmap(true);
        gPaint.setDither(true);
        gPaint.setARGB(255, 255, 255, 255);

        if (mFirstDraw) {
            cropSetup();
            mFirstDraw = false;
        }
        float rotation = getLocalRotation();
        drawTransformedBitmap(canvas, image, gPaint, true);

        gPaint.setARGB(255, 125, 255, 128);
        gPaint.setStrokeWidth(3);
        gPaint.setStyle(Paint.Style.STROKE);
        drawStraighten(canvas, gPaint);
        RectF scaledCrop = unrotatedCropBounds();
        int decoded_moving = decoder(movingEdges, rotation);
        canvas.save();
        canvas.rotate(rotation, mCenterX, mCenterY);
        boolean notMoving = decoded_moving == 0;
        if (((decoded_moving & MOVE_TOP) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, scaledCrop.centerX(), scaledCrop.top);
        }
        if (((decoded_moving & MOVE_BOTTOM) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, scaledCrop.centerX(), scaledCrop.bottom);
        }
        if (((decoded_moving & MOVE_LEFT) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, scaledCrop.left, scaledCrop.centerY());
        }
        if (((decoded_moving & MOVE_RIGHT) != 0) || notMoving) {
            drawIndicator(canvas, cropIndicator, scaledCrop.right, scaledCrop.centerY());
        }
        canvas.restore();
    }

    private int bitCycleLeft(int x, int times, int d){
        int mask = (1 << d) - 1;
        int mout = x & mask;
        times %= d;
        int hi = mout >> (d - times);
        int low = (mout << times) & mask;
        int ret = x & ~mask;
        ret |= low;
        ret |= hi;
        return ret;
    }

    protected int decoder(int movingEdges, float rotation) {
        int rot = constrainedRotation(rotation);
        switch(rot){
            case 90:
                return bitCycleLeft(movingEdges, 3, 4);
            case 180:
                return bitCycleLeft(movingEdges, 2, 4);
            case 270:
                return bitCycleLeft(movingEdges, 1, 4);
            default:
                return movingEdges;
        }
    }
}