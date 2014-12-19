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

package com.android.camera.one.v2.initialization;

import java.util.List;
import java.util.concurrent.Future;

import android.hardware.camera2.CameraAccessException;
import android.os.Handler;
import android.view.Surface;

import com.android.camera.async.FutureResult;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;

/**
 * Asynchronously creates capture sessions.
 */
class CaptureSessionCreator {
    private final CameraDeviceProxy mDevice;
    private final Handler mCameraHandler;

    /**
     * @param device The device on which to create the capture session.
     * @param cameraHandler The handler on which to process capture session
     *            state callbacks.
     */
    public CaptureSessionCreator(CameraDeviceProxy device, Handler cameraHandler) {
        mDevice = device;
        mCameraHandler = cameraHandler;
    }

    /**
     * Asynchronously creates a capture session, returning a future to it.
     *
     * @param surfaces The set of output surfaces for the camera capture session.
     * @return A Future for the camera capture session.
     */
    public Future<CameraCaptureSessionProxy> createCaptureSession(List<Surface> surfaces) {
        final FutureResult<CameraCaptureSessionProxy> sessionFuture = new FutureResult<>();
        try {
            mDevice.createCaptureSession(surfaces, new CameraCaptureSessionProxy.StateCallback() {
                @Override
                public void onActive(CameraCaptureSessionProxy session) {
                    // Ignore.
                }

                @Override
                public void onConfigureFailed(CameraCaptureSessionProxy session) {
                    sessionFuture.setCancelled();
                    session.close();
                }

                @Override
                public void onConfigured(CameraCaptureSessionProxy session) {
                    boolean valueSet = sessionFuture.setValue(session);
                    if (!valueSet) {
                        // If the future was already marked with cancellation or
                        // an exception, close the session.
                        session.close();
                    }
                }

                @Override
                public void onReady(CameraCaptureSessionProxy session) {
                    // Ignore.
                }

                @Override
                public void onClosed(CameraCaptureSessionProxy session) {
                    sessionFuture.setCancelled();
                    session.close();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            sessionFuture.setException(e);
        }
        return sessionFuture;
    }
}
