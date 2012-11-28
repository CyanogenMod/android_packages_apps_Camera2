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
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.ImageFilterGeometry;

public class GeometryMetadata {
    // Applied in order: rotate, crop, scale.
    // Do not scale saved image (presumably?).
    private static final ImageFilterGeometry mImageFilter = new ImageFilterGeometry();
    private static final String LOGTAG = "GeometryMetadata";
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

    public boolean hasModifications() {
        if (mScaleFactor != 1.0f) {
            return true;
        }
        if (mRotation != 0) {
            return true;
        }
        if (mStraightenRotation != 0) {
            return true;
        }
        Rect cropBounds = GeometryMath.roundNearest(mCropBounds);
        Rect photoBounds = GeometryMath.roundNearest(mPhotoBounds);
        if (!cropBounds.equals(photoBounds)) {
            return true;
        }
        if (!mFlip.equals(FLIP.NONE)) {
            return true;
        }
        return false;
    }

    public Bitmap apply(Bitmap original, float scaleFactor, boolean highQuality) {
        if (!hasModifications()) {
            return original;
        }
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

    public RectF getPreviewCropBounds() {
        return new RectF(mCropBounds);
    }

    public RectF getCropBounds(Bitmap bitmap) {
        float scale = 1.0f;
        scale = GeometryMath.scale(mPhotoBounds.width(), mPhotoBounds.height(), bitmap.getWidth(),
                bitmap.getHeight());
        return new RectF(mCropBounds.left * scale, mCropBounds.top * scale,
                mCropBounds.right * scale, mCropBounds.bottom * scale);
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

    // TODO: refactor away
    protected static Matrix getHorizontalMatrix(float width) {
        Matrix flipHorizontalMatrix = new Matrix();
        flipHorizontalMatrix.setScale(-1, 1);
        flipHorizontalMatrix.postTranslate(width, 0);
        return flipHorizontalMatrix;
    }

    protected static void concatHorizontalMatrix(Matrix m, float width) {
        m.postScale(-1, 1);
        m.postTranslate(width, 0);
    }

    // TODO: refactor away
    protected static Matrix getVerticalMatrix(float height) {
        Matrix flipVerticalMatrix = new Matrix();
        flipVerticalMatrix.setScale(1, -1);
        flipVerticalMatrix.postTranslate(0, height);
        return flipVerticalMatrix;
    }

    protected static void concatVerticalMatrix(Matrix m, float height) {
        m.postScale(1, -1);
        m.postTranslate(0, height);
    }

    // TODO: refactor away
    public static Matrix getFlipMatrix(float width, float height, FLIP type) {
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

    public static void concatMirrorMatrix(Matrix m, float width, float height, FLIP type) {
        if (type == FLIP.HORIZONTAL) {
            concatHorizontalMatrix(m, width);
        } else if (type == FLIP.VERTICAL) {
            concatVerticalMatrix(m, height);
        } else if (type == FLIP.BOTH) {
            concatVerticalMatrix(m, height);
            concatHorizontalMatrix(m, width);
        }
    }

    public Matrix getMatrixOriginalOrientation(int orientation, float originalWidth,
            float originalHeight) {
        Matrix imageRotation = new Matrix();
        switch (orientation) {
            case ImageLoader.ORI_ROTATE_90: {
                imageRotation.setRotate(90, originalWidth / 2f, originalHeight / 2f);
                imageRotation.postTranslate(-(originalWidth - originalHeight) / 2f,
                        -(originalHeight - originalWidth) / 2f);
                break;
            }
            case ImageLoader.ORI_ROTATE_180: {
                imageRotation.setRotate(180, originalWidth / 2f, originalHeight / 2f);
                break;
            }
            case ImageLoader.ORI_ROTATE_270: {
                imageRotation.setRotate(270, originalWidth / 2f, originalHeight / 2f);
                imageRotation.postTranslate(-(originalWidth - originalHeight) / 2f,
                        -(originalHeight - originalWidth) / 2f);
                break;
            }
            case ImageLoader.ORI_FLIP_HOR: {
                imageRotation.preScale(-1, 1);
                break;
            }
            case ImageLoader.ORI_FLIP_VERT: {
                imageRotation.preScale(1, -1);
                break;
            }
            case ImageLoader.ORI_TRANSPOSE: {
                imageRotation.setRotate(90, originalWidth / 2f, originalHeight / 2f);
                imageRotation.postTranslate(-(originalWidth - originalHeight) / 2f,
                        -(originalHeight - originalWidth) / 2f);
                imageRotation.preScale(1, -1);
                break;
            }
            case ImageLoader.ORI_TRANSVERSE: {
                imageRotation.setRotate(270, originalWidth / 2f, originalHeight / 2f);
                imageRotation.postTranslate(-(originalWidth - originalHeight) / 2f,
                        -(originalHeight - originalWidth) / 2f);
                imageRotation.preScale(1, -1);
                break;
            }
        }
        return imageRotation;
    }

    public Matrix getOriginalToScreen(boolean rotate, float originalWidth, float originalHeight,
            float viewWidth, float viewHeight) {
        RectF photoBounds = getPhotoBounds();
        RectF cropBounds = getPreviewCropBounds();
        float imageWidth = cropBounds.width();
        float imageHeight = cropBounds.height();

        int orientation = ImageLoader.getZoomOrientation();
        Matrix imageRotation = getMatrixOriginalOrientation(orientation, originalWidth,
                originalHeight);
        if (orientation == ImageLoader.ORI_ROTATE_90 ||
                orientation == ImageLoader.ORI_ROTATE_270 ||
                orientation == ImageLoader.ORI_TRANSPOSE ||
                orientation == ImageLoader.ORI_TRANSVERSE) {
            float tmp = originalWidth;
            originalWidth = originalHeight;
            originalHeight = tmp;
        }

        float preScale = GeometryMath.scale(originalWidth, originalHeight,
                photoBounds.width(), photoBounds.height());
        float scale = GeometryMath.scale(imageWidth, imageHeight, viewWidth, viewHeight);
        // checks if local rotation is an odd multiple of 90.
        if (((int) (getRotation() / 90)) % 2 != 0) {
            scale = GeometryMath.scale(imageWidth, imageHeight, viewHeight, viewWidth);
        }
        // put in screen coordinates
        RectF scaledCrop = GeometryMath.scaleRect(cropBounds, scale);
        RectF scaledPhoto = GeometryMath.scaleRect(photoBounds, scale);
        float[] displayCenter = {
                viewWidth / 2f, viewHeight / 2f
        };
        Matrix m1 = GeometryMetadata.buildWanderingCropMatrix(scaledPhoto, scaledCrop,
                getRotation(), getStraightenRotation(), getFlipType(), displayCenter);
        float[] cropCenter = {
                scaledCrop.centerX(), scaledCrop.centerY()
        };
        m1.mapPoints(cropCenter);
        GeometryMetadata.concatRecenterMatrix(m1, cropCenter, displayCenter);
        m1.preRotate(getStraightenRotation(), scaledPhoto.centerX(), scaledPhoto.centerY());
        m1.preScale(scale, scale);
        m1.preScale(preScale, preScale);
        m1.preConcat(imageRotation);

        return m1;
    }

    // TODO: refactor away
    public Matrix getFlipMatrix(float width, float height) {
        FLIP type = getFlipType();
        return getFlipMatrix(width, height, type);
    }

    public boolean hasSwitchedWidthHeight() {
        return (((int) (mRotation / 90)) % 2) != 0;
    }

    // TODO: refactor away
    public Matrix buildGeometryMatrix(float width, float height, float scaling, float dx, float dy,
            float rotation) {
        float dx0 = width / 2;
        float dy0 = height / 2;
        Matrix m = getFlipMatrix(width, height);
        m.postTranslate(-dx0, -dy0);
        m.postRotate(rotation);
        m.postScale(scaling, scaling);
        m.postTranslate(dx, dy);
        return m;
    }

    // TODO: refactor away
    public Matrix buildGeometryMatrix(float width, float height, float scaling, float dx, float dy,
            boolean onlyRotate) {
        float rot = mRotation;
        if (!onlyRotate) {
            rot += mStraightenRotation;
        }
        return buildGeometryMatrix(width, height, scaling, dx, dy, rot);
    }

    // TODO: refactor away
    public Matrix buildGeometryUIMatrix(float scaling, float dx, float dy) {
        float w = mPhotoBounds.width();
        float h = mPhotoBounds.height();
        return buildGeometryMatrix(w, h, scaling, dx, dy, false);
    }

    public static Matrix buildPhotoMatrix(RectF photo, RectF crop, float rotation,
            float straighten, FLIP type) {
        Matrix m = new Matrix();
        m.setRotate(straighten, photo.centerX(), photo.centerY());
        concatMirrorMatrix(m, photo.right, photo.bottom, type);
        m.postRotate(rotation, crop.centerX(), crop.centerY());

        return m;
    }

    public static Matrix buildCropMatrix(RectF crop, float rotation) {
        Matrix m = new Matrix();
        m.setRotate(rotation, crop.centerX(), crop.centerY());
        return m;
    }

    public static void concatRecenterMatrix(Matrix m, float[] currentCenter, float[] newCenter) {
        m.postTranslate(newCenter[0] - currentCenter[0], newCenter[1] - currentCenter[1]);
    }

    /**
     * Builds a matrix to transform a bitmap of width bmWidth and height
     * bmHeight so that the region of the bitmap being cropped to is oriented
     * and centered at displayCenter.
     *
     * @param bmWidth
     * @param bmHeight
     * @param displayCenter
     * @return
     */
    public Matrix buildTotalXform(float bmWidth, float bmHeight, float[] displayCenter) {
        RectF rp = getPhotoBounds();
        RectF rc = getPreviewCropBounds();

        float scale = GeometryMath.scale(rp.width(), rp.height(), bmWidth, bmHeight);
        RectF scaledCrop = GeometryMath.scaleRect(rc, scale);
        RectF scaledPhoto = GeometryMath.scaleRect(rp, scale);

        Matrix m1 = GeometryMetadata.buildWanderingCropMatrix(scaledPhoto, scaledCrop,
                getRotation(), getStraightenRotation(),
                getFlipType(), displayCenter);
        float[] cropCenter = {
                scaledCrop.centerX(), scaledCrop.centerY()
        };
        m1.mapPoints(cropCenter);

        GeometryMetadata.concatRecenterMatrix(m1, cropCenter, displayCenter);
        m1.preRotate(getStraightenRotation(), scaledPhoto.centerX(),
                scaledPhoto.centerY());
        return m1;
    }

    /**
     * Builds a matrix that rotates photo rect about it's center by the
     * straighten angle, mirrors it about the crop center, and rotates it about
     * the crop center by the rotation angle, and re-centers the photo rect.
     *
     * @param photo
     * @param crop
     * @param rotation
     * @param straighten
     * @param type
     * @param newCenter
     * @return
     */
    public static Matrix buildCenteredPhotoMatrix(RectF photo, RectF crop, float rotation,
            float straighten, FLIP type, float[] newCenter) {
        Matrix m = buildPhotoMatrix(photo, crop, rotation, straighten, type);
        float[] center = {
                photo.centerX(), photo.centerY()
        };
        m.mapPoints(center);
        concatRecenterMatrix(m, center, newCenter);
        return m;
    }

    /**
     * Builds a matrix that rotates a crop rect about it's center by rotation
     * angle, then re-centers the crop rect.
     *
     * @param crop
     * @param rotation
     * @param newCenter
     * @return
     */
    public static Matrix buildCenteredCropMatrix(RectF crop, float rotation, float[] newCenter) {
        Matrix m = buildCropMatrix(crop, rotation);
        float[] center = {
                crop.centerX(), crop.centerY()
        };
        m.mapPoints(center);
        concatRecenterMatrix(m, center, newCenter);
        return m;
    }

    /**
     * Builds a matrix that transforms the crop rect to its view coordinates
     * inside the photo rect.
     *
     * @param photo
     * @param crop
     * @param rotation
     * @param straighten
     * @param type
     * @param newCenter
     * @return
     */
    public static Matrix buildWanderingCropMatrix(RectF photo, RectF crop, float rotation,
            float straighten, FLIP type, float[] newCenter) {
        Matrix m = buildCenteredPhotoMatrix(photo, crop, rotation, straighten, type, newCenter);
        m.preRotate(-straighten, photo.centerX(), photo.centerY());
        return m;
    }
}
