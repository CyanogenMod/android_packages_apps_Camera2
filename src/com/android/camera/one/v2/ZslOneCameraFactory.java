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

import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import android.view.Surface;

import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.MainThread;
import com.android.camera.async.Observable;
import com.android.camera.async.Updatable;
import com.android.camera.debug.Log.Tag;
import com.android.camera.debug.Logger;
import com.android.camera.debug.Loggers;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.v2.camera2proxy.AndroidImageReaderProxy;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceRequestBuilderFactory;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.common.BasicCameraFactory;
import com.android.camera.one.v2.common.SimpleCaptureStream;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.FrameServerFactory;
import com.android.camera.one.v2.core.RequestTemplate;
import com.android.camera.one.v2.core.ResponseListeners;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.initialization.CameraStarter;
import com.android.camera.one.v2.initialization.InitializedOneCameraFactory;
import com.android.camera.one.v2.photo.ZslPictureTakerFactory;
import com.android.camera.one.v2.sharedimagereader.ZslSharedImageReaderFactory;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ZslOneCameraFactory implements OneCameraFactory {
    private static Tag TAG = new Tag("ZslOneCamFactory");

    private final Logger mLogger;
    private final int mImageFormat;
    private final int mMaxImageCount;

    public ZslOneCameraFactory(int imageFormat, int maxImageCount) {
        mImageFormat = imageFormat;
        mMaxImageCount = maxImageCount;
        mLogger = Loggers.tagFactory().create(TAG);
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
        mLogger.v("Applying Nexus5 specific framerate backoff of "+frameRateBackOff);
        requestTemplate.setParam(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, frameRateBackOff);
    }

    @Override
    public OneCamera createOneCamera(final CameraDeviceProxy device,
            final OneCameraCharacteristics characteristics,
            final MainThread mainThread,
            Size pictureSize, final ImageSaver.Builder imageSaverBuilder,
            final Observable<OneCamera.PhotoCaptureParameters.Flash> flashSetting) {
        Lifetime lifetime = new Lifetime();

        final ImageReaderProxy imageReader = new CloseWhenDoneImageReader(
                new LoggingImageReader(AndroidImageReaderProxy.newInstance(
                        pictureSize.getWidth(), pictureSize.getHeight(),
                        mImageFormat, mMaxImageCount), Loggers.tagFactory()));

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
            public CameraControls startCamera(Lifetime cameraLifetime,
                    CameraCaptureSessionProxy cameraCaptureSession,
                    Surface previewSurface,
                    Observable<Float> zoomState,
                    Updatable<TotalCaptureResultProxy> metadataCallback,
                    Updatable<Boolean> readyState) {
                // Create the FrameServer from the CaptureSession.
                FrameServer frameServer = new FrameServerFactory(
                        new Lifetime(cameraLifetime), cameraCaptureSession, new HandlerFactory())
                        .provideFrameServer();

                // Create a thread pool on which to execute camera operations.
                ScheduledExecutorService miscThreadPool = Executors.newScheduledThreadPool(1);

                // Create the request builder used by all camera operations.
                // Streams, ResponseListeners, and Parameters added to
                // this will be applied to *all* requests sent to the camera.
                RequestTemplate rootBuilder = new RequestTemplate(
                        new CameraDeviceRequestBuilderFactory(device));
                rootBuilder.addStream(new SimpleCaptureStream(previewSurface));
                rootBuilder.addResponseListener(
                        ResponseListeners.forFinalMetadata(metadataCallback));

                // Create the shared image reader.
                ZslSharedImageReaderFactory sharedImageReaderFactory =
                        new ZslSharedImageReaderFactory(new Lifetime(cameraLifetime),
                                imageReader, rootBuilder, new HandlerFactory());

                boolean isBackCamera = characteristics.getCameraDirection() ==
                        OneCamera.Facing.BACK;

                if (isBackCamera && ApiHelper.IS_NEXUS_5) {
                    applyNexus5BackCameraFrameRateWorkaround(rootBuilder);
                }

                // Create basic functionality (zoom, AE, AF).
                BasicCameraFactory basicCameraFactory = new BasicCameraFactory(
                        new Lifetime(cameraLifetime), characteristics,
                        frameServer, sharedImageReaderFactory.provideRequestTemplate(),
                        miscThreadPool, flashSetting, zoomState,
                        CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);

                // Create the picture-taker.
                CameraCommandExecutor cameraCommandExecutor = new CameraCommandExecutor(
                        miscThreadPool);

                ZslPictureTakerFactory pictureTakerFactory = new ZslPictureTakerFactory(
                        mainThread,
                        cameraCommandExecutor,
                        imageSaverBuilder,
                        frameServer,
                        basicCameraFactory.provideMeteredZoomedRequestBuilder(),
                        sharedImageReaderFactory.provideSharedImageReader(),
                        sharedImageReaderFactory.provideZSLCaptureStream(),
                        sharedImageReaderFactory.provideMetadataPool(), flashSetting);

                basicCameraFactory.providePreviewStarter().run();

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
                supportedPreviewSizes, direction).provideOneCamera();
    }
}
