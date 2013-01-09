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

import android.util.Log;

import com.android.gallery3d.filtershow.cache.ImageLoader;

import java.util.Vector;

public class FiltersManager {

    private static final String LOGTAG = "FiltersManager";

    public static void addFilters(Vector<ImageFilter> filters, ImageLoader imageLoader) {
        filters.add(new ImageFilterTinyPlanet());
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
        filters.add(new ImageFilterDownsample(imageLoader));
    }
}
