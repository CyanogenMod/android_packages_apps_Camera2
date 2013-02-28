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

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public abstract class BaseFiltersManager {
    protected HashMap<Class, ImageFilter> mFilters = null;

    protected void addFilters(Map<Class, ImageFilter> filters) {
        filters.put(ImageFilterTinyPlanet.class, new ImageFilterTinyPlanet());
        filters.put(ImageFilterRedEye.class, new ImageFilterRedEye());
        filters.put(ImageFilterWBalance.class, new ImageFilterWBalance());
        filters.put(ImageFilterExposure.class, new ImageFilterExposure());
        filters.put(ImageFilterVignette.class, new ImageFilterVignette());
        filters.put(ImageFilterContrast.class, new ImageFilterContrast());
        filters.put(ImageFilterShadows.class, new ImageFilterShadows());
        filters.put(ImageFilterHighlights.class, new ImageFilterHighlights());
        filters.put(ImageFilterVibrance.class, new ImageFilterVibrance());
        filters.put(ImageFilterSharpen.class, new ImageFilterSharpen());
        filters.put(ImageFilterCurves.class, new ImageFilterCurves());
        filters.put(ImageFilterDraw.class, new ImageFilterDraw());
        filters.put(ImageFilterHue.class, new ImageFilterHue());
        filters.put(ImageFilterSaturated.class, new ImageFilterSaturated());
        filters.put(ImageFilterBwFilter.class, new ImageFilterBwFilter());
        filters.put(ImageFilterNegative.class, new ImageFilterNegative());
        filters.put(ImageFilterEdge.class, new ImageFilterEdge());
        filters.put(ImageFilterKMeans.class, new ImageFilterKMeans());
        filters.put(ImageFilterFx.class, new ImageFilterFx());
        filters.put(ImageFilterBorder.class, new ImageFilterBorder());
        filters.put(ImageFilterParametricBorder.class, new ImageFilterParametricBorder());
        filters.put(ImageFilterGeometry.class, new ImageFilterGeometry());

    }

    public ImageFilter getFilter(Class c) {
        return mFilters.get(c);
    }

    public ImageFilter getFilterForRepresentation(FilterRepresentation representation) {
        return mFilters.get(representation.getFilterClass());
    }

    public void addFilter(Class filterClass, ImageFilter filter) {
        mFilters.put(filterClass, filter);
    }

    public FilterRepresentation getRepresentation(Class c) {
        ImageFilter filter = mFilters.get(c);
        if (filter != null) {
            return filter.getDefaultRepresentation();
        }
        return null;
    }

    public void addLooks(Vector<FilterRepresentation> representations) {
        // Override
    }

    public void addEffects(Vector<FilterRepresentation> representations) {
        representations.add(getRepresentation(ImageFilterTinyPlanet.class));
        representations.add(getRepresentation(ImageFilterRedEye.class));
        representations.add(getRepresentation(ImageFilterWBalance.class));
        representations.add(getRepresentation(ImageFilterExposure.class));
        representations.add(getRepresentation(ImageFilterVignette.class));
        representations.add(getRepresentation(ImageFilterContrast.class));
        representations.add(getRepresentation(ImageFilterShadows.class));
        representations.add(getRepresentation(ImageFilterHighlights.class));
        representations.add(getRepresentation(ImageFilterVibrance.class));
        representations.add(getRepresentation(ImageFilterSharpen.class));
        representations.add(getRepresentation(ImageFilterCurves.class));
        representations.add(getRepresentation(ImageFilterDraw.class));
        representations.add(getRepresentation(ImageFilterHue.class));
        representations.add(getRepresentation(ImageFilterSaturated.class));
        representations.add(getRepresentation(ImageFilterBwFilter.class));
        representations.add(getRepresentation(ImageFilterNegative.class));
        representations.add(getRepresentation(ImageFilterEdge.class));
        representations.add(getRepresentation(ImageFilterKMeans.class));
    }

    public void resetBitmapsRS() {
        for (Class c : mFilters.keySet()) {
            ImageFilter filter = mFilters.get(c);
            if (filter instanceof ImageFilterRS) {
                ImageFilterRS filterRS = (ImageFilterRS) filter;
                filterRS.resetBitmap();
            }
        }
    }
}
