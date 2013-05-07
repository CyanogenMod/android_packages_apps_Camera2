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

package com.android.gallery3d.filtershow.presets;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.support.v8.renderscript.Allocation;
import android.util.Log;

import com.android.gallery3d.filtershow.cache.CachingPipeline;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.BaseFiltersManager;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.state.State;
import com.android.gallery3d.filtershow.state.StateAdapter;
import com.android.gallery3d.util.UsageStatistics;

import java.util.Vector;

public class ImagePreset {

    private static final String LOGTAG = "ImagePreset";

    private FilterRepresentation mBorder = null;
    public static final int QUALITY_ICON = 0;
    public static final int QUALITY_PREVIEW = 1;
    public static final int QUALITY_FINAL = 2;
    public static final int STYLE_ICON = 3;

    private ImageLoader mImageLoader = null;

    private Vector<FilterRepresentation> mFilters = new Vector<FilterRepresentation>();

    protected String mName = "Original";
    private String mHistoryName = "Original";
    protected boolean mIsFxPreset = false;

    private boolean mDoApplyGeometry = true;
    private boolean mDoApplyFilters = true;

    public final GeometryMetadata mGeoData = new GeometryMetadata();
    private boolean mPartialRendering = false;
    private Rect mPartialRenderingBounds;

    private Bitmap mPreviewImage;

    public ImagePreset() {
        setup();
    }

    public ImagePreset(String historyName) {
        setHistoryName(historyName);
        setup();
    }

    public ImagePreset(ImagePreset source, String historyName) {
        this(source);
        if (historyName != null) {
            setHistoryName(historyName);
        }
    }

    public ImagePreset(ImagePreset source) {
        try {
            if (source.mBorder != null) {
                mBorder = source.mBorder.clone();
            }
            for (int i = 0; i < source.mFilters.size(); i++) {
                FilterRepresentation representation = source.mFilters.elementAt(i).clone();
                addFilter(representation);
            }
        } catch (java.lang.CloneNotSupportedException e) {
            Log.v(LOGTAG, "Exception trying to clone: " + e);
        }
        mName = source.name();
        mHistoryName = source.name();
        mIsFxPreset = source.isFx();
        mImageLoader = source.getImageLoader();
        mPreviewImage = source.getPreviewImage();

        mGeoData.set(source.mGeoData);
    }

