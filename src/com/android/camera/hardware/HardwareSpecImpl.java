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

package com.android.camera.hardware;

import android.hardware.Camera;

import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;

import java.util.List;

/**
 * HardwareSpecImpl is the default implementation of
 * {@link com.android.camera.hardware.HardwareSpec} for
 * a camera device opened using the {@link android.hardware.Camera}
 * api.
 */
public class HardwareSpecImpl implements HardwareSpec {

    private final boolean mIsFrontCameraSupported;
    private final boolean mIsHdrSupported;
    private final boolean mIsHdrPlusSupported;
    private final boolean mIsFlashSupported;

    /**
     * Compute the supported values for all
     * {@link com.android.camera.hardware.HardwareSpec} methods
     * based on {@link android.hardware.Camera.Parameters}.
     */
    public HardwareSpecImpl(Camera.Parameters parameters) {
        // Cache whether front camera is supported.
        mIsFrontCameraSupported = (Camera.getNumberOfCameras() > 1);

        // Cache whether hdr is supported.
        mIsHdrSupported = CameraUtil.isCameraHdrSupported(parameters);

        // Cache whether hdr plus is supported.
        mIsHdrPlusSupported = GcamHelper.hasGcamCapture();

        // Cache whether flash is supported.
        mIsFlashSupported = isFlashSupported(parameters);
    }

    @Override
    public boolean isFrontCameraSupported() {
        return mIsFrontCameraSupported;
    }

    @Override
    public boolean isHdrSupported() {
        return mIsHdrSupported;
    }

    @Override
    public boolean isHdrPlusSupported() {
        return mIsHdrPlusSupported;
    }

    @Override
    public boolean isFlashSupported() {
        return mIsFlashSupported;
    }

    /**
     * Returns whether flash is supported and flash has more than
     * one possible value.
     */
    private boolean isFlashSupported(Camera.Parameters parameters) {
        List<String> supportedFlashModes = parameters.getSupportedFlashModes();
        return !(supportedFlashModes == null || (supportedFlashModes.size() == 1));
    }
}