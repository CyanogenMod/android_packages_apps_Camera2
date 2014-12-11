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

import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

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
    private final Executor mThreadPoolExecutor;
    private final CameraCaptureSessionCreatedListener mSessionListener;

    /**
     * @param outputSurfaces The set of output surfaces (except for the preview
     *            surface) to use.
     * @param captureSessionCreator
     * @param threadPoolExecutor A thread pool on which to wait for the capture
     *            session to be created and then invoke sessionListener.
     * @param sessionListener A callback to be invoked when the capture session
     *            has been created. It is executed on threadPoolExecutor.
     */
    public PreviewStarter(List<Surface> outputSurfaces,
            CaptureSessionCreator captureSessionCreator, Executor threadPoolExecutor,
            CameraCaptureSessionCreatedListener sessionListener) {
        mOutputSurfaces = outputSurfaces;
        mCaptureSessionCreator = captureSessionCreator;
        mThreadPoolExecutor = threadPoolExecutor;
        mSessionListener = sessionListener;
    }

    /**
     * See {@link OneCamera#startPreview}.
     *
     * @param surface The preview surface to use.
     * @param successCallback A thread-safe callback to return upon success or
     *            failure.
     */
    public void startPreview(final Surface surface, final Updatable<Boolean> successCallback) {
        try {
            // When we have the preview surface, start the capture session.
            List<Surface> surfaceList = new ArrayList<Surface>(mOutputSurfaces);
            surfaceList.add(surface);
            // TODO This could be cleaner by using a ListenableFuture for
            // the capture session since we would no longer need to spawn a
            // thread on miscThreadPool just to wait for the capture session
            // to become ready.
            final Future<CameraCaptureSessionProxy> sessionFuture = mCaptureSessionCreator
                    .createCaptureSession(surfaceList);

            mThreadPoolExecutor.execute(new Runnable() {
                public void run() {
                    boolean success = false;
                    try {
                        CameraCaptureSessionProxy captureSession = sessionFuture.get();
                        mSessionListener.onCameraCaptureSessionCreated(captureSession, surface);
                        success = true;
                    } catch (InterruptedException e) {
                        // Impossible exception.
                        throw new RuntimeException(e);
                    } catch (ExecutionException e) {
                        // Impossible exception.
                        throw new RuntimeException(e);
                    } catch (CancellationException e) {
                        e.printStackTrace();
                        // TODO Log
                        // This may have been cancelled because configuration
                        // failed, or because the camera was closed before the
                        // capture session could be configured.
                    } finally {
                        successCallback.update(success);
                    }
                }
            });
        } catch (Exception e) {
            successCallback.update(false);
            throw e;
        }
    }
}
