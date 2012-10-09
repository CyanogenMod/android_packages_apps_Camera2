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
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.android.gallery3d.filtershow.imageshow.GeometryMetadata.FLIP;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public abstract class ImageGeometry extends ImageSlave {
    private boolean mVisibilityGained = false;
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
    private GeometryMetadata mLocalGeoMetadata = null;
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

    private void calculateLocalScalingFactorAndOffset() {
        if (mLocalGeoMetadata == null || mLocalDisplayBounds == null)
            return;
        RectF imageBounds = mLocalGeoMetadata.getPhotoBounds();
        float imageWidth = imageBounds.width();
        float imageHeight = imageBounds.height();
        float displayWidth = mLocalDisplayBounds.width();
        float displayHeight = mLocalDisplayBounds.height();

        mCenterX = displayWidth / 2;
        mCenterY = displayHeight / 2;
        float zoom = displayWidth / imageWidth;
        if (imageHeight > imageWidth) {
            zoom = displayHeight / imageHeight;
        }
        mYOffset = (displayHeight - imageHeight) / 2.0f;
        mXOffset = (displayWidth - imageWidth) / 2.0f;
        mLocalGeoMetadata.setScaleFactor(zoom);
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

    private GeometryMetadata getMasterImageGeometryMetadataCopy() {
        return getMaster().getGeometry();
    }

    // Overwrites local with master
    protected void syncLocalToMasterGeometry() {
        mLocalGeoMetadata = getMasterImageGeometryMetadataCopy();
        calculateLocalScalingFactorAndOffset();
    }

    protected RectF getLocalPhotoBounds() {
        return mLocalGeoMetadata.getPhotoBounds();
    }

    protected RectF getLocalCropBounds() {
        return mLocalGeoMetadata.getCropBounds();
    }

    protected RectF getLocalDisplayBounds() {
        return new RectF(mLocalDisplayBounds);
    }

    protected float getLocalScale() {
        return mLocalGeoMetadata.getScaleFactor();
    }

    protected float getLocalRotation() {
        return mLocalGeoMetadata.getRotation();
    }

    protected float getLocalStraighten() {
        return mLocalGeoMetadata.getStraightenRotation();
    }

    protected void setLocalScale(float s) {
        mLocalGeoMetadata.setScaleFactor(s);
    }

    protected void setLocalRotation(float r) {
        mLocalGeoMetadata.setRotation(r);
    }

    protected void setLocalStraighten(float r) {
        mLocalGeoMetadata.setStraightenRotation(r);
    }

    protected void setLocalCropBounds(RectF c) {
        mLocalGeoMetadata.setCropBounds(c);
    }

    protected FLIP getLocalFlip() {
        return mLocalGeoMetadata.getFlipType();
    }

    protected void setLocalFlip(FLIP flip) {
        mLocalGeoMetadata.setFlipType(flip);
    }

    protected float getTotalLocalRotation() {
        return getLocalRotation() + getLocalStraighten();
    }

    protected Bitmap getMasterImage() {
        if (getMaster() == null)
            return null;
        return getMaster().mForegroundImage;
    }

    protected static Matrix getHorizontalMatrix(int width) {
        Matrix flipHorizontalMatrix = new Matrix();
        flipHorizontalMatrix.setScale(-1, 1);
        flipHorizontalMatrix.postTranslate(width, 0);
        return flipHorizontalMatrix;
    }

    protected static Matrix getVerticalMatrix(int height) {
        Matrix flipVerticalMatrix = new Matrix();
        flipVerticalMatrix.setScale(1, -1);
        flipVerticalMatrix.postTranslate(0, height);
        return flipVerticalMatrix;
    }

    protected static Matrix getFlipMatrix(FLIP type, int width, int height) {
        if (type == FLIP.HORIZONTAL) {
            return getHorizontalMatrix(width);
        } else if (type == FLIP.VERTICAL) {
            return getVerticalMatrix(height);
        } else if (type == FLIP.BOTH) {
            Matrix flipper = getVerticalMatrix(height);
            flipper.postConcat(getHorizontalMatrix(width));
            return flipper;
        } else {
            Matrix m = new Matrix();
            m.reset(); // identity
            return m;
        }
    }

    protected static float clamp(float i, float low, float high) {
        return Math.max(Math.min(i, high), low);
    }

    protected static float[] getCornersFromRect(RectF r) {
        // Order is:
        // 0------->1
        // ^ |
        // | v
        // 3<-------2
        float[] corners = {
                r.left, r.top, // 0
                r.right, r.top, // 1
                r.right, r.bottom,// 2
                r.left, r.bottom
        };// 3
        return corners;
    }

    // Returns maximal rectangular crop bound that still fits within
    // the image bound after the image has been rotated.
    protected static RectF findCropBoundForRotatedImg(RectF cropBound,
            RectF imageBound,
            float rotation,
            float centerX,
            float centerY) {
        Matrix m = new Matrix();
        float[] cropEdges = getCornersFromRect(cropBound);
        m.setRotate(rotation, centerX, centerY);
        Matrix m0 = new Matrix();
        if (!m.invert(m0))
            return null;
        m0.mapPoints(cropEdges);
        getEdgePoints(imageBound, cropEdges);
        m.mapPoints(cropEdges);
        return trapToRect(cropEdges);
    }

    // If edge point [x, y] in array [x0, y0, x1, y1, ...] is outside of the
    // image bound rectangle, clamps it to the edge of the rectangle.
    protected static void getEdgePoints(RectF imageBound, float[] array) {
        if (array.length < 2)
            return;
        for (int x = 0; x < array.length; x += 2) {
            array[x] = clamp(array[x], imageBound.left, imageBound.right);
            array[x + 1] = clamp(array[x + 1], imageBound.top, imageBound.bottom);
        }
    }

    protected static RectF trapToRect(float[] array) {
        float dx0 = array[4] - array[0];
        float dy0 = array[5] - array[1];
        float dx1 = array[6] - array[2];
        float dy1 = array[7] - array[3];
        float l0 = dx0 * dx0 + dy0 * dy0;
        float l1 = dx1 * dx1 + dy1 * dy1;
        if (l0 > l1) {
            RectF n = new RectF(array[2], array[3], array[6], array[7]);
            n.sort();
            return n;
        } else {
            RectF n = new RectF(array[0], array[1], array[4], array[5]);
            n.sort();
            return n;
        }
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

    protected static float[] shortestVectorFromPointToLine(float[] point, float[] l1, float[] l2) {
        float x1 = l1[0];
        float x2 = l2[0];
        float y1 = l1[1];
        float y2 = l2[1];
        float xdelt = x2 - x1;
        float ydelt = y2 - y1;
        if (xdelt == 0 && ydelt == 0)
            return null;
        float u = ((point[0] - x1) * xdelt + (point[1] - y1) * ydelt)
                / (xdelt * xdelt + ydelt * ydelt);
        float[] ret = {
                (x1 + u * (x2 - x1)), (y1 + u * (y2 - y1))
        };
        return ret;
    }

    protected static void fixAspectRatio(RectF r, float w, float h) {
        float scale = Math.min(r.width() / w, r.height() / h);
        r.set(r.left, r.top, scale * w, scale * h);
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
            syncLocalToMasterGeometry();
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
        // TODO: Override this stub.
    }

    protected void lostVisibility() {
        // TODO: Override this stub.
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
        if (getPanelController() != null) {
            getPanelController().onNewValue((int) getLocalValue());
        }
        invalidate();
        return true;
    }

    protected int getLocalValue() {
        return 0; // TODO: Override this
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

    protected void saveAndSetPreset() {
        ImagePreset copy = new ImagePreset(getImagePreset());
        copy.setGeometry(mLocalGeoMetadata);
        setImagePreset(copy);
    }

    @Override
    public void updateImage() {
    }

    protected void drawRegularFlippedBitmap(Canvas canvas, Bitmap image, Paint p) {
        float zoom = getLocalScale();
        canvas.save();
        canvas.rotate(getTotalLocalRotation(), mCenterX, mCenterY);
        canvas.scale(zoom, zoom, mCenterX, mCenterY);
        canvas.translate(mXOffset, mYOffset);
        Matrix flipper = getFlipMatrix(getLocalFlip(), image.getWidth(), image.getHeight());
        canvas.drawBitmap(image, flipper, p);
        canvas.restore();
    }

    protected void drawRegularBitmap(Canvas canvas, Bitmap image, Paint p) {
        float zoom = getLocalScale();
        canvas.save();
        canvas.rotate(getTotalLocalRotation(), mCenterX, mCenterY);
        canvas.scale(zoom, zoom, mCenterX, mCenterY);
        canvas.translate(mXOffset, mYOffset);
        canvas.drawBitmap(image, 0, 0, p);
        canvas.restore();
    }

    protected RectF getCropBoundsDisplayed() {
        return getCropBoundsDisplayed(getLocalCropBounds());
    }

    protected RectF getCropBoundsDisplayed(RectF bounds) {
        RectF crop = new RectF(bounds);
        Matrix m = new Matrix();
        float zoom = getLocalScale();
        m.setScale(zoom, zoom, mCenterX, mCenterY);
        m.preTranslate(mXOffset, mYOffset);
        m.mapRect(crop);
        return crop;
    }

    protected static float[] correctAngles(float rotation, float straighten) {
        float[] ret = {
                rotation, straighten
        };
        if (straighten >= MIN_STRAIGHTEN_ANGLE && straighten <= MAX_STRAIGHTEN_ANGLE
                && (rotation % 90 == 0)) {
            return ret;
        }
        float remainder = rotation % 90;
        float newRot = (int) (rotation / 90);
        float tempStraighten = straighten + remainder;
        newRot += (int) (tempStraighten / 90);
        tempStraighten %= 90;
        if (tempStraighten > MAX_STRAIGHTEN_ANGLE) {
            tempStraighten -= 90;
            newRot += 1;
        } else if (tempStraighten < MIN_STRAIGHTEN_ANGLE) {
            tempStraighten += 90;
            newRot -= 1;
        }
        ret[0] = newRot * 90;
        ret[1] = tempStraighten;
        return ret;
    }

    protected void correctStraightenRotateAngles() {
        float straighten = getLocalStraighten();
        float rotation = getLocalRotation();
        float[] angles = correctAngles(rotation, straighten);
        setLocalStraighten(angles[1]);
        setLocalRotation(angles[0]);
    }

    protected static Matrix getCropRotationMatrix(float rotation) {
        Matrix m = new Matrix();
        m.setRotate(rotation);
        if (!m.rectStaysRect()) {
            return null;
        }
        return m;
    }

    protected RectF getStraightenCropBounds() {
        RectF imageRect = getLocalPhotoBounds();
        Matrix m = new Matrix();
        float lRot = getLocalRotation();
        m.setRotate(lRot);
        if (!m.rectStaysRect()) {
            correctStraightenRotateAngles();
            m.setRotate(getLocalRotation());
        }
        RectF crop = getStraightenCropBounds(imageRect, getLocalStraighten());
        setLocalCropBounds(crop);
        if (!m.mapRect(crop)) {
            return null;
        }
        return crop;
    }

    protected RectF getStraightenCropBounds(RectF imageRect, float straightenAngle) {
        RectF boundsRect = getUntranslatedStraightenCropBounds(imageRect, straightenAngle);
        RectF nonRotateImage = getLocalPhotoBounds();
        Matrix m1 = new Matrix();
        m1.setTranslate(nonRotateImage.centerX() - boundsRect.centerX(), nonRotateImage.centerY()
                - boundsRect.centerY());
        m1.mapRect(boundsRect);
        return boundsRect;
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

    protected void drawShadows(Canvas canvas, RectF innerBounds, RectF outerBounds, Paint p) {
        float dWidth = outerBounds.width();
        float dHeight = outerBounds.height();
        canvas.drawRect(0, 0, dWidth, innerBounds.top, p);
        canvas.drawRect(0, innerBounds.bottom, dWidth, dHeight, p);
        canvas.drawRect(0, innerBounds.top, innerBounds.left, innerBounds.bottom,
                p);
        canvas.drawRect(innerBounds.right, innerBounds.top, dWidth,
                innerBounds.bottom, p);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (getDirtyGeometryFlag()) {
            syncLocalToMasterGeometry();
            clearDirtyGeometryFlag();
        }
        Bitmap image = getMasterImage();
        if (image == null) {
            return;
        }
        mHasDrawn = true;
        drawShape(canvas, image);
    }

    protected void drawShape(Canvas canvas, Bitmap image) {
        // TODO: Override this stub.
    }
}
