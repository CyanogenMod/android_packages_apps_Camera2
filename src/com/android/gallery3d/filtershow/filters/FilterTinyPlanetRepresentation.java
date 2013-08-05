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
import com.android.gallery3d.filtershow.editors.EditorTinyPlanet;

public class FilterTinyPlanetRepresentation extends FilterBasicRepresentation {
    private static final String LOGTAG = "FilterTinyPlanetRepresentation";
    private float mAngle = 0;

    public FilterTinyPlanetRepresentation() {
        super("TinyPlanet", 0, 50, 100);
        setShowParameterValue(true);
        setFilterClass(ImageFilterTinyPlanet.class);
        setPriority(FilterRepresentation.TYPE_TINYPLANET);
        setTextId(R.string.tinyplanet);
        setButtonId(R.id.tinyplanetButton);
        setEditorId(EditorTinyPlanet.ID);
        setMinimum(1);
    }

    @Override
    public FilterRepresentation clone() throws CloneNotSupportedException {
        FilterTinyPlanetRepresentation representation = (FilterTinyPlanetRepresentation) super
                .clone();
        representation.mAngle = mAngle;
        representation.setZoom(getZoom());
        return representation;
    }

    @Override
    public void useParametersFrom(FilterRepresentation a) {
        FilterTinyPlanetRepresentation representation = (FilterTinyPlanetRepresentation) a;
        super.useParametersFrom(a);
        mAngle = representation.mAngle;
        setZoom(representation.getZoom());
    }

    public void setAngle(float angle) {
        mAngle = angle;
    }

    public float getAngle() {
        return mAngle;
    }

    public int getZoom() {
        return getValue();
    }

    public void setZoom(int zoom) {
        setValue(zoom);
    }

    public boolean isNil() {
        // TinyPlanet always has an effect
        return false;
    }
}
