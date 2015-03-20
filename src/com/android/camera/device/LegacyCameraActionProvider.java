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

import android.hardware.Camera;

import com.android.camera.async.HandlerFactory;
import com.android.camera.debug.Logger;

/**
 * Provides a set of executable actions that can be used to open or close
 * a Legacy API camera device.
 */
public class LegacyCameraActionProvider implements CameraDeviceActionProvider<Camera> {
    private final HandlerFactory mHandlerFactory;
    private final Logger.Factory mLogFactory;

    public LegacyCameraActionProvider(HandlerFactory handlerFactory, Logger.Factory logFactory) {
        mHandlerFactory = handlerFactory;
        mLogFactory = logFactory;
    }

    public SingleDeviceActions<Camera> get(CameraDeviceKey key) {
        return new LegacyCameraActions(key, mHandlerFactory, mLogFactory);
    }
}
