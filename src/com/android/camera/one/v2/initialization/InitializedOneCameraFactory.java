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
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;

import com.android.camera.async.CloseableHandlerThread;
import com.android.camera.async.ConcurrentState;
import com.android.camera.async.FilteredUpdatable;
import com.android.camera.async.FutureResult;
import com.android.camera.async.HandlerExecutor;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Listenable;
import com.android.camera.async.ListenableConcurrentState;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.autofocus.ManualAutoFocus;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.one.v2.photo.PictureTaker;
import com.android.camera.util.Size;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final CameraStarter mCameraStarter;
    private final CameraDeviceProxy mDevice;
    private final CameraCharacteristics mCameraCharacteristics;
    private final List<Surface> mOutputSurfaces;
    private final int mImageFormat;
    private final Handler mMainHandler;

    /**
     * @param cameraStarter Starts the camera, after initialization of the
     *            preview stream and capture session is complete.
     * @param device
     * @param cameraCharacteristics
     * @param outputSurfaces The set of output Surfaces (excluding the
     *            not-yet-available preview Surface) to use when configuring the
     *            capture session.
     * @param imageFormat The image format of the Surface for full-size images
     *            to be saved.
     * @param mainHandler
     */
    public InitializedOneCameraFactory(
            CameraStarter cameraStarter,
            CameraDeviceProxy device,
            CameraCharacteristics cameraCharacteristics,
            List<Surface> outputSurfaces,
            int imageFormat,
            Handler mainHandler) {
        mCameraStarter = cameraStarter;
        mDevice = device;
        mCameraCharacteristics = cameraCharacteristics;
        mOutputSurfaces = outputSurfaces;
        mImageFormat = imageFormat;
        mMainHandler = mainHandler;
    }

    public OneCamera provideOneCamera() {
        // Assembles and returns a OneCamera based on the CameraStarter.

        // All resources tied to the camera are contained (directly or
        // transitively) by this.
        final Lifetime cameraLifetime = new Lifetime();
        cameraLifetime.add(mDevice);

        // Create/wrap required threads.
        Executor mainThreadExecutor = new HandlerExecutor(mMainHandler);

        final CloseableHandlerThread cameraHandler = new CloseableHandlerThread("CameraHandler");
        cameraLifetime.add(cameraHandler);

        final ExecutorService miscThreadPool = Executors.newCachedThreadPool();

        // Since we cannot create an actual PictureTaker and ManualAutoFocus
        // until the CaptureSession is available, so create ones which defer to
        // a Future of the actual implementation.
        final FutureResult<PictureTaker> mPictureTaker = new FutureResult<>();
        PictureTaker pictureTaker = new DeferredPictureTaker(mPictureTaker);

        final FutureResult<ManualAutoFocus> mManualAutoFocus = new FutureResult<>();
        ManualAutoFocus manualAutoFocus = new DeferredManualAutoFocus(
                mManualAutoFocus);

        // The OneCamera interface exposes various types of state, either
        // through getters, setters, or the ability to register listeners.
        // Since these values are interacted with by multiple threads, we can
        // use {@link ConcurrentState} to provide this functionality safely.
        final ConcurrentState<Float> zoomState = new ConcurrentState<>();
        final ConcurrentState<Integer> afState = new ConcurrentState<>();
        final ConcurrentState<OneCamera.FocusState> focusState = new ConcurrentState<>();
        final ConcurrentState<Integer> afMode = new ConcurrentState<>();
        final ConcurrentState<Boolean> readyState = new ConcurrentState<>();
        final ConcurrentState<Boolean> previewStartSuccessState = new ConcurrentState<>();

        // Wrap state to be able to register listeners which run on the main
        // thread.
        Listenable<Integer> afStateListenable = new ListenableConcurrentState<>(afState,
                mainThreadExecutor);
        Listenable<OneCamera.FocusState> focusStateListenable = new ListenableConcurrentState<>(
                focusState, mainThreadExecutor);
        Listenable<Boolean> readyStateListenable = new ListenableConcurrentState<>(readyState,
                mainThreadExecutor);
        Listenable<Boolean> previewStateListenable = new ListenableConcurrentState<>
                (previewStartSuccessState, mainThreadExecutor);

        // Wrap each value in a filter to ensure that only differences pass through.
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
        final CaptureSessionCreator captureSessionCreator = new CaptureSessionCreator(mDevice,
                cameraHandler.get());

        PreviewStarter mPreviewStarter = new PreviewStarter(mOutputSurfaces,
                captureSessionCreator, miscThreadPool,
                new PreviewStarter.CameraCaptureSessionCreatedListener() {
                    @Override
                    public void onCameraCaptureSessionCreated(CameraCaptureSessionProxy session,
                            Surface previewSurface) {
                        CameraStarter.CameraControls controls = mCameraStarter.startCamera(
                                new Lifetime(cameraLifetime),
                                session, previewSurface,
                                zoomState, metadataCallback, readyState);
                        mPictureTaker.setValue(controls.getPictureTaker());
                        mManualAutoFocus.setValue(controls.getManualAutoFocus());
                    }
                });

        // Various constants/functionality which OneCamera implementations must
        // provide which do not depend on anything other than the
        // characteristics.
        PreviewSizeSelector previewSizeSelector = new PreviewSizeSelector(mImageFormat,
                getSupportedPreviewSizes());
        float maxZoom = getMaxZoom();
        Size[] supportedPreviewSizes = getSupportedPreviewSizes();
        float fullSizeAspectRatio = getFullSizeAspectRatio();
        OneCamera.Facing direction = getDirection();

        return new GenericOneCameraImpl(cameraLifetime, pictureTaker,
                manualAutoFocus, mainThreadExecutor, afStateListenable, focusStateListenable,
                readyStateListenable, maxZoom, zoomState, supportedPreviewSizes,
                fullSizeAspectRatio, direction, previewSizeSelector, previewStateListenable,
                mPreviewStarter);

    }

    private OneCamera.Facing getDirection() {
        switch (mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING)) {
            case CameraMetadata.LENS_FACING_BACK:
                return OneCamera.Facing.BACK;
            case CameraMetadata.LENS_FACING_FRONT:
                return OneCamera.Facing.FRONT;
        }
        return OneCamera.Facing.BACK;
    }

    private float getFullSizeAspectRatio() {
        Rect activeArraySize = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        return ((float) (activeArraySize.width())) / activeArraySize.height();
    }

    private Size[] getSupportedPreviewSizes() {
        StreamConfigurationMap config = mCameraCharacteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return Size.convert(config.getOutputSizes(SurfaceTexture.class));
    }

    private float getMaxZoom() {
        return mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }
}
