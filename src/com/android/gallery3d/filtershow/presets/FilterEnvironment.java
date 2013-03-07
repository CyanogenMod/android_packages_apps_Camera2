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

package com.android.gallery3d.filtershow.presets;

import android.graphics.Bitmap;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.filters.ImageFilter;

public class FilterEnvironment {
    private ImagePreset mImagePreset;
    private float mScaleFactor;
    private int mQuality;
    private FiltersManager mFiltersManager;

    public void setImagePreset(ImagePreset imagePreset) {
        mImagePreset = imagePreset;
    }

    public ImagePreset getImagePreset() {
        return mImagePreset;
    }

    public void setScaleFactor(float scaleFactor) {
        mScaleFactor = scaleFactor;
    }

    public float getScaleFactor() {
        return mScaleFactor;
    }

    public void setQuality(int quality) {
        mQuality = quality;
    }

    public int getQuality() {
        return mQuality;
    }

    public void setFiltersManager(FiltersManager filtersManager) {
        mFiltersManager = filtersManager;
    }

    public FiltersManager getFiltersManager() {
        return mFiltersManager;
    }

    public Bitmap applyRepresentation(FilterRepresentation representation, Bitmap bitmap) {
        ImageFilter filter = mFiltersManager.getFilterForRepresentation(representation);
        filter.useRepresentation(representation);
        filter.setImagePreset(mImagePreset);
        return filter.apply(bitmap, mScaleFactor, mQuality);
    }
}
