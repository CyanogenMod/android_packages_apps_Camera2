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

import android.content.Context;
import android.os.Handler;
import android.util.DisplayMetrics;

import com.android.camera.FatalErrorHandler;
import com.android.camera.SoundPlayer;
import com.android.camera.async.MainThread;
import com.android.camera.burst.BurstFacade;
import com.android.camera.debug.Log.Tag;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.config.OneCameraFeatureConfig;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.google.common.base.Optional;

/**
 * The camera manager is responsible for instantiating {@link OneCamera}
 * instances.
 */
public abstract class OneCameraManager {
    private static Tag TAG = new Tag("OneCameraManager");

    /**
     * Attempts to open the camera facing the given direction with the given
     * capture size.
     * <p>
     * Exactly one call will always be made to a single method in the provided
     * {@link OpenCallback}.
     * 
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
            OneCameraCaptureSetting captureSetting,
            Handler handler,
            MainThread mainThread,
            ImageRotationCalculator imageRotationCalculator,
            BurstFacade burstController,
            SoundPlayer soundPlayer,
            OpenCallback openCallback,
            FatalErrorHandler fatalErrorHandler);

    // TODO: Move this to OneCameraCharacteristics class.
    /**
     * Returns whether the device has a camera facing the given direction.
     */
    public abstract boolean hasCameraFacing(Facing facing);

    /**
     * Retrieve the characteristics for the camera facing at the given
     * direction. The first camera found in the given direction will be chosen.
     *
     * @param facing The facing direction of the camera.
     * @return A #{link com.android.camera.one.OneCameraCharacteristics} object
     *         to provide camera characteristics information. Returns null if
     *         there is no camera facing the given direction.
     */
    public abstract OneCameraCharacteristics getCameraCharacteristics(Facing facing)
            throws OneCameraAccessException;

    /**
     * Creates a camera manager that is based on Camera2 API, if available, or
     * otherwise uses the portability layer API.
     *
     * @throws OneCameraException Thrown if an error occurred while trying to
     *             access the camera.
     */
    public static OneCameraManager get(
            OneCameraFeatureConfig featureConfig,
            Context context,
            DisplayMetrics displayMetrics) throws OneCameraException {
        return create(featureConfig, context, displayMetrics);
    }

    /**
     * Creates a new camera manager that is based on Camera2 API, if available.
     *
     * @throws OneCameraException Thrown if an error occurred while trying to
     *             access the camera.
     */
    private static OneCameraManager create(
            OneCameraFeatureConfig featureConfig,
            Context context,
            DisplayMetrics displayMetrics) throws OneCameraException {
        Optional<OneCameraManager> manager = com.android.camera.one.v2.OneCameraManagerImpl.create(
                featureConfig, context, displayMetrics);
        if (!manager.isPresent()) {
            manager = com.android.camera.one.v1.OneCameraManagerImpl.create();
        }
        if (!manager.isPresent()) {
            throw new OneCameraException("No camera manager is available.");
        }
        return manager.get();
    }
}
