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
import com.android.camera.one.OneCameraCharacteristics;
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
    public SupportedHardwareLevel getSupportedHardwareLevel() {
        throw new RuntimeException("Not implemented yet.");
    }
}
