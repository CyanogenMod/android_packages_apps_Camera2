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

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.view.Surface;

import com.android.camera.FatalErrorHandler;
import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.MainThread;
import com.android.camera.async.Observable;
import com.android.camera.async.Observables;
import com.android.camera.async.Updatable;
import com.android.camera.burst.BurstFacade;
import com.android.camera.debug.Loggers;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.config.OneCameraFeatureConfig;
import com.android.camera.one.v2.camera2proxy.AndroidImageReaderProxy;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceRequestBuilderFactory;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.BasicPreviewCommandFactory;
import com.android.camera.one.v2.common.BasicCameraFactory;
import com.android.camera.one.v2.common.SimpleCaptureStream;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.FrameServerFactory;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.RequestTemplate;
import com.android.camera.one.v2.core.ResponseListeners;
import com.android.camera.one.v2.errorhandling.FramerateJankDetector;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.initialization.CameraStarter;
import com.android.camera.one.v2.initialization.InitializedOneCameraFactory;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.one.v2.photo.LegacyPictureTakerFactory;
import com.android.camera.one.v2.photo.PictureTaker;
import com.android.camera.one.v2.photo.PictureTakerFactory;
import com.android.camera.one.v2.sharedimagereader.ManagedImageReader;
import com.android.camera.one.v2.sharedimagereader.SharedImageReaderFactory;
import com.android.camera.stats.UsageStatistics;
import com.android.camera.util.AndroidContext;
import com.android.camera.util.GservicesHelper;
import com.android.camera.util.Provider;
import com.android.camera.util.Size;
import com.google.common.base.Supplier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Creates a camera which takes jpeg images using the hardware encoder with
 * baseline functionality.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SimpleOneCameraFactory implements OneCameraFactory {
    private final int mImageFormat;
    private final int mMaxImageCount;
    private final ImageRotationCalculator mImageRotationCalculator;

    /**
     * @param imageFormat The {@link ImageFormat} to use for full-size images to
     *            be saved.
     * @param maxImageCount The size of the image reader to use for full-size
     *            images.
     */
    public SimpleOneCameraFactory(int imageFormat, int maxImageCount,
            ImageRotationCalculator imageRotationCalculator) {
        mImageFormat = imageFormat;
        mMaxImageCount = maxImageCount;
        mImageRotationCalculator = imageRotationCalculator;
    }

    @Override
    public OneCamera createOneCamera(final CameraDeviceProxy device,
            final OneCameraCharacteristics characteristics,
            final OneCameraFeatureConfig.CaptureSupportLevel supportLevel,
            final MainThread mainExecutor,
            final Size pictureSize,
            final ImageSaver.Builder imageSaverBuilder,
            final Observable<OneCamera.PhotoCaptureParameters.Flash> flashSetting,
            final Observable<Integer> exposureSetting,
            final Observable<Boolean> hdrSceneSetting,
            final BurstFacade burstFacade,
            final FatalErrorHandler fatalErrorHandler) {
        final Lifetime lifetime = new Lifetime();

        final ImageReaderProxy imageReader = new CloseWhenDoneImageReader(new LoggingImageReader(
                AndroidImageReaderProxy.newInstance(
                        pictureSize.getWidth(), pictureSize.getHeight(),
                        mImageFormat, mMaxImageCount),
                Loggers.tagFactory()));

        lifetime.add(imageReader);
        lifetime.add(device);

        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(imageReader.getSurface());

        /**
         * Finishes constructing the camera when prerequisites, e.g. the preview
         * stream and capture session, are ready.
         */
        CameraStarter cameraStarter = new CameraStarter() {
            @Override
            public CameraStarter.CameraControls startCamera(Lifetime cameraLifetime,
                    CameraCaptureSessionProxy cameraCaptureSession,
                    Surface previewSurface,
                    Observable<Float> zoomState,
                    Updatable<TotalCaptureResultProxy> metadataCallback,
                    Updatable<Boolean> readyState) {
                // Create the FrameServer from the CaptureSession.
                FrameServerFactory frameServerComponent = new FrameServerFactory(
                        new Lifetime(cameraLifetime), cameraCaptureSession, new HandlerFactory());

                CameraCommandExecutor cameraCommandExecutor = new CameraCommandExecutor(
                        Loggers.tagFactory(),
                        new Provider<ExecutorService>() {
                            @Override
                            public ExecutorService get() {
                                // Use a dynamically-expanding thread pool to
                                // allow any number of commands to execute
                                // simultaneously.
                                return Executors.newCachedThreadPool();
                            }
                        });

                // Create the shared image reader.
                SharedImageReaderFactory sharedImageReaderFactory =
                        new SharedImageReaderFactory(new Lifetime(cameraLifetime), imageReader,
                                new HandlerFactory());
                Updatable<Long> globalTimestampCallback =
                        sharedImageReaderFactory.provideGlobalTimestampQueue();
                ManagedImageReader managedImageReader =
                        sharedImageReaderFactory.provideSharedImageReader();

                // Create the request builder used by all camera operations.
                // Streams, ResponseListeners, and Parameters added to
                // this will be applied to *all* requests sent to the camera.
                RequestTemplate rootBuilder = new RequestTemplate
                        (new CameraDeviceRequestBuilderFactory(device));
                // The shared image reader must be wired to receive every
                // timestamp for every image (including the preview).
                rootBuilder.addResponseListener(
                        ResponseListeners.forTimestamps(globalTimestampCallback));
                rootBuilder.addStream(new SimpleCaptureStream(previewSurface));
                rootBuilder.addResponseListener(ResponseListeners.forFinalMetadata(
                        metadataCallback));

                FrameServer ephemeralFrameServer =
                      frameServerComponent.provideEphemeralFrameServer();

                // Create basic functionality (zoom, AE, AF).
                BasicCameraFactory basicCameraFactory = new BasicCameraFactory(new Lifetime
                        (cameraLifetime),
                        characteristics,
                        ephemeralFrameServer,
                        rootBuilder,
                        cameraCommandExecutor,
                        new BasicPreviewCommandFactory(ephemeralFrameServer),
                        flashSetting,
                        exposureSetting,
                        zoomState,
                        hdrSceneSetting,
                        CameraDevice.TEMPLATE_PREVIEW);

                // Register the dynamic updater via orientation supplier
                rootBuilder.setParam(CaptureRequest.JPEG_ORIENTATION,
                        mImageRotationCalculator.getSupplier());

                if (GservicesHelper.isJankStatisticsEnabled(AndroidContext.instance().get()
                      .getContentResolver())) {
                    rootBuilder.addResponseListener(
                          new FramerateJankDetector(Loggers.tagFactory(),
                                UsageStatistics.instance()));
                }

                RequestBuilder.Factory meteredZoomedRequestBuilder =
                        basicCameraFactory.provideMeteredZoomedRequestBuilder();

                // Create the picture-taker.
                PictureTaker pictureTaker;
                if (supportLevel == OneCameraFeatureConfig.CaptureSupportLevel.LEGACY_JPEG) {
                    pictureTaker = new LegacyPictureTakerFactory(imageSaverBuilder,
                            cameraCommandExecutor, mainExecutor,
                            frameServerComponent.provideFrameServer(),
                            meteredZoomedRequestBuilder, managedImageReader).providePictureTaker();
                } else {
                    pictureTaker = PictureTakerFactory.create(Loggers.tagFactory(), mainExecutor,
                            cameraCommandExecutor, imageSaverBuilder,
                            frameServerComponent.provideFrameServer(),
                            meteredZoomedRequestBuilder, managedImageReader, flashSetting)
                            .providePictureTaker();
                }

                // Wire-together ready-state.
                final Observable<Integer> availableImageCount = sharedImageReaderFactory
                        .provideAvailableImageCount();
                final Observable<Boolean> frameServerAvailability = frameServerComponent
                        .provideReadyState();
                Observable<Boolean> ready = Observables.transform(
                        Arrays.asList(availableImageCount, frameServerAvailability),
                        new Supplier<Boolean>() {
                            @Override
                            public Boolean get() {
                                boolean atLeastOneImageAvailable = availableImageCount.get() >= 1;
                                boolean frameServerAvailable = frameServerAvailability.get();
                                return atLeastOneImageAvailable && frameServerAvailable;
                            }
                        });

                lifetime.add(Observables.addThreadSafeCallback(ready, readyState));

                basicCameraFactory.providePreviewUpdater().run();

                return new CameraStarter.CameraControls(
                        pictureTaker,
                        basicCameraFactory.provideManualAutoFocus());
            }
        };

        float maxZoom = characteristics.getAvailableMaxDigitalZoom();
        List<Size> supportedPreviewSizes = characteristics.getSupportedPreviewSizes();
        OneCamera.Facing direction = characteristics.getCameraDirection();

        return new InitializedOneCameraFactory(lifetime, cameraStarter, device, outputSurfaces,
                mainExecutor, new HandlerFactory(), maxZoom, supportedPreviewSizes,
                characteristics.getLensFocusRange(), direction)
                .provideOneCamera();
    }
}
