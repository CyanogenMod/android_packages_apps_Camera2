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

import android.hardware.camera2.CameraAccessException;
import android.os.Handler;
import android.view.Surface;

import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;

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
     * @param surfaces The set of output surfaces for the camera capture
     *            session.
     * @return A Future for the camera capture session.
     */
    public ListenableFuture<CameraCaptureSessionProxy> createCaptureSession(
            List<Surface> surfaces) {
        final SettableFuture<CameraCaptureSessionProxy> sessionFuture = SettableFuture.create();
        try {
            mDevice.createCaptureSession(surfaces, new CameraCaptureSessionProxy.StateCallback() {
                @Override
                public void onActive(CameraCaptureSessionProxy session) {
                    // Ignore.
                }

                @Override
                public void onConfigureFailed(CameraCaptureSessionProxy session) {
                    sessionFuture.cancel(true);
                    session.close();
                }

                @Override
                public void onConfigured(CameraCaptureSessionProxy session) {
                    boolean valueSet = sessionFuture.set(session);
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
                    sessionFuture.cancel(true);
                    session.close();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            sessionFuture.setException(e);
        }
        return sessionFuture;
    }
}
