/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.app;

import android.hardware.Camera;
import android.os.Handler;

import com.android.camera.app.CameraManager.CameraExceptionCallback;

/**
 * An interface which defines the camera provider.
 */
public interface CameraProvider {

    /**
     * Requests the camera device. If the camera device of the same ID is
     * already requested, then no-op here.
     *
     * @param id The ID of the requested camera device.
     */
    public void requestCamera(int id);

    /**
     * Releases the camera device.
     *
     * @param id The camera ID.
     */
    public void releaseCamera(int id);

    /**
     * Sets a callback for handling camera api runtime exceptions on
     * a handler.
     */
    public void setCameraDefaultExceptionCallback(CameraExceptionCallback callback,
            Handler handler);

    /**
     * Get the {@link android.hardware.Camera.CameraInfo} of all the cameras.
     *
     * @return An array of the {@link android.hardware.Camera.CameraInfo}.
     */
    public Camera.CameraInfo[] getCameraInfo();

    /**
     * Returns the total number of cameras available on the device.
     */
    public int getNumberOfCameras();

    /**
     * @returns The lowest ID of the back camera or -1 if not available.
     */
    public int getFirstBackCameraId();

    /**
     * @return The lowest ID of the front camera or -1 if not available.
     */
    public int getFirstFrontCameraId();

    /**
     * @returns Whether the camera is facing front.
     */
    public boolean isFrontFacingCamera(int id);

    /**
     * @returns Whether the camera is facing back.
     */
    public boolean isBackFacingCamera(int id);
}
