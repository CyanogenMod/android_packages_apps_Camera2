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

package com.android.camera.captureintent.state;

import com.android.camera.async.SafeCloseable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.util.Size;

public final class ResourceOpenedCamera implements SafeCloseable {
    /** The camera object. */
    private final OneCamera mCamera;

    /** The camera facing. */
    private final OneCamera.Facing mCameraFacing;

    /** The camera characteristics. */
    private final OneCameraCharacteristics mCameraCharacteristics;

    /** The desired picture size. */
    private final Size mPictureSize;

    /** The camera ready callback. */
    private final OneCamera.CaptureReadyCallback mCaptureReadyCallback;

    /** The current zoom ratio. */
    private float mZoomRatio;

    public ResourceOpenedCamera(
            OneCamera camera,
            OneCamera.Facing cameraFacing,
            OneCameraCharacteristics cameraCharacteristics,
            Size pictureSize,
            OneCamera.CaptureReadyCallback captureReadyCallback) {
        mCamera = camera;
        mCameraFacing = cameraFacing;
        mCameraCharacteristics = cameraCharacteristics;
        mPictureSize = pictureSize;
        mCaptureReadyCallback = captureReadyCallback;
        mZoomRatio = 1.0f;
    }

    @Override
    public void close() {
        mCamera.setFocusStateListener(null);
        mCamera.close();
    }

    public OneCamera getCamera() {
        return mCamera;
    }

    public Size getPictureSize() {
        return mPictureSize;
    }

    public float getZoomRatio() {
        return mZoomRatio;
    }

    public void setZoomRatio(float zoomRatio) {
        mCamera.setZoom(zoomRatio);
    }

    public OneCamera.CaptureReadyCallback getCaptureReadyCallback() {
        return mCaptureReadyCallback;
    }
}
