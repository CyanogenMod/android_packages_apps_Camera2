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

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.editors.BasicEditor;

import android.graphics.Bitmap;

public class ImageFilterHue extends SimpleImageFilter {
    private ColorSpaceMatrix cmatrix = null;

    public ImageFilterHue() {
        mName = "Hue";
        cmatrix = new ColorSpaceMatrix();
    }

    public FilterRepresentation getDefaultRepresentation() {
        FilterBasicRepresentation representation = (FilterBasicRepresentation) super.getDefaultRepresentation();
        representation.setName("Hue");
        representation.setFilterClass(ImageFilterHue.class);
        representation.setMinimum(-180);
        representation.setMaximum(180);
        return representation;
    }

    @Override
    public int getButtonId() {
        return R.id.hueButton;
    }

    @Override
    public int getTextId() {
        return R.string.hue;
    }

    @Override
    public int getEditingViewId() {
        return BasicEditor.ID;
    }

    @Override
    public ImageFilter clone() throws CloneNotSupportedException {
        ImageFilterHue filter = (ImageFilterHue) super.clone();
        filter.cmatrix = new ColorSpaceMatrix(cmatrix);
        return filter;
    }

    native protected void nativeApplyFilter(Bitmap bitmap, int w, int h, float []matrix);

    @Override
    public Bitmap apply(Bitmap bitmap, float scaleFactor, boolean highQuality) {
        if (getParameters() == null) {
            return bitmap;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float value = getParameters().getValue();
        cmatrix.identity();
        cmatrix.setHue(value);

        nativeApplyFilter(bitmap, w, h, cmatrix.getMatrix());

        return bitmap;
    }
}
