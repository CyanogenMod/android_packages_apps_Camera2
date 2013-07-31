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

package com.android.gallery3d.filtershow.pipeline;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.support.v8.renderscript.Allocation;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.BaseFiltersManager;
import com.android.gallery3d.filtershow.filters.FilterCropRepresentation;
import com.android.gallery3d.filtershow.filters.FilterFxRepresentation;
import com.android.gallery3d.filtershow.filters.FilterImageBorderRepresentation;
import com.android.gallery3d.filtershow.filters.FilterMirrorRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FilterRotateRepresentation;
import com.android.gallery3d.filtershow.filters.FilterStraightenRepresentation;
import com.android.gallery3d.filtershow.filters.FilterUserPresetRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.state.State;
import com.android.gallery3d.filtershow.state.StateAdapter;
import com.android.gallery3d.util.UsageStatistics;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Vector;

public class ImagePreset {

    private static final String LOGTAG = "ImagePreset";

    private Vector<FilterRepresentation> mFilters = new Vector<FilterRepresentation>();

    private boolean mDoApplyGeometry = true;
    private boolean mDoApplyFilters = true;

    private boolean mPartialRendering = false;
    private Rect mPartialRenderingBounds;
    private static final boolean DEBUG = false;

    public ImagePreset() {
    }

    public ImagePreset(ImagePreset source) {
        for (int i = 0; i < source.mFilters.size(); i++) {
            FilterRepresentation representation = null;
            FilterRepresentation sourceRepresentation = source.mFilters.elementAt(i);
            if (sourceRepresentation instanceof GeometryMetadata) {
                GeometryMetadata geoData = new GeometryMetadata();
                GeometryMetadata srcGeo = (GeometryMetadata) sourceRepresentation;
                geoData.set(srcGeo);
                representation = geoData;
            } else {
                representation = sourceRepresentation.copy();
            }
            mFilters.add(representation);
        }
    }

    public Vector<FilterRepresentation> getFilters() {
        return mFilters;
    }

    public FilterRepresentation getFilterRepresentation(int position) {
        FilterRepresentation representation = null;

        representation = mFilters.elementAt(position).copy();

        return representation;
    }

    public int getPositionForRepresentation(FilterRepresentation representation) {
        for (int i = 0; i < mFilters.size(); i++) {
            if (mFilters.elementAt(i).getFilterClass() == representation.getFilterClass()) {
                return i;
            }
        }
        return -1;
    }

    private FilterRepresentation getFilterRepresentationForType(int type) {
        for (int i = 0; i < mFilters.size(); i++) {
            if (mFilters.elementAt(i).getFilterType() == type) {
                return mFilters.elementAt(i);
            }
        }
        return null;
    }

    public int getPositionForType(int type) {
        for (int i = 0; i < mFilters.size(); i++) {
            if (mFilters.elementAt(i).getFilterType() == type) {
                return i;
            }
        }
        return -1;
    }

    public FilterRepresentation getFilterRepresentationCopyFrom(FilterRepresentation filterRepresentation) {
        // TODO: add concept of position in the filters (to allow multiple instances)
        if (filterRepresentation == null) {
            return null;
        }
        int position = getPositionForRepresentation(filterRepresentation);
        if (position == -1) {
            return null;
        }
        FilterRepresentation representation = mFilters.elementAt(position);
        if (representation != null) {
            representation = representation.copy();
        }
        return representation;
    }

    public void updateFilterRepresentation(FilterRepresentation representation) {
        if (representation == null) {
            return;
        }
        if (representation instanceof GeometryMetadata) {
            setGeometry((GeometryMetadata) representation);
        } else {
            int position = getPositionForRepresentation(representation);
            if (position == -1) {
                return;
            }
            FilterRepresentation old = mFilters.elementAt(position);
            old.useParametersFrom(representation);
        }
        MasterImage.getImage().invalidatePreview();
        fillImageStateAdapter(MasterImage.getImage().getState());
    }

    public void setDoApplyGeometry(boolean value) {
        mDoApplyGeometry = value;
    }

    public void setDoApplyFilters(boolean value) {
        mDoApplyFilters = value;
    }

    public boolean getDoApplyFilters() {
        return mDoApplyFilters;
    }

    public GeometryMetadata getGeometry() {
        for (FilterRepresentation representation : mFilters) {
            if (representation instanceof GeometryMetadata) {
                return (GeometryMetadata) representation;
            }
        }
        GeometryMetadata geo = new GeometryMetadata();
        mFilters.add(0, geo); // Hard Requirement for now -- Geometry ought to be first.
        return geo;
    }

