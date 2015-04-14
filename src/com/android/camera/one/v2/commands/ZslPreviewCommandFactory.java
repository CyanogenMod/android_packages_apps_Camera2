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

import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;

/**
 * Create a two stage frame server update command for ZSL implementations.
 */
public class ZslPreviewCommandFactory implements PreviewCommandFactory {
    private final FrameServer mFrameServer;
    private final RequestBuilder.Factory mZslRequestBuilder;

    public ZslPreviewCommandFactory(
          FrameServer frameServer,
          RequestBuilder.Factory zslRequestBuilder) {
        mFrameServer = frameServer;
        mZslRequestBuilder = zslRequestBuilder;
    }

    @Override
    public CameraCommand get(RequestBuilder.Factory previewRequestBuilder, int templateType) {
        return new ZslPreviewCommand(mFrameServer, mZslRequestBuilder, templateType,
              previewRequestBuilder, templateType, 5 /* warmupBurstSize */);
    }
}
