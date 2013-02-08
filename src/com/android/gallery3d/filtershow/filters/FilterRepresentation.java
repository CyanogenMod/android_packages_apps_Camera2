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

import com.android.gallery3d.app.Log;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.util.HashMap;

public class FilterRepresentation implements Cloneable {
    private static final String LOGTAG = "FilterRepresentation";
    private String mName;
    private int mPriority;
    private ImagePreset mPreset;
    private boolean mShowParameterValue;
    private Class mFilterClass;

    public static String DEFAULT = "Default";

    public FilterRepresentation(String name) {
        mName = name;
    }

    @Override
    public FilterRepresentation clone() throws CloneNotSupportedException {
        FilterRepresentation representation = (FilterRepresentation) super.clone();
        representation.setName(getName());
        representation.setPriority(getPriority());
        representation.setFilterClass(getFilterClass());
        Log.v(LOGTAG, "cloning from <" + this + "> to <" + representation + ">");
        return representation;
    }

    public boolean equals(FilterRepresentation representation) {
        if (representation.mFilterClass == representation.mFilterClass
                && representation.mName.equalsIgnoreCase(mName)
                && representation.mPriority == mPriority
                && representation.mShowParameterValue == mShowParameterValue) {
            return true;
        }
        return false;
    }

    public String toString() {
        return mName;
    }

    public void setName(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void setPriority(int priority) {
        mPriority = priority;
    }

    public int getPriority() {
        return mPriority;
    }

    public void setImagePreset(ImagePreset preset) {
        mPreset = preset;
    }

    public boolean isNil() {
        return false;
    }

    public void useParametersFrom(FilterRepresentation a) {
    }

    public void setShowParameterValue(boolean showParameterValue) {
        mShowParameterValue = showParameterValue;
    }

    public boolean showParameterValue() {
        return mShowParameterValue;
    }

    public Class getFilterClass() {
        return mFilterClass;
    }

    public void setFilterClass(Class filterClass) {
        mFilterClass = filterClass;
    }

    public boolean same(FilterRepresentation b) {
        if (b == null) {
            return false;
        }
        return getFilterClass() == b.getFilterClass();
    }
}
