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
import android.location.Location;
import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.CallbackRunnable;
import com.android.camera.async.ConcurrentState;
import com.android.camera.async.FilteredUpdatable;
import com.android.camera.async.HandlerExecutor;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Pollable;
import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.autofocus.ManualAutoFocus;
import com.android.camera.one.v2.autofocus.ManualAutoFocusFactory;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceProxy;
import com.android.camera.one.v2.camera2proxy.CameraDeviceRequestBuilderFactory;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.PreviewCommand;
import com.android.camera.one.v2.commands.RunnableCameraCommand;
import com.android.camera.one.v2.common.MetadataResponseListener;
import com.android.camera.one.v2.common.SimpleCaptureStream;
import com.android.camera.one.v2.common.TimestampResponseListener;
import com.android.camera.one.v2.common.ZoomedCropRegion;
import com.android.camera.one.v2.core.CaptureStream;
import com.android.camera.one.v2.core.DecoratingRequestBuilderBuilder;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.FrameServerFactory;
import com.android.camera.one.v2.initialization.CameraStarter;
import com.android.camera.one.v2.initialization.InitializedOneCameraFactory;
import com.android.camera.one.v2.photo.ImageSaver;
import com.android.camera.one.v2.photo.PictureTaker;
import com.android.camera.one.v2.photo.ZslPictureTakerFactory;
import com.android.camera.one.v2.sharedimagereader.ImageStreamFactory;
import com.android.camera.one.v2.sharedimagereader.ZslSharedImageReaderFactory;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageStream;
import com.android.camera.util.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Creates a camera which takes YUV images with zero shutter lag.
 */
public class ZslOneCameraFactory {
    /**
     * Finishes constructing the camera when prerequisites, e.g. the preview
     * stream and capture session, are ready.
     */
    private static class CameraStarterImpl implements CameraStarter {
        private final CameraDeviceProxy mDevice;
        private final CameraCharacteristics mCameraCharacteristics;
        private final ImageReader mImageReader;
        private final Handler mMainHandler;

        private CameraStarterImpl(
                CameraDeviceProxy device,
                CameraCharacteristics cameraCharacteristics,
                ImageReader imageReader, Handler mainHandler) {
            mDevice = device;
            mCameraCharacteristics = cameraCharacteristics;
            mImageReader = imageReader;
            mMainHandler = mainHandler;
        }

