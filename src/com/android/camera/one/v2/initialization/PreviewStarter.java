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

package com.android.camera.one.v2.initialization;

import android.view.Surface;

import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.util.ApiHelper;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

/**
 * When the preview surface is available, creates a capture session, and then
 * notifies the listener when the session is available.
 */
class PreviewStarter {
    public interface CameraCaptureSessionCreatedListener {
        public void onCameraCaptureSessionCreated(CameraCaptureSessionProxy session, Surface
                previewSurface);
    }

    private final List<Surface> mOutputSurfaces;
    private final CaptureSessionCreator mCaptureSessionCreator;
    private final CameraCaptureSessionCreatedListener mSessionListener;

    /**
     * @param outputSurfaces The set of output surfaces (except for the preview
     *            surface) to use.
     * @param captureSessionCreator
     * @param sessionListener A callback to be invoked when the capture session
     *            has been created. It is executed on threadPoolExecutor.
     */
    public PreviewStarter(List<Surface> outputSurfaces,
            CaptureSessionCreator captureSessionCreator,
            CameraCaptureSessionCreatedListener sessionListener) {
        mOutputSurfaces = outputSurfaces;
        mCaptureSessionCreator = captureSessionCreator;
        mSessionListener = sessionListener;
    }

    /**
     * See {@link OneCamera#startPreview}.
     *
     * @param surface The preview surface to use.
     */
    public ListenableFuture<Void> startPreview(final Surface surface) {
        // When we have the preview surface, start the capture session.
        List<Surface> surfaceList = new ArrayList<>();

        // Workaround of the face detection failure on Nexus 5 and L. (b/21039466)
        // Need to create a capture session with the single preview stream first
        // to lock it as the first stream. Then resend the another session with preview
        // and JPEG stream.
        if (ApiHelper.isLorLMr1() && ApiHelper.IS_NEXUS_5) {
            surfaceList.add(surface);
            mCaptureSessionCreator.createCaptureSession(surfaceList);
            surfaceList.addAll(mOutputSurfaces);
        } else {
            surfaceList.addAll(mOutputSurfaces);
            surfaceList.add(surface);
        }

        final ListenableFuture<CameraCaptureSessionProxy> sessionFuture =
                mCaptureSessionCreator.createCaptureSession(surfaceList);

        return Futures.transform(sessionFuture,
                new AsyncFunction<CameraCaptureSessionProxy, Void>() {
                    @Override
                    public ListenableFuture<Void> apply(
                            CameraCaptureSessionProxy captureSession) throws Exception {
                        mSessionListener.onCameraCaptureSessionCreated(captureSession, surface);
                        return Futures.immediateFuture(null);
                    }
                });
    }
}
