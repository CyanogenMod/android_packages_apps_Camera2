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

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;

import java.util.List;

/**
 * A CameraCaptureSessionProxy backed by an
 * {@link android.hardware.camera2.CameraCaptureSession}.
 */
public class AndroidCameraCaptureSessionProxy implements CameraCaptureSessionProxy {
    private class AndroidCaptureCallback extends CameraCaptureSession.CaptureCallback {
        private final CaptureCallback mCallback;

        private AndroidCaptureCallback(CaptureCallback callback) {
            mCallback = callback;
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
            mCallback.onCaptureStarted(AndroidCameraCaptureSessionProxy.this, request, timestamp,
                    frameNumber);
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                CaptureResult partialResult) {
            mCallback.onCaptureProgressed(AndroidCameraCaptureSessionProxy.this, request,
                    partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            mCallback.onCaptureCompleted(AndroidCameraCaptureSessionProxy.this, request, result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            mCallback.onCaptureFailed(AndroidCameraCaptureSessionProxy.this, request, failure);
        }

        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId,
                long frameNumber) {
            mCallback.onCaptureSequenceCompleted(AndroidCameraCaptureSessionProxy.this,
                    sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
            mCallback.onCaptureSequenceAborted(AndroidCameraCaptureSessionProxy.this, sequenceId);
        }
    }

    private final CameraCaptureSession mSession;

    public AndroidCameraCaptureSessionProxy(CameraCaptureSession session) {
        mSession = session;
    }

    @Override
    public void abortCaptures() throws CameraAccessException, CameraCaptureSessionClosedException {
        try {
            mSession.abortCaptures();
        } catch (IllegalStateException e) {
            throw new CameraCaptureSessionClosedException(e);
        }
    }

    @Override
    public int capture(CaptureRequest request, CaptureCallback listener, Handler handler)
            throws CameraAccessException, CameraCaptureSessionClosedException {
        try {
            return mSession.capture(request, new AndroidCaptureCallback(listener), handler);
        } catch (IllegalStateException e) {
            throw new CameraCaptureSessionClosedException(e);
        }
    }

    @Override
    public int captureBurst(List<CaptureRequest> requests, CaptureCallback listener, Handler handler)
            throws CameraAccessException, CameraCaptureSessionClosedException {
        try {
            return mSession.captureBurst(requests, new AndroidCaptureCallback(listener), handler);
        } catch (IllegalStateException e) {
            throw new CameraCaptureSessionClosedException(e);
        }
    }

    @Override
    public void close() {
        mSession.close();
    }

    @Override
    public CameraDeviceProxy getDevice() {
        return new AndroidCameraDeviceProxy(mSession.getDevice());
    }

    @Override
    public int setRepeatingBurst(List<CaptureRequest> requests, CaptureCallback listener,
            Handler handler) throws CameraAccessException, CameraCaptureSessionClosedException {
        try {
            return mSession.setRepeatingBurst(requests, new AndroidCaptureCallback(listener), handler);
        } catch (IllegalStateException e) {
            throw new CameraCaptureSessionClosedException(e);
        }
    }

    @Override
    public int setRepeatingRequest(CaptureRequest request, CaptureCallback listener, Handler handler)
            throws CameraAccessException, CameraCaptureSessionClosedException {
        try {
            return mSession.setRepeatingRequest(request, new AndroidCaptureCallback(listener),
                    handler);
        } catch (IllegalStateException e) {
            throw new CameraCaptureSessionClosedException(e);
        }
    }

    @Override
    public void stopRepeating() throws CameraAccessException, CameraCaptureSessionClosedException {
        try {
            mSession.stopRepeating();
        } catch (IllegalStateException e) {
            throw new CameraCaptureSessionClosedException(e);
        }
    }
}
