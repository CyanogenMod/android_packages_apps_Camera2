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

import android.annotation.TargetApi;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Build;

import com.google.common.base.Function;
import com.google.common.base.Optional;

import com.android.camera.one.OneCamera;

/**
 * Contains the logic for which Camera API and features should be enabled on the
 * current device.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class OneCameraFeatureConfig {

    /** The camera API 2 support levels for capture module. */
    public static enum CaptureSupportLevel {
        /**
         * Our app maintains a YUV ringbuffer on FULL devices that support it.
         * App-level JPEG compression. (Option 1).
         */
        ZSL,
        /** This mode is required on LEGACY devices. (Option 2). */
        LEGACY_JPEG,
        /** Requests JPEG on LIMITED or FULL devices. (Option 3). */
        LIMITED_JPEG,
        /**
         * Requests YUV images on LIMITED or FULL with app-level JPEG
         * compression. (Option 4).
         */
        LIMITED_YUV;

        /** Given the GServices override flag, returns the support level. */
        public static Optional<CaptureSupportLevel> fromFlag(int flag) {
            switch (flag) {
                case 1:
                    return Optional.of(ZSL);
                case 2:
                    return Optional.of(LEGACY_JPEG);
                case 3:
                    return Optional.of(LIMITED_JPEG);
                case 4:
                    return Optional.of(LIMITED_YUV);
                default:
                    return Optional.absent();
            }
        }
    }

    /** The HDR+ support levels. */
    public static enum HdrPlusSupportLevel {
        /** No HDR+ supported. */
        NONE,
        /** Nexus 5 on KitKat using Camera shim. */
        LEGACY,
        /** Full API 2 HDR+ support. */
        FULL
    }

    /** Whether the capture module should be used (instead of PhotoModule). */
    private final boolean mUseCaptureModule;
    /** Determines the mode for regular capture on this device. */
    private final Function<CameraCharacteristics, CaptureSupportLevel> mCaptureModeDetector;
    /** The level of HDR+ support. */
    private final HdrPlusSupportLevel mHdrPlusSupportLevel;
    /**
     * The maximum amount of memory can be consumed by all opened cameras
     * during capture and processing, in megabytes.
     */
    private final int mMaxMemoryMB;

    /**
     * The maximum number of images the camera should allocate in the image reader.
     */
    private final int mMaxAllowedImageReaderCount;

    OneCameraFeatureConfig(boolean useCaptureModule,
            Function<CameraCharacteristics, CaptureSupportLevel> captureModeDetector,
            HdrPlusSupportLevel hdrPlusSupportLevel,
            int maxMemoryMB,
            int maxAllowedImageReaderCount) {
        mUseCaptureModule = useCaptureModule;
        mCaptureModeDetector = captureModeDetector;
        mHdrPlusSupportLevel = hdrPlusSupportLevel;
        mMaxMemoryMB = maxMemoryMB;
        mMaxAllowedImageReaderCount = maxAllowedImageReaderCount;
    }

    /**
     * @return Whether CaptureModule should be used for photo capture.
     */
    public boolean isUsingCaptureModule() {
        return mUseCaptureModule;
    }

    /**
     * @param characteristics the characteristics of the camera.
     * @return Whether the camera with the given characteristics supports
     *         app-level ZSL.
     */
    public CaptureSupportLevel getCaptureSupportLevel(CameraCharacteristics characteristics) {
        return mCaptureModeDetector.apply(characteristics);
    }

    /**
     * @return The general support level for HDR+ on this device.
     */
    public HdrPlusSupportLevel getHdrPlusSupportLevel(OneCamera.Facing cameraFacing) {
        if (cameraFacing == OneCamera.Facing.FRONT) {
            return HdrPlusSupportLevel.NONE;
        }
        return mHdrPlusSupportLevel;
    }

    /**
     * @return The maximum amount of memory can be consumed by all opened
     *         cameras during capture and processing, in megabytes.
     */
    public int getMaxMemoryMB() {
        return mMaxMemoryMB;
    }

    /**
     * @return The maximum number of images the camera should allocate in the
     *         image reader.
     */
    public int getMaxAllowedImageReaderCount() {
        return mMaxAllowedImageReaderCount;
    }
}