    public boolean hasModifications() {
        for (int i = 0; i < mFilters.size(); i++) {
            FilterRepresentation filter = mFilters.elementAt(i);
            if (filter instanceof GeometryMetadata) {
                if (((GeometryMetadata) filter).hasModifications()) {
                    return true;
                }
            } else if (!filter.isNil()) {
                return true;
            }
        }
        return false;
    }

    public boolean isPanoramaSafe() {
        for (FilterRepresentation representation : mFilters) {
            if (representation instanceof GeometryMetadata) {
                if (((GeometryMetadata) representation).hasModifications()) {
                    return false;
                }
            }
            if (representation.getFilterType() == FilterRepresentation.TYPE_BORDER
                    && !representation.isNil()) {
                return false;
            }
            if (representation.getFilterType() == FilterRepresentation.TYPE_VIGNETTE
                    && !representation.isNil()) {
                return false;
            }
            if (representation.getFilterType() == FilterRepresentation.TYPE_TINYPLANET
                    && !representation.isNil()) {
                return false;
            }
        }
        return true;
    }

    public void setGeometry(GeometryMetadata representation) {
        GeometryMetadata geoData = getGeometry();
        if (geoData != representation) {
            geoData.set(representation);
        }
    }

    public boolean equals(ImagePreset preset) {
        if (!same(preset)) {
            return false;
        }
        if (mDoApplyFilters && preset.mDoApplyFilters) {
            for (int i = 0; i < preset.mFilters.size(); i++) {
                FilterRepresentation a = preset.mFilters.elementAt(i);
                FilterRepresentation b = mFilters.elementAt(i);
                if (!a.equals(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean same(ImagePreset preset) {
        if (preset == null) {
            return false;
        }

        if (preset.mFilters.size() != mFilters.size()) {
            return false;
        }

        if (mDoApplyGeometry != preset.mDoApplyGeometry) {
            return false;
        }

        if (mDoApplyGeometry && !getGeometry().equals(preset.getGeometry())) {
            return false;
        }

        if (mDoApplyFilters != preset.mDoApplyFilters) {
            if (mFilters.size() > 0 || preset.mFilters.size() > 0) {
                return false;
            }
        }

        if (mDoApplyFilters && preset.mDoApplyFilters) {
            for (int i = 0; i < preset.mFilters.size(); i++) {
                FilterRepresentation a = preset.mFilters.elementAt(i);
                FilterRepresentation b = mFilters.elementAt(i);
                if (a instanceof GeometryMetadata) {
                    // Note: Geometry will always be at the same place
                    continue;
                }
                if (!a.same(b)) {
                    return false;
                }
            }
        }

        return true;
    }

    public int similarUpTo(ImagePreset preset) {
        if (!getGeometry().equals(preset.getGeometry())) {
            return -1;
        }

        for (int i = 0; i < preset.mFilters.size(); i++) {
            FilterRepresentation a = preset.mFilters.elementAt(i);
            if (i < mFilters.size()) {
                FilterRepresentation b = mFilters.elementAt(i);
                if (!a.same(b)) {
                    return i;
                }
                if (!a.equals(b)) {
                    return i;
                }
            } else {
                return i;
            }
        }
        return preset.mFilters.size();
    }

    public void showFilters() {
        Log.v(LOGTAG, "\\\\\\ showFilters -- " + mFilters.size() + " filters");
        int n = 0;
        for (FilterRepresentation representation : mFilters) {
            Log.v(LOGTAG, " filter " + n + " : " + representation.toString());
            n++;
        }
        Log.v(LOGTAG, "/// showFilters -- " + mFilters.size() + " filters");
    }

    public FilterRepresentation getLastRepresentation() {
        if (mFilters.size() > 0) {
            return mFilters.lastElement();
        }
        return null;
    }

    public void removeFilter(FilterRepresentation filterRepresentation) {
        if (filterRepresentation.getFilterType() == FilterRepresentation.TYPE_BORDER) {
            for (int i = 0; i < mFilters.size();i++) {
                if (mFilters.elementAt(i).getFilterType()
                        == filterRepresentation.getFilterType()) {
                    mFilters.remove(i);
                    break;
                }
            }
        } else {
            for (int i = 0; i < mFilters.size(); i++) {
                if (mFilters.elementAt(i).getFilterClass()
                        == filterRepresentation.getFilterClass()) {
                    mFilters.remove(i);
                    break;
                }
            }
        }
    }

    // If the filter is an "None" effect or border, then just don't add this
    // filter.
    public void addFilter(FilterRepresentation representation) {
        if (representation instanceof GeometryMetadata) {
            setGeometry((GeometryMetadata) representation);
            return;
        }
        if (representation instanceof FilterUserPresetRepresentation) {
            ImagePreset preset = ((FilterUserPresetRepresentation) representation).getImagePreset();
            // user preset replace everything but geometry
            GeometryMetadata geometry = getGeometry();
            mFilters.clear();
            mFilters.add(geometry);
            for (int i = 0; i < preset.nbFilters(); i++) {
                FilterRepresentation rep = preset.getFilterRepresentation(i);
                if (!(representation instanceof GeometryMetadata)) {
                    addFilter(rep);
                }
            }
            mFilters.add(representation);
            return;
        }
        if (representation.getFilterType() == FilterRepresentation.TYPE_BORDER) {
            removeFilter(representation);
            if (!isNoneBorderFilter(representation)) {
                mFilters.add(representation);
            }
        } else if (representation.getFilterType() == FilterRepresentation.TYPE_FX) {
            boolean found = false;
            for (int i = 0; i < mFilters.size(); i++) {
                FilterRepresentation current = mFilters.elementAt(i);
                int type = current.getFilterType();
                if (found) {
                    if (type != FilterRepresentation.TYPE_VIGNETTE) {
                        mFilters.remove(i);
                        continue;
                    }
                }
                if (type == FilterRepresentation.TYPE_FX) {
                    if (current instanceof FilterUserPresetRepresentation) {
                        ImagePreset preset = ((FilterUserPresetRepresentation) current)
                                .getImagePreset();
                        // If we had an existing user preset, let's remove all the presets that
                        // were added by it
                        for (int j = 0; j < preset.nbFilters(); j++) {
                            FilterRepresentation rep = preset.getFilterRepresentation(j);
                            int pos = getPositionForRepresentation(rep);
                            if (pos != -1) {
                                mFilters.remove(pos);
                            }
                        }
                        int pos = getPositionForRepresentation(current);
                        if (pos != -1) {
                            mFilters.remove(pos);
                        } else {
                            pos = 0;
                        }
                        if (!isNoneFxFilter(representation)) {
                            mFilters.add(pos, representation);
                        }

                    } else {
                        mFilters.remove(i);
                        if (!isNoneFxFilter(representation)) {
                            mFilters.add(i, representation);
                        }
                    }
                    found = true;
                }
            }
            if (!found) {
                if (!isNoneFxFilter(representation)) {
                    mFilters.add(representation);
                }
            }
        } else {
            mFilters.add(representation);
        }
    }

    private boolean isNoneBorderFilter(FilterRepresentation representation) {
        return representation instanceof FilterImageBorderRepresentation &&
                ((FilterImageBorderRepresentation) representation).getDrawableResource() == 0;
    }

    private boolean isNoneFxFilter(FilterRepresentation representation) {
        return representation instanceof FilterFxRepresentation &&
                ((FilterFxRepresentation)representation).getNameResource() == R.string.none;
    }

    public FilterRepresentation getRepresentation(FilterRepresentation filterRepresentation) {
        for (int i = 0; i < mFilters.size(); i++) {
            FilterRepresentation representation = mFilters.elementAt(i);
            if (representation.getFilterClass() == filterRepresentation.getFilterClass()) {
                return representation;
            }
        }
        return null;
    }

    public Bitmap apply(Bitmap original, FilterEnvironment environment) {
        Bitmap bitmap = original;
        bitmap = applyFilters(bitmap, -1, -1, environment);
        return applyBorder(bitmap, environment);
    }

    public Bitmap applyGeometry(Bitmap bitmap, FilterEnvironment environment) {
        // Apply any transform -- 90 rotate, flip, straighten, crop
        // Returns a new bitmap.
        if (mDoApplyGeometry) {
            GeometryMetadata geoData = getGeometry();
            bitmap = environment.applyRepresentation(geoData, bitmap);
        }
        return bitmap;
    }

    public Bitmap applyBorder(Bitmap bitmap, FilterEnvironment environment) {
        // get the border from the list of filters.
        FilterRepresentation border = getFilterRepresentationForType(
                FilterRepresentation.TYPE_BORDER);
        if (border != null && mDoApplyGeometry) {
            bitmap = environment.applyRepresentation(border, bitmap);
            if (environment.getQuality() == FilterEnvironment.QUALITY_FINAL) {
                UsageStatistics.onEvent(UsageStatistics.COMPONENT_EDITOR,
                        "SaveBorder", border.getSerializationName(), 1);
            }
        }
        return bitmap;
    }

    public int nbFilters() {
        return mFilters.size();
    }

    public Bitmap applyFilters(Bitmap bitmap, int from, int to, FilterEnvironment environment) {
        if (mDoApplyFilters) {
            if (from < 0) {
                from = 0;
            }
            if (to == -1) {
                to = mFilters.size();
            }
            if (environment.getQuality() == FilterEnvironment.QUALITY_FINAL) {
                UsageStatistics.onEvent(UsageStatistics.COMPONENT_EDITOR,
                        "SaveFilters", "Total", to - from + 1);
            }
            for (int i = from; i < to; i++) {
                FilterRepresentation representation = mFilters.elementAt(i);
                if (representation instanceof GeometryMetadata) {
                    // skip the geometry as it's already applied.
                    continue;
                }
                if (representation.getFilterType() == FilterRepresentation.TYPE_BORDER) {
                    // for now, let's skip the border as it will be applied in applyBorder()
                    // TODO: might be worth getting rid of applyBorder.
                    continue;
                }
                bitmap = environment.applyRepresentation(representation, bitmap);
                if (environment.getQuality() == FilterEnvironment.QUALITY_FINAL) {
                    UsageStatistics.onEvent(UsageStatistics.COMPONENT_EDITOR,
                            "SaveFilter", representation.getSerializationName(), 1);
                }
                if (environment.needsStop()) {
                    return bitmap;
                }
            }
        }

        return bitmap;
    }

    public void applyBorder(Allocation in, Allocation out,
                            boolean copyOut, FilterEnvironment environment) {
        FilterRepresentation border = getFilterRepresentationForType(
                FilterRepresentation.TYPE_BORDER);
        if (border != null && mDoApplyGeometry) {
            // TODO: should keep the bitmap around
            Allocation bitmapIn = in;
            if (copyOut) {
                bitmapIn = Allocation.createTyped(
                        CachingPipeline.getRenderScriptContext(), in.getType());
                bitmapIn.copyFrom(out);
            }
            environment.applyRepresentation(border, bitmapIn, out);
        }
    }

    public void applyFilters(int from, int to, Allocation in, Allocation out,
                             FilterEnvironment environment) {
        if (mDoApplyFilters) {
            if (from < 0) {
                from = 0;
            }
            if (to == -1) {
                to = mFilters.size();
            }
            for (int i = from; i < to; i++) {
                FilterRepresentation representation = mFilters.elementAt(i);
                if (representation instanceof GeometryMetadata) {
                    // skip the geometry as it's already applied.
                    continue;
                }
                if (representation.getFilterType() == FilterRepresentation.TYPE_BORDER) {
                    // for now, let's skip the border as it will be applied in applyBorder()
                    continue;
                }
                if (i > from) {
                    in.copyFrom(out);
                }
                environment.applyRepresentation(representation, in, out);
            }
        }
    }

    public boolean canDoPartialRendering() {
        if (MasterImage.getImage().getZoomOrientation() != ImageLoader.ORI_NORMAL) {
            return false;
        }
        for (int i = 0; i < mFilters.size(); i++) {
            FilterRepresentation representation = mFilters.elementAt(i);
            if (representation instanceof GeometryMetadata
                && ((GeometryMetadata) representation).hasModifications()) {
                return false;
            }
            if (!representation.supportsPartialRendering()) {
                return false;
            }
        }
        return true;
    }

    public void fillImageStateAdapter(StateAdapter imageStateAdapter) {
        if (imageStateAdapter == null) {
            return;
        }
        Vector<State> states = new Vector<State>();
        for (FilterRepresentation filter : mFilters) {
            if (filter instanceof GeometryMetadata) {
                // TODO: supports Geometry representations in the state panel.
                continue;
            }
            if (filter instanceof FilterUserPresetRepresentation) {
                // do not show the user preset itself in the state panel
                continue;
            }
            State state = new State(filter.getName());
            state.setFilterRepresentation(filter);
            states.add(state);
        }
        imageStateAdapter.fill(states);
    }

    public void setPartialRendering(boolean partialRendering, Rect bounds) {
        mPartialRendering = partialRendering;
        mPartialRenderingBounds = bounds;
    }

    public boolean isPartialRendering() {
        return mPartialRendering;
    }

    public Rect getPartialRenderingBounds() {
        return mPartialRenderingBounds;
    }

    public Vector<ImageFilter> getUsedFilters(BaseFiltersManager filtersManager) {
        Vector<ImageFilter> usedFilters = new Vector<ImageFilter>();
        for (int i = 0; i < mFilters.size(); i++) {
            FilterRepresentation representation = mFilters.elementAt(i);
            ImageFilter filter = filtersManager.getFilterForRepresentation(representation);
            usedFilters.add(filter);
        }
        return usedFilters;
    }

    public String getJsonString(String name) {
        StringWriter swriter = new StringWriter();
        try {
            JsonWriter writer = new JsonWriter(swriter);
            writeJson(writer, name);
            writer.close();
        } catch (IOException e) {
            return null;
        }
        return swriter.toString();
    }

    public void writeJson(JsonWriter writer, String name) {
        int numFilters =  mFilters.size();
        try {
            writer.beginObject();
            for (int i = 0; i < numFilters; i++) {
                FilterRepresentation filter = mFilters.get(i);
                if (filter instanceof FilterUserPresetRepresentation) {
                    continue;
                }
                String sname = filter.getSerializationName();
                if (DEBUG) {
                    Log.v(LOGTAG, "Serialization: " + sname);
                    if (sname == null) {
                        Log.v(LOGTAG, "Serialization name null for filter: " + filter);
                    }
                }
                writer.name(sname);
                filter.serializeRepresentation(writer);
            }
            writer.endObject();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * populates preset from JSON string
     * @param filterString a JSON string
     * @return true on success if false ImagePreset is undefined
     */
    public boolean readJsonFromString(String filterString) {
        if (DEBUG) {
            Log.v(LOGTAG,"reading preset: \""+filterString+"\"");
        }
        StringReader sreader = new StringReader(filterString);
        try {
            JsonReader reader = new JsonReader(sreader);
            boolean ok = readJson(reader);
            if (!ok) {
                reader.close();
                return false;
            }
            reader.close();
        } catch (Exception e) {
            Log.e(LOGTAG,"parsing the filter parameters:",e);
            return false;
        }
        return true;
    }

    /**
     * populates preset from JSON stream
     * @param sreader a JSON string
     * @return true on success if false ImagePreset is undefined
     */
    public boolean readJson(JsonReader sreader) throws IOException {
        sreader.beginObject();

        while (sreader.hasNext()) {
            String name = sreader.nextName();
            FilterRepresentation filter = creatFilterFromName(name);
            if (filter == null) {
                Log.w(LOGTAG,"UNKNOWN FILTER! "+name);
                return false;
            }
            filter.deSerializeRepresentation(sreader);
            addFilter(filter);
        }
        sreader.endObject();
        return true;
    }

    FilterRepresentation creatFilterFromName(String name) {
        // TODO: move these to FiltersManager pattern.
        if (GeometryMetadata.SERIALIZATION_NAME.equalsIgnoreCase(name)) {
            return new GeometryMetadata();
        } else if (FilterRotateRepresentation.SERIALIZATION_NAME.equals(name)) {
            return new FilterRotateRepresentation();
        } else if (FilterMirrorRepresentation.SERIALIZATION_NAME.equals(name)) {
            return new FilterMirrorRepresentation();
        } else if (FilterStraightenRepresentation.SERIALIZATION_NAME.equals(name)) {
            return new FilterStraightenRepresentation();
        } else if (FilterCropRepresentation.SERIALIZATION_NAME.equals(name)) {
            return new FilterCropRepresentation();
        }
        FiltersManager filtersManager = FiltersManager.getManager();
        return filtersManager.createFilterFromName(name);
    }

    public void updateWith(ImagePreset preset) {
        if (preset.mFilters.size() != mFilters.size()) {
            Log.e(LOGTAG, "Updating a preset with an incompatible one");
            return;
        }
        for (int i = 0; i < mFilters.size(); i++) {
            FilterRepresentation destRepresentation = mFilters.elementAt(i);
            FilterRepresentation sourceRepresentation = preset.mFilters.elementAt(i);
            destRepresentation.useParametersFrom(sourceRepresentation);
        }
    }
}
