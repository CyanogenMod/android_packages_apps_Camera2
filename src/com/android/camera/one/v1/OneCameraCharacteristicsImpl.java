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

package com.android.camera.one.v1;

import android.graphics.Rect;
import android.hardware.Camera;

import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.ui.focus.LensRangeCalculator;
import com.android.camera.ui.motion.LinearScale;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a OneCamera device which is on top of camera1 API.
 */
public class OneCameraCharacteristicsImpl implements OneCameraCharacteristics {
    private final Camera.CameraInfo mCameraInfo;
    private final Camera.Parameters mCameraParameters;

    /** The supported picture sizes. */
    private final ArrayList<Size> mSupportedPictureSizes = new ArrayList<Size>();

    /** The supported preview sizes. */
    private final ArrayList<Size> mSupportedPreviewSizes = new ArrayList<Size>();

    public OneCameraCharacteristicsImpl(
            Camera.CameraInfo cameraInfo, Camera.Parameters cameraParameters) {
        mCameraInfo = cameraInfo;
        mCameraParameters = cameraParameters;

        List<Camera.Size> supportedPictureSizes = cameraParameters.getSupportedPictureSizes();
        if (supportedPictureSizes != null) {
            for (Camera.Size pictureSize : supportedPictureSizes) {
                mSupportedPictureSizes.add(new Size(pictureSize));
            }
        }

        List<Camera.Size> supportedPreviewSizes = cameraParameters.getSupportedPreviewSizes();
        if (supportedPreviewSizes != null) {
            for (Camera.Size previewSize : supportedPreviewSizes) {
                mSupportedPreviewSizes.add(new Size(previewSize));
            }
        }
    }

    @Override
    public List<Size> getSupportedPictureSizes(int imageFormat) {
        return mSupportedPictureSizes;
    }

    @Override
    public List<Size> getSupportedPreviewSizes() {
        return mSupportedPreviewSizes;
    }

    @Override
    public int getSensorOrientation() {
        return mCameraInfo.orientation;
    }

    @Override
    public OneCamera.Facing getCameraDirection() {
        int direction = mCameraInfo.facing;
        if (direction == Camera.CameraInfo.CAMERA_FACING_BACK) {
            return OneCamera.Facing.BACK;
        } else {
            return OneCamera.Facing.FRONT;
        }
    }

    @Override
    public Rect getSensorInfoActiveArraySize() {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public float getAvailableMaxDigitalZoom() {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public boolean isFlashSupported() {
        return (mCameraParameters.getFlashMode() != null);
    }

    @Override
    public boolean isHdrSceneSupported() {
        return mCameraParameters.getSupportedSceneModes().contains(
              Camera.Parameters.SCENE_MODE_HDR);
    }

    @Override
    public SupportedHardwareLevel getSupportedHardwareLevel() {
        throw new RuntimeException("Not implemented yet.");
    }

    @Override
    public List<FaceDetectMode> getSupportedFaceDetectModes() {
        List<FaceDetectMode> oneModes = new ArrayList<>(1);
        oneModes.add(FaceDetectMode.NONE);
        return oneModes;
    }

    @Override
    public LinearScale getLensFocusRange() {
        // Diopter range is not supported on legacy camera devices.
        return LensRangeCalculator.getNoOp();
    }

    @Override
    public List<Float> getAvailableFocalLengths() {
        List<Float> list = new ArrayList<>(1);
        list.add(mCameraParameters.getFocalLength());
        return list;
    }

    @Override
    public boolean isExposureCompensationSupported() {
        // Turn off exposure compensation for Nexus 6 on L (API level 21)
        // because the bug in framework b/19219128.
        if (ApiHelper.IS_NEXUS_6 && ApiHelper.isLollipop()) {
            return false;
        }
        return mCameraParameters.getMinExposureCompensation() != 0 ||
                mCameraParameters.getMaxExposureCompensation() != 0;
    }

    @Override
    public int getMinExposureCompensation() {
        if (!isExposureCompensationSupported()) {
            return -1;
        }
        return mCameraParameters.getMinExposureCompensation();
    }

    @Override
    public int getMaxExposureCompensation() {
        if (!isExposureCompensationSupported()) {
            return -1;
        }
        return mCameraParameters.getMaxExposureCompensation();
    }

    @Override
    public float getExposureCompensationStep() {
        if (!isExposureCompensationSupported()) {
            return -1.0f;
        }
        return mCameraParameters.getExposureCompensationStep();
    }

    @Override
    public boolean isAutoFocusSupported() {
        // Custom AF is only supported on the back camera for legacy devices.
        return getCameraDirection() == Facing.BACK;
    }

    @Override
    public boolean isAutoExposureSupported() {
        // Custom AE is only supported on the back camera for legacy devices.
        return getCameraDirection() == Facing.BACK;
    }
}
