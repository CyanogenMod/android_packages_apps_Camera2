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

package com.android.camera.one.v2;

import static com.google.common.base.Preconditions.checkNotNull;

import android.annotation.TargetApi;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Range;
import android.util.Rational;

import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.ui.focus.LensRangeCalculator;
import com.android.camera.ui.motion.LinearScale;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Size;
import com.google.common.primitives.Floats;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a OneCamera device which is on top of camera2 API. This is
 * essential a wrapper for #{link
 * android.hardware.camera2.CameraCharacteristics}.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class OneCameraCharacteristicsImpl implements OneCameraCharacteristics {
    private static final int CONTROL_SCENE_MODE_HDR = 0x12;
    private static final Log.Tag TAG = new Log.Tag("OneCamCharImpl");

    private final CameraCharacteristics mCameraCharacteristics;

    public OneCameraCharacteristicsImpl(CameraCharacteristics cameraCharacteristics) {
        mCameraCharacteristics = cameraCharacteristics;
    }

    @Override
    public List<Size> getSupportedPictureSizes(int imageFormat) {
        StreamConfigurationMap configMap;
        try {
            configMap = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to obtain picture sizes.", ex);
            // See b/19623115   where java.lang.AssertionError can be thrown due to HAL error
            return new ArrayList<>(0);
        }

        ArrayList<Size> supportedPictureSizes = new ArrayList<>();
        for (android.util.Size androidSize : configMap.getOutputSizes(imageFormat)) {
            supportedPictureSizes.add(new Size(androidSize));
        }
        return supportedPictureSizes;
    }

    @Override
    public List<Size> getSupportedPreviewSizes() {
        StreamConfigurationMap configMap;
        try {
            configMap = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to obtain preview sizes.", ex);
            // See b/19623115   where java.lang.AssertionError can be thrown due to HAL error
            return new ArrayList<>(0);
        }
        ArrayList<Size> supportedPictureSizes = new ArrayList<>();
        for (android.util.Size androidSize : configMap.getOutputSizes(SurfaceTexture.class)) {
            supportedPictureSizes.add(new Size(androidSize));
        }
        return supportedPictureSizes;
    }

    @Override
    public int getSensorOrientation() {
        return mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    }

    @Override
    public OneCamera.Facing getCameraDirection() {
        int direction = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (direction == CameraCharacteristics.LENS_FACING_BACK) {
            return OneCamera.Facing.BACK;
        } else {
            return OneCamera.Facing.FRONT;
        }
    }

    @Override
    public Rect getSensorInfoActiveArraySize() {
        return mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    }

    @Override
    public float getAvailableMaxDigitalZoom() {
        return mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    @Override
    public boolean isFlashSupported() {
        return mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
    }

    @Override
    public boolean isHdrSceneSupported() {
        // API 21 omitted this constant officially, but kept it around as a hidden constant
        // MR1 brings it back officially as the same int value.
        int[] availableSceneModes = mCameraCharacteristics.get(
              CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        for (int availableSceneMode : availableSceneModes) {
            if (availableSceneMode == CONTROL_SCENE_MODE_HDR) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SupportedHardwareLevel getSupportedHardwareLevel() {
        Integer supportedHardwareLevel = mCameraCharacteristics
                .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        // If this fails, it is a framework bug, per API documentation.
        checkNotNull(supportedHardwareLevel, "INFO_SUPPORTED_HARDWARE_LEVEL not found");
        switch (supportedHardwareLevel) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                return SupportedHardwareLevel.FULL;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                return SupportedHardwareLevel.LIMITED;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                return SupportedHardwareLevel.LEGACY;
            default:
                if (supportedHardwareLevel >
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL) {
                    Log.i(TAG, "Unknown higher hardware level mapped to FULL: "
                            + supportedHardwareLevel);
                    return SupportedHardwareLevel.FULL;
                }
                throw new IllegalStateException("Invalid value for INFO_SUPPORTED_HARDWARE_LEVEL");
        }
    }

    @Override
    public List<FaceDetectMode> getSupportedFaceDetectModes() {
        int[] modes = mCameraCharacteristics.get(
              CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);

        List<FaceDetectMode> oneModes = new ArrayList<>(modes.length);

        for(int i=0; i < modes.length; i++) {
            if (modes[i] == CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL) {
                oneModes.add(FaceDetectMode.FULL);
            }
            if (modes[i] == CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE) {
                oneModes.add(FaceDetectMode.SIMPLE);
            }
            if (modes[i] == CameraMetadata.STATISTICS_FACE_DETECT_MODE_OFF) {
                oneModes.add(FaceDetectMode.NONE);
            }
        }

        return oneModes;
    }

    @Override
    public LinearScale getLensFocusRange() {
        return LensRangeCalculator.getDiopterToRatioCalculator(mCameraCharacteristics);
    }

    @Override
    public List<Float> getAvailableFocalLengths() {
        return Floats.asList(mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
    }

    @Override
    public boolean isExposureCompensationSupported() {
        // Turn off exposure compensation for Nexus 6 on L (API level 21)
        // because the bug in framework b/19219128.
        if (ApiHelper.IS_NEXUS_6 && ApiHelper.isLollipop()) {
            return false;
        }
        Range<Integer> compensationRange =
                mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return compensationRange.getLower() != 0 || compensationRange.getUpper() != 0;
    }

    @Override
    public int getMinExposureCompensation() {
        if (!isExposureCompensationSupported()) {
            return -1;
        }
        Range<Integer> compensationRange =
                mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return compensationRange.getLower();
    }

    @Override
    public int getMaxExposureCompensation() {
        if (!isExposureCompensationSupported()) {
            return -1;
        }
        Range<Integer> compensationRange =
                mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return compensationRange.getUpper();
    }

    @Override
    public float getExposureCompensationStep() {
        if (!isExposureCompensationSupported()) {
            return -1.0f;
        }
        Rational compensationStep = mCameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        return (float) compensationStep.getNumerator() / compensationStep.getDenominator();
    }

    @Override
    public boolean isAutoFocusSupported() {
        Integer maxAfRegions = mCameraCharacteristics.get(
              CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        // Auto-Focus is supported if the device supports one or more AF regions
        return maxAfRegions != null && maxAfRegions > 0;
    }

    @Override
    public boolean isAutoExposureSupported() {
        Integer maxAeRegions = mCameraCharacteristics.get(
              CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        // Auto-Exposure is supported if the device supports one or more AE regions
        return maxAeRegions != null && maxAeRegions > 0;
    }
}
