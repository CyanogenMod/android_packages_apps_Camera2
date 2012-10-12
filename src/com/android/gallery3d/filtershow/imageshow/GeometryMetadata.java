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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;

import com.android.gallery3d.filtershow.filters.ImageFilterGeometry;

/**
 * This class holds metadata about an image's geometry. Specifically: rotation,
 * scaling, cropping, and image boundaries. It maintains the invariant that the
 * cropping boundaries are within or equal to the image boundaries (before
 * rotation) WHEN mSafe is true.
 */

public class GeometryMetadata {
    // Applied in order: rotate, crop, scale.
    // Do not scale saved image (presumably?).
    private static final ImageFilterGeometry mImageFilter = new ImageFilterGeometry();
    private float mScaleFactor = 1.0f;
    private float mRotation = 0;
    private float mStraightenRotation = 0;
    private final RectF mCropBounds = new RectF();
    private final RectF mPhotoBounds = new RectF();
    private FLIP mFlip = FLIP.NONE;
    private boolean mSafe = false;

    private Matrix mMatrix = new Matrix();

    private RectF mBounds = new RectF();

    public enum FLIP {
        NONE, VERTICAL, HORIZONTAL, BOTH
    }

    public GeometryMetadata() {
    }

    public GeometryMetadata(GeometryMetadata g) {
        set(g);
    }

    public Bitmap apply(Bitmap original, float scaleFactor, boolean highQuality){
        mImageFilter.setGeometryMetadata(this);
        Bitmap m = mImageFilter.apply(original, scaleFactor, highQuality);
        return m;
    }

    // Safe as long as invariant holds.
    public void set(GeometryMetadata g) {
        mScaleFactor = g.mScaleFactor;
        mRotation = g.mRotation;
        mStraightenRotation = g.mStraightenRotation;
        mCropBounds.set(g.mCropBounds);
        mPhotoBounds.set(g.mPhotoBounds);
        mFlip = g.mFlip;
        mSafe = g.mSafe;
        mMatrix = g.mMatrix;
        mBounds = g.mBounds;
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    public float getRotation() {
        return mRotation;
    }

    public float getStraightenRotation() {
        return mStraightenRotation;
    }

    public RectF getCropBounds() {
        return new RectF(mCropBounds);
    }

    public FLIP getFlipType() {
        return mFlip;
    }

    public RectF getPhotoBounds() {
        return new RectF(mPhotoBounds);
    }

    public boolean safe() {
        return mSafe;
    }

    public void setScaleFactor(float scale) {
        mScaleFactor = scale;
    }

    public void setFlipType(FLIP flip) {
        mFlip = flip;
    }

    public void setRotation(float rotation) {
        mRotation = rotation;
    }

    public void setStraightenRotation(float straighten) {
        mStraightenRotation = straighten;
    }

    /**
     * Sets crop bounds to be the intersection of mPhotoBounds and the new crop
     * bounds. If there was no intersection, returns false and does not set crop
     * bounds
     */
    public boolean safeSetCropBounds(RectF newCropBounds) {
        if (mCropBounds.setIntersect(newCropBounds, mPhotoBounds)) {
            mSafe = true;
            return true;
        }
        return false;
    }

    public void setCropBounds(RectF newCropBounds) {
        mCropBounds.set(newCropBounds);
        mSafe = false;
    }

    /**
     * Sets mPhotoBounds to be the new photo bounds and sets mCropBounds to be
     * the intersection of the new photo bounds and the old crop bounds. Sets
     * the crop bounds to mPhotoBounds if there is no intersection.
     */

    public void safeSetPhotoBounds(RectF newPhotoBounds) {
        mPhotoBounds.set(newPhotoBounds);
        if (!mCropBounds.intersect(mPhotoBounds)) {
            mCropBounds.set(mPhotoBounds);
        }
        mSafe = true;
    }

    public void setPhotoBounds(RectF newPhotoBounds) {
        mPhotoBounds.set(newPhotoBounds);
        mSafe = false;
    }

    public boolean cropFitsInPhoto(RectF cropBounds) {
        return mPhotoBounds.contains(cropBounds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        GeometryMetadata d = (GeometryMetadata) o;
        return (mScaleFactor == d.mScaleFactor &&
                mRotation == d.mRotation &&
                mStraightenRotation == d.mStraightenRotation &&
                mFlip == d.mFlip && mSafe == d.mSafe &&
                mCropBounds.equals(d.mCropBounds) && mPhotoBounds.equals(d.mPhotoBounds));
    }

    @Override
    public int hashCode() {
        int result = 23;
        result = 31 * result + Float.floatToIntBits(mRotation);
        result = 31 * result + Float.floatToIntBits(mStraightenRotation);
        result = 31 * result + Float.floatToIntBits(mScaleFactor);
        result = 31 * result + mFlip.hashCode();
        result = 31 * result + mCropBounds.hashCode();
        result = 31 * result + mPhotoBounds.hashCode();
        result = 31 * result + (mSafe ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + "scale=" + mScaleFactor
                + ",rotation=" + mRotation + ",flip=" + mFlip + ",safe="
                + (mSafe ? "true" : "false") + ",straighten="
                + mStraightenRotation + ",cropRect=" + mCropBounds.toShortString()
                + ",photoRect=" + mPhotoBounds.toShortString() + "]";
    }

    protected Matrix getHorizontalMatrix(float width) {
        Matrix flipHorizontalMatrix = new Matrix();
        flipHorizontalMatrix.setScale(-1, 1);
        flipHorizontalMatrix.postTranslate(width, 0);
        return flipHorizontalMatrix;
    }

    protected Matrix getVerticalMatrix(float height) {
        Matrix flipVerticalMatrix = new Matrix();
        flipVerticalMatrix.setScale(1, -1);
        flipVerticalMatrix.postTranslate(0, height);
        return flipVerticalMatrix;
    }

    public Matrix getFlipMatrix(float width, float height) {
        FLIP type = getFlipType();
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

    public Matrix getMatrix() {
        return mMatrix;
    }
}
