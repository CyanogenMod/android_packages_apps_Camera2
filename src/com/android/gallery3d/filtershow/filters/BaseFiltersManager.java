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

import android.content.Context;
import android.content.res.Resources;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.presets.ImagePreset;

import java.util.HashMap;
import java.util.Vector;

public abstract class BaseFiltersManager {
    protected HashMap<Class, ImageFilter> mFilters = null;

    protected void init() {
        mFilters = new HashMap<Class, ImageFilter>();
        Vector<Class> filters = new Vector<Class>();
        addFilterClasses(filters);
        for (Class filterClass : filters) {
            try {
                Object filterInstance = filterClass.newInstance();
                if (filterInstance instanceof ImageFilter) {
                    mFilters.put(filterClass, (ImageFilter) filterInstance);
                }
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
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

    public FilterRepresentation getRepresentation(Class c) {
        ImageFilter filter = mFilters.get(c);
        if (filter != null) {
            return filter.getDefaultRepresentation();
        }
        return null;
    }

    public void freeFilterResources(ImagePreset preset) {
        if (preset == null) {
            return;
        }
        Vector<ImageFilter> usedFilters = preset.getUsedFilters(this);
        for (Class c : mFilters.keySet()) {
            ImageFilter filter = mFilters.get(c);
            if (!usedFilters.contains(filter)) {
                filter.freeResources();
            }
        }
    }

    public void freeRSFilterScripts() {
        for (Class c : mFilters.keySet()) {
            ImageFilter filter = mFilters.get(c);
            if (filter != null && filter instanceof ImageFilterRS) {
                ((ImageFilterRS) filter).resetScripts();
            }
        }
    }

    protected void addFilterClasses(Vector<Class> filters) {
        filters.add(ImageFilterTinyPlanet.class);
        //filters.add(ImageFilterRedEye.class);
        filters.add(ImageFilterWBalance.class);
        filters.add(ImageFilterExposure.class);
        filters.add(ImageFilterVignette.class);
        filters.add(ImageFilterContrast.class);
        filters.add(ImageFilterShadows.class);
        filters.add(ImageFilterHighlights.class);
        filters.add(ImageFilterVibrance.class);
        filters.add(ImageFilterSharpen.class);
        filters.add(ImageFilterCurves.class);
        // filters.add(ImageFilterDraw.class);
        filters.add(ImageFilterHue.class);
        filters.add(ImageFilterSaturated.class);
        filters.add(ImageFilterBwFilter.class);
        filters.add(ImageFilterNegative.class);
        filters.add(ImageFilterEdge.class);
        filters.add(ImageFilterKMeans.class);
        filters.add(ImageFilterFx.class);
        filters.add(ImageFilterBorder.class);
        filters.add(ImageFilterParametricBorder.class);
        filters.add(ImageFilterGeometry.class);
    }

    public void addBorders(Context context, Vector<FilterRepresentation> representations) {

    }

    public void addLooks(Context context, Vector<FilterRepresentation> representations) {
        int[] drawid = {
                R.drawable.filtershow_fx_0005_punch,
                R.drawable.filtershow_fx_0000_vintage,
                R.drawable.filtershow_fx_0004_bw_contrast,
                R.drawable.filtershow_fx_0002_bleach,
                R.drawable.filtershow_fx_0001_instant,
                R.drawable.filtershow_fx_0007_washout,
                R.drawable.filtershow_fx_0003_blue_crush,
                R.drawable.filtershow_fx_0008_washout_color,
                R.drawable.filtershow_fx_0006_x_process
        };

        int[] fxNameid = {
                R.string.ffx_punch,
                R.string.ffx_vintage,
                R.string.ffx_bw_contrast,
                R.string.ffx_bleach,
                R.string.ffx_instant,
                R.string.ffx_washout,
                R.string.ffx_blue_crush,
                R.string.ffx_washout_color,
                R.string.ffx_x_process
        };

        for (int i = 0; i < drawid.length; i++) {
            FilterFxRepresentation fx = new FilterFxRepresentation(
                    context.getString(fxNameid[i]), drawid[i], fxNameid[i]);
            representations.add(fx);
        }
    }

    public void addEffects(Vector<FilterRepresentation> representations) {
        representations.add(getRepresentation(ImageFilterTinyPlanet.class));
        representations.add(getRepresentation(ImageFilterWBalance.class));
        representations.add(getRepresentation(ImageFilterExposure.class));
        representations.add(getRepresentation(ImageFilterVignette.class));
        representations.add(getRepresentation(ImageFilterContrast.class));
        representations.add(getRepresentation(ImageFilterShadows.class));
        representations.add(getRepresentation(ImageFilterHighlights.class));
        representations.add(getRepresentation(ImageFilterVibrance.class));
        representations.add(getRepresentation(ImageFilterSharpen.class));
        representations.add(getRepresentation(ImageFilterCurves.class));
        representations.add(getRepresentation(ImageFilterHue.class));
        representations.add(getRepresentation(ImageFilterSaturated.class));
        representations.add(getRepresentation(ImageFilterBwFilter.class));
        representations.add(getRepresentation(ImageFilterNegative.class));
        representations.add(getRepresentation(ImageFilterEdge.class));
        representations.add(getRepresentation(ImageFilterKMeans.class));
    }

    public void addTools(Vector<FilterRepresentation> representations) {
        //representations.add(getRepresentation(ImageFilterRedEye.class));
        // representations.add(getRepresentation(ImageFilterDraw.class));
    }

    public void setFilterResources(Resources resources) {
        ImageFilterBorder filterBorder = (ImageFilterBorder) getFilter(ImageFilterBorder.class);
        filterBorder.setResources(resources);
        ImageFilterFx filterFx = (ImageFilterFx) getFilter(ImageFilterFx.class);
        filterFx.setResources(resources);
    }
}
