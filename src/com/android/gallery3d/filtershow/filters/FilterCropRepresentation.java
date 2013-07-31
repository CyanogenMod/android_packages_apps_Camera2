/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.filters;

import android.graphics.RectF;
import android.util.JsonReader;
import android.util.JsonWriter;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorCrop;

import java.io.IOException;

public class FilterCropRepresentation extends FilterRepresentation {
    public static final String SERIALIZATION_NAME = "CROP";
    public static final String[] BOUNDS = {
            "C0", "C1", "C2", "C3", "I0", "I1", "I2", "I3"
    };
    private static final String TAG = FilterCropRepresentation.class.getSimpleName();

    RectF mCrop = new RectF();
    RectF mImage = new RectF();

    public FilterCropRepresentation(RectF crop, RectF image) {
        super(FilterCropRepresentation.class.getSimpleName());
        setSerializationName(SERIALIZATION_NAME);
        setShowParameterValue(true);
        setFilterClass(FilterCropRepresentation.class);
        setFilterType(FilterRepresentation.TYPE_GEOMETRY);
        setTextId(R.string.crop);
        setEditorId(EditorCrop.ID);
        setCrop(crop);
        setImage(image);
    }

    public FilterCropRepresentation(FilterCropRepresentation m) {
        this(m.getCrop(), m.getImage());
    }

    public FilterCropRepresentation() {
        this(new RectF(), new RectF());
    }

    public void set(FilterCropRepresentation r) {
        mCrop.set(r.mCrop);
        mImage.set(r.mImage);
    }

    @Override
    public boolean equals(FilterRepresentation rep) {
        if (!(rep instanceof FilterCropRepresentation)) {
            return false;
        }
        FilterCropRepresentation crop = (FilterCropRepresentation) rep;
        if (mCrop.bottom != crop.mCrop.bottom
            || mCrop.left != crop.mCrop.left
            || mCrop.right != crop.mCrop.right
            || mCrop.top != crop.mCrop.top
            || mImage.bottom != crop.mImage.bottom
            || mImage.left != crop.mImage.left
            || mImage.right != crop.mImage.right
            || mImage.top != crop.mImage.top) {
            return false;
        }
        return true;
    }

    public RectF getCrop() {
        return new RectF(mCrop);
    }

    public void getCrop(RectF r) {
        r.set(mCrop);
    }

    public void setCrop(RectF crop) {
        if (crop == null) {
            throw new IllegalArgumentException("Argument to setCrop is null");
        }
        mCrop.set(crop);
    }

    public RectF getImage() {
        return new RectF(mImage);
    }

    public void getImage(RectF r) {
        r.set(mImage);
    }

    public void setImage(RectF image) {
        if (image == null) {
            throw new IllegalArgumentException("Argument to setImage is null");
        }
        mImage.set(image);
    }

    @Override
    public boolean allowsSingleInstanceOnly() {
        return true;
    }

    @Override
    public FilterRepresentation copy(){
        return new FilterCropRepresentation(this);
    }

    @Override
    protected void copyAllParameters(FilterRepresentation representation) {
        if (!(representation instanceof FilterCropRepresentation)) {
            throw new IllegalArgumentException("calling copyAllParameters with incompatible types!");
        }
        super.copyAllParameters(representation);
        representation.useParametersFrom(this);
    }

    @Override
    public void useParametersFrom(FilterRepresentation a) {
        if (!(a instanceof FilterCropRepresentation)) {
            throw new IllegalArgumentException("calling useParametersFrom with incompatible types!");
        }
        setCrop(((FilterCropRepresentation) a).mCrop);
        setImage(((FilterCropRepresentation) a).mImage);
    }

    @Override
    public boolean isNil() {
        return mCrop.equals(mImage);
    }

    @Override
    public void serializeRepresentation(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name(BOUNDS[0]).value(mCrop.left);
        writer.name(BOUNDS[1]).value(mCrop.top);
        writer.name(BOUNDS[2]).value(mCrop.right);
        writer.name(BOUNDS[3]).value(mCrop.bottom);
        writer.name(BOUNDS[4]).value(mImage.left);
        writer.name(BOUNDS[5]).value(mImage.top);
        writer.name(BOUNDS[6]).value(mImage.right);
        writer.name(BOUNDS[7]).value(mImage.bottom);
        writer.endObject();
    }

    @Override
    public void deSerializeRepresentation(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (BOUNDS[0].equals(name)) {
                mCrop.left = (float) reader.nextDouble();
            } else if (BOUNDS[1].equals(name)) {
                mCrop.top = (float) reader.nextDouble();
            } else if (BOUNDS[2].equals(name)) {
                mCrop.right = (float) reader.nextDouble();
            } else if (BOUNDS[3].equals(name)) {
                mCrop.bottom = (float) reader.nextDouble();
            } else if (BOUNDS[4].equals(name)) {
                mImage.left = (float) reader.nextDouble();
            } else if (BOUNDS[5].equals(name)) {
                mImage.top = (float) reader.nextDouble();
            } else if (BOUNDS[6].equals(name)) {
                mImage.right = (float) reader.nextDouble();
            } else if (BOUNDS[7].equals(name)) {
                mImage.bottom = (float) reader.nextDouble();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }
}
