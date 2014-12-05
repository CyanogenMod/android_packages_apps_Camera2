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

package com.android.camera.one.v2;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.BufferQueue;
import com.android.camera.async.CallbackRunnable;
import com.android.camera.async.CloseableHandlerThread;
import com.android.camera.async.ConcurrentBufferQueue;
import com.android.camera.async.ConcurrentState;
import com.android.camera.async.ConstantPollable;
import com.android.camera.async.FilteredUpdatable;
import com.android.camera.async.FutureResult;
import com.android.camera.async.HandlerExecutor;
import com.android.camera.async.Listenable;
import com.android.camera.async.ListenableConcurrentState;
import com.android.camera.async.Pollable;
import com.android.camera.async.ResettingDelayedExecutor;
import com.android.camera.async.SafeCloseable;
import com.android.camera.async.Updatable;
import com.android.camera.one.CameraDirectionProvider;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.FocusState;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceRequestBuilderFactory;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.commands.AFScanHoldReset;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.FullAFScanCommand;
import com.android.camera.one.v2.commands.LoggingCameraCommand;
import com.android.camera.one.v2.commands.PreviewCommand;
import com.android.camera.one.v2.commands.RunnableCameraCommand;
import com.android.camera.one.v2.commands.StaticPictureCommand;
import com.android.camera.one.v2.common.CaptureSessionCreator;
import com.android.camera.one.v2.common.DeferredManualAutoFocus;
import com.android.camera.one.v2.common.DeferredPictureTaker;
import com.android.camera.one.v2.common.FullSizeAspectRatioProvider;
import com.android.camera.one.v2.common.GenericOneCameraImpl;
import com.android.camera.one.v2.common.ManualAutoFocusImpl;
import com.android.camera.one.v2.common.MeteringParameters;
import com.android.camera.one.v2.common.PictureCallbackAdaptor;
import com.android.camera.one.v2.common.PollableAEMode;
import com.android.camera.one.v2.common.PollableAERegion;
import com.android.camera.one.v2.common.PollableAFRegion;
import com.android.camera.one.v2.common.PollableZoomedCropRegion;
import com.android.camera.one.v2.common.PreviewSizeSelector;
import com.android.camera.one.v2.common.SensorOrientationProvider;
import com.android.camera.one.v2.common.SupportedPreviewSizeProvider;
import com.android.camera.one.v2.core.DecoratingRequestBuilderBuilder;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.MetadataResponseListener;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.SimpleCaptureStream;
import com.android.camera.one.v2.core.TagDispatchCaptureSession;
import com.android.camera.one.v2.core.TimestampResponseListener;
import com.android.camera.one.v2.sharedimagereader.ImageDistributor;
import com.android.camera.one.v2.sharedimagereader.ImageDistributorOnImageAvailableListener;
import com.android.camera.one.v2.sharedimagereader.SharedImageReader;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.ScopedFactory;
import com.android.camera.util.Size;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 */
public class SimpleJpegOneCameraFactory {
    /**
     * All of the variables available when the CameraDevice is available.
     */
    private static class CameraScope {
        public final CameraDevice device;
        public final CameraCharacteristics characteristics;
        public final Handler mainHandler;
        public final FutureResult<GenericOneCameraImpl.PictureTaker> pictureTaker;
        public final FutureResult<GenericOneCameraImpl.ManualAutoFocus> manualAutoFocus;
        public final ConcurrentState<Integer> afState;
        public final ConcurrentState<FocusState> focusState;
        public final ConcurrentState<Boolean> readyState;
        public final ConcurrentState<Float> zoomState;
        public final ConcurrentState<Boolean> previewStartSuccess;
        public final Size pictureSize;

        private CameraScope(
                CameraDevice device,
                CameraCharacteristics characteristics,
                Handler mainHandler,
                FutureResult<GenericOneCameraImpl.PictureTaker> pictureTaker,
                FutureResult<GenericOneCameraImpl.ManualAutoFocus> manualAutoFocus,
                ConcurrentState<Integer> afState,
                ConcurrentState<FocusState> focusState,
                ConcurrentState<Boolean> readyState,
                ConcurrentState<Float> zoomState,
                ConcurrentState<Boolean> previewStartSuccess, Size pictureSize) {
            this.device = device;
            this.characteristics = characteristics;
            this.mainHandler = mainHandler;
            this.pictureTaker = pictureTaker;
            this.manualAutoFocus = manualAutoFocus;
            this.afState = afState;
            this.focusState = focusState;
            this.readyState = readyState;
            this.zoomState = zoomState;
            this.previewStartSuccess = previewStartSuccess;
            this.pictureSize = pictureSize;
        }
    }

