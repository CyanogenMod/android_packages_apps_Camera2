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

import android.annotation.TargetApi;
import android.hardware.camera2.CaptureResult;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.FilteredUpdatable;
import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Listenable;
import com.android.camera.async.MainThread;
import com.android.camera.one.OneCamera;
import com.android.camera.one.PreviewSizeSelector;
import com.android.camera.one.v2.autofocus.ManualAutoFocus;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.one.v2.photo.PictureTaker;
import com.android.camera.ui.motion.LinearScale;
import com.android.camera.util.Size;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Simplifies the construction of OneCamera instances which use the camera2 API
 * by handling the initialization sequence.
 * <p>
 * The type of camera created is specified by a {@link CameraStarter}.
 * <p>
 * This manages camera startup, which is nontrivial because it requires the
 * asynchronous acquisition of several dependencies:
 * <ol>
 * <li>The camera2 CameraDevice, which is available immediately.</li>
 * <li>The preview Surface, which is available after
 * {@link OneCamera#startPreview} is called.</li>
 * <li>The camera2 CameraCaptureSession, created asynchronously using the
 * CameraDevice and preview Surface.</li>
 * </ol>
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class InitializedOneCameraFactory {
    private final GenericOneCameraImpl mOneCamera;

    /**
     * @param cameraStarter Starts the camera, after initialization of the
     *            preview stream and capture session is complete.
     * @param outputSurfaces The set of output Surfaces (excluding the
     *            not-yet-available preview Surface) to use when configuring the
     *            capture session.
     */
    public InitializedOneCameraFactory(
            final Lifetime lifetime, final CameraStarter cameraStarter, CameraDeviceProxy device,
            List<Surface> outputSurfaces, MainThread mainThreadExecutor,
            HandlerFactory handlerFactory, float maxZoom, List<Size> supportedPreviewSizes,
            LinearScale lensRange, OneCamera.Facing direction) {
        // Assembles and returns a OneCamera based on the CameraStarter.

        // Create/wrap required threads.
        final Handler cameraHandler = handlerFactory.create(lifetime, "CameraHandler");

        // Since we cannot create an actual PictureTaker and ManualAutoFocus
        // until the CaptureSession is available, so create ones which defer to
        // a Future of the actual implementation.
        final SettableFuture<PictureTaker> mPictureTaker = SettableFuture.create();
        PictureTaker pictureTaker = new DeferredPictureTaker(mPictureTaker);

        final SettableFuture<ManualAutoFocus> mManualAutoFocus = SettableFuture.create();
        ManualAutoFocus manualAutoFocus = new DeferredManualAutoFocus(
                mManualAutoFocus);

        // The OneCamera interface exposes various types of state, either
        // through getters, setters, or the ability to register listeners.
        // Since these values are interacted with by multiple threads, we can
        // use {@link ConcurrentState} to provide this functionality safely.
        final ConcurrentState<Float> zoomState = new ConcurrentState<>(1.0f);
        final ConcurrentState<Integer> afState = new ConcurrentState<>(
                CaptureResult.CONTROL_AF_STATE_INACTIVE);
        final ConcurrentState<OneCamera.FocusState> focusState = new ConcurrentState<>(new
                OneCamera.FocusState(0.0f, false));
        final ConcurrentState<Integer> afMode = new ConcurrentState<>(CaptureResult
                .CONTROL_AF_MODE_OFF);
        final ConcurrentState<Boolean> readyState = new ConcurrentState<>(false);

        // Wrap state to be able to register listeners which run on the main
        // thread.
        Listenable<Integer> afStateListenable = new Listenable<>(afState,
                mainThreadExecutor);
        Listenable<OneCamera.FocusState> focusStateListenable = new Listenable<>(
                focusState, mainThreadExecutor);
        Listenable<Boolean> readyStateListenable = new Listenable<>(readyState,
                mainThreadExecutor);

        // Wrap each value in a filter to ensure that only differences pass
        // through.
        final MetadataCallback metadataCallback = new MetadataCallback(
                new FilteredUpdatable<>(afState),
                new FilteredUpdatable<>(focusState),
                new FilteredUpdatable<>(afMode));

        // The following handles the initialization sequence in which we receive
        // various dependencies at different times in the following sequence:
        // 1. CameraDevice
        // 2. The Surface on which to render the preview stream
        // 3. The CaptureSession
        // When all three of these are available, the {@link #CameraFactory} can
        // be used to assemble the actual camera functionality (e.g. to take
        // pictures, and run AF scans).

        // Note that these must be created in reverse-order to when they are run
        // because each stage depends on the previous one.
        final CaptureSessionCreator captureSessionCreator = new CaptureSessionCreator(device,
                cameraHandler);

        PreviewStarter mPreviewStarter = new PreviewStarter(outputSurfaces,
                captureSessionCreator,
                new PreviewStarter.CameraCaptureSessionCreatedListener() {
                    @Override
                    public void onCameraCaptureSessionCreated(CameraCaptureSessionProxy session,
                            Surface previewSurface) {
                        CameraStarter.CameraControls controls = cameraStarter.startCamera(
                                new Lifetime(lifetime),
                                session, previewSurface,
                                zoomState, metadataCallback, readyState);
                        mPictureTaker.set(controls.getPictureTaker());
                        mManualAutoFocus.set(controls.getManualAutoFocus());
                    }
                });

        PreviewSizeSelector previewSizeSelector =
              new Camera2PreviewSizeSelector(supportedPreviewSizes);

        mOneCamera = new GenericOneCameraImpl(lifetime, pictureTaker, manualAutoFocus, lensRange,
                mainThreadExecutor, afStateListenable, focusStateListenable, readyStateListenable,
                maxZoom, zoomState, direction, previewSizeSelector, mPreviewStarter);
    }

    public OneCamera provideOneCamera() {
        return mOneCamera;
    }
}