    public FilterRepresentation getFilterRepresentation(int position) {
        FilterRepresentation representation = null;
        try {
            representation = mFilters.elementAt(position).clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
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

    public FilterRepresentation getFilterRepresentationCopyFrom(FilterRepresentation filterRepresentation) {
        // TODO: add concept of position in the filters (to allow multiple instances)
        if (filterRepresentation == null) {
            return null;
        }
        FilterRepresentation representation = null;
        if ((mBorder != null)
                && (mBorder.getFilterClass() == filterRepresentation.getFilterClass())) {
            // TODO: instead of special casing for border, we should correctly implements "filters priority set"
            representation = mBorder;
        } else {
            int position = getPositionForRepresentation(filterRepresentation);
            if (position == -1) {
                return null;
            }
            representation = mFilters.elementAt(position);
        }
        if (representation != null) {
            try {
                representation = representation.clone();
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
            }
        }
        return representation;
    }

    public void updateFilterRepresentation(FilterRepresentation representation) {
        if (representation == null) {
            return;
        }
        synchronized (mFilters) {
            if (representation instanceof GeometryMetadata) {
                setGeometry((GeometryMetadata) representation);
            } else {
                if ((mBorder != null)
                        && (mBorder.getFilterClass() == representation.getFilterClass())) {
                    mBorder.updateTempParametersFrom(representation);
                } else {
                    int position = getPositionForRepresentation(representation);
                    if (position == -1) {
                        return;
                    }
                    FilterRepresentation old = mFilters.elementAt(position);
                    old.updateTempParametersFrom(representation);
                }
            }
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

    public synchronized GeometryMetadata getGeometry() {
        return mGeoData;
    }

    public boolean hasModifications() {
        if (mBorder != null && !mBorder.isNil()) {
            return true;
        }
        if (mGeoData.hasModifications()) {
            return true;
        }
        for (int i = 0; i < mFilters.size(); i++) {
            FilterRepresentation filter = mFilters.elementAt(i);
            if (!filter.isNil() && !filter.getName().equalsIgnoreCase("none")) {
                return true;
            }
        }
        return false;
    }

    public boolean isPanoramaSafe() {
        if (mBorder != null && !mBorder.isNil()) {
            return false;
        }
        if (mGeoData.hasModifications()) {
            return false;
        }
        for (FilterRepresentation representation : mFilters) {
            if (representation.getPriority() == FilterRepresentation.TYPE_VIGNETTE
                && !representation.isNil()) {
                return false;
            }
            if (representation.getPriority() == FilterRepresentation.TYPE_TINYPLANET
                && !representation.isNil()) {
                return false;
            }
        }
        return true;
    }

    public synchronized void setGeometry(GeometryMetadata m) {
        mGeoData.set(m);
        MasterImage.getImage().notifyGeometryChange();
    }

    private void setBorder(FilterRepresentation filter) {
        mBorder = filter;
    }

    public void resetBorder() {
        mBorder = null;
    }

    public boolean isFx() {
        return mIsFxPreset;
    }

    public void setIsFx(boolean value) {
        mIsFxPreset = value;
    }

    public void setName(String name) {
        mName = name;
        mHistoryName = name;
    }

    public void setHistoryName(String name) {
        mHistoryName = name;
    }

    public ImageLoader getImageLoader() {
        return mImageLoader;
    }

    public void setImageLoader(ImageLoader mImageLoader) {
        this.mImageLoader = mImageLoader;
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

        if (!mName.equalsIgnoreCase(preset.name())) {
            return false;
        }

        if (mDoApplyGeometry != preset.mDoApplyGeometry) {
            return false;
        }

        if (mDoApplyGeometry && !mGeoData.equals(preset.mGeoData)) {
            return false;
        }

        if (mDoApplyGeometry && mBorder != preset.mBorder) {
            return false;
        }

        if (mBorder != null && !mBorder.equals(preset.mBorder)) {
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
                if (!a.same(b)) {
                    return false;
                }
            }
        }

        return true;
    }

    public int similarUpTo(ImagePreset preset) {
        if (!mGeoData.equals(preset.mGeoData)) {
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

    public String name() {
        return mName;
    }

    public String historyName() {
        return mHistoryName;
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
        if (filterRepresentation.getPriority() == FilterRepresentation.TYPE_BORDER) {
            setBorder(null);
            setHistoryName("Remove");
            return;
        }
        for (int i = 0; i < mFilters.size(); i++) {
            if (mFilters.elementAt(i).getFilterClass() == filterRepresentation.getFilterClass()) {
                mFilters.remove(i);
                setHistoryName("Remove");
                return;
            }
        }
    }

    public void addFilter(FilterRepresentation representation) {
        if (representation instanceof GeometryMetadata) {
            setGeometry((GeometryMetadata) representation);
            return;
        }
        if (representation.getPriority() == FilterRepresentation.TYPE_BORDER) {
            setHistoryName(representation.getName());
            setBorder(representation);
        } else if (representation.getPriority() == FilterRepresentation.TYPE_FX) {
            boolean found = false;
            for (int i = 0; i < mFilters.size(); i++) {
                int type = mFilters.elementAt(i).getPriority();
                if (found) {
                    if (type != FilterRepresentation.TYPE_VIGNETTE) {
                        mFilters.remove(i);
                        continue;
                    }
                }
                if (type == FilterRepresentation.TYPE_FX) {
                    mFilters.remove(i);
                    mFilters.add(i, representation);
                    setHistoryName(representation.getName());
                    found = true;
                }
            }
            if (!found) {
                mFilters.add(representation);
                setHistoryName(representation.getName());
            }
        } else {
            mFilters.add(representation);
            setHistoryName(representation.getName());
        }
    }

    public FilterRepresentation getRepresentation(FilterRepresentation filterRepresentation) {
        if (filterRepresentation instanceof GeometryMetadata) {
            return mGeoData;
        }
        for (int i = 0; i < mFilters.size(); i++) {
            FilterRepresentation representation = mFilters.elementAt(i);
            if (representation.getFilterClass() == filterRepresentation.getFilterClass()) {
                return representation;
            }
        }
        if (mBorder != null && mBorder.getFilterClass() == filterRepresentation.getFilterClass()) {
            return mBorder;
        }
        return null;
    }

    public void setup() {
        // do nothing here
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
            mGeoData.synchronizeRepresentation();
            bitmap = environment.applyRepresentation(mGeoData, bitmap);
        }
        return bitmap;
    }

    public Bitmap applyBorder(Bitmap bitmap, FilterEnvironment environment) {
        if (mBorder != null && mDoApplyGeometry) {
            mBorder.synchronizeRepresentation();
            bitmap = environment.applyRepresentation(mBorder, bitmap);
            if (environment.getQuality() == QUALITY_FINAL) {
                UsageStatistics.onEvent(UsageStatistics.COMPONENT_EDITOR,
                        "SaveBorder", mBorder.getName(), 1);
            }
        }
        return bitmap;
    }

    public Bitmap applyFilters(Bitmap bitmap, int from, int to, FilterEnvironment environment) {
        if (mDoApplyFilters) {
            if (from < 0) {
                from = 0;
            }
            if (to == -1) {
                to = mFilters.size();
            }
            for (int i = from; i < to; i++) {
                FilterRepresentation representation = null;
                synchronized (mFilters) {
                    representation = mFilters.elementAt(i);
                    representation.synchronizeRepresentation();
                }
                bitmap = environment.applyRepresentation(representation, bitmap);
                if (environment.getQuality() == QUALITY_FINAL) {
                    UsageStatistics.onEvent(UsageStatistics.COMPONENT_EDITOR,
                            "SaveFilter", representation.getName(), 1);
                }
                if (environment.needsStop()) {
                    return bitmap;
                }
            }
        }

        return bitmap;
    }

    public void applyBorder(Allocation in, Allocation out, FilterEnvironment environment) {
        if (mBorder != null && mDoApplyGeometry) {
            mBorder.synchronizeRepresentation();
            // TODO: should keep the bitmap around
            Allocation bitmapIn = Allocation.createTyped(CachingPipeline.getRenderScriptContext(), in.getType());
            bitmapIn.copyFrom(out);
            environment.applyRepresentation(mBorder, bitmapIn, out);
        }
    }

    public void applyFilters(int from, int to, Allocation in, Allocation out, FilterEnvironment environment) {
        if (mDoApplyFilters) {
            if (from < 0) {
                from = 0;
            }
            if (to == -1) {
                to = mFilters.size();
            }
            for (int i = from; i < to; i++) {
                FilterRepresentation representation = null;
                synchronized (mFilters) {
                    representation = mFilters.elementAt(i);
                    representation.synchronizeRepresentation();
                }
                if (i > from) {
                    in.copyFrom(out);
                }
                environment.applyRepresentation(representation, in, out);
            }
        }
    }

    public boolean canDoPartialRendering() {
        if (mGeoData.hasModifications()) {
            return false;
        }
        if (mBorder != null && !mBorder.supportsPartialRendering()) {
            return false;
        }
        if (ImageLoader.getZoomOrientation() != ImageLoader.ORI_NORMAL) {
            return false;
        }
        for (int i = 0; i < mFilters.size(); i++) {
            FilterRepresentation representation = null;
            synchronized (mFilters) {
                representation = mFilters.elementAt(i);
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
        // TODO: supports Geometry representations in the state panel.
        if (false && mGeoData != null && mGeoData.hasModifications()) {
            State geo = new State("Geometry");
            geo.setFilterRepresentation(mGeoData);
            states.add(geo);
        }
        for (FilterRepresentation filter : mFilters) {
            State state = new State(filter.getName());
            state.setFilterRepresentation(filter);
            states.add(state);
        }
        if (mBorder != null) {
            State border = new State(mBorder.getName());
            border.setFilterRepresentation(mBorder);
            states.add(border);
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

    public Bitmap getPreviewImage() {
        return mPreviewImage;
    }

    public void setPreviewImage(Bitmap previewImage) {
        mPreviewImage = previewImage;
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

}
