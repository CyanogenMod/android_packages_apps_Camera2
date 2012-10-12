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

    public void set(GeometryMetadata g) {
        mScaleFactor = g.mScaleFactor;
        mRotation = g.mRotation;
        mStraightenRotation = g.mStraightenRotation;
        mCropBounds.set(g.mCropBounds);
        mPhotoBounds.set(g.mPhotoBounds);
        mFlip = g.mFlip;
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

    public void setCropBounds(RectF newCropBounds) {
        mCropBounds.set(newCropBounds);
    }

    public void setPhotoBounds(RectF newPhotoBounds) {
        mPhotoBounds.set(newPhotoBounds);
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
                mFlip == d.mFlip &&
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
        return result;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + "scale=" + mScaleFactor
                + ",rotation=" + mRotation + ",flip=" + mFlip + ",straighten="
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

    public boolean hasSwitchedWidthHeight(){
        return (((int) (mRotation / 90)) % 2) != 0;
    }

    public Matrix buildGeometryMatrix(float width, float height, float scaling, float dx, float dy){
        float dx0 = width/2;
        float dy0 = height/2;
        Matrix m = getFlipMatrix(width, height);
        m.postTranslate(-dx0, -dy0);
        float rot = mRotation % 360;
        if (rot < 0)
            rot += 360;
        m.postRotate(rot + mStraightenRotation);
        m.postScale(scaling, scaling);
        m.postTranslate(dx, dy);
        return m;
    }

    public Matrix buildGeometryUIMatrix(float scaling, float dx, float dy){
        float w = mPhotoBounds.width();
        float h = mPhotoBounds.height();
        return buildGeometryMatrix(w, h, scaling, dx, dy);
    }
}
