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

import com.android.gallery3d.filtershow.cache.ImageLoader;

import java.util.HashMap;
import java.util.Vector;

public class FiltersManager {

    private static final String LOGTAG = "FiltersManager";
    private static FiltersManager gInstance = null;
    private static HashMap<Class, ImageFilter> mFilters = new HashMap<Class, ImageFilter>();

    private FiltersManager() {
        Vector<ImageFilter> filters = new Vector<ImageFilter>();
        FiltersManager.addFilters(filters);
        filters.add(new ImageFilterFx());
        filters.add(new ImageFilterBorder());
        filters.add(new ImageFilterParametricBorder());
        for (ImageFilter filter : filters) {
            mFilters.put(filter.getClass(), filter);
        }
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

    public static FiltersManager getManager() {
        if (gInstance == null) {
            gInstance = new FiltersManager();
        }
        return gInstance;
    }

    public static FilterRepresentation getRepresentation(Class c) {
        ImageFilter filter = mFilters.get(c);
        if (filter != null) {
            return filter.getDefaultRepresentation();
        }
        return null;
    }

    public static void addFilterRepresentations(Vector<FilterRepresentation> representations) {
        representations.add(getRepresentation(ImageFilterTinyPlanet.class));
        representations.add(getRepresentation(ImageFilterRedEye.class));
        representations.add(getRepresentation(ImageFilterWBalance.class));
        representations.add(getRepresentation(ImageFilterExposure.class));
        representations.add(getRepresentation(ImageFilterVignette.class));
        representations.add(getRepresentation(ImageFilterContrast.class));
        representations.add(getRepresentation(ImageFilterShadows.class));
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

    public static void addFilters(Vector<ImageFilter> filters) {
        filters.add(new ImageFilterTinyPlanet());
        filters.add(new ImageFilterRedEye());
        filters.add(new ImageFilterWBalance());
        filters.add(new ImageFilterExposure());
        filters.add(new ImageFilterVignette());
        filters.add(new ImageFilterContrast());
        filters.add(new ImageFilterShadows());
        filters.add(new ImageFilterVibrance());
        filters.add(new ImageFilterSharpen());
        filters.add(new ImageFilterCurves());
        filters.add(new ImageFilterDraw());
        filters.add(new ImageFilterHue());
        filters.add(new ImageFilterSaturated());
        filters.add(new ImageFilterBwFilter());
        filters.add(new ImageFilterNegative());
        filters.add(new ImageFilterEdge());
        filters.add(new ImageFilterKMeans());
    }

    public static void addFilters(Vector<ImageFilter> filters, ImageLoader imageLoader) {
        FiltersManager.addFilters(filters);
        filters.add(new ImageFilterDownsample(imageLoader));
        FiltersManager.getManager().addFilter(ImageFilterDownsample.class,
                new ImageFilterDownsample(imageLoader));
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
