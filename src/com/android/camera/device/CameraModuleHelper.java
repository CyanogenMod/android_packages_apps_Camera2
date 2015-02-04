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

package com.android.camera.device;

import android.hardware.camera2.CameraManager;

import com.android.camera.async.HandlerFactory;
import com.android.camera.debug.Loggers;
import com.android.camera.util.AndroidContext;
import com.android.camera.util.AndroidServices;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Injection and singleton construction helpers.
 */
public class CameraModuleHelper {
    public static LegacyCameraActionProvider provideLegacyCameraActionProvider() {
        HandlerFactory handlerFactory = new HandlerFactory();
        return new LegacyCameraActionProvider(handlerFactory, Loggers.tagFactory());
    }

    public static PortabilityCameraActionProvider providePortabilityActionProvider() {
        HandlerFactory handlerFactory = new HandlerFactory();
        ExecutorService backgroundRunner = Executors.newSingleThreadExecutor();
        return new PortabilityCameraActionProvider(handlerFactory, backgroundRunner,
              AndroidContext.instance().get(), Loggers.tagFactory());
    }

    public static Camera2ActionProvider provideCamera2ActionProvider() {
        CameraManager cameraManager = AndroidServices.instance().provideCameraManager();

        HandlerFactory handlerFactory = new HandlerFactory();
        ExecutorService backgroundRunner = Executors.newSingleThreadExecutor();

        return new Camera2ActionProvider(cameraManager, handlerFactory, backgroundRunner,
              Loggers.tagFactory());
    }
}
