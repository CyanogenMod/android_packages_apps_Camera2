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

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;

import com.android.camera.ui.motion.LinearScale;
import com.android.camera.util.Size;

import java.util.List;

/**
 * The properties describing a OneCamera device. These properties are fixed for
 * a given OneCamera device.
 */
public interface OneCameraCharacteristics {
    public enum SupportedHardwareLevel {
        FULL, LIMITED, LEGACY
    }

    public enum FaceDetectMode {
        FULL, SIMPLE, NONE
    }

    /**
     * Gets the supported picture sizes for the given image format.
     *
     * @param imageFormat The specific image format listed on
     *            {@link ImageFormat}.
     */
    public List<Size> getSupportedPictureSizes(int imageFormat);

    /**
     * Gets the supported preview sizes.
     */
    public List<Size> getSupportedPreviewSizes();

    /**
     * @See {@link CameraCharacteristics#SENSOR_ORIENTATION}
     */
    public int getSensorOrientation();

    /**
     * @Return The direction of the camera
     */
    public OneCamera.Facing getCameraDirection();

    /**
     * @See {@link CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE}
     */
    public Rect getSensorInfoActiveArraySize();

    /**
     * @See {@link CameraCharacteristics#SCALER_AVAILABLE_MAX_DIGITAL_ZOOM}
     */
    public float getAvailableMaxDigitalZoom();

    /**
     * @return If flash is supported for this camera.
     */
    public boolean isFlashSupported();

    /**
     * @return If Scene-mode HDR is supported.
     */
    public boolean isHdrSceneSupported();

    /**
     * @return The supported hardware level.
     */
    public SupportedHardwareLevel getSupportedHardwareLevel();

    /**
     * @return The supported face detection modes.
     */
    public List<FaceDetectMode> getSupportedFaceDetectModes();

    /**
     * A converter from the physical focus range of the camera to a ratio.
     */
    public LinearScale getLensFocusRange();

    /**
     * @return A List of available focal lengths for this camera.
     */
    public List<Float> getAvailableFocalLengths();

    /**
     * Whether exposure compensation is supported for this camera.
     *
     * @return true if exposure compensation is supported for this camera.
     */
    public boolean isExposureCompensationSupported();

    /**
     * @return The min exposure compensation index. The EV is the compensation
     * index multiplied by the step value. If {@link
     * #isExposureCompensationSupported()} is false, return -1.
     */
    public int getMinExposureCompensation();

    /**
     * @return The max exposure compensation index. The EV is the compensation
     * index multiplied by the step value. If {@link
     * #isExposureCompensationSupported()} is false, return -1.
     */
    public int getMaxExposureCompensation();

    /**
     * @return The exposure compensation step. The EV is the compensation index
     * multiplied by the step value. If {@link
     * #isExposureCompensationSupported()} is false, return -1.
     */
    public float getExposureCompensationStep();

    /**
     * @return true if this camera supports custom AutoFocus regions.
     */
    public boolean isAutoFocusSupported();

    /**
     * @return true if this camera supports custom AutoExposure regions.
     */
    public boolean isAutoExposureSupported();
}
