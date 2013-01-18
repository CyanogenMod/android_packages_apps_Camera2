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

    public static int DEFAULT_MAX_PARAMETER = 100;
    public static int DEFAULT_MIN_PARAMETER = -100;
    public static int DEFAULT_INITIAL_PARAMETER = 0;

    protected int mMaxParameter = DEFAULT_MAX_PARAMETER;
    protected int mMinParameter = DEFAULT_MIN_PARAMETER;
    protected int mPreviewParameter = mMaxParameter;
    protected int mDefaultParameter = DEFAULT_INITIAL_PARAMETER;
    protected int mParameter = DEFAULT_INITIAL_PARAMETER;
    private ImagePreset mImagePreset;

    protected String mName = "Original";
    private final String LOGTAG = "ImageFilter";
    public static final byte TYPE_BORDER = 1;
    public static final byte TYPE_FX = 2;
    public static final byte TYPE_WBALANCE = 3;
    public static final byte TYPE_VIGNETTE = 4;
    public static final byte TYPE_NORMAL = 5;
    public static final byte TYPE_TINYPLANET = 6;
    private byte filterType = TYPE_NORMAL;

    public byte getFilterType() {
        return filterType;
    }

    protected void setFilterType(byte type) {
        filterType = type;
    }

    public int getButtonId() {
        return 0;
    }

    public int getTextId() {
        return 0;
    }

    public int getOverlayBitmaps() {
        return 0;
    }

    public int getEditingViewId() {
        return BasicEditor.ID;
    }

    public boolean showEditingControls() {
        return true;
    }

    public boolean showParameterValue() {
        return true;
    }

    public boolean showUtilityPanel() {
        return true;
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilter filter = (ImageFilter) super.clone();
        filter.setName(getName());
        filter.setParameter(getParameter());
        filter.setFilterType(filterType);
        filter.mMaxParameter = mMaxParameter;
        filter.mMinParameter = mMinParameter;
        filter.mImagePreset = mImagePreset;
        filter.mDefaultParameter = mDefaultParameter;
        filter.mPreviewParameter = mPreviewParameter;
        return filter;
    }

    public void reset() {
        setParameter(mDefaultParameter);
    }

    public boolean isNil() {
        if (mParameter == mDefaultParameter) {
            return true;
        }
        return false;
    }

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
        int param = getParameter();
        setParameter(getPreviewParameter());
        bitmap = apply(bitmap, scaleFactor, highQuality);
        setParameter(param);
        return bitmap;
    }

    public int getParameter() {
        return mParameter;
    }

    public void setParameter(int value) {
        mParameter = value;
    }

    /**
     * The maximum allowed value (inclusive)
     * @return maximum value allowed as input to this filter
     */
    public int getMaxParameter() {
        return mMaxParameter;
    }

    /**
     * The parameter value to be used in previews.
     * @return parameter value to be used to preview the filter
     */
    public int getPreviewParameter() {
        return mPreviewParameter;
    }

    /**
     * The minimum allowed value (inclusive)
     * @return minimum value allowed as input to this filter
     */
    public int getMinParameter() {
        return mMinParameter;
    }

    /**
     * Returns the default value returned by this filter.
     * @return default value
     */
    public int getDefaultParameter() {
        return mDefaultParameter;
    }

    public ImagePreset getImagePreset() {
        return mImagePreset;
    }

    public void setImagePreset(ImagePreset mPreset) {
        this.mImagePreset = mPreset;
    }

    public boolean same(ImageFilter filter) {
        if (filter == null) {
            return false;
        }
        if (!filter.getName().equalsIgnoreCase(getName())) {
            return false;
        }
        return true;
    }

    native protected void nativeApplyGradientFilter(Bitmap bitmap, int w, int h,
            int[] redGradient, int[] greenGradient, int[] blueGradient);

}
