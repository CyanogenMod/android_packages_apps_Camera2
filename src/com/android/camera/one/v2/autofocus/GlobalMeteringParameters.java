/*
 * Copyright (C) 2015 The Android Open Source Project
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

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
final class GlobalMeteringParameters implements MeteringParameters {
    /** Zero weight 3A region, to reset regions per API. */
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[] {
            new MeteringRectangle(0, 0, 0, 0, 0)
    };

    private static class Singleton {
        private static final GlobalMeteringParameters INSTANCE = new GlobalMeteringParameters();
    }

    public static MeteringParameters create() {
        return Singleton.INSTANCE;
    }

    @Override
    public MeteringRectangle[] getAFRegions(Rect cropRegion) {
        return ZERO_WEIGHT_3A_REGION;
    }

    @Override
    public MeteringRectangle[] getAERegions(Rect cropRegion) {
        return ZERO_WEIGHT_3A_REGION;
    }
}
