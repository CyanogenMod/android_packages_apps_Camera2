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

import android.graphics.Bitmap;
import com.android.gallery3d.app.Log;
import com.android.gallery3d.filtershow.editors.ImageOnlyEditor;

public class FilterFxRepresentation extends FilterRepresentation {
    private static final String LOGTAG = "FilterFxRepresentation";
    // TODO: When implementing serialization, we should find a unique way of
    // specifying bitmaps / names (the resource IDs being random)
    private int mBitmapResource = 0;
    private int mNameResource = 0;

    public FilterFxRepresentation(String name, int bitmapResource, int nameResource) {
        super(name);
        mBitmapResource = bitmapResource;
        mNameResource = nameResource;
        setFilterClass(ImageFilterFx.class);
        setPriority(FilterRepresentation.TYPE_FX);
        setTextId(nameResource);
        setEditorId(ImageOnlyEditor.ID);
        setShowEditingControls(false);
        setShowParameterValue(false);
        setShowUtilityPanel(false);
        setSupportsPartialRendering(true);
    }

    public String toString() {
        return "FilterFx: " + hashCode() + " : " + getName() + " bitmap rsc: " + mBitmapResource;
    }

    @Override
    public synchronized FilterRepresentation clone() throws CloneNotSupportedException {
        FilterFxRepresentation representation = (FilterFxRepresentation) super.clone();
        representation.setName(getName());
        representation.setBitmapResource(getBitmapResource());
        representation.setNameResource(getNameResource());
        return representation;
    }

    public synchronized void useParametersFrom(FilterRepresentation a) {
        if (a instanceof FilterFxRepresentation) {
            FilterFxRepresentation representation = (FilterFxRepresentation) a;
            setName(representation.getName());
            setBitmapResource(representation.getBitmapResource());
            setNameResource(representation.getNameResource());
        }
    }

    @Override
    public boolean equals(FilterRepresentation representation) {
        if (!super.equals(representation)) {
            return false;
        }
        if (representation instanceof FilterFxRepresentation) {
            FilterFxRepresentation fx = (FilterFxRepresentation) representation;
            if (fx.mNameResource == mNameResource
                    && fx.mBitmapResource == mBitmapResource) {
                return true;
            }
        }
        return false;
    }

    public boolean same(FilterRepresentation representation) {
        if (!super.same(representation)) {
            return false;
        }
        return equals(representation);
    }

    public boolean allowsMultipleInstances() {
        return true;
    }

    public int getNameResource() {
        return mNameResource;
    }

    public void setNameResource(int nameResource) {
        mNameResource = nameResource;
    }

    public int getBitmapResource() {
        return mBitmapResource;
    }

    public void setBitmapResource(int bitmapResource) {
        mBitmapResource = bitmapResource;
    }
}
