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

package com.android.camera.one;

import android.hardware.camera2.params.MeteringRectangle;

/**
 * Contains 3A parameters common to all camera flavors.
 * TODO: Move to GservicesHelper.
 */
public class Settings3A {

    /**
     * Width of touch AF region relative to shortest edge at 1.0 zoom.
     * Was 0.125 * longest edge prior to L release.
     */
    private static final float AF_REGION_BOX = 0.2f;

    /**
     * Width of touch metering region relative to shortest edge at 1.0 zoom.
     * Larger than {@link #AF_REGION_BOX} because exposure is sensitive and it is
     * easy to over- or underexposure if area is too small.
     */
    private static final float AE_REGION_BOX = 0.3f;

    /** Metering region weight between 0 and 1. */
    private static final float REGION_WEIGHT = 0.25f;

    /** camera2 API metering region weight. */
    private static final int CAMERA2_REGION_WEIGHT = (int)
            (((1 - REGION_WEIGHT) * MeteringRectangle.METERING_WEIGHT_MIN +
                    REGION_WEIGHT * MeteringRectangle.METERING_WEIGHT_MAX));

    /** Zero weight 3A region, to reset regions per API. */
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)
    };

    /** Duration to hold after manual tap to focus. */
    private static final int FOCUS_HOLD_MILLIS = 3000;


    public static float getAutoFocusRegionWidth() {
        return AF_REGION_BOX;
    }

    public static float getMeteringRegionWidth() {
        return AE_REGION_BOX;
    }

    public static int getCamera2MeteringWeight() {
        return CAMERA2_REGION_WEIGHT;
    }

    public static MeteringRectangle[] getZeroWeightRegion() {
        return ZERO_WEIGHT_3A_REGION;
    }

    public static int getFocusHoldMillis() {
        return FOCUS_HOLD_MILLIS;
    }
}