        @Override
        public CameraControls startCamera(Lifetime cameraLifetime,
                CameraCaptureSessionProxy cameraCaptureSession,
                Surface previewSurface,
                ConcurrentState<Float> zoomState,
                Updatable<Integer> afState,
                Updatable<Boolean> readyState) {
            // Build the FrameServer from the CaptureSession
            FrameServerFactory frameServerFactory =
                    new FrameServerFactory(new Lifetime(cameraLifetime), cameraCaptureSession);
            FrameServer frameServer = frameServerFactory.provideFrameServer();

            // Build the shared image reader
            ZslSharedImageReaderFactory sharedImageReaderFactory =
                    new ZslSharedImageReaderFactory(new Lifetime(cameraLifetime), mImageReader);

            Updatable<Long> globalTimestampCallback =
                    sharedImageReaderFactory.provideGlobalTimestampQueue();
            ImageStreamFactory imageStreamFactory =
                    sharedImageReaderFactory.provideSharedImageReader();
            ImageStream zslImageStream = sharedImageReaderFactory.provideZSLCaptureStream();

            // The request builder used by all camera operations.
            // Streams, ResponseListeners, and Parameters added to
            // this will be applied to *all* requests sent to the camera.
            DecoratingRequestBuilderBuilder rootBuilder = new DecoratingRequestBuilderBuilder
                    (new CameraDeviceRequestBuilderFactory(mDevice));
            // The shared image reader must be wired to receive every timestamp
            // for every image (including the preview).
            rootBuilder.withResponseListener(
                    new TimestampResponseListener(globalTimestampCallback));

            // TODO Change AE mode depending on flash mode.
            rootBuilder.withParam(CaptureRequest.CONTROL_AE_MODE, CaptureRequest
                    .CONTROL_AE_MODE_ON);

            rootBuilder.withStream(zslImageStream);

            rootBuilder.withResponseListener(
                    new MetadataResponseListener<Integer>(
                            CaptureResult.CONTROL_AF_STATE, new FilteredUpdatable<>(afState)));

            Pollable<Rect> cropRegion = new ZoomedCropRegion(mCameraCharacteristics, zoomState);
            rootBuilder.withParam(CaptureRequest.SCALER_CROP_REGION, cropRegion);

            CaptureStream previewStream = new SimpleCaptureStream(previewSurface);
            rootBuilder.withStream(previewStream);

            ScheduledExecutorService miscThreadPool = Executors.newScheduledThreadPool(1);

            CameraCommandExecutor cameraCommandExecutor = new CameraCommandExecutor(miscThreadPool);
            PreviewCommand previewCommand = new PreviewCommand(frameServer, rootBuilder,
                    CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);

            Runnable previewRunner = new RunnableCameraCommand(cameraCommandExecutor,
                    previewCommand);

            // Resend the repeating preview request when the zoom changes to
            // apply the new crop factor.
            zoomState.addCallback(new CallbackRunnable(previewRunner), miscThreadPool);

            OrientationManager.DeviceOrientation sensorOrientation = getSensorOrientation();

            ManualAutoFocusFactory manualAutoFocusFactory = new ManualAutoFocusFactory(new
                    Lifetime(cameraLifetime), frameServer, miscThreadPool, cropRegion,
                    sensorOrientation, previewRunner, rootBuilder,
                    CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            ManualAutoFocus autoFocus = manualAutoFocusFactory.provideManualAutoFocus();
            Pollable<MeteringRectangle[]> aeRegions =
                    manualAutoFocusFactory.provideAEMeteringRegion();
            Pollable<MeteringRectangle[]> afRegions =
                    manualAutoFocusFactory.provideAFMeteringRegion();

            rootBuilder.withParam(CaptureRequest.CONTROL_AE_REGIONS, aeRegions);
            rootBuilder.withParam(CaptureRequest.CONTROL_AF_REGIONS, afRegions);

            HandlerExecutor mainExecutor = new HandlerExecutor(mMainHandler);

            // FIXME TODO Replace stub with real implementation
            ImageSaver.Builder imageSaverBuilder = new ImageSaver.Builder() {

                @Override
                public void setTitle(String title) {

                }

                @Override
                public void setOrientation(OrientationManager.DeviceOrientation orientation) {

                }

                @Override
                public void setLocation(Location location) {

                }

                @Override
                public void setThumbnailCallback(Updatable<byte[]> callback) {

                }

                @Override
                public ImageSaver build() {
                    return new ImageSaver() {
                        @Override
                        public void saveAndCloseImage(final ImageProxy imageProxy) {
                            // Fake implementation which sleeps for 2 seconds before closing the
                            // image
                            Executors.newCachedThreadPool().submit(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.sleep(2000);
                                    } catch (InterruptedException e) {
                                        // Impossible exception.
                                        throw new RuntimeException(e);
                                    } finally {
                                        imageProxy.close();
                                    }
                                }
                            });
                        }
                    };
                }
            };

            ZslPictureTakerFactory pictureTakerFactory = new ZslPictureTakerFactory(mainExecutor,
                    cameraCommandExecutor, imageSaverBuilder, frameServer, rootBuilder,
                    imageStreamFactory, zslImageStream);
            PictureTaker pictureTaker = pictureTakerFactory.providePictureTaker();

            previewRunner.run();

            return new CameraControls(pictureTaker, autoFocus);
        }

        private OrientationManager.DeviceOrientation getSensorOrientation() {
            Integer degrees = mCameraCharacteristics.get(CameraCharacteristics
                    .SENSOR_ORIENTATION);

            switch (degrees) {
                case 0:
                    return OrientationManager.DeviceOrientation.CLOCKWISE_0;
                case 90:
                    return OrientationManager.DeviceOrientation.CLOCKWISE_90;
                case 180:
                    return OrientationManager.DeviceOrientation.CLOCKWISE_180;
                case 270:
                    return OrientationManager.DeviceOrientation.CLOCKWISE_270;
                default:
                    return OrientationManager.DeviceOrientation.CLOCKWISE_0;
            }
        }
    }

    private final InitializedOneCameraFactory mInitializedOneCameraFactory;

    public ZslOneCameraFactory(CameraDevice cameraDevice,
            CameraCharacteristics characteristics, Handler mainHandler, Size pictureSize) {
        CameraDeviceProxy device = new CameraDeviceProxy(cameraDevice);

        int imageFormat = ImageFormat.YUV_420_888;
        // TODO This is totally arbitrary, and could probably be increased.
        int maxImageCount = 10;

        ImageReader imageReader = ImageReader.newInstance(pictureSize.getWidth(),
                pictureSize.getHeight(), imageFormat, maxImageCount);

        // FIXME TODO Close the ImageReader when all images have been freed!

        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(imageReader.getSurface());

        CameraStarter cameraStarter = new CameraStarterImpl(device, characteristics, imageReader,
                mainHandler);

        mInitializedOneCameraFactory =
                new InitializedOneCameraFactory(cameraStarter, device, characteristics,
                        outputSurfaces, imageFormat, mainHandler);
    }

    public OneCamera provideOneCamera() {
        return mInitializedOneCameraFactory.provideOneCamera();
    }
}