    private static class PreviewSurfaceScope {
        public final CameraScope cameraScope;
        public final Surface previewSurface;
        public final ImageReader imageReader;
        public final CloseableHandlerThread captureSessionOpenHandler;

        private PreviewSurfaceScope(CameraScope cameraScope,
                Surface previewSurface,
                ImageReader imageReader,
                CloseableHandlerThread captureSessionOpenHandler) {
            this.cameraScope = cameraScope;
            this.previewSurface = previewSurface;
            this.imageReader = imageReader;
            this.captureSessionOpenHandler = captureSessionOpenHandler;
        }
    }

    private static class CameraCaptureSessionScope {
        public final Runnable startPreviewRunnable;
        public final GenericOneCameraImpl.PictureTaker pictureTaker;
        public final GenericOneCameraImpl.ManualAutoFocus manualAutoFocus;

        private CameraCaptureSessionScope(Runnable startPreviewRunnable,
                GenericOneCameraImpl.PictureTaker pictureTaker,
                GenericOneCameraImpl.ManualAutoFocus manualAutoFocus) {
            this.startPreviewRunnable = startPreviewRunnable;
            this.pictureTaker = pictureTaker;
            this.manualAutoFocus = manualAutoFocus;
        }
    }

    private static CameraScope provideCameraScope(CameraDevice device, CameraCharacteristics
            characteristics, Handler mainHandler, Size pictureSize) {
        FutureResult<GenericOneCameraImpl.PictureTaker> pictureTakerFutureResult = new FutureResult<>();
        FutureResult<GenericOneCameraImpl.ManualAutoFocus> manualAutoFocusFutureResult = new FutureResult<>();
        ConcurrentState<Integer> afState = new ConcurrentState<>();
        ConcurrentState<FocusState> focusState = new ConcurrentState<>();
        ConcurrentState<Boolean> readyState = new ConcurrentState<>();
        ConcurrentState<Float> zoomState = new ConcurrentState<>();
        ConcurrentState<Boolean> previewStartSuccess = new ConcurrentState<>();

        return new CameraScope(device, characteristics, mainHandler, pictureTakerFutureResult,
                manualAutoFocusFutureResult, afState, focusState,
                readyState, zoomState, previewStartSuccess, pictureSize);
    }

    private static Set<SafeCloseable> provideCloseListeners(final CameraScope scope) {
        // FIXME Something in here must close() the CameraDevice, ImageReader,
        // and CameraCaptureSession.
        // TODO Maybe replace this with two things:
        // 1. A blocking AutoCloseable which will wait until the device is
        // closed.
        // 2. A ConcurrentState<> for other things to subscribe to, or poll, for
        // closing state.
        // - Close the session
        // - Close all CloseableHandlerThreads
        // - Close the global timestamp stream
        // - Close the CameraCommandExecutor
        // - Close the SharedImageReader (on a separate thread to only release
        // it when all consumers release any outstanding images.)
        Set<SafeCloseable> closeables = new HashSet<>();
        closeables.add(new SafeCloseable() {
            @Override
            public void close() {
                scope.device.close();
            }
        });
        return closeables;
    }

    private static Executor provideMainHandlerExecutor(CameraScope scope) {
        return new HandlerExecutor(scope.mainHandler);
    }

    private static Listenable<Integer> provideAFStateListenable(CameraScope scope) {
        return new ListenableConcurrentState<>(scope.afState, provideMainHandlerExecutor(scope));
    }

    private static Listenable<FocusState> provideFocusStateListenable(CameraScope scope) {
        return new ListenableConcurrentState<>(scope.focusState, provideMainHandlerExecutor(scope));
    }

