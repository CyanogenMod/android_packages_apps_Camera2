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
    private float mScaleFactor = 0;
    private float mRotation = 0;
    private float mStraightenRotation = 0;
    private final RectF mCropBounds = new RectF();
    private final RectF mPhotoBounds = new RectF();
    private FLIP mFlip = FLIP.NONE;
    private boolean mSafe = false;

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
        mPhotoBounds.set(0,0, m.getWidth(), m.getHeight());
        mCropBounds.set(mPhotoBounds);
        mScaleFactor = 0;
        mRotation = 0;
        mStraightenRotation = 0;
        mFlip = FLIP.NONE;
        mSafe = false;
        return m;
    }

    public GeometryMetadata(float scale, float rotation, float straighten, RectF cropBounds,
            RectF photoBounds, FLIP flipType) {
        mScaleFactor = scale;
        mRotation = rotation;
        mStraightenRotation = straighten;
        mCropBounds.set(cropBounds);
        mPhotoBounds.set(photoBounds);
        mFlip = flipType;
        mSafe = cropFitsInPhoto(mCropBounds);
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
    }

    public void safeSet(GeometryMetadata g) {
        if (g.safe()) {
            set(g);
            return;
        }

        mScaleFactor = g.mScaleFactor;
        mRotation = g.mRotation;
        mStraightenRotation = g.mStraightenRotation;
        mCropBounds.set(g.mCropBounds);
        safeSetPhotoBounds(g.mPhotoBounds);
        mFlip = g.mFlip;
    }

    public void safeSet(float scale,
            float rotation,
            float straighten,
            RectF cropBounds,
            RectF photoBounds,
            FLIP flipType) {
        mScaleFactor = scale;
        mStraightenRotation = straighten;
        mRotation = rotation;
        mCropBounds.set(cropBounds);
        safeSetPhotoBounds(photoBounds);
        mFlip = flipType;
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

}
