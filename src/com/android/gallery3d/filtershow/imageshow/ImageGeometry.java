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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.filtershow.imageshow.GeometryMetadata.FLIP;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public abstract class ImageGeometry extends ImageShow {
    protected boolean mVisibilityGained = false;
    private boolean mHasDrawn = false;

    protected static final float MAX_STRAIGHTEN_ANGLE = 45;
    protected static final float MIN_STRAIGHTEN_ANGLE = -45;

    protected float mCenterX;
    protected float mCenterY;

    protected float mCurrentX;
    protected float mCurrentY;
    protected float mTouchCenterX;
    protected float mTouchCenterY;

    // Local geometry data
    private GeometryMetadata mLocalGeometry = null;
    private RectF mLocalDisplayBounds = null;
    protected float mXOffset = 0;
    protected float mYOffset = 0;

    protected enum MODES {
        NONE, DOWN, UP, MOVE
    }

    protected MODES mMode = MODES.NONE;

    private static final String LOGTAG = "ImageGeometry";

    public ImageGeometry(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageGeometry(Context context) {
        super(context);
    }

    private void setupLocalDisplayBounds(RectF b) {
        mLocalDisplayBounds = b;
        calculateLocalScalingFactorAndOffset();
    }

    protected static float angleFor(float dx, float dy) {
        return (float) (Math.atan2(dx, dy) * 180 / Math.PI);
    }

    protected static int snappedAngle(float angle) {
        float remainder = angle % 90;
        int current = (int) (angle / 90); // truncates
        if (remainder < -45) {
            --current;
        } else if (remainder > 45) {
            ++current;
        }
        return current * 90;
    }

    protected float getCurrentTouchAngle() {
        if (mCurrentX == mTouchCenterX && mCurrentY == mTouchCenterY) {
            return 0;
        }
        float dX1 = mTouchCenterX - mCenterX;
        float dY1 = mTouchCenterY - mCenterY;
        float dX2 = mCurrentX - mCenterX;
        float dY2 = mCurrentY - mCenterY;

        float angleA = angleFor(dX1, dY1);
        float angleB = angleFor(dX2, dY2);
        return (angleB - angleA) % 360;
    }

    protected float computeScale(float width, float height) {
        float imageWidth = mLocalGeometry.getPhotoBounds().width();
        float imageHeight = mLocalGeometry.getPhotoBounds().height();
        return GeometryMath.scale(imageWidth, imageHeight, width, height);
    }

    private void calculateLocalScalingFactorAndOffset() {
        if (mLocalGeometry == null || mLocalDisplayBounds == null)
            return;
        RectF imageBounds = mLocalGeometry.getPhotoBounds();
        float imageWidth = imageBounds.width();
        float imageHeight = imageBounds.height();
        float displayWidth = mLocalDisplayBounds.width();
        float displayHeight = mLocalDisplayBounds.height();

        mCenterX = displayWidth / 2;
        mCenterY = displayHeight / 2;
        mYOffset = (displayHeight - imageHeight) / 2.0f;
        mXOffset = (displayWidth - imageWidth) / 2.0f;
        updateScale();
    }

    @Override
    public void resetParameter() {
        super.resetParameter();
        setLocalRotation(0);
        setLocalStraighten(0);
        setLocalCropBounds(getLocalPhotoBounds());
        setLocalFlip(FLIP.NONE);
        saveAndSetPreset();
        invalidate();
    }

    // Overwrites local with master
    public void syncLocalToMasterGeometry() {
        mLocalGeometry = getGeometry();
        calculateLocalScalingFactorAndOffset();
    }

    protected RectF getLocalPhotoBounds() {
        return mLocalGeometry.getPhotoBounds();
    }

    protected RectF getLocalCropBounds() {
        return mLocalGeometry.getPreviewCropBounds();
    }

    protected RectF getLocalDisplayBounds() {
        return new RectF(mLocalDisplayBounds);
    }

    protected float getLocalScale() {
        return mLocalGeometry.getScaleFactor();
    }

    protected float getLocalRotation() {
        return mLocalGeometry.getRotation();
    }

    protected float getLocalStraighten() {
        return mLocalGeometry.getStraightenRotation();
    }

    protected void setLocalScale(float s) {
        mLocalGeometry.setScaleFactor(s);
    }

    protected void updateScale() {
        RectF bounds = getUntranslatedStraightenCropBounds(mLocalGeometry.getPhotoBounds(),
                getLocalStraighten());
        float zoom = computeScale(bounds.width(), bounds.height());
        setLocalScale(zoom);
    }

    protected void setLocalRotation(float r) {
        mLocalGeometry.setRotation(r);
        updateScale();
    }

    /**
     * Constrains rotation to be in [0, 90, 180, 270].
     */
    protected int constrainedRotation(float rotation) {
        int r = (int) ((rotation % 360) / 90);
        r = (r < 0) ? (r + 4) : r;
        return r * 90;
    }

    protected boolean isHeightWidthSwapped() {
        return ((int) (getLocalRotation() / 90)) % 2 != 0;
    }

    protected void setLocalStraighten(float r) {
        mLocalGeometry.setStraightenRotation(r);
        updateScale();
    }

    protected void setLocalCropBounds(RectF c) {
        mLocalGeometry.setCropBounds(c);
        updateScale();
    }

    protected FLIP getLocalFlip() {
        return mLocalGeometry.getFlipType();
    }

    protected void setLocalFlip(FLIP flip) {
        mLocalGeometry.setFlipType(flip);
    }

    protected float getTotalLocalRotation() {
        return getLocalRotation() + getLocalStraighten();
    }

    protected static Path drawClosedPath(Canvas canvas, Paint paint, float[] points) {
        Path crop = new Path();
        crop.moveTo(points[0], points[1]);
        crop.lineTo(points[2], points[3]);
        crop.lineTo(points[4], points[5]);
        crop.lineTo(points[6], points[7]);
        crop.close();
        canvas.drawPath(crop, paint);
        return crop;
    }

    protected static float getNewHeightForWidthAspect(float width, float w, float h) {
        return width * h / w;
    }

    protected static float getNewWidthForHeightAspect(float height, float w, float h) {
        return height * w / h;
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) {
            mVisibilityGained = true;
            MasterImage.getImage().invalidateFiltersOnly();
            syncLocalToMasterGeometry();
            updateScale();
            gainedVisibility();
        } else {
            if (mVisibilityGained == true && mHasDrawn == true) {
                lostVisibility();
            }
            mVisibilityGained = false;
            mHasDrawn = false;
        }
    }

    protected void gainedVisibility() {
        // Override this stub.
    }

    protected void lostVisibility() {
        // Override this stub.
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupLocalDisplayBounds(new RectF(0, 0, w, h));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case (MotionEvent.ACTION_DOWN):
                setActionDown(event.getX(), event.getY());
                break;
            case (MotionEvent.ACTION_UP):
                setActionUp();
                saveAndSetPreset();
                break;
            case (MotionEvent.ACTION_MOVE):
                setActionMove(event.getX(), event.getY());
                break;
            default:
                setNoAction();
        }
        invalidate();
        return true;
    }

    protected int getLocalValue() {
        return 0; // Override this
    }

    protected void setActionDown(float x, float y) {
        mTouchCenterX = x;
        mTouchCenterY = y;
        mCurrentX = x;
        mCurrentY = y;
        mMode = MODES.DOWN;
    }

    protected void setActionMove(float x, float y) {
        mCurrentX = x;
        mCurrentY = y;
        mMode = MODES.MOVE;
    }

    protected void setActionUp() {
        mMode = MODES.UP;
    }

    protected void setNoAction() {
        mMode = MODES.NONE;
    }

    @Override
    public boolean showTitle() {
        return false;
    }

    public String getName() {
        return "Geometry";
    }

    public void saveAndSetPreset() {
        ImagePreset lastHistoryItem = MasterImage.getImage().getHistory().getLast();
        if (lastHistoryItem != null && lastHistoryItem.historyName().equalsIgnoreCase(getName())) {
            getImagePreset().setGeometry(mLocalGeometry);
            resetImageCaches(this);
        } else {
            if (mLocalGeometry.hasModifications()) {
                ImagePreset copy = new ImagePreset(getImagePreset());
                copy.setGeometry(mLocalGeometry);
                copy.setHistoryName(getName());
                copy.setIsFx(false);
                MasterImage.getImage().setPreset(copy, true);
            }
        }
        invalidate();
    }

    public static RectF getUntranslatedStraightenCropBounds(RectF imageRect, float straightenAngle) {
        float deg = straightenAngle;
        if (deg < 0) {
            deg = -deg;
        }
        double a = Math.toRadians(deg);
        double sina = Math.sin(a);
        double cosa = Math.cos(a);

        double rw = imageRect.width();
        double rh = imageRect.height();
        double h1 = rh * rh / (rw * sina + rh * cosa);
        double h2 = rh * rw / (rw * cosa + rh * sina);
        double hh = Math.min(h1, h2);
        double ww = hh * rw / rh;

        float left = (float) ((rw - ww) * 0.5f);
        float top = (float) ((rh - hh) * 0.5f);
        float right = (float) (left + ww);
        float bottom = (float) (top + hh);

        return new RectF(left, top, right, bottom);
    }

    protected RectF straightenBounds() {
        RectF bounds = getUntranslatedStraightenCropBounds(getLocalPhotoBounds(),
                getLocalStraighten());
        float scale = computeScale(getWidth(), getHeight());
        bounds = GeometryMath.scaleRect(bounds, scale);
        float dx = (getWidth() / 2) - bounds.centerX();
        float dy = (getHeight() / 2) - bounds.centerY();
        bounds.offset(dx, dy);
        return bounds;
    }

    protected static void drawRotatedShadows(Canvas canvas, Paint p, RectF innerBounds,
            RectF outerBounds,
            float rotation, float centerX, float centerY) {
        canvas.save();
        canvas.rotate(rotation, centerX, centerY);

        float x = (outerBounds.left - outerBounds.right);
        float y = (outerBounds.top - outerBounds.bottom);
        float longest = (float) Math.sqrt(x * x + y * y) / 2;
        float minX = centerX - longest;
        float maxX = centerX + longest;
        float minY = centerY - longest;
        float maxY = centerY + longest;
        canvas.drawRect(minX, minY, innerBounds.right, innerBounds.top, p);
        canvas.drawRect(minX, innerBounds.top, innerBounds.left, maxY, p);
        canvas.drawRect(innerBounds.left, innerBounds.bottom, maxX, maxY,
                p);
        canvas.drawRect(innerBounds.right, minY, maxX,
                innerBounds.bottom, p);
        canvas.rotate(-rotation, centerX, centerY);
        canvas.restore();
    }

    protected void drawShadows(Canvas canvas, Paint p, RectF innerBounds) {
        float w = getWidth();
        float h = getHeight();
        canvas.drawRect(0f, 0f, w, innerBounds.top, p);
        canvas.drawRect(0f, innerBounds.top, innerBounds.left, innerBounds.bottom, p);
        canvas.drawRect(innerBounds.right, innerBounds.top, w, innerBounds.bottom, p);
        canvas.drawRect(0f, innerBounds.bottom, w, h, p);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (getDirtyGeometryFlag()) {
            syncLocalToMasterGeometry();
            clearDirtyGeometryFlag();
        }
        Bitmap image = getFiltersOnlyImage();
        if (image == null) {
            invalidate();
            return;
        }
        mHasDrawn = true;

        drawShape(canvas, image);
    }

    protected void drawShape(Canvas canvas, Bitmap image) {
        // TODO: Override this stub.
    }

    /**
     * Sets up inputs for buildCenteredPhotoMatrix and buildWanderingCropMatrix
     * and returns the scale factor.
     */
    protected float getTransformState(RectF photo, RectF crop, float[] displayCenter) {
        RectF photoBounds = getLocalPhotoBounds();
        RectF cropBounds = getLocalCropBounds();
        float scale = computeScale(getWidth(), getHeight());
        // checks if local rotation is an odd multiple of 90.
        if (isHeightWidthSwapped()) {
            scale = computeScale(getHeight(), getWidth());
        }
        // put in screen coordinates
        if (crop != null) {
            crop.set(GeometryMath.scaleRect(cropBounds, scale));
        }
        if (photo != null) {
            photo.set(GeometryMath.scaleRect(photoBounds, scale));
        }
        if (displayCenter != null && displayCenter.length >= 2) {
            displayCenter[0] = getWidth() / 2f;
            displayCenter[1] = getHeight() / 2f;
        }
        return scale;
    }

    protected RectF drawTransformed(Canvas canvas, Bitmap photo, Paint p, float[] offset) {
        p.setARGB(255, 0, 0, 0);
        float[] displayCenter = new float[2];
        RectF scaledCrop = new RectF();
        RectF scaledPhoto = new RectF();
        float scale = getTransformState(scaledPhoto, scaledCrop, displayCenter);
        Matrix m = GeometryMetadata.buildCenteredPhotoMatrix(scaledPhoto, scaledCrop,
                getLocalRotation(), getLocalStraighten(), getLocalFlip(), displayCenter);

        Matrix m1 = GeometryMetadata.buildWanderingCropMatrix(scaledPhoto, scaledCrop,
                getLocalRotation(), getLocalStraighten(), getLocalFlip(), displayCenter);
        m1.mapRect(scaledCrop);
        Path path = new Path();
        scaledCrop.offset(-offset[0], -offset[1]);
        path.addRect(scaledCrop, Path.Direction.CCW);

        m.preScale(scale, scale);
        m.postTranslate(-offset[0], -offset[1]);
        canvas.save();
        canvas.drawBitmap(photo, m, p);
        canvas.restore();

        p.setColor(Color.WHITE);
        p.setStyle(Style.STROKE);
        p.setStrokeWidth(2);
        canvas.drawPath(path, p);

        p.setColor(getDefaultBackgroundColor());
        p.setAlpha(128);
        p.setStyle(Paint.Style.FILL);
        drawShadows(canvas, p, scaledCrop);
        return scaledCrop;
    }

    protected void drawTransformedCropped(Canvas canvas, Bitmap photo, Paint p) {
        RectF photoBounds = getLocalPhotoBounds();
        RectF cropBounds = getLocalCropBounds();
        float imageWidth = cropBounds.width();
        float imageHeight = cropBounds.height();
        float scale = GeometryMath.scale(imageWidth, imageHeight, getWidth(), getHeight());
        // checks if local rotation is an odd multiple of 90.
        if (isHeightWidthSwapped()) {
            scale = GeometryMath.scale(imageWidth, imageHeight, getHeight(), getWidth());
        }
        // put in screen coordinates
        RectF scaledCrop = GeometryMath.scaleRect(cropBounds, scale);
        RectF scaledPhoto = GeometryMath.scaleRect(photoBounds, scale);
        float[] displayCenter = {
                getWidth() / 2f, getHeight() / 2f
        };
        Matrix m1 = GeometryMetadata.buildWanderingCropMatrix(scaledPhoto, scaledCrop,
                getLocalRotation(), getLocalStraighten(), getLocalFlip(), displayCenter);
        float[] cropCenter = {
                scaledCrop.centerX(), scaledCrop.centerY()
        };
        m1.mapPoints(cropCenter);
        GeometryMetadata.concatRecenterMatrix(m1, cropCenter, displayCenter);
        m1.preRotate(getLocalStraighten(), scaledPhoto.centerX(), scaledPhoto.centerY());
        m1.preScale(scale, scale);

        p.setARGB(255, 0, 0, 0);
        canvas.save();
        canvas.drawBitmap(photo, m1, p);
        canvas.restore();

        p.setColor(getDefaultBackgroundColor());
        p.setStyle(Paint.Style.FILL);
        scaledCrop.offset(displayCenter[0] - scaledCrop.centerX(), displayCenter[1]
                - scaledCrop.centerY());
        RectF display = new RectF(0, 0, getWidth(), getHeight());
        drawRotatedShadows(canvas, p, scaledCrop, display, getLocalRotation(), getWidth() / 2,
                getHeight() / 2);
    }
}
