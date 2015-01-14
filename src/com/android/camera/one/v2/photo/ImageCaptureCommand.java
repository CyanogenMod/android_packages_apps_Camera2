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

package com.android.camera.one.v2.photo;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.imagesaver.ImageSaver;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public interface ImageCaptureCommand {
    /**
     * Like {@link CameraCommand}, but takes parameters and callbacks.
     *
     * @throws InterruptedException
     * @throws CameraAccessException
     * @throws CameraCaptureSessionClosedException
     * @throws ResourceAcquisitionFailedException
     */
    public void run(Updatable<Void> imageExposeCallback,
                    ImageSaver imageSaver)
            throws InterruptedException,
            CameraAccessException,
            CameraCaptureSessionClosedException,
            ResourceAcquisitionFailedException;
}
