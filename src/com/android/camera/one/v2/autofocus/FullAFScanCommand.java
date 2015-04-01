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

package com.android.camera.one.v2.autofocus;

import static com.android.camera.one.v2.core.ResponseListeners.forPartialMetadata;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;

import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;

import java.util.Arrays;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Performs a full auto focus scan.
 */
@ParametersAreNonnullByDefault
final class FullAFScanCommand implements CameraCommand {
    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mBuilderFactory;
    private final int mTemplateType;

    /**
     * @param frameServer Used for sending requests to the camera.
     * @param builder Used for building requests.
     * @param templateType See
     *            {@link android.hardware.camera2.CameraDevice#createCaptureRequest}
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
    @Override
    public void run() throws InterruptedException, CameraAccessException,
            CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        FrameServer.Session session = mFrameServer.tryCreateExclusiveSession();
        if (session == null) {
            // If there are already other commands interacting with the
            // FrameServer, don't wait to run the AF command, instead just
            // abort.
            return;
        }
        try {
            AFTriggerResult afScanResult = new AFTriggerResult();

            // Start a repeating sequence of idle requests
            RequestBuilder idleBuilder = createAFIdleRequest(null);
            session.submitRequest(Arrays.asList(idleBuilder.build()),
                    FrameServer.RequestType.REPEATING);

            // Workaround for Nexus 6:
            // Sending an AF_TRIGGER_START, followed immediately by another
            // AF_TRIGGER_START may result in the driver deadlocking in its AF
            // state machine, in certain cases
            // (it's easy to reproduce this issue in relatively dark scenes
            // ~1-3inches from the device with AF_MODE_ON_ALWAYS_FLASH).
            // So, to avoid triggering this issue, always send an
            // AF_TRIGGER_CANCEL before *every* AF_TRIGGER_START.
            RequestBuilder cancelBuilder = createAFCancelRequest(null);
            session.submitRequest(Arrays.asList(cancelBuilder.build()),
                    FrameServer.RequestType.NON_REPEATING);

            // Start a repeating sequence of idle requests
            idleBuilder = createAFIdleRequest(afScanResult);
            session.submitRequest(Arrays.asList(idleBuilder.build()),
                    FrameServer.RequestType.REPEATING);

            // Build a request to send a single AF_TRIGGER
            RequestBuilder triggerBuilder = createAFTriggerRequest(afScanResult);
            session.submitRequest(Arrays.asList(triggerBuilder.build()),
                    FrameServer.RequestType.NON_REPEATING);

            // Block until the scan is done.
            // TODO If the HAL never transitions out of scanning mode, this will
            // block forever (or until interrupted because the app is paused).
            // So, maybe use a generous timeout and log as HAL errors.
            afScanResult.get();
        } finally {
            session.close();
        }
    }

    private RequestBuilder createAFIdleRequest(@Nullable AFTriggerResult triggerResultListener)
            throws CameraAccessException {
        RequestBuilder idleBuilder = mBuilderFactory.create(mTemplateType);
        if (triggerResultListener != null) {
            idleBuilder.addResponseListener(forPartialMetadata(triggerResultListener));
        }
        idleBuilder.setParam(CaptureRequest.CONTROL_MODE, CaptureRequest
                .CONTROL_MODE_AUTO);
        idleBuilder.setParam(CaptureRequest.CONTROL_AF_MODE, CaptureRequest
                .CONTROL_AF_MODE_AUTO);
        idleBuilder.setParam(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        return idleBuilder;
    }

    private RequestBuilder createAFTriggerRequest(AFTriggerResult afScanResult) throws
            CameraAccessException {
        RequestBuilder triggerBuilder = mBuilderFactory.create(mTemplateType);
        triggerBuilder.addResponseListener(forPartialMetadata(afScanResult));
        triggerBuilder.setParam(CaptureRequest.CONTROL_MODE, CaptureRequest
                .CONTROL_MODE_AUTO);
        triggerBuilder.setParam(CaptureRequest.CONTROL_AF_MODE, CaptureRequest
                .CONTROL_AF_MODE_AUTO);
        triggerBuilder.setParam(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START);
        return triggerBuilder;
    }

    private RequestBuilder createAFCancelRequest(@Nullable AFTriggerResult afScanResult) throws
            CameraAccessException {
        RequestBuilder triggerBuilder = mBuilderFactory.create(mTemplateType);
        if (afScanResult != null) {
            triggerBuilder.addResponseListener(forPartialMetadata(afScanResult));
        }
        triggerBuilder.setParam(CaptureRequest.CONTROL_MODE, CaptureRequest
                .CONTROL_MODE_AUTO);
        triggerBuilder.setParam(CaptureRequest.CONTROL_AF_MODE, CaptureRequest
                .CONTROL_AF_MODE_AUTO);
        triggerBuilder.setParam(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        return triggerBuilder;
    }
}
