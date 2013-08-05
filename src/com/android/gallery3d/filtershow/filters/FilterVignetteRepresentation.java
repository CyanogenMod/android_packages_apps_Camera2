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

package com.android.gallery3d.filtershow.filters;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.EditorVignette;
import com.android.gallery3d.filtershow.imageshow.Oval;

public class FilterVignetteRepresentation extends FilterBasicRepresentation implements Oval {
    private static final String LOGTAG = "FilterVignetteRepresentation";
    private float mCenterX = Float.NaN;
    private float mCenterY;
    private float mRadiusX = Float.NaN;
    private float mRadiusY;

    public FilterVignetteRepresentation() {
        super("Vignette", -100, 50, 100);
        setShowParameterValue(true);
        setPriority(FilterRepresentation.TYPE_VIGNETTE);
        setTextId(R.string.vignette);
        setButtonId(R.id.vignetteEditor);
        setEditorId(EditorVignette.ID);
        setName("Vignette");
        setFilterClass(ImageFilterVignette.class);

        setMinimum(-100);
        setMaximum(100);
        setDefaultValue(0);
    }

    @Override
    public void useParametersFrom(FilterRepresentation a) {
        super.useParametersFrom(a);
        mCenterX = ((FilterVignetteRepresentation) a).mCenterX;
        mCenterY = ((FilterVignetteRepresentation) a).mCenterY;
        mRadiusX = ((FilterVignetteRepresentation) a).mRadiusX;
        mRadiusY = ((FilterVignetteRepresentation) a).mRadiusY;
    }

    @Override
    public FilterRepresentation clone() throws CloneNotSupportedException {
        FilterVignetteRepresentation representation = (FilterVignetteRepresentation) super
                .clone();
        representation.mCenterX = mCenterX;
        representation.mCenterY = mCenterY;

        return representation;
    }

    @Override
    public void setCenter(float centerX, float centerY) {
        mCenterX = centerX;
        mCenterY = centerY;
    }

    @Override
    public float getCenterX() {
        return mCenterX;
    }

    @Override
    public float getCenterY() {
        return mCenterY;
    }

    @Override
    public void setRadius(float radiusX, float radiusY) {
        mRadiusX = radiusX;
        mRadiusY = radiusY;
    }

    @Override
    public void setRadiusX(float radiusX) {
        mRadiusX = radiusX;
    }

    @Override
    public void setRadiusY(float radiusY) {
        mRadiusY = radiusY;
    }

    @Override
    public float getRadiusX() {
        return mRadiusX;
    }

    @Override
    public float getRadiusY() {
        return mRadiusY;
    }

    public boolean isCenterSet() {
        return mCenterX != Float.NaN;
    }

    @Override
    public boolean isNil() {
        return getValue() == 0;
    }
}
