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

package com.android.camera.ui.focus;

import android.annotation.TargetApi;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build.VERSION_CODES;

import com.android.camera.ui.motion.LinearScale;

/**
 * Compute diopter range scale to convert lens focus distances into
 * a ratio value.
 */
@TargetApi(VERSION_CODES.LOLLIPOP)
public class LensRangeCalculator {

    /**
     * A NoOp linear scale for computing diopter values will always return 0
     */
    public static LinearScale getNoOp() {
        return new LinearScale(0, 0, 0, 0);
    }

    /**
     * Compute the focus range from the camera characteristics and build
     * a linear scale model that maps a focus distance to a ratio between
     * the min and max range.
     */
    public static LinearScale getDiopterToRatioCalculator(CameraCharacteristics characteristics) {
        // From the android documentation:
        //
        // 0.0f represents farthest focus, and LENS_INFO_MINIMUM_FOCUS_DISTANCE
        // represents the nearest focus the device can achieve.
        //
        // Example:
        //
        // Infinity    Hyperfocal                 Minimum   Camera
        //  <----------|-----------------------------|         |
        // [0.0]     [0.31]                       [14.29]
        Float nearest = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        Float hyperfocal = characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE);

        if (nearest == null && hyperfocal == null) {
            return getNoOp();
        }

        nearest = (nearest == null) ? 0.0f : nearest;
        hyperfocal = (hyperfocal == null) ? 0.0f : hyperfocal;

        if (nearest > hyperfocal) {
            return new LinearScale(hyperfocal, nearest, 0, 1);
        }

        return new LinearScale(nearest, hyperfocal, 0, 1);
    }
}
