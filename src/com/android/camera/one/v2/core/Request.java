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

import android.hardware.camera2.CaptureRequest;

import com.android.camera.one.v2.camera2proxy.CaptureRequestBuilderProxy;

/**
 * A generic request for an image to be captured from a camera device via a
 * {@link FrameServer}.
 *
 * @See {@link RequestBuilder}
 */
public interface Request {
    /**
     * Implementations must allocate/acquire all resources necessary for this
     * request and block until acquisition is complete.
     * <p/>
     * For example, a request may need to acquire/reserve space in an
     * ImageReader to ensure that the image can be saved when the request
     * completes.
     *
     * @return The a proxy to an android {@link CaptureRequest.Builder} to build
     *         the {@link CaptureRequest} to be sent.
     * @throws InterruptedException if interrupted while blocking for resources
     *             to become available.
     */
    public CaptureRequestBuilderProxy allocateCaptureRequest() throws InterruptedException,
            ResourceAcquisitionFailedException;

    /**
     * @return The {@link ResponseListener} to receive events related to this
     *         request.
     */
    public ResponseListener getResponseListener();

    /**
     * Invoked if the associated request has been aborted before ever being
     * submitted to the {@link android.hardware.camera2.CameraCaptureSession}.<br>
     * Any resources acquired in {@link #allocateCaptureRequest} must be
     * released.<br>
     * No {@link ResponseListener} methods will be invoked if this is called.<br>
     * Implementations must tolerate multiple calls to abort().
     */
    public void abort();
}
