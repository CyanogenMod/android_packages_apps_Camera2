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

package com.android.camera.one.v2.core;

import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

/**
 * Like {@link android.hardware.camera2.CameraCaptureSession.CaptureCallback},
 * but for events related to single requests.
 * <p>
 * See {@link ResponseListeners} for helper functions.
 */
public abstract class ResponseListener {
    /**
     * Note that this is typically invoked on the camera thread and at high
     * frequency, so implementations must execute quickly and not make
     * assumptions regarding the thread they are on.
     *
     * @See {@link android.hardware.camera2.CameraCaptureSession.CaptureCallback#onCaptureStarted}
     */
    public void onStarted(long timestamp) {
    }

    /**
     * Note that this is typically invoked on the camera thread and at high
     * frequency, so implementations must execute quickly and not make
     * assumptions regarding the thread they are on.
     *
     * @See {@link android.hardware.camera2.CameraCaptureSession.CaptureCallback#onCaptureProgressed}
     */
    public void onProgressed(CaptureResult partialResult) {
    }

    /**
     * Note that this is typically invoked on the camera thread and at high
     * frequency, so implementations must execute quickly and not make
     * assumptions regarding the thread they are on.
     *
     * @See {@link android.hardware.camera2.CameraCaptureSession.CaptureCallback#onCaptureCompleted}
     */
    public void onCompleted(TotalCaptureResult result) {
    }

    /**
     * Note that this is typically invoked on the camera thread and at high
     * frequency, so implementations must execute quickly and not make
     * assumptions regarding the thread they are on.
     *
     * @See {@link android.hardware.camera2.CameraCaptureSession.CaptureCallback#onCaptureFailed}
     */
    public void onFailed(CaptureFailure failure) {
    }

    /**
     * Note that this is typically invoked on the camera thread and at high
     * frequency, so implementations must execute quickly and not make
     * assumptions regarding the thread they are on.
     *
     * @See {@link android.hardware.camera2.CameraCaptureSession.CaptureCallback#onCaptureSequenceAborted}
     */
    public void onSequenceAborted(int sequenceId) {
    }

    /**
     * Note that this is typically invoked on the camera thread and at high
     * frequency, so implementations must execute quickly and not make
     * assumptions regarding the thread they are on.
     *
     * @See {@link android.hardware.camera2.CameraCaptureSession.CaptureCallback#onCaptureSequenceCompleted}
     */
    public void onSequenceCompleted(int sequenceId, long frameNumber) {
    }
}
