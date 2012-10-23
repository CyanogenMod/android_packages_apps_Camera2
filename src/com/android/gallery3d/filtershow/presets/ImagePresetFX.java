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

import com.android.gallery3d.filtershow.filters.ImageFilterFx;

public class ImagePresetFX extends ImagePreset {
    String name;
    Bitmap fxBitmap;

    @Override
    public String name() {
        return name;
    }

    public ImagePresetFX(Bitmap bitmap, String name) {
        fxBitmap = bitmap;
        this.name = name;
        setup();
    }

    @Override
    public void setup() {
        if (fxBitmap != null) {
            mFilters.add(new ImageFilterFx(fxBitmap,name));
        }
    }

}
