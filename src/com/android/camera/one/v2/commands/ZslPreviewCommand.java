/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.one.v2.commands;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.FrameServer.RequestType;
import com.android.camera.one.v2.core.Request;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.util.ApiHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delegate the first run of a frameserver command to a different
 * camera command than subsequent executions.
 */
public class ZslPreviewCommand implements CameraCommand {
    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mPreviewWarmupRequestBuilder;
    private final int mPreviewWarmupRequestType;
    private final RequestBuilder.Factory mZslRequestBuilder;
    private final int mZslRequestType;
    private final RequestBuilder.Factory mZslAndPreviewRequestBuilder;
    private final int mZslAndPreviewRequestType;
    private final int mWarmupBurstSize;
    private final AtomicBoolean mIsFirstRun;

    /**
     * Constructs a Preview. Note that it is the responsiblity of the
     * {@link RequestBuilder.Factory} to attach the relevant
     * {@link com.android.camera.one.v2.core.CaptureStream}s, such as the
     * viewfinder surface.
     */
    public ZslPreviewCommand(FrameServer frameServer,
          RequestBuilder.Factory previewWarmupRequestBuilder,
          int previewWarmupRequestType,
          RequestBuilder.Factory zslRequestBuilder,
          int zslRequestType,
          RequestBuilder.Factory zslAndPreviewRequestBuilder,
          int zslAndPreviewRequestType,
          int warmupBurstSize) {
        mFrameServer = frameServer;
        mPreviewWarmupRequestBuilder = previewWarmupRequestBuilder;
        mPreviewWarmupRequestType = previewWarmupRequestType;
        mZslRequestBuilder = zslRequestBuilder;
        mZslRequestType = zslRequestType;
        mZslAndPreviewRequestBuilder = zslAndPreviewRequestBuilder;
        mZslAndPreviewRequestType = zslAndPreviewRequestType;
        mWarmupBurstSize = warmupBurstSize;
        mIsFirstRun = new AtomicBoolean(true);
    }

    public void run() throws InterruptedException, CameraAccessException,
          CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        try (FrameServer.Session session = mFrameServer.createExclusiveSession()) {
            if (mIsFirstRun.getAndSet(false)) {
                if (ApiHelper.isLorLMr1() && ApiHelper.IS_NEXUS_6) {
                    // This is the work around of the face detection failure in b/20724126.
                    // We need to request a single preview frame followed by a burst of 5-frame ZSL
                    // before requesting the repeating preview and ZSL requests. We do it only for
                    // L, Nexus 6 and Haleakala.
                    List<Request> previewWarming = createWarmupBurst(mPreviewWarmupRequestBuilder,
                            mPreviewWarmupRequestType, 1);
                    session.submitRequest(previewWarming, RequestType.NON_REPEATING);
                }

                // Only run a warmup burst the first time this command is executed.
                List<Request> zslWarmingBurst =
                      createWarmupBurst(mZslRequestBuilder, mZslRequestType, mWarmupBurstSize);
                session.submitRequest(zslWarmingBurst, RequestType.NON_REPEATING);
            }

            // Build the zsl + preview repeating request.
            RequestBuilder zslAndPreviewRequest = mZslAndPreviewRequestBuilder.create(
                  mZslAndPreviewRequestType);
            List<Request> zslAndPreviewRepeating = Arrays.asList(zslAndPreviewRequest.build());

            // Submit the normal repeating request.
            session.submitRequest(zslAndPreviewRepeating, RequestType.REPEATING);
        }
    }

    private List<Request> createWarmupBurst(RequestBuilder.Factory builder, int type, int size)
          throws CameraAccessException {
        RequestBuilder zslRequest = builder.create(type);
        Request zslWarmingRequest = zslRequest.build();
        List<Request> zslWarmingBurst = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            zslWarmingBurst.add(zslWarmingRequest);
        }
        return zslWarmingBurst;
    }
}
