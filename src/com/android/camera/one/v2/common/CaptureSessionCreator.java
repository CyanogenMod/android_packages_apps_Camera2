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

package com.android.camera.one.v2.common;

import java.util.List;

import android.hardware.camera2.CameraAccessException;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.android.camera.async.FutureResult;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.util.ScopedFactory;

/**
 * Asynchronously creates capture sessions.
 */
public class CaptureSessionCreator implements Runnable {
    private final CameraDeviceProxy mDevice;
    private final Handler mCameraHandler;
    private final List<Surface> mSurfaces;
    private final FutureResult<CameraCaptureSessionProxy> mSessionFuture;
    private final ScopedFactory<CameraCaptureSessionProxy, Runnable> mCaptureSessionScopeEntrance;

    /**
     * @param device The device on which to create the capture session.
     * @param cameraHandler The handler on which to process capture session
     *            state callbacks.
     * @param surfaces The surfaces to configure the session with.
     * @param sessionFuture The {@link com.android.camera.async.FutureResult} in
     *            which to place the asynchronously-created.
     * @param captureSessionScopeEntrance A factory to produce a runnable to
     *            execute if/when the CameraCaptureSession is available.
     */
    public CaptureSessionCreator(CameraDeviceProxy device, Handler cameraHandler,
            List<Surface> surfaces, FutureResult<CameraCaptureSessionProxy> sessionFuture,
            ScopedFactory<CameraCaptureSessionProxy, Runnable> captureSessionScopeEntrance) {
        mDevice = device;
        mCameraHandler = cameraHandler;
        mSurfaces = surfaces;
        mSessionFuture = sessionFuture;
        mCaptureSessionScopeEntrance = captureSessionScopeEntrance;
    }

    @Override
    public void run() {
        try {
            mDevice.createCaptureSession(mSurfaces, new CameraCaptureSessionProxy.StateCallback() {
                @Override
                public void onActive(CameraCaptureSessionProxy session) {
                    // Ignore.
                }

                @Override
                public void onConfigureFailed(CameraCaptureSessionProxy session) {
                    mSessionFuture.setCancelled();
                    session.close();
                }

                @Override
                public void onConfigured(CameraCaptureSessionProxy session) {
                    boolean valueSet = mSessionFuture.setValue(session);
                    if (valueSet) {
                        mCaptureSessionScopeEntrance.get(session).run();
                    } else {
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
                    mSessionFuture.setCancelled();
                    session.close();
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            mSessionFuture.setException(e);
        }
    }
}
