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

package com.android.camera.one.v2.photo;

import android.annotation.TargetApi;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;

import com.android.camera.app.OrientationManager;
import com.android.camera.util.CameraUtil;

/**
 * Default implementation of ImageRotationCalculator which takes the camera's
 * sensor rotation and front/back-facing property to calculate image rotations.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ImageRotationCalculatorImpl implements ImageRotationCalculator {

    private final int mSensorOrientationDegrees;
    private final boolean mFrontFacing;

    /**
     * Create a calculator with the given hardware properties of the camera.
     *
     * @param sensorOrientationDegrees the orientation of the sensor, in
     *            degrees.
     * @param frontFacing whether the camera is front-facing.
     */
    public ImageRotationCalculatorImpl(int sensorOrientationDegrees, boolean frontFacing) {
        mSensorOrientationDegrees = sensorOrientationDegrees;
        mFrontFacing = frontFacing;
    }

    /**
     * Create a calculator based on Camera characteristics.
     */
    public static ImageRotationCalculator from(CameraCharacteristics characteristics) {
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        return new ImageRotationCalculatorImpl(sensorOrientation,
                lensFacing == CameraCharacteristics.LENS_FACING_FRONT);
    }

    @Override
    public OrientationManager.DeviceOrientation toImageRotation(
            OrientationManager.DeviceOrientation deviceOrientation) {
        int imageRotation = CameraUtil.getImageRotation(mSensorOrientationDegrees,
                deviceOrientation.getDegrees(), mFrontFacing);
        return OrientationManager.DeviceOrientation.from(imageRotation);
    }
}
