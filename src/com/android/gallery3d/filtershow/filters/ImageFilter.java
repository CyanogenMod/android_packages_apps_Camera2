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

import android.graphics.Bitmap;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.BasicEditor;
import com.android.gallery3d.filtershow.presets.ImagePreset;

public class ImageFilter implements Cloneable {

    private ImagePreset mImagePreset;

    protected String mName = "Original";
    private final String LOGTAG = "ImageFilter";

    public void setName(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        // do nothing here, subclasses will implement filtering here
        return bitmap;
    }

    /**
     * Called on small bitmaps to create button icons for each filter.
     * Override this to provide filter-specific button icons.
     */
    public Bitmap iconApply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        return apply(bitmap, scaleFactor, highQuality);
    }

    public ImagePreset getImagePreset() {
        return mImagePreset;
    }

    public void useRepresentation(FilterRepresentation representation) {
    }

    native protected void nativeApplyGradientFilter(Bitmap bitmap, int w, int h,
            int[] redGradient, int[] greenGradient, int[] blueGradient);

    public FilterRepresentation getDefaultRepresentation() {
        return null;
    }

}
