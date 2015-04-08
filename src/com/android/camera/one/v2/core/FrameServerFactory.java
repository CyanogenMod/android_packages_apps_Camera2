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

import android.os.Handler;

import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Observable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;

public class FrameServerFactory {
    private FrameServer mEphemeralFrameServer;
    private FrameServer mFrameServer;
    private Observable<Boolean> mReadyState;

    public FrameServerFactory(Lifetime lifetime, CameraCaptureSessionProxy cameraCaptureSession,
            HandlerFactory handlerFactory) {
        // The camera handler will be created with a very very high thread
        // priority because missing any input event potentially stalls the
        // camera preview and HAL.
        Handler cameraHandler = handlerFactory.create(lifetime, "CameraMetadataHandler",
              Thread.MAX_PRIORITY);

        // TODO Maybe enable closing the FrameServer along with the lifetime?
        // It would allow clean reuse of the cameraCaptureSession with
        // non-frameserver interaction.
        mEphemeralFrameServer = new FrameServerImpl(new TagDispatchCaptureSession
                (cameraCaptureSession, cameraHandler));

        ObservableFrameServer ofs = new ObservableFrameServer(mEphemeralFrameServer);
        mFrameServer = ofs;
        mReadyState = ofs;
    }

    /**
     * @return The {@link FrameServer} to use for interactions with the camera
     *         device which should affect the ready-state.
     */
    public FrameServer provideFrameServer() {
        return mFrameServer;
    }

    /**
     * @return The {@link FrameServer} to use for interactions with the camera
     *         device which should not affect the ready-state (e.g. trivial
     *         interactions such as restarting the preview as well as
     *         interactions which should not block the ready-state, such as
     *         performing a tap-to-focus routine).
     */
    public FrameServer provideEphemeralFrameServer() {
        return mEphemeralFrameServer;
    }

    /**
     * @return A hint as to whether or not a {@link FrameServer} session can be
     *         acquired immediately.
     */
    public Observable<Boolean> provideReadyState() {
        return mReadyState;
    }
}
