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

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Size;

import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCamera.OpenCallback;

/**
 * The camera manager is responsible for instantiating {@link OneCamera}
 * instances.
 */
public abstract class OneCameraManager {
    private static Tag TAG = new Tag("OneCameraManager");

    /**
     * Attempts to open the camera facing the given direction with the given
     * capture size.
     *
     * @param facing which camera to open. The first camera found in the given
     *            direction will be opened.
     * @param captureSize the capture size. This must be one of the supported
     *            sizes.
     * @param callback this listener is called when the camera was opened or
     *            when it failed to open.
     */
    public abstract void open(Facing facing, Size captureSize, OpenCallback callback);

    /**
     * Returns whether the device has a camera facing the given direction.
     */
    public abstract boolean hasCameraFacing(Facing facing);

    /**
     * Singleton camera manager to be used throughout the app.
     */
    private static OneCameraManager sCameraManager;

    /**
     * Returns a camera manager that is based on Camera2 API, if available, or
     * otherwise uses the portability layer API.
     * <p>
     * The instance is created the first time this method is called and cached
     * in a singleton thereafter, so successive calls are cheap.
     */
    public static OneCameraManager get(Activity activity) {
        if (sCameraManager == null) {
            sCameraManager = create(activity);
        }
        return sCameraManager;
    }

    /**
     * Creates a new camera manager that is based on Camera2 API, if available,
     * or otherwise uses the portability API.
     */
    private static OneCameraManager create(Activity activity) {
        CameraManager cameraManager = (CameraManager) activity
                .getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager != null && isCamera2FullySupported(cameraManager)) {
            return new com.android.camera.one.v2.OneCameraManagerImpl(cameraManager);
        } else {
            return new com.android.camera.one.v1.OneCameraManagerImpl();
        }
    }

    /**
     * Returns whether the device fully supports API2,
     *
     * @param cameraManager the Camera2 API manager.
     * @return If this device is only emulating Camera2 API on top of an older
     *         HAL (such as the Nexus 4, 7 or 10), this method returns false. It
     *         only returns true, if Camera2 is fully supported through newer
     *         HALs.
     */
    private static boolean isCamera2FullySupported(CameraManager cameraManager) {
        try {
            final String id = cameraManager.getCameraIdList()[0];
            return cameraManager.getCameraCharacteristics(id).get(
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL;
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Could not access camera to determine hardware-level API support.");
            return false;
        }
    }
}
