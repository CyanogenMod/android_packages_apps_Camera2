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

import android.content.Context;

import com.android.camera.async.HandlerFactory;
import com.android.camera.debug.Logger;
import com.android.camera.device.CameraDeviceKey.ApiType;
import com.android.ex.camera2.portability.CameraAgent.CameraProxy;
import com.android.ex.camera2.portability.CameraAgentFactory.CameraApi;

import java.util.concurrent.ExecutorService;

/**
 * Provides a set of executable actions that can be used to open or close
 * a portability layer camera device object.
 */
public class PortabilityCameraActionProvider implements CameraDeviceActionProvider<CameraProxy> {
    private final HandlerFactory mHandlerFactory;
    private final ExecutorService mBackgroundRunner;
    private final Context mAppContext;
    private final Logger.Factory mLogFactory;

    public PortabilityCameraActionProvider(HandlerFactory handlerFactory,
          ExecutorService backgroundRunner,
          Context appContext,
          Logger.Factory logFactory) {

        mHandlerFactory = handlerFactory;
        mBackgroundRunner = backgroundRunner;
        mAppContext = appContext;
        mLogFactory = logFactory;
    }

    @Override
    public SingleDeviceActions<CameraProxy> get(CameraDeviceKey key) {
        return new PortabilityCameraActions(key, mAppContext, getApiFromKey(key),
              mBackgroundRunner, mHandlerFactory, mLogFactory);
    }

    private CameraApi getApiFromKey(CameraDeviceKey key) {
        if (key.getApiType() == ApiType.CAMERA_API_PORTABILITY_API2) {
            return CameraApi.API_2;
        } else if (key.getApiType() == ApiType.CAMERA_API_PORTABILITY_API1) {
            return CameraApi.API_1;
        }

        return CameraApi.AUTO;
    }
}
