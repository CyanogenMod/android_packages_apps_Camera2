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
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Build.VERSION_CODES;
import android.util.Range;
import android.view.Surface;

import com.android.camera.FatalErrorHandler;
import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.MainThread;
import com.android.camera.async.Observable;
import com.android.camera.async.Observables;
import com.android.camera.async.Updatable;
import com.android.camera.burst.BurstFacade;
import com.android.camera.burst.BurstTaker;
import com.android.camera.burst.BurstTakerImpl;
import com.android.camera.debug.Log.Tag;
import com.android.camera.debug.Logger;
import com.android.camera.debug.Loggers;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.config.OneCameraFeatureConfig.CaptureSupportLevel;
import com.android.camera.one.v2.camera2proxy.AndroidImageReaderProxy;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceRequestBuilderFactory;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.ZslPreviewCommandFactory;
import com.android.camera.one.v2.common.BasicCameraFactory;
import com.android.camera.one.v2.common.SimpleCaptureStream;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.FrameServerFactory;
import com.android.camera.one.v2.core.RequestTemplate;
import com.android.camera.one.v2.core.ResponseListener;
import com.android.camera.one.v2.core.ResponseListeners;
import com.android.camera.one.v2.errorhandling.FramerateJankDetector;
import com.android.camera.one.v2.errorhandling.RepeatFailureHandlerComponent;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.initialization.CameraStarter;
import com.android.camera.one.v2.initialization.InitializedOneCameraFactory;
import com.android.camera.one.v2.photo.ZslPictureTakerFactory;
import com.android.camera.one.v2.sharedimagereader.ZslSharedImageReaderFactory;
import com.android.camera.stats.UsageStatistics;
import com.android.camera.util.AndroidContext;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.GservicesHelper;
import com.android.camera.util.Provider;
import com.android.camera.util.Size;
import com.google.common.base.Supplier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TargetApi(VERSION_CODES.LOLLIPOP)
public class ZslOneCameraFactory implements OneCameraFactory {
    private static Tag TAG = new Tag("ZslOneCamFactory");

    private final Logger mLogger;
    private final int mImageFormat;
    private final int mMaxImageCount;
    private final int maxRingBufferSize;

    public ZslOneCameraFactory(int imageFormat, int maxImageCount) {
        mImageFormat = imageFormat;
        mMaxImageCount = maxImageCount;
        mLogger = Loggers.tagFactory().create(TAG);

        // Determines the maximum size of the ZSL ring-buffer.
        // Note that this is *different* from mMaxImageCount.
        // mMaxImageCount determines the size of the ImageReader used for large
        // (typically YUV) images to be saved. It is correlated with the total
        // number of in-progress captures which can simultaneously occur by
        // buffering captured images.
        // maxRingBufferSize determines the maximum size of the ring-buffer
        // (which uses a subset of the capacity of the ImageReader). This is
        // correlated to the maximum amount of look-back for zero-shutter-lag
        // photography. If this is greater than mMaxImageCount - 2, then it
        // places no additional constraints on ring-buffer size. That is,
        // the ring-buffer will expand to fill the entire capacity of the
        // ImageReader whenever possible.

        // A value of 1 here is adequate for single-frame ZSL capture, but
        // *must* be increased to support multi-frame burst capture with
        // zero-shutter-lag.
        maxRingBufferSize = 1;
    }

    /**
     * Slows down the requested camera frame for Nexus 5 back camera issue. This
     * hack is for the Back Camera for Nexus 5. Requesting on full YUV frames at
     * 30 fps causes the video preview to deliver frames out of order, mostly
     * likely due to the overloading of the ISP, and/or image bandwith. The
     * short-term solution is to back off the frame rate to unadvertised, valid
     * frame rate of 28 fps. The long-term solution is to advertise this [7,28]
     * frame rate range in the HAL and get buy-in from the manufacturer to
     * support and CTS this feature. Then framerate process can occur in more
     * integrated manner. The tracking bug for this issue is b/18950682.
     *
     * @param requestTemplate Request template that will be applied to the
     *            current camera device
     */
    private void applyNexus5BackCameraFrameRateWorkaround(RequestTemplate requestTemplate) {
        Range<Integer> frameRateBackOff = new Range<>(7, 28);
        mLogger.v("Applying Nexus5 specific framerate backoff of " + frameRateBackOff);
        requestTemplate.setParam(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, frameRateBackOff);
    }

