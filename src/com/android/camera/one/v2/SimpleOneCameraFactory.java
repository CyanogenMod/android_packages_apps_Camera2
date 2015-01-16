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
import android.media.ImageReader;
import android.os.Build;
import android.view.Surface;

import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.MainThread;
import com.android.camera.async.Observable;
import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceRequestBuilderFactory;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.common.BasicCameraFactory;
import com.android.camera.one.v2.common.SimpleCaptureStream;
import com.android.camera.one.v2.common.TimestampResponseListener;
import com.android.camera.one.v2.common.TotalCaptureResultResponseListener;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.FrameServerFactory;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.RequestTemplate;
import com.android.camera.one.v2.initialization.CameraStarter;
import com.android.camera.one.v2.initialization.InitializedOneCameraFactory;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.photo.PictureTakerFactory;
import com.android.camera.one.v2.sharedimagereader.ImageStreamFactory;
import com.android.camera.one.v2.sharedimagereader.SharedImageReaderFactory;
import com.android.camera.util.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Creates a camera which takes jpeg images using the hardware encoder with
 * baseline functionality.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SimpleOneCameraFactory implements OneCameraFactory {
    private final int mImageFormat;
    private final int mMaxImageCount;

    /**
     * @param imageFormat The {@link ImageFormat} to use for full-size images to
     *            be saved.
     * @param maxImageCount The size of the image reader to use for full-size
     *            images.
     */
    public SimpleOneCameraFactory(int imageFormat, int maxImageCount) {
        mImageFormat = imageFormat;
        mMaxImageCount = maxImageCount;
    }

    @Override
    public OneCamera createOneCamera(final CameraDeviceProxy device,
            final OneCameraCharacteristics characteristics, final MainThread mainExecutor,
            Size pictureSize, final ImageSaver.Builder imageSaverBuilder,
            final Observable<OneCamera.PhotoCaptureParameters.Flash> flashSetting) {

        final ImageReader imageReader = ImageReader.newInstance(pictureSize.getWidth(),
                pictureSize.getHeight(), mImageFormat, mMaxImageCount);
        // FIXME TODO Close the ImageReader when all images have been freed!

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
                FrameServer frameServer = new FrameServerFactory(
                        new Lifetime(cameraLifetime), cameraCaptureSession, new HandlerFactory())
                        .provideFrameServer();

                // Create a thread pool on which to execute camera operations.
                ScheduledExecutorService miscThreadPool = Executors.newScheduledThreadPool(1);

                // Create the shared image reader.
                SharedImageReaderFactory sharedImageReaderFactory =
                        new SharedImageReaderFactory(new Lifetime(cameraLifetime), imageReader);
                Updatable<Long> globalTimestampCallback =
                        sharedImageReaderFactory.provideGlobalTimestampQueue();
                ImageStreamFactory imageStreamFactory =
                        sharedImageReaderFactory.provideSharedImageReader();

                // Create the request builder used by all camera operations.
                // Streams, ResponseListeners, and Parameters added to
                // this will be applied to *all* requests sent to the camera.
                RequestTemplate rootBuilder = new RequestTemplate
                        (new CameraDeviceRequestBuilderFactory(device));
                // The shared image reader must be wired to receive every
                // timestamp for every image (including the preview).
                rootBuilder.addResponseListener(
                        new TimestampResponseListener(globalTimestampCallback));
                rootBuilder.addStream(new SimpleCaptureStream(previewSurface));
                rootBuilder.addResponseListener(new TotalCaptureResultResponseListener(
                        metadataCallback));

                // Create basic functionality (zoom, AE, AF).
                BasicCameraFactory basicCameraFactory = new BasicCameraFactory(new Lifetime
                        (cameraLifetime), characteristics, frameServer, rootBuilder,
                        miscThreadPool, flashSetting, zoomState, CameraDevice
                        .TEMPLATE_ZERO_SHUTTER_LAG);

                RequestBuilder.Factory meteredZooomedRequestBuilder =
                        basicCameraFactory.provideMeteredZoomedRequestBuilder();

                // Create the picture-taker.
                CameraCommandExecutor cameraCommandExecutor = new CameraCommandExecutor(
                        miscThreadPool);

                PictureTakerFactory pictureTakerFactory = new PictureTakerFactory(mainExecutor,
                        cameraCommandExecutor, imageSaverBuilder, frameServer,
                        meteredZooomedRequestBuilder, imageStreamFactory);

                basicCameraFactory.providePreviewStarter().run();

                return new CameraStarter.CameraControls(
                        pictureTakerFactory.providePictureTaker(),
                        basicCameraFactory.provideManualAutoFocus());
            }
        };

        float maxZoom = characteristics.getAvailableMaxDigitalZoom();
        List<Size> supportedPreviewSizes = characteristics.getSupportedPreviewSizes();
        OneCamera.Facing direction = characteristics.getCameraDirection();

        return new InitializedOneCameraFactory(cameraStarter, device, outputSurfaces, mainExecutor,
                new HandlerFactory(), maxZoom, supportedPreviewSizes, direction).provideOneCamera();
    }
}
