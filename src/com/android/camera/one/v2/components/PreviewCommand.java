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

import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;

/**
 * Sends preview requests to a {@link FrameServer}.
 */
public class PreviewCommand implements CameraCommand {
    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mBuilderFactory;
    private final int mRequestType;

    /**
     * Constructs a Preview. Note that it is the {@link RequestBuilder.Factory}
     * 's responsibility to attach the relevant
     * {@link com.android.camera.one.v2.core.CaptureStream}s, such as the
     * viewfinder surface.
     */
    public PreviewCommand(FrameServer frameServer, RequestBuilder.Factory builder,
            int requestType) {
        mFrameServer = frameServer;
        mBuilderFactory = builder;
        mRequestType = requestType;
    }

    public void run() throws InterruptedException, CameraAccessException {
        FrameServer.Session session = mFrameServer.createSession();
        try {
            RequestBuilder photoRequest = mBuilderFactory.create(mRequestType);
            session.submitRequest(Arrays.asList(photoRequest.build()), true);
        } finally {
            session.close();
        }
    }
}
