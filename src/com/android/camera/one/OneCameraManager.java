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

import com.android.camera.device.CameraId;
import com.android.camera.one.OneCamera.Facing;

/**
 * The camera manager is responsible for providing details about the
 * available camera hardware on the current device.
 */
public interface OneCameraManager {

    /**
     * Returns true if this hardware platform currently has any cameras at
     * all.
     */
    public boolean hasCamera();

    /**
     * Returns whether the device has a camera facing the given direction.
     */
    public boolean hasCameraFacing(Facing facing);

    /**
     * Get a platform specific device key for the first camera in the list
     * of all camera devices.
     */
    public CameraId findFirstCamera();

    /**
     * Get a platform specific device key for a camera facing a particular
     * direction.
     */
    public CameraId findFirstCameraFacing(Facing facing);

    /**
     * Retrieve the characteristics for the camera facing at the given
     * direction. The first camera found in the given direction will be chosen.
     *
     * @return A #{link com.android.camera.one.OneCameraCharacteristics} object
     *         to provide camera characteristics information. Returns null if
     *         there is no camera facing the given direction.
     */
    public OneCameraCharacteristics getOneCameraCharacteristics(CameraId cameraId)
          throws OneCameraAccessException;

    public static class Factory {

    }
}