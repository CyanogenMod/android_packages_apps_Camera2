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

package com.android.camera.one.v2.camera2proxy;

import java.util.List;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.view.Surface;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class AndroidCameraDeviceProxy implements CameraDeviceProxy {
    private static class AndroidCaptureSessionStateCallback extends
            CameraCaptureSession.StateCallback {
        private final CameraCaptureSessionProxy.StateCallback mStateCallback;

        private AndroidCaptureSessionStateCallback(
                CameraCaptureSessionProxy.StateCallback stateCallback) {
            mStateCallback = stateCallback;
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            mStateCallback.onConfigured(new AndroidCameraCaptureSessionProxy(session));
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            mStateCallback.onConfigureFailed(new AndroidCameraCaptureSessionProxy(session));
        }

        @Override
        public void onReady(CameraCaptureSession session) {
            mStateCallback.onReady(new AndroidCameraCaptureSessionProxy(session));
        }

        @Override
        public void onActive(CameraCaptureSession session) {
            mStateCallback.onActive(new AndroidCameraCaptureSessionProxy(session));
        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            mStateCallback.onClosed(new AndroidCameraCaptureSessionProxy(session));
        }
    }

    private final CameraDevice mCameraDevice;

    public AndroidCameraDeviceProxy(CameraDevice cameraDevice) {
        mCameraDevice = cameraDevice;
    }

    @Override
    public String getId() {
        return mCameraDevice.getId();
    }

    @Override
    public void createCaptureSession(List<Surface> list,
                                     CameraCaptureSessionProxy.StateCallback stateCallback,
                                     @Nullable Handler handler)
            throws CameraAccessException {
        mCameraDevice.createCaptureSession(list, new AndroidCaptureSessionStateCallback(
                stateCallback), handler);
    }

    @Override
    public CaptureRequest.Builder createCaptureRequest(int i) throws CameraAccessException {
        return mCameraDevice.createCaptureRequest(i);
    }

    @Override
    public void close() {
        mCameraDevice.close();
    }
}
