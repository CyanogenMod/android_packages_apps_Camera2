/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.one.v2.autofocus;

import android.graphics.Rect;
import android.hardware.camera2.params.MeteringRectangle;

import com.google.common.base.Supplier;

/**
 * Computes the current AF metering rectangles based on the current metering
 * parameters and crop region.
 */
class AFMeteringRegion implements Supplier<MeteringRectangle[]> {
    private final Supplier<MeteringParameters> mMeteringParameters;
    private final Supplier<Rect> mCropRegion;

    public AFMeteringRegion(Supplier<MeteringParameters> meteringParameters,
            Supplier<Rect> cropRegion) {
        mMeteringParameters = meteringParameters;
        mCropRegion = cropRegion;
    }

    @Override
    public MeteringRectangle[] get() {
        return mMeteringParameters.get().getAFRegions(mCropRegion.get());
    }
}
