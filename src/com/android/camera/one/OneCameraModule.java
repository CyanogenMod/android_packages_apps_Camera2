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

package com.android.camera.one;

import android.content.Context;
import android.util.DisplayMetrics;

import com.android.camera.device.ActiveCameraDeviceTracker;
import com.android.camera.one.config.OneCameraFeatureConfig;
import com.android.camera.one.v1.LegacyOneCameraManagerImpl;
import com.android.camera.one.v1.LegacyOneCameraOpenerImpl;
import com.android.camera.one.v2.Camera2OneCameraManagerImpl;
import com.android.camera.one.v2.Camera2OneCameraOpenerImpl;
import com.google.common.base.Optional;

/**
 * Factory methods and functions for selecting and creating instances of
 * OneCamera objects.
 */
public final class OneCameraModule {
    private OneCameraModule() { }

    /**
     * Creates a new camera manager that is based on Camera2 API, if available.
     *
     * @throws OneCameraException Thrown if an error occurred while trying to
     *             access the camera.
     */
    public static OneCameraOpener provideOneCameraOpener(
            OneCameraFeatureConfig featureConfig,
            Context context,
            ActiveCameraDeviceTracker activeCameraDeviceTracker,
            DisplayMetrics displayMetrics) throws OneCameraException {
        Optional<OneCameraOpener> manager = Camera2OneCameraOpenerImpl.create(
              featureConfig, context, activeCameraDeviceTracker, displayMetrics);
        if (!manager.isPresent()) {
            manager = LegacyOneCameraOpenerImpl.create();
        }
        if (!manager.isPresent()) {
            throw new OneCameraException("No camera manager is available.");
        }
        return manager.get();
    }

    /**
     * Creates a new hardware manager that is based on Camera2 API, if available.
     *
     * @throws OneCameraException Thrown if an error occurred while trying to
     *             access the camera which may occur when accessing the legacy
     *             hardware manager.
     */
    public static OneCameraManager provideOneCameraManager() throws OneCameraException {
        Optional<Camera2OneCameraManagerImpl> camera2HwManager = Camera2OneCameraManagerImpl
              .create();
        if (camera2HwManager.isPresent()) {
            return camera2HwManager.get();
        }

        Optional<LegacyOneCameraManagerImpl> legacyHwManager = LegacyOneCameraManagerImpl.instance();
        if (legacyHwManager.isPresent()) {
            return legacyHwManager.get();
        }

        throw new OneCameraException("No hardware manager is available.");
    }
}
