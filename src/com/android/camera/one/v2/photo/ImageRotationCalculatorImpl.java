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

import com.google.common.base.Supplier;

import android.annotation.TargetApi;
import android.os.Build;

import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.util.CameraUtil;

/**
 * Default implementation of ImageRotationCalculator which takes the camera's
 * sensor rotation and front/back-facing property to calculate image rotations.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ImageRotationCalculatorImpl implements ImageRotationCalculator {

    private final int mSensorOrientationDegrees;
    private final boolean mFrontFacing;
    private final OrientationManager mOrientationManager;

    /**
     * Create a calculator with the given hardware properties of the camera.
     *
     * @param sensorOrientationDegrees the orientation of the sensor, in
     *            degrees.
     * @param frontFacing whether the camera is front-facing.
     */
    public ImageRotationCalculatorImpl(OrientationManager orientationManager,
            int sensorOrientationDegrees, boolean frontFacing) {
        mSensorOrientationDegrees = sensorOrientationDegrees;
        mFrontFacing = frontFacing;
        mOrientationManager = orientationManager;
    }

    /**
     * Create a calculator based on Camera characteristics.
     */
    public static ImageRotationCalculator from(OrientationManager orientationManager,
            OneCameraCharacteristics characteristics) {
        int sensorOrientation = characteristics.getSensorOrientation();
        OneCamera.Facing lensDirection = characteristics.getCameraDirection();
        return new ImageRotationCalculatorImpl(orientationManager, sensorOrientation,
                lensDirection == OneCamera.Facing.FRONT);
    }

    @Override
    public OrientationManager.DeviceOrientation toImageRotation() {
        int imageRotation = CameraUtil.getImageRotation(mSensorOrientationDegrees,
                mOrientationManager.getDeviceOrientation().getDegrees(), mFrontFacing);
        return OrientationManager.DeviceOrientation.from(imageRotation);
    }

    @Override
    public Supplier<Integer> getSupplier() {
        return new Supplier<Integer>() {
            @Override
            public Integer get() {
                return Integer.valueOf(toImageRotation().getDegrees());
            }
        };
    }
}
