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

import android.os.Handler;

import com.android.camera.FatalErrorHandler;
import com.android.camera.SoundPlayer;
import com.android.camera.async.MainThread;
import com.android.camera.burst.BurstFacade;
import com.android.camera.device.CameraId;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.v2.photo.ImageRotationCalculator;

/**
 * The camera manager is responsible for instantiating {@link OneCamera}
 * instances.
 */
public interface OneCameraOpener {
    /**
     * Attempts to open the given camera with the provided parameters and
     * settings.
     * <p>
     * Exactly one call will always be made to a single method in the provided
     * {@link OpenCallback}.
     *
     * @param cameraId the specific camera to open.
     * @param captureSetting the related settings to configure the camera for
     *            capture.
     * @param handler the handler on which callback methods are invoked.
     * @param mainThread Main thread executor
     * @param imageRotationCalculator Image rotation calculator required for
     *            Camera Factory initialization
     * @param burstController the burst facade to configure
     * @param soundPlayer the sound player.
     * @param openCallback this listener is called when the camera was opened or
     *            when it failed to open.
     * @param fatalErrorHandler the fatal error handler to use for indicating
     *            fatal errors
     */
    public abstract void open(
            CameraId cameraId,
            OneCameraCaptureSetting captureSetting,
            Handler handler,
            MainThread mainThread,
            ImageRotationCalculator imageRotationCalculator,
            BurstFacade burstController,
            SoundPlayer soundPlayer,
            OpenCallback openCallback,
            FatalErrorHandler fatalErrorHandler);

}
