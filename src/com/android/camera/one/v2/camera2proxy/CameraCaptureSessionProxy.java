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
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Handler;

import com.android.camera.async.SafeCloseable;

/**
 * Interface for {@link android.hardware.camera2.CameraCaptureSession}.
 * <p>
 * Note that this also enables translation of IllegalStateException (an
 * unchecked exception) resulting from the underlying session being closed into
 * a checked exception, forcing callers to explicitly handle this edge case.
 * </p>
 */
public interface CameraCaptureSessionProxy extends SafeCloseable {
    public interface CaptureCallback {
        public void onCaptureCompleted(CameraCaptureSessionProxy session, CaptureRequest request,
                TotalCaptureResult result);

        public void onCaptureFailed(CameraCaptureSessionProxy session, CaptureRequest request,
                CaptureFailure failure);

        public void onCaptureProgressed(CameraCaptureSessionProxy session, CaptureRequest request,
                CaptureResult partialResult);

        public void onCaptureSequenceAborted(CameraCaptureSessionProxy session, int sequenceId);

        public void onCaptureSequenceCompleted(CameraCaptureSessionProxy session, int sequenceId,
                long frameNumber);

        public void onCaptureStarted(CameraCaptureSessionProxy session, CaptureRequest request,
                long timestamp, long frameNumber);
    }

    public interface StateCallback {
        public void onActive(CameraCaptureSessionProxy session);

        public void onClosed(CameraCaptureSessionProxy session);

        public void onConfigureFailed(CameraCaptureSessionProxy session);

        public void onConfigured(CameraCaptureSessionProxy session);

        public void onReady(CameraCaptureSessionProxy session);
    }

    public void abortCaptures() throws CameraAccessException, CameraCaptureSessionClosedException;

    public int capture(CaptureRequest request, CaptureCallback listener, Handler handler)
            throws CameraAccessException, CameraCaptureSessionClosedException;

    public int captureBurst(List<CaptureRequest> requests, CaptureCallback listener, Handler
            handler) throws CameraAccessException, CameraCaptureSessionClosedException;

    @Override
    public void close();

    public CameraDeviceProxy getDevice();

    public int setRepeatingBurst(List<CaptureRequest> requests, CaptureCallback listener, Handler
            handler) throws CameraAccessException, CameraCaptureSessionClosedException;

    public int setRepeatingRequest(CaptureRequest request, CaptureCallback listener, Handler
            handler) throws CameraAccessException, CameraCaptureSessionClosedException;

    public void stopRepeating() throws CameraAccessException, CameraCaptureSessionClosedException;
}
