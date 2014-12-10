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

package com.android.camera.one.v2.common;

import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;

/**
 * Provides the full-size aspect ratio of the camera with the given characteristics.
 */
public class FullSizeAspectRatioProvider {
    private final CameraCharacteristics mCharacteristics;

    public FullSizeAspectRatioProvider(CameraCharacteristics characteristics) {
        mCharacteristics = characteristics;
    }

    /**
     * Calculate the aspect ratio of the full size capture on this device.
     *
     * @return The aspect ratio, in terms of width/height of the full capture
     *         size.
     */
    public float get() {
        Rect activeArraySize = mCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        return ((float) (activeArraySize.width())) / activeArraySize.height();
    }
}
