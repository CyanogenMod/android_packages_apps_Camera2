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
import android.util.Log;

import com.android.gallery3d.filtershow.ImageStateAdapter;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.ImageFilter;
import com.android.gallery3d.filtershow.imageshow.GeometryMetadata;
import com.android.gallery3d.filtershow.imageshow.ImageShow;

import java.util.Vector;

public class ImagePreset {

    private static final String LOGTAG = "ImagePreset";

    private ImageShow mEndPoint = null;
    private ImageFilter mImageBorder = null;
    private float mScaleFactor = 1.0f;
    private boolean mIsHighQuality = false;
    private ImageLoader mImageLoader = null;

    protected Vector<ImageFilter> mFilters = new Vector<ImageFilter>();
    protected String mName = "Original";
    private String mHistoryName = "Original";
    protected boolean mIsFxPreset = false;

    private boolean mDoApplyGeometry = true;
    private boolean mDoApplyFilters = true;

    public final GeometryMetadata mGeoData = new GeometryMetadata();

    enum FullRotate {
        ZERO, NINETY, HUNDRED_EIGHTY, TWO_HUNDRED_SEVENTY
    }

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
            if (source.mImageBorder != null) {
                mImageBorder = source.mImageBorder.clone();
            }
            for (int i = 0; i < source.mFilters.size(); i++) {
                ImageFilter filter = source.mFilters.elementAt(i).clone();
                filter.setImagePreset(this);
                add(filter);
            }
        } catch (java.lang.CloneNotSupportedException e) {
            Log.v(LOGTAG, "Exception trying to clone: " + e);
        }
        mName = source.name();
        mHistoryName = source.name();
        mIsFxPreset = source.isFx();
        mImageLoader = source.getImageLoader();

        mGeoData.set(source.mGeoData);
    }

    public void setDoApplyGeometry(boolean value) {
        mDoApplyGeometry = value;
    }

    public void setDoApplyFilters(boolean value) {
        mDoApplyFilters = value;
    }

    public boolean hasModifications() {
        if (mImageBorder != null && !mImageBorder.isNil()) {
            return true;
        }
        if (mGeoData.hasModifications()) {
            return true;
        }
        for (int i = 0; i < mFilters.size(); i++) {
            ImageFilter filter = mFilters.elementAt(i);
            if (!filter.isNil()) {
                return true;
            }
        }
        return false;
    }

    public boolean isPanoramaSafe() {
        if (mImageBorder != null && !mImageBorder.isNil()) {
            return false;
        }
        if (mGeoData.hasModifications()) {
            return false;
        }
        for (ImageFilter filter : mFilters) {
            if (filter.getFilterType() == ImageFilter.TYPE_VIGNETTE
                    && !filter.isNil()) {
                return false;
            }
            if (filter.getFilterType() == ImageFilter.TYPE_TINYPLANET
                    && !filter.isNil()) {
                return false;
            }
        }
        return true;
    }

    public void setGeometry(GeometryMetadata m) {
        mGeoData.set(m);
    }

    private void setBorder(ImageFilter filter) {
        mImageBorder = filter;
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

    public boolean same(ImagePreset preset) {
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

        if (mDoApplyGeometry && mImageBorder != preset.mImageBorder) {
            return false;
        }

        if (mImageBorder != null && !mImageBorder.same(preset.mImageBorder)) {
            return false;
        }

        if (mDoApplyFilters != preset.mDoApplyFilters) {
            if (mFilters.size() > 0 || preset.mFilters.size() > 0) {
                return false;
            }
        }

        if (mDoApplyFilters && preset.mDoApplyFilters) {
            for (int i = 0; i < preset.mFilters.size(); i++) {
                ImageFilter a = preset.mFilters.elementAt(i);
                ImageFilter b = mFilters.elementAt(i);
                if (!a.same(b)) {
                    return false;
                }
            }
        }
        return true;
    }

    public String name() {
        return mName;
    }

    public String historyName() {
        return mHistoryName;
    }

    public void add(ImageFilter filter) {

        if (filter.getFilterType() == ImageFilter.TYPE_BORDER) {
            setHistoryName(filter.getName());
            setBorder(filter);
        } else if (filter.getFilterType() == ImageFilter.TYPE_FX) {
            boolean found = false;
            for (int i = 0; i < mFilters.size(); i++) {
                byte type = mFilters.get(i).getFilterType();
                if (found) {
                    if (type != ImageFilter.TYPE_VIGNETTE) {
                        mFilters.remove(i);
                        continue;
                    }
                }
                if (type == ImageFilter.TYPE_FX) {
                    mFilters.remove(i);
                    mFilters.add(i, filter);
                    setHistoryName(filter.getName());
                    found = true;
                }
            }
            if (!found) {
                mFilters.add(filter);
                setHistoryName(filter.getName());
            }
        } else {
            mFilters.add(filter);
            setHistoryName(filter.getName());
        }
        filter.setImagePreset(this);
    }

    public void remove(String filterName) {
        ImageFilter filter = getFilter(filterName);
        if (filter != null) {
            mFilters.remove(filter);
        }
    }

    public int getCount() {
        return mFilters.size();
    }

    public ImageFilter getFilter(String name) {
        for (int i = 0; i < mFilters.size(); i++) {
            ImageFilter filter = mFilters.elementAt(i);
            if (filter.getName().equalsIgnoreCase(name)) {
                return filter;
            }
        }
        return null;
    }

    public void setup() {
        // do nothing here
    }

    public void setEndpoint(ImageShow image) {
        mEndPoint = image;
    }

    public Bitmap apply(Bitmap original) {
        // First we apply any transform -- 90 rotate, flip, straighten, crop
        Bitmap bitmap = original;

        if (mDoApplyGeometry) {
            bitmap = mGeoData.apply(original, mScaleFactor, mIsHighQuality);
        }

        if (mDoApplyFilters) {
            for (int i = 0; i < mFilters.size(); i++) {
                ImageFilter filter = mFilters.elementAt(i);
                bitmap = filter.apply(bitmap, mScaleFactor, mIsHighQuality);
            }
        }

        if (mImageBorder != null && mDoApplyGeometry) {
            bitmap = mImageBorder.apply(bitmap, mScaleFactor, mIsHighQuality);
        }

        if (mEndPoint != null) {
            mEndPoint.updateFilteredImage(bitmap);
        }

        return bitmap;
    }

    public void fillImageStateAdapter(ImageStateAdapter imageStateAdapter) {
        if (imageStateAdapter == null) {
            return;
        }
        imageStateAdapter.clear();
        imageStateAdapter.addAll(mFilters);
        imageStateAdapter.notifyDataSetChanged();
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    public boolean isHighQuality() {
        return mIsHighQuality;
    }

    public void setIsHighQuality(boolean value) {
        mIsHighQuality = value;
    }

    public void setScaleFactor(float value) {
        mScaleFactor = value;
    }
}
