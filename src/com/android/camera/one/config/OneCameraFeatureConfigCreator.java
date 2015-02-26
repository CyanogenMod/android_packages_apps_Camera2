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

package com.android.camera.one.config;

import android.content.ContentResolver;
import android.hardware.camera2.CameraCharacteristics;

import com.android.camera.debug.Log;
import com.android.camera.one.config.OneCameraFeatureConfig.CaptureSupportLevel;
import com.android.camera.one.config.OneCameraFeatureConfig.HdrPlusSupportLevel;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.GservicesHelper;
import com.google.common.base.Function;
import com.google.common.base.Optional;

/**
 * Creates the OneCamera feature configurations for the GoogleCamera app.
 */
public class OneCameraFeatureConfigCreator {
    private static final Log.Tag TAG = new Log.Tag("OneCamFtrCnfgCrtr");

    /**
     * Create the default camera feature config.
     */
    public static OneCameraFeatureConfig createDefault(ContentResolver contentResolver) {
        // Enable CaptureModule on all L devices unless the device is
        // black-listed.
        boolean useCaptureModule = ApiHelper.HAS_CAMERA_2_API
                && !GservicesHelper.isCaptureModuleDisabled(contentResolver);
        Log.i(TAG, "CaptureModule? " + useCaptureModule);

        // HDR+ has multiple levels of support.
        HdrPlusSupportLevel hdrPlusSupportLevel =
                GcamHelper.determineHdrPlusSupportLevel(contentResolver, useCaptureModule);
        return new OneCameraFeatureConfig(useCaptureModule,
                buildCaptureModuleDetector(contentResolver),
                hdrPlusSupportLevel);
    }

    private static Function<CameraCharacteristics, CaptureSupportLevel> buildCaptureModuleDetector(
            final ContentResolver contentResolver) {
        return new Function<CameraCharacteristics, CaptureSupportLevel>() {
            @Override
            public CaptureSupportLevel apply(CameraCharacteristics characteristics) {
                // If a capture support level override exists, use it. Otherwise
                // dynamically check the capabilities of the current device.
                Optional<CaptureSupportLevel> override =
                        getCaptureSupportLevelOverride(characteristics, contentResolver);
                if (override.isPresent()) {
                    Log.i(TAG, "Camera support level override: " + override.get().name());
                    return override.get();
                }

                Integer supportedLevel = characteristics
                        .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

                // A hardware level should always be supported, so we should
                // never have to return here. If no hardware level is supported
                // on a LEGACY device, the LIMITED_JPEG fallback will not work.
                if (supportedLevel == null) {
                    Log.e(TAG, "Device does not report supported hardware level.");
                    return CaptureSupportLevel.LIMITED_JPEG;
                }

                // LEGACY_JPEG is the ONLY mode that is supported on LEGACY
                // devices.
                if (supportedLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    return CaptureSupportLevel.LEGACY_JPEG;
                }

                // On a non-LEGACY device prior to Lollipop MR1 we fall back to
                // LIMITED_JPEG due to Nexus 5 and 6 HAL bugs.
                if (!ApiHelper.isLMr1OrHigher() && (ApiHelper.IS_NEXUS_5 || ApiHelper.IS_NEXUS_6)) {
                    return CaptureSupportLevel.LIMITED_JPEG;
                }

                // On FULL devices starting with L-MR1 we can run ZSL.
                if (supportedLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL) {
                    return CaptureSupportLevel.ZSL;
                }

                // On LIMITED devices starting with L-MR1 we run a simple YUV
                // capture mode.
                if (supportedLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) {
                    return CaptureSupportLevel.LIMITED_YUV;
                }

                // We should never get here. If we do, let's fall back to a mode
                // that should work on all non-LEGACY devices.
                Log.e(TAG, "Unknown support level: " + supportedLevel);
                return CaptureSupportLevel.LIMITED_JPEG;
            }
        };
    }

    /**
     * @return If an override exits, this returns the capture support hardware
     *         level that should be used on this device.
     */
    private static Optional<CaptureSupportLevel> getCaptureSupportLevelOverride(
            CameraCharacteristics cameraCharacteristics, ContentResolver contentResolver) {
        Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing == null) {
            Log.e(TAG, "Camera not facing anywhere.");
            return Optional.absent();
        }

        switch (facing) {
            case CameraCharacteristics.LENS_FACING_BACK: {
                int override = GservicesHelper.getCaptureSupportLevelOverrideBack(contentResolver);
                return CaptureSupportLevel.fromFlag(override);
            }
            case CameraCharacteristics.LENS_FACING_FRONT: {
                int override = GservicesHelper.getCaptureSupportLevelOverrideFront(contentResolver);
                return CaptureSupportLevel.fromFlag(override);
            }
            default:
                Log.e(TAG, "Not sure where camera is facing to.");
                return Optional.absent();
        }
    }
}