    private static Listenable<Boolean> provideReadyStateListenable(CameraScope scope) {
        return new ListenableConcurrentState<>(scope.readyState, provideMainHandlerExecutor(scope));
    }

    private static CameraDirectionProvider provideCameraDirectionProvider(CameraScope scope) {
        return new CameraDirectionProvider(scope.characteristics);
    }

    private static OneCamera.Facing provideCameraDirection(CameraScope scope) {
        return provideCameraDirectionProvider(scope).getDirection();
    }

    private static float provideMaxZoom(CameraCharacteristics characteristics) {
        return characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    private static Updatable<Float> provideZoom(CameraScope scope) {
        return scope.zoomState;
    }

    private static ScopedFactory<Surface, Runnable> providePreviewStarter(final CameraScope
            cameraScope) {
        return new ScopedFactory<Surface, Runnable>() {
            @Override
            public Runnable get(Surface previewSurface) {
                PreviewSurfaceScope previewSurfaceScope = providePreviewSurfaceScope(cameraScope,
                        previewSurface);
                return provideCaptureSessionCreator(previewSurfaceScope);
            }
        };
    }

    private static List<Surface> provideSurfaceList(PreviewSurfaceScope scope) {
        List<Surface> surfaces = new ArrayList<>();
        surfaces.add(scope.imageReader.getSurface());
        surfaces.add(scope.previewSurface);
        return surfaces;
    }

    private static CaptureSessionCreator provideCaptureSessionCreator(PreviewSurfaceScope scope) {
        CameraDeviceProxy device = new CameraDeviceProxy(scope.cameraScope.device);
        Handler cameraHandler = scope.captureSessionOpenHandler.get();
        List<Surface> surfaces = provideSurfaceList(scope);
        FutureResult<CameraCaptureSessionProxy> sessionFuture = provideCaptureSessionFuture(scope);
        ScopedFactory<CameraCaptureSessionProxy, Runnable> captureSessionScopeEntrance =
                provideCaptureSessionScopeEntrance(scope);
        return new CaptureSessionCreator(device, cameraHandler, surfaces, sessionFuture,
                captureSessionScopeEntrance);
    }

    private static ScopedFactory<CameraCaptureSessionProxy, Runnable>
            provideCaptureSessionScopeEntrance(
                    final PreviewSurfaceScope previewSurfaceScope) {
        return new ScopedFactory<CameraCaptureSessionProxy, Runnable>() {
            @Override
            public Runnable get(CameraCaptureSessionProxy cameraCaptureSession) {
                final CameraCaptureSessionScope scope = provideCameraCaptureSessionScope
                        (previewSurfaceScope, cameraCaptureSession);
                return new Runnable() {
                    @Override
                    public void run() {
                        // Update the future for image capture
                        previewSurfaceScope.cameraScope.pictureTaker.setValue(scope.pictureTaker);
                        // Update the future for tap-to-focus
                        previewSurfaceScope.cameraScope.manualAutoFocus.setValue(scope
                                .manualAutoFocus);

                        // Dispatch to startPreviewRunnable
                        scope.startPreviewRunnable.run();
                    }
                };
            }
        };
    }

    private static CameraCaptureSessionScope provideCameraCaptureSessionScope(
            final PreviewSurfaceScope previewSurfaceScope, CameraCaptureSessionProxy
            cameraCaptureSession) {
        ConcurrentBufferQueue<Long> globalTimestampStream = new ConcurrentBufferQueue<>();
        // FIXME Wire this up to be closed when done.
        CloseableHandlerThread imageDistributorThread = new CloseableHandlerThread
                ("ImageDistributor");
        ImageDistributor imageDistributor = provideImageDistributor(previewSurfaceScope
                .imageReader, globalTimestampStream, imageDistributorThread.get());
        final SharedImageReader sharedImageReader = provideSharedImageReader(previewSurfaceScope,
                imageDistributor);
        final FrameServer frameServer = new FrameServer(new TagDispatchCaptureSession
                (cameraCaptureSession, previewSurfaceScope.captureSessionOpenHandler.get()));

        ExecutorService miscThreadPool = Executors.newCachedThreadPool();

        final CameraCommandExecutor commandExecutor = new CameraCommandExecutor(miscThreadPool);

        SimpleCaptureStream previewSurfaceStream = new SimpleCaptureStream(
                previewSurfaceScope.previewSurface);
        RequestBuilder.Factory rootRequestBuilder = new DecoratingRequestBuilderBuilder(
                new CameraDeviceRequestBuilderFactory(previewSurfaceScope.cameraScope.device))
                .withResponseListener(new MetadataResponseListener<Integer>(CaptureResult
                        .CONTROL_AF_STATE, new FilteredUpdatable<Integer>(previewSurfaceScope
                                .cameraScope.afState)))
                .withResponseListener(new TimestampResponseListener
                        (globalTimestampStream));
        ConcurrentState<MeteringParameters> meteringState = new ConcurrentState<>();
        final Pollable<Rect> cropRegion = new PollableZoomedCropRegion
                (previewSurfaceScope.cameraScope.characteristics,
                        previewSurfaceScope.cameraScope.zoomState);
        OrientationManager.DeviceOrientation sensorOrientation = new SensorOrientationProvider
                (previewSurfaceScope.cameraScope
                        .characteristics).getSensorOrientation();
        final Pollable<MeteringRectangle[]> afRegionState = new PollableAFRegion(meteringState,
                cropRegion, sensorOrientation);
        final Pollable<MeteringRectangle[]> aeRegionState = new PollableAERegion(meteringState,
                cropRegion, sensorOrientation);
        // FIXME Use settings to retrieve current flash mode.
        final Pollable<Integer> aeModeState = new PollableAEMode(new ConstantPollable<>(OneCamera
                .PhotoCaptureParameters.Flash.OFF));
        final RequestBuilder.Factory zoomedRequestBuilderFactory = new DecoratingRequestBuilderBuilder(
                rootRequestBuilder)
                .withParam(CaptureRequest.SCALER_CROP_REGION, cropRegion);
        final RequestBuilder.Factory meteredZoomedRequestBuilderFactory = new
                DecoratingRequestBuilderBuilder(zoomedRequestBuilderFactory)
                        .withParam(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .withParam(CaptureRequest.CONTROL_AF_REGIONS, afRegionState)
                        .withParam(CaptureRequest.CONTROL_AE_REGIONS, aeRegionState)
                        .withParam(CaptureRequest.CONTROL_AE_MODE, aeModeState);
        final RequestBuilder.Factory previewRequestBuilder =
                new DecoratingRequestBuilderBuilder(meteredZoomedRequestBuilderFactory)
                        .withStream(previewSurfaceStream);
        // TODO Implement Manual Exposure: Decorate with current manual
        // exposure level, or Auto.
        final RequestBuilder.Factory continuousPreviewRequestBuilder =
                new DecoratingRequestBuilderBuilder(previewRequestBuilder)
                        .withParam(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        .withParam(CaptureRequest.CONTROL_AF_MODE, CaptureRequest
                                .CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        int templateType = CameraDevice.TEMPLATE_PREVIEW;
        PreviewCommand previewCommand = new PreviewCommand(frameServer,
                continuousPreviewRequestBuilder, templateType);
        final RunnableCameraCommand previewRunner = new RunnableCameraCommand(commandExecutor,
                new LoggingCameraCommand(previewCommand, "preview"));
        // Restart the preview whenever the zoom, ae regions, or af regions
        // changes.
        previewSurfaceScope.cameraScope.zoomState.addCallback(new CallbackRunnable(previewRunner)
                , miscThreadPool);

        CameraCommand afScanCommand = new LoggingCameraCommand(new FullAFScanCommand(frameServer,
                previewRequestBuilder, templateType), "AF Scan");
        // TODO Ensure that this is closed
        ResettingDelayedExecutor afResetDelayedExecutor = new ResettingDelayedExecutor(Executors
                .newSingleThreadScheduledExecutor(), 3L, TimeUnit.SECONDS);
        AFScanHoldReset afScanHoldResetCommand = new AFScanHoldReset(afScanCommand,
                afResetDelayedExecutor, previewRunner, meteringState);
        final RunnableCameraCommand afRunner = new RunnableCameraCommand(commandExecutor,
                afScanHoldResetCommand);

        // TODO Implement Manual Exposure: Add a separate listener to the
        // current exposure state to run previewRunner on each update.
        // TODO Implement ready-state: Add a listener for shared-image-reader
        // availability and frame-server availability, AND the results together
        // and update the ready-state.
        final Updatable<ImageProxy> imageSaver = new Updatable<ImageProxy>() {
            @Override
            public void update(ImageProxy imageProxy) {
                // FIXME Replace stub with actual implementation.
                imageProxy.close();
            }
        };

        GenericOneCameraImpl.PictureTaker pictureTaker = new GenericOneCameraImpl.PictureTaker() {
            @Override
            public void takePicture(OneCamera.PhotoCaptureParameters params, CaptureSession session) {
                RequestBuilder.Factory requestBuilderFactory = new
                        DecoratingRequestBuilderBuilder(previewRequestBuilder);
                Runnable pictureTakingRunnable = providePictureRunnable(params, session,
                        commandExecutor, frameServer, requestBuilderFactory, sharedImageReader,
                        imageSaver, provideMainHandlerExecutor(previewSurfaceScope.cameraScope));
                pictureTakingRunnable.run();
            }
        };
        GenericOneCameraImpl.ManualAutoFocus manualAutoFocus = new ManualAutoFocusImpl(
                meteringState, afRunner);
        return new CameraCaptureSessionScope(previewRunner, pictureTaker, manualAutoFocus);
    }

    private static Runnable providePictureRunnable(OneCamera.PhotoCaptureParameters params,
            CaptureSession session, CameraCommandExecutor cameraCommandExecutor,
            FrameServer frameServer, RequestBuilder.Factory requestBuilderFactory,
            SharedImageReader sharedImageReader, Updatable<ImageProxy> imageSaver,
            Executor mainExecutor) {
        // TODO Add Flash support via PhotoCommand & FlashCommand
        PictureCallbackAdaptor pictureCallbackAdaptor = new PictureCallbackAdaptor(params
                .callback, mainExecutor);
        Updatable<Void> imageExposureUpdatable = pictureCallbackAdaptor
                .provideQuickExposeUpdatable();
        CameraCommand photoCommand = new StaticPictureCommand(frameServer, requestBuilderFactory,
                sharedImageReader, imageSaver, imageExposureUpdatable);
        return new RunnableCameraCommand(cameraCommandExecutor, new LoggingCameraCommand
                (photoCommand, "Static Picture"));
    }

    private static CameraCommand providePhotoCommand(FrameServer frameServer, RequestBuilder
            .Factory builder, SharedImageReader imageReader, Updatable<ImageProxy> imageSaver,
            Updatable<Void> imageExposeUpdatable) {
        return new StaticPictureCommand(frameServer, builder, imageReader, imageSaver,
                imageExposeUpdatable);
    }

    private static SharedImageReader provideSharedImageReader(PreviewSurfaceScope scope,
            ImageDistributor imageDistributor) {
        return new SharedImageReader(scope.imageReader.getSurface(),
                scope.imageReader.getMaxImages() - 1, imageDistributor);
    }

    private static ImageDistributor provideImageDistributor(ImageReader imageReader,
            BufferQueue<Long> globalTimestampStream,
            Handler imageDistributorHandler) {
        ImageDistributor imageDistributor = new ImageDistributor(globalTimestampStream);
        imageReader.setOnImageAvailableListener(new ImageDistributorOnImageAvailableListener
                (imageDistributor), imageDistributorHandler);
        return imageDistributor;
    }

    private static FutureResult<CameraCaptureSessionProxy> provideCaptureSessionFuture(
            final PreviewSurfaceScope scope) {
        return new FutureResult<>(new Updatable<Future<CameraCaptureSessionProxy>>() {
            @Override
            public void update(Future<CameraCaptureSessionProxy> cameraCaptureSessionFuture) {
                try {
                    cameraCaptureSessionFuture.get();
                    scope.cameraScope.previewStartSuccess.update(true);
                } catch (InterruptedException | ExecutionException | CancellationException e) {
                    scope.cameraScope.previewStartSuccess.update(false);
                }
            }
        });
    }

    private static PreviewSurfaceScope providePreviewSurfaceScope(CameraScope cameraScope,
            Surface previewSurface) {
        CloseableHandlerThread captureSessionOpenHandler = new CloseableHandlerThread
                ("CaptureSessionStateHandler");
        ImageReader imageReader = provideImageReader(cameraScope.pictureSize);
        return new PreviewSurfaceScope(cameraScope, previewSurface, imageReader,
                captureSessionOpenHandler);
    }

    private static ImageReader provideImageReader(Size size) {
        return ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG,
                provideImageReaderSize());
    }

    private static int provideImageReaderSize() {
        return 10;
    }

    private static SupportedPreviewSizeProvider provideSupportedPreviewSizesProvider(
            CameraScope scope) {
        return new SupportedPreviewSizeProvider(scope.characteristics);
    }

    private static Size[] provideSupportedPreviewSizes(CameraScope scope) {
        return provideSupportedPreviewSizesProvider(scope).getSupportedPreviewSizes();
    }

    private static FullSizeAspectRatioProvider provideFullSizeAspectRatioProvider(CameraScope scope) {
        return new FullSizeAspectRatioProvider(scope.characteristics);
    }

    private static float provideFullSizeAspectRatio(CameraScope scope) {
        return provideFullSizeAspectRatioProvider(scope).get();
    }

    private static OneCamera.Facing provideDirection(CameraScope scope) {
        return new CameraDirectionProvider(scope.characteristics).getDirection();
    }

    private static GenericOneCameraImpl.ManualAutoFocus provideManualAutoFocus(CameraScope scope) {
        return new DeferredManualAutoFocus(scope.manualAutoFocus);
    }

    private static GenericOneCameraImpl.PictureTaker providePictureTaker(CameraScope scope) {
        return new DeferredPictureTaker(scope.pictureTaker);
    }

    private static Listenable<Boolean> providePreviewStartSuccessListenable(CameraScope scope) {
        return new ListenableConcurrentState<Boolean>(scope.previewStartSuccess,
                provideMainHandlerExecutor(scope));
    }

    private static PreviewSizeSelector providePreviewSizeSelector(CameraScope scope) {
        return new PreviewSizeSelector(provideImageFormat(), provideSupportedPreviewSizes(scope));
    }

    private static int provideImageFormat() {
        return ImageFormat.JPEG;
    }

    private static OneCamera provideOneCamera(CameraScope scope) {
        Set<SafeCloseable> closeListeners = provideCloseListeners(scope);
        GenericOneCameraImpl.PictureTaker pictureTaker = providePictureTaker(scope);
        GenericOneCameraImpl.ManualAutoFocus manualAutoFocus = provideManualAutoFocus(scope);
        Listenable<Integer> afStateListenable = provideAFStateListenable(scope);
        Listenable<FocusState> focusStateListenable = provideFocusStateListenable(scope);
        Listenable<Boolean> readyStateListenable = provideReadyStateListenable(scope);
        float maxZoom = provideMaxZoom(scope.characteristics);
        Updatable<Float> zoom = provideZoom(scope);
        ScopedFactory<Surface, Runnable> surfaceRunnableScopedFactory = providePreviewStarter(scope);
        Size[] supportedPreviewSizes = provideSupportedPreviewSizes(scope);
        float fullSizeAspectRatio = provideFullSizeAspectRatio(scope);
        OneCamera.Facing direction = provideDirection(scope);
        PreviewSizeSelector previewSizeSelector = providePreviewSizeSelector(scope);
        Listenable<Boolean> previewStartSuccessListenable = providePreviewStartSuccessListenable(scope);

        return new GenericOneCameraImpl(closeListeners, pictureTaker, manualAutoFocus,
                afStateListenable, focusStateListenable, readyStateListenable, maxZoom, zoom,
                supportedPreviewSizes, fullSizeAspectRatio, direction, previewSizeSelector,
                previewStartSuccessListenable, surfaceRunnableScopedFactory);
    }

    public static OneCamera provideOneCamera(CameraDevice device, CameraCharacteristics
            characteristics, Handler mainHandler, Size pictureSize) {
        return provideOneCamera(provideCameraScope(device, characteristics, mainHandler,
                pictureSize));
    }
}