    @Override
    public OneCamera createOneCamera(final CameraDeviceProxy device,
            final OneCameraCharacteristics characteristics,
            CaptureSupportLevel featureConfig,
            final MainThread mainThread,
            Size pictureSize,
            final ImageSaver.Builder imageSaverBuilder,
            final Observable<OneCamera.PhotoCaptureParameters.Flash> flashSetting,
            final Observable<Integer> exposureSetting,
            final Observable<Boolean> hdrSceneSetting,
            final BurstFacade burstFacade,
            final FatalErrorHandler fatalErrorHandler) {
        final Lifetime lifetime = new Lifetime();

        final ImageReaderProxy imageReader = new CloseWhenDoneImageReader(
                new LoggingImageReader(AndroidImageReaderProxy.newInstance(
                        pictureSize.getWidth(), pictureSize.getHeight(),
                        mImageFormat, mMaxImageCount), Loggers.tagFactory()));

        lifetime.add(imageReader);
        lifetime.add(device);

        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(imageReader.getSurface());
        if (burstFacade.getInputSurface() != null) {
            outputSurfaces.add(burstFacade.getInputSurface());
        }

        /**
         * Finishes constructing the camera when prerequisites, e.g. the preview
         * stream and capture session, are ready.
         */
        CameraStarter cameraStarter = new CameraStarter() {
            @Override
            public CameraControls startCamera(Lifetime cameraLifetime,
                    CameraCaptureSessionProxy cameraCaptureSession,
                    Surface previewSurface,
                    Observable<Float> zoomState,
                    Updatable<TotalCaptureResultProxy> metadataCallback,
                    Updatable<Boolean> readyStateCallback) {
                // Create the FrameServer from the CaptureSession.
                FrameServerFactory frameServerComponent = new FrameServerFactory(new Lifetime
                        (cameraLifetime), cameraCaptureSession, new HandlerFactory());

                FrameServer frameServer = frameServerComponent.provideFrameServer();
                FrameServer ephemeralFrameServer = frameServerComponent
                        .provideEphemeralFrameServer();

                // Create the shared image reader.
                ZslSharedImageReaderFactory sharedImageReaderFactory =
                        new ZslSharedImageReaderFactory(new Lifetime(cameraLifetime),
                                imageReader, new HandlerFactory(), maxRingBufferSize);

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

                // Create the request builder used by all camera operations.
                // Streams, ResponseListeners, and Parameters added to
                // this will be applied to *all* requests sent to the camera.
                RequestTemplate rootTemplate = new RequestTemplate(
                        new CameraDeviceRequestBuilderFactory(device));
                rootTemplate.addResponseListener(sharedImageReaderFactory
                        .provideGlobalResponseListener());
                rootTemplate.addResponseListener(ResponseListeners
                        .forFinalMetadata(metadataCallback));

                // Create the request builder for the preview warmup in order to workaround
                // the face detection failure. This is a work around of the HAL face detection
                // failure in b/20724126.
                RequestTemplate previewWarmupTemplate = new RequestTemplate(rootTemplate);
                previewWarmupTemplate.addStream(new SimpleCaptureStream(previewSurface));

                // Create the request builder for the ZSL stream
                RequestTemplate zslTemplate = new RequestTemplate(rootTemplate);
                zslTemplate.addStream(sharedImageReaderFactory.provideZSLStream());

                // Create the request builder that will be used by most camera
                // operations.
                RequestTemplate zslAndPreviewTemplate = new RequestTemplate(zslTemplate);
                zslAndPreviewTemplate.addStream(new SimpleCaptureStream(previewSurface));

                boolean isBackCamera = characteristics.getCameraDirection() ==
                        OneCamera.Facing.BACK;

                if (isBackCamera && ApiHelper.IS_NEXUS_5) {
                    applyNexus5BackCameraFrameRateWorkaround(zslTemplate);
                }

                // Create basic functionality (zoom, AE, AF).
                BasicCameraFactory basicCameraFactory = new BasicCameraFactory(
                        new Lifetime(cameraLifetime),
                        characteristics,
                        ephemeralFrameServer,
                        zslAndPreviewTemplate,
                        cameraCommandExecutor,
                        new ZslPreviewCommandFactory(ephemeralFrameServer,
                                previewWarmupTemplate,
                                zslTemplate),
                        flashSetting,
                        exposureSetting,
                        zoomState,
                        hdrSceneSetting,
                        CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);

                lifetime.add(cameraCommandExecutor);

                // Create the picture-taker.
                ZslPictureTakerFactory pictureTakerFactory = ZslPictureTakerFactory.create(
                        Loggers.tagFactory(),
                        mainThread,
                        cameraCommandExecutor,
                        imageSaverBuilder,
                        frameServer,
                        basicCameraFactory.provideMeteredZoomedRequestBuilder(),
                        sharedImageReaderFactory.provideSharedImageReader(),
                        sharedImageReaderFactory.provideZSLStream(),
                        sharedImageReaderFactory.provideMetadataPool(),
                        flashSetting,
                        zslAndPreviewTemplate);

                BurstTaker burstTaker = new BurstTakerImpl(cameraCommandExecutor,
                        frameServer,
                        basicCameraFactory.provideMeteredZoomedRequestBuilder(),
                        sharedImageReaderFactory.provideSharedImageReader(),
                        burstFacade.getInputSurface(),
                        basicCameraFactory.providePreviewUpdater(),
                        // ImageReader#acquireLatestImage() requires two images
                        // as the margin so
                        // specify that as the maximum number of images that can
                        // be used by burst.
                        mMaxImageCount - 2);
                burstFacade.setBurstTaker(burstTaker);

                if (isBackCamera && ApiHelper.IS_NEXUS_5) {
                    // Workaround for bug: 19061883
                    ResponseListener failureDetector = RepeatFailureHandlerComponent.create(
                            Loggers.tagFactory(),
                            fatalErrorHandler,
                            cameraCaptureSession,
                            cameraCommandExecutor,
                            basicCameraFactory.providePreviewUpdater(),
                            UsageStatistics.instance(),
                            10 /* consecutiveFailureThreshold */).provideResponseListener();
                    zslTemplate.addResponseListener(failureDetector);
                }

                if (GservicesHelper.isJankStatisticsEnabled(AndroidContext.instance().get()
                        .getContentResolver())) {
                    // Don't add jank detection unless the preview is running.
                    zslAndPreviewTemplate.addResponseListener(
                          new FramerateJankDetector(Loggers.tagFactory(),
                                UsageStatistics.instance()));
                }

                final Observable<Integer> availableImageCount = sharedImageReaderFactory
                        .provideAvailableImageCount();
                final Observable<Boolean> frameServerAvailability = frameServerComponent
                        .provideReadyState();
                Observable<Boolean> readyObservable = Observables.transform(
                        Arrays.asList(availableImageCount, frameServerAvailability),
                        new Supplier<Boolean>() {
                            @Override
                            public Boolean get() {
                                boolean atLeastOneImageAvailable = availableImageCount.get() >= 1;
                                boolean frameServerAvailable = frameServerAvailability.get();
                                return atLeastOneImageAvailable && frameServerAvailable;
                            }
                        });

                lifetime.add(Observables.addThreadSafeCallback(readyObservable,
                        readyStateCallback));

                basicCameraFactory.providePreviewUpdater().run();

                return new CameraControls(
                        pictureTakerFactory.providePictureTaker(),
                        basicCameraFactory.provideManualAutoFocus());
            }
        };

        float maxZoom = characteristics.getAvailableMaxDigitalZoom();
        List<Size> supportedPreviewSizes = characteristics.getSupportedPreviewSizes();
        OneCamera.Facing direction = characteristics.getCameraDirection();
        return new InitializedOneCameraFactory(lifetime, cameraStarter, device,
                outputSurfaces, mainThread, new HandlerFactory(), maxZoom,
                supportedPreviewSizes, characteristics.getLensFocusRange(),
                direction).provideOneCamera();
    }
}
