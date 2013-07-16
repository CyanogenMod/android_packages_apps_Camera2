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
import android.util.JsonReader;
import android.util.JsonWriter;

import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.editors.EditorCrop;
import com.android.gallery3d.filtershow.editors.EditorFlip;
import com.android.gallery3d.filtershow.editors.EditorRotate;
import com.android.gallery3d.filtershow.editors.EditorStraighten;
import com.android.gallery3d.filtershow.filters.FilterCropRepresentation;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation.Mirror;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRotateRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRotateRepresentation.Rotation;
import com.android.gallery3d.filtershow.filters.FilterStraightenRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilterGeometry;

import java.util.HashMap;
import java.io.IOException;

public class GeometryMetadata extends FilterRepresentation {
    public static final String SERIALIZATION_NAME = "GEOM";
    public static final String SERIALIZATION_VALUE_SCALE = "scalevalue";
    private static final String LOGTAG = "GeometryMetadata";
    private float mScaleFactor = 1.0f;

    private FilterRotateRepresentation mRotationRep = new FilterRotateRepresentation();
    private FilterStraightenRepresentation mStraightenRep = new FilterStraightenRepresentation();
    private FilterCropRepresentation mCropRep = new FilterCropRepresentation();
    private FilterMirrorRepresentation mMirrorRep = new FilterMirrorRepresentation();

    public GeometryMetadata() {
        super("GeometryMetadata");
        setSerializationName(SERIALIZATION_NAME);
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
        if (!mRotationRep.isNil()) {
            return true;
        }
        if (!mStraightenRep.isNil()) {
            return true;
        }
        if (!mCropRep.isNil()) {
            return true;
        }
        if (!mMirrorRep.isNil()) {
            return true;
        }
        return false;
    }

    public void set(GeometryMetadata g) {
        mScaleFactor = g.mScaleFactor;
        mRotationRep.set(g.mRotationRep);
        mStraightenRep.set(g.mStraightenRep);
        mCropRep.set(g.mCropRep);
        mMirrorRep.set(g.mMirrorRep);
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    public int getRotation() {
        return mRotationRep.getRotation().value();
    }

    public float getStraightenRotation() {
        return mStraightenRep.getStraighten();
    }

    public RectF getPreviewCropBounds() {
        return mCropRep.getCrop();
    }

    public RectF getCropBounds(Bitmap bitmap) {
        float scale = 1.0f;
        RectF photoBounds = mCropRep.getImage();
        RectF cropBounds = mCropRep.getCrop();
        scale = GeometryMath.scale(photoBounds.width(), photoBounds.height(), bitmap.getWidth(),
                bitmap.getHeight());
        RectF croppedRegion = new RectF(cropBounds.left * scale, cropBounds.top * scale,
                cropBounds.right * scale, cropBounds.bottom * scale);

        // If no crop has been applied, make sure to use the exact size values.
        // Multiplying using scale will introduce rounding errors that modify
        // even un-cropped images.
        if (cropBounds.left == 0 && cropBounds.right == photoBounds.right) {
            croppedRegion.left = 0;
            croppedRegion.right = bitmap.getWidth();
        }
        if (cropBounds.top == 0 && cropBounds.bottom == photoBounds.bottom) {
            croppedRegion.top = 0;
            croppedRegion.bottom = bitmap.getHeight();
        }
        return croppedRegion;
    }

    public Mirror getMirrorType() {
        return mMirrorRep.getMirror();
    }

    public void setMirrorType(Mirror m) {
        mMirrorRep.setMirror(m);
    }

    public RectF getPhotoBounds() {
        return mCropRep.getImage();
    }

    public void setScaleFactor(float scale) {
        mScaleFactor = scale;
    }

    public void setRotation(int rotation) {
        Rotation r = Rotation.fromValue(rotation % 360);
        mRotationRep.setRotation((r == null) ? Rotation.ZERO : r);
    }

    public void setStraightenRotation(float straighten) {
        mStraightenRep.setStraighten(straighten);
    }

    public void setCropBounds(RectF newCropBounds) {
        mCropRep.setCrop(newCropBounds);
    }

    public void setPhotoBounds(RectF newPhotoBounds) {
        mCropRep.setImage(newPhotoBounds);
    }

    protected static void concatHorizontalMatrix(Matrix m, float width) {
        m.postScale(-1, 1);
        m.postTranslate(width, 0);
    }

    protected static void concatVerticalMatrix(Matrix m, float height) {
        m.postScale(1, -1);
        m.postTranslate(0, height);
    }

    public static void concatMirrorMatrix(Matrix m, float width, float height, Mirror type) {
        if (type == Mirror.HORIZONTAL) {
            concatHorizontalMatrix(m, width);
        } else if (type == Mirror.VERTICAL) {
            concatVerticalMatrix(m, height);
        } else if (type == Mirror.BOTH) {
            concatVerticalMatrix(m, height);
            concatHorizontalMatrix(m, width);
        }
    }

    public static Matrix getMatrixOriginalOrientation(int orientation, float originalWidth,
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

        int orientation = MasterImage.getImage().getZoomOrientation();
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
                getRotation(), getStraightenRotation(), getMirrorType(), displayCenter);
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
        return (((int) (mRotationRep.getRotation().value() / 90)) % 2) != 0;
    }

    public static Matrix buildPhotoMatrix(RectF photo, RectF crop, float rotation,
            float straighten, Mirror type) {
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
                getMirrorType(), displayCenter);
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
            float straighten, Mirror type, float[] newCenter) {
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
            float straighten, Mirror type, float[] newCenter) {
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
    public FilterRepresentation copy() {
        GeometryMetadata representation = new GeometryMetadata();
        copyAllParameters(representation);
        return representation;
    }

    @Override
    protected void copyAllParameters(FilterRepresentation representation) {
        super.copyAllParameters(representation);
        representation.useParametersFrom(this);
    }

    @Override
    public void serializeRepresentation(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name(FilterRotateRepresentation.SERIALIZATION_NAME);
        mRotationRep.serializeRepresentation(writer);
        writer.name(FilterMirrorRepresentation.SERIALIZATION_NAME);
        mMirrorRep.serializeRepresentation(writer);
        writer.name(FilterStraightenRepresentation.SERIALIZATION_NAME);
        mStraightenRep.serializeRepresentation(writer);
        writer.name(FilterCropRepresentation.SERIALIZATION_NAME);
        mCropRep.serializeRepresentation(writer);
        writer.name(SERIALIZATION_VALUE_SCALE).value(mScaleFactor);
        writer.endObject();
    }

    @Override
    public void deSerializeRepresentation(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (FilterRotateRepresentation.SERIALIZATION_NAME.equals(name)) {
                mRotationRep.deSerializeRepresentation(reader);
            } else if (FilterMirrorRepresentation.SERIALIZATION_NAME.equals(name)) {
                mMirrorRep.deSerializeRepresentation(reader);
            } else if (FilterStraightenRepresentation.SERIALIZATION_NAME.equals(name)) {
                mStraightenRep.deSerializeRepresentation(reader);
            } else if (FilterCropRepresentation.SERIALIZATION_NAME.equals(name)) {
                mCropRep.deSerializeRepresentation(reader);
            } else if (SERIALIZATION_VALUE_SCALE.equals(name)) {
                mScaleFactor = (float) reader.nextDouble();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }
}
