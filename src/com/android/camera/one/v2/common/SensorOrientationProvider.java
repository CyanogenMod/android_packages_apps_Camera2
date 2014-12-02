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

import android.hardware.camera2.CameraCharacteristics;

import com.android.camera.app.OrientationManager;

public class SensorOrientationProvider {
    private final CameraCharacteristics mCameraCharacteristics;

    public SensorOrientationProvider(CameraCharacteristics cameraCharacteristics) {
        mCameraCharacteristics = cameraCharacteristics;
    }

    public OrientationManager.DeviceOrientation getSensorOrientation() {
        switch (mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)) {
            case 0:
                return OrientationManager.DeviceOrientation.CLOCKWISE_0;
            case 90:
                return OrientationManager.DeviceOrientation.CLOCKWISE_90;
            case 180:
                return OrientationManager.DeviceOrientation.CLOCKWISE_180;
            case 270:
                return OrientationManager.DeviceOrientation.CLOCKWISE_270;
            default:
                // Per API documentation, this case should never execute.
                return OrientationManager.DeviceOrientation.CLOCKWISE_0;
        }
    }
}
