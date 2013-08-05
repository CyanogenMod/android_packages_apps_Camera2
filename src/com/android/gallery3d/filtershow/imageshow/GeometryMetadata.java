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
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.filtershow.editors.EditorCrop;
import com.android.gallery3d.filtershow.editors.EditorFlip;
import com.android.gallery3d.filtershow.editors.EditorRotate;
import com.android.gallery3d.filtershow.editors.EditorStraighten;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilterGeometry;

public class GeometryMetadata extends FilterRepresentation {
    private static final String LOGTAG = "GeometryMetadata";
    private float mScaleFactor = 1.0f;
    private float mRotation = 0;
    private float mStraightenRotation = 0;
    private final RectF mCropBounds = new RectF();
    private final RectF mPhotoBounds = new RectF();
    private FLIP mFlip = FLIP.NONE;

    public enum FLIP {
        NONE, VERTICAL, HORIZONTAL, BOTH
    }

    // Output format data from intent extras
    private boolean mUseCropExtras = false;
    private CropExtras mCropExtras = null;
    public void setUseCropExtrasFlag(boolean f){
        mUseCropExtras = f;
    }

    public boolean getUseCropExtrasFlag(){
        return mUseCropExtras;
    }

    public void setCropExtras(CropExtras e){
        mCropExtras = e;
    }

    public CropExtras getCropExtras(){
        return mCropExtras;
    }

    public GeometryMetadata() {
        super("GeometryMetadata");
        setFilterClass(ImageFilterGeometry.class);
        setEditorId(EditorCrop.ID);
        setTextId(0);
        setShowParameterValue(true);
    }

    @Override
    public int[] getEditorIds() {
        return new int[] {
                EditorCrop.ID,
                EditorStraighten.ID,
                EditorRotate.ID,
                EditorFlip.ID
        };
    }

    public GeometryMetadata(GeometryMetadata g) {
        super("GeometryMetadata");
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

    public void set(GeometryMetadata g) {
        mScaleFactor = g.mScaleFactor;
        mRotation = g.mRotation;
        mStraightenRotation = g.mStraightenRotation;
        mCropBounds.set(g.mCropBounds);
        mPhotoBounds.set(g.mPhotoBounds);
        mFlip = g.mFlip;

        mUseCropExtras = g.mUseCropExtras;
        if (g.mCropExtras != null){
            mCropExtras = new CropExtras(g.mCropExtras);
        }
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
        RectF croppedRegion = new RectF(mCropBounds.left * scale, mCropBounds.top * scale,
                mCropBounds.right * scale, mCropBounds.bottom * scale);

        // If no crop has been applied, make sure to use the exact size values.
        // Multiplying using scale will introduce rounding errors that modify
        // even un-cropped images.
        if (mCropBounds.left == 0 && mCropBounds.right == mPhotoBounds.right) {
            croppedRegion.left = 0;
            croppedRegion.right = bitmap.getWidth();
        }
        if (mCropBounds.top == 0 && mCropBounds.bottom == mPhotoBounds.bottom) {
            croppedRegion.top = 0;
            croppedRegion.bottom = bitmap.getHeight();
        }
        return croppedRegion;
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

    private boolean compareRectF(RectF a, RectF b) {
        return ((int) a.left == (int) b.left)
                && ((int) a.right == (int) b.right)
                && ((int) a.top == (int) b.top)
                && ((int) a.bottom == (int) b.bottom);
    }

    @Override
    public boolean equals(FilterRepresentation o) {
        if (this == o)
            return true;
        if (o == null || !(o instanceof GeometryMetadata))
            return false;

        GeometryMetadata d = (GeometryMetadata) o;
        return (mScaleFactor == d.mScaleFactor
                && mRotation == d.mRotation
                && mStraightenRotation == d.mStraightenRotation
                && mFlip == d.mFlip
                && compareRectF(mCropBounds, d.mCropBounds)
                && compareRectF(mPhotoBounds, d.mPhotoBounds));
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

    protected static void concatHorizontalMatrix(Matrix m, float width) {
        m.postScale(-1, 1);
        m.postTranslate(width, 0);
    }

    protected static void concatVerticalMatrix(Matrix m, float height) {
        m.postScale(1, -1);
        m.postTranslate(0, height);
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

    public boolean hasSwitchedWidthHeight() {
        return (((int) (mRotation / 90)) % 2) != 0;
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

        // If no crop has been applied, make sure to use the exact size values.
        // Multiplying using scale will introduce rounding errors that modify
        // even un-cropped images.
        if (rc.left == 0 && rc.right == rp.right) {
            scaledCrop.left = scaledPhoto.left = 0;
            scaledCrop.right = scaledPhoto.right = bmWidth;
        }
        if (rc.top == 0 && rc.bottom == rp.bottom) {
            scaledCrop.top = scaledPhoto.top = 0;
            scaledCrop.bottom = scaledPhoto.bottom = bmHeight;
        }

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

    @Override
    public void useParametersFrom(FilterRepresentation a) {
        GeometryMetadata data = (GeometryMetadata) a;
        set(data);
    }

    @Override
    public FilterRepresentation clone() throws CloneNotSupportedException {
        GeometryMetadata representation = (GeometryMetadata) super.clone();
        representation.useParametersFrom(this);
        return representation;
    }
}
