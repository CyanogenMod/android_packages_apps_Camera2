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

package com.android.camera.one.v2.components;

import java.util.Arrays;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;

import com.android.camera.one.v2.async.BufferQueue;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.MetadataChangeResponseListener;
import com.android.camera.one.v2.core.RequestBuilder;

/**
 * Performs a full auto focus scan.
 */
public class FullAFScanCommand implements CameraCommand {
    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mBuilderFactory;
    private final int mTemplateType;

    /**
     * @param frameServer Used for sending requests to the camera.
     * @param builder Used for building requests.
     * @param templateType See
     *            {@link android.hardware.camera2.CameraDevice#createCaptureRequest}
     *            .
     */
    public FullAFScanCommand(FrameServer frameServer, RequestBuilder.Factory builder, int
            templateType) {
        mFrameServer = frameServer;
        mBuilderFactory = builder;
        mTemplateType = templateType;
    }

    /**
     * Performs an auto-focus scan, blocking until the scan starts, runs, and
     * completes.
     */
    public void run() throws InterruptedException, CameraAccessException {
        FrameServer.Session session = mFrameServer.createExclusiveSession();
        try {
            // Build a request to send a repeating AF_IDLE
            RequestBuilder idleBuilder = mBuilderFactory.create(mTemplateType);
            // Listen to AF state changes resulting from this repeating AF_IDLE
            // request
            MetadataChangeResponseListener<Integer> mFocusStateChangeListener = new
                    MetadataChangeResponseListener<>(CaptureResult.CONTROL_AF_STATE);
            idleBuilder.addResponseListener(mFocusStateChangeListener);

            // Build a request to send a single AF_TRIGGER
            RequestBuilder triggerBuilder = mBuilderFactory.create(mTemplateType);
            triggerBuilder.setParam(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            triggerBuilder.setParam(CaptureRequest.CONTROL_AF_MODE, CaptureRequest
                    .CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            triggerBuilder.setParam(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);

            // Request a stream of changes to the AF-state
            try (BufferQueue<Integer> afStateBufferQueue =
                    mFocusStateChangeListener.getValueStream()) {
                // Submit the repeating request first.
                session.submitRequest(Arrays.asList(idleBuilder.build()), true);
                session.submitRequest(Arrays.asList(triggerBuilder.build()), false);

                while (true) {
                    try {
                        // FIXME The current MetadataChangeResponseListener
                        // doesn't actually close the stream, so this may block
                        // forever.
                        // TODO Using a timeout here would solve this problem.
                        Integer newAFState = afStateBufferQueue.getNext();
                        if (newAFState == CaptureResult.CONTROL_AF_STATE_INACTIVE ||
                                newAFState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                newAFState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                            return;
                        }
                    } catch (BufferQueue.BufferQueueClosedException e) {
                        // No more CaptureResults are being provided for the
                        // repeating request from {@link idleBuilder}. If we get
                        // here, it's because capture has been aborted, or a
                        // framework error (since we have an exclusive session,
                        // nothing else should have submitted repeating requests
                        // which would end this one). Either way, we should just
                        // return and release control of the session.
                        return;
                    }
                }
            }
        } finally {
            session.close();
        }
    }
}
