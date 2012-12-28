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

import java.lang.Math;

import android.app.Activity;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptC;
import android.renderscript.Type;
import android.util.Log;

import com.android.gallery3d.R;

public class ImageFilterDrama extends ImageFilterRS {

    private static final String LOGTAG = "ImageFilterDrama";

    public ImageFilterDrama() {
        mName = "Drama";
        mMinParameter = 0;
    }

    @Override
    public int getButtonId() {
        return R.id.dramaButton;
    }

    @Override
    public int getTextId() {
        return R.string.drama;
    }

    @Override
    public int getOverlayBitmaps() {
        return R.drawable.filtershow_button_colors_snapseed;
    }

    @Override
    public int getEditingViewId() {
        return R.id.imageZoom;
    }

    @Override
    public void createFilter(android.content.res.Resources res, float scaleFactor,
            boolean highQuality) {
    }

    @Override
    public void runFilter() {
    }

}
