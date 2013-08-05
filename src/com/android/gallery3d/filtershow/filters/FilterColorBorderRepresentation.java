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

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.ImageOnlyEditor;

public class FilterColorBorderRepresentation extends FilterRepresentation {
    private int mColor;
    private int mBorderSize;
    private int mBorderRadius;

    public FilterColorBorderRepresentation(int color, int size, int radius) {
        super("ColorBorder");
        mColor = color;
        mBorderSize = size;
        mBorderRadius = radius;
        setFilterClass(ImageFilterParametricBorder.class);
        setPriority(FilterRepresentation.TYPE_BORDER);
        setTextId(R.string.borders);
        setEditorId(ImageOnlyEditor.ID);
        setShowEditingControls(false);
        setShowParameterValue(false);
        setShowUtilityPanel(false);
    }

    public String toString() {
        return "FilterBorder: " + getName();
    }

    @Override
    public FilterRepresentation clone() throws CloneNotSupportedException {
        FilterColorBorderRepresentation representation = (FilterColorBorderRepresentation) super.clone();
        representation.setName(getName());
        representation.setColor(getColor());
        representation.setBorderSize(getBorderSize());
        representation.setBorderRadius(getBorderRadius());
        return representation;
    }

    public void useParametersFrom(FilterRepresentation a) {
        if (a instanceof FilterColorBorderRepresentation) {
            FilterColorBorderRepresentation representation = (FilterColorBorderRepresentation) a;
            setName(representation.getName());
            setColor(representation.getColor());
            setBorderSize(representation.getBorderSize());
            setBorderRadius(representation.getBorderRadius());
        }
    }

    @Override
    public boolean equals(FilterRepresentation representation) {
        if (!super.equals(representation)) {
            return false;
        }
        if (representation instanceof FilterColorBorderRepresentation) {
            FilterColorBorderRepresentation border = (FilterColorBorderRepresentation) representation;
            if (border.mColor == mColor
                    && border.mBorderSize == mBorderSize
                    && border.mBorderRadius == mBorderRadius) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsMultipleInstances() {
        return true;
    }

    @Override
    public int getTextId() {
        return R.string.borders;
    }

    public int getColor() {
        return mColor;
    }

    public void setColor(int color) {
        mColor = color;
    }

    public int getBorderSize() {
        return mBorderSize;
    }

    public void setBorderSize(int borderSize) {
        mBorderSize = borderSize;
    }

    public int getBorderRadius() {
        return mBorderRadius;
    }

    public void setBorderRadius(int borderRadius) {
        mBorderRadius = borderRadius;
    }
}
