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

package com.android.camera.one.v2.commands;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;

/**
 * A generic camera command which may take an arbitrary, indefinite amount of
 * time to execute. Camera commands typically interact with the camera device,
 * capture session, image reader, and other resources.
 * <p>
 * When shutting down, it is critical that commands gracefully exit when these
 * resources are no longer available.
 */
public interface CameraCommand {
    /**
     * @throws InterruptedException If interrupted while executing the command.
     * @throws CameraAccessException If the camera is not available when
     *             accessed by the command.
     * @throws CameraCaptureSessionClosedException If the capture session was
     *             closed and not available when accessed by the command.
     * @throws ResourceAcquisitionFailedException If various non-camera
     *             resources required for the command to execute could not be
     *             acquired, either because they do not exist, or things are
     *             being shut down. For example, a command may throw this if it
     *             failed to allocate logical space in a shared image reader
     *             because the image reader is being closed, or because the
     *             requested space was greater than the capacity of the image
     *             reader.
     */
    public void run() throws InterruptedException, CameraAccessException,
            CameraCaptureSessionClosedException, ResourceAcquisitionFailedException;
}
