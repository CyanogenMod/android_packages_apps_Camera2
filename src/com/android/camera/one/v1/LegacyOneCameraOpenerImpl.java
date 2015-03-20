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

import android.os.Handler;

import com.android.camera.FatalErrorHandler;
import com.android.camera.SoundPlayer;
import com.android.camera.async.MainThread;
import com.android.camera.burst.BurstFacade;
import com.android.camera.device.CameraId;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.OneCameraCaptureSetting;
import com.android.camera.one.OneCameraOpener;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.google.common.base.Optional;
/**
 * The {@link com.android.camera.one.OneCameraOpener} implementation on top of the Camera API 1.
 */
public class LegacyOneCameraOpenerImpl implements OneCameraOpener {
    public static Optional<OneCameraOpener> create() {
        OneCameraOpener cameraManager = new LegacyOneCameraOpenerImpl();
        return Optional.of(cameraManager);
    }

    /**
     * Instantiates a new {@link com.android.camera.one.OneCameraOpener} for Camera1 API.
     */
    public LegacyOneCameraOpenerImpl() {
    }

    @Override
    public void open(
            CameraId cameraId,
            OneCameraCaptureSetting captureSetting,
            Handler handler,
            MainThread mainThread,
            ImageRotationCalculator imageRotationCalculator,
            BurstFacade burstController,
            SoundPlayer soundPlayer,
            OpenCallback openCallback, FatalErrorHandler fatalErrorHandler) {
        throw new RuntimeException("Not implemented yet.");
    }
}