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

import com.android.camera.async.CloseableHandlerThread;
import com.android.camera.async.Lifetime;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;

public class FrameServerFactory {
    private FrameServer mFrameServer;

    public FrameServerFactory(Lifetime lifetime, CameraCaptureSessionProxy cameraCaptureSession) {
        CloseableHandlerThread cameraHandler = new CloseableHandlerThread("CameraMetadataHandler");
        lifetime.add(cameraHandler);
        // TODO Maybe enable closing the FrameServer along with the lifetime?
        // It would allow clean reuse of the cameraCaptureSession with
        // non-frameserver interaction
        mFrameServer = new FrameServerImpl(new TagDispatchCaptureSession(cameraCaptureSession,
                cameraHandler.get()));
    }

    public FrameServer provideFrameServer() {
        return mFrameServer;
    }
}
