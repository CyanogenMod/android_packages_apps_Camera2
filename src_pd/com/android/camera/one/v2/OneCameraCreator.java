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

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.util.DisplayMetrics;

import com.android.camera.FatalErrorHandler;
import com.android.camera.SoundPlayer;
import com.android.camera.async.MainThread;
import com.android.camera.burst.BurstFacade;
import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCaptureSetting;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.config.OneCameraFeatureConfig;
import com.android.camera.one.config.OneCameraFeatureConfig.CaptureSupportLevel;
import com.android.camera.one.v2.camera2proxy.AndroidCameraDeviceProxy;
import com.android.camera.one.v2.common.PictureSizeCalculator;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.imagesaver.JpegImageBackendImageSaver;
import com.android.camera.one.v2.imagesaver.YuvImageBackendImageSaver;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.processing.ProcessingServiceManager;
import com.android.camera.processing.imagebackend.ImageBackend;

public class OneCameraCreator {
    private static Log.Tag TAG = new Log.Tag("OneCamCreator");

    public static OneCamera create(
            CameraDevice device,
            CameraCharacteristics characteristics,
            OneCameraFeatureConfig featureConfig,
            OneCameraCaptureSetting captureSetting,
            DisplayMetrics displayMetrics,
            Context context,
            MainThread mainThread,
            ImageRotationCalculator imageRotationCalculator,
            BurstFacade burstController,
            SoundPlayer soundPlayer,
            FatalErrorHandler fatalErrorHandler) throws OneCameraAccessException {
        // TODO: Might want to switch current camera to vendor HDR.

        CaptureSupportLevel captureSupportLevel = featureConfig
                .getCaptureSupportLevel(characteristics);
        Log.i(TAG, "Camera support level: " + captureSupportLevel.name());

        OneCameraCharacteristics oneCharacteristics =
                new OneCameraCharacteristicsImpl(characteristics);

        PictureSizeCalculator pictureSizeCalculator =
                new PictureSizeCalculator(oneCharacteristics);
        PictureSizeCalculator.Configuration configuration = null;

        OneCameraFactory cameraFactory = null;
        ImageSaver.Builder imageSaverBuilder = null;
        ImageBackend imageBackend = ProcessingServiceManager.instance().getImageBackend();

        // Depending on the support level of the camera, choose the right
        // configuration.
        switch (captureSupportLevel) {
            case LIMITED_JPEG:
            case LEGACY_JPEG:
                // LIMITED and LEGACY have different picture takers which will
                // be selected by the support level that is passes into
                // #createOneCamera below - otherwise they use the same OneCamera and image backend.
                cameraFactory = new SimpleOneCameraFactory(ImageFormat.JPEG,
                        featureConfig.getMaxAllowedImageReaderCount(),
                        imageRotationCalculator);
                configuration = pictureSizeCalculator.computeConfiguration(
                        captureSetting.getCaptureSize(),
                        ImageFormat.JPEG);
                imageSaverBuilder = new JpegImageBackendImageSaver(imageRotationCalculator,
                        imageBackend, configuration.getPostCaptureCrop());
                break;
            case LIMITED_YUV:
                // Same as above, but we're using YUV images.
                cameraFactory = new SimpleOneCameraFactory(ImageFormat.YUV_420_888,
                        featureConfig.getMaxAllowedImageReaderCount(),
                        imageRotationCalculator);
                configuration = pictureSizeCalculator.computeConfiguration(
                        captureSetting.getCaptureSize(),
                        ImageFormat.YUV_420_888);
                imageSaverBuilder = new YuvImageBackendImageSaver(imageRotationCalculator,
                        imageBackend,
                        configuration.getPostCaptureCrop());
                break;
            case ZSL:
                // ZSL has its own OneCamera and produces YUV images.
                cameraFactory = new ZslOneCameraFactory(ImageFormat.YUV_420_888,
                        featureConfig.getMaxAllowedImageReaderCount());
                configuration = pictureSizeCalculator.computeConfiguration(
                        captureSetting.getCaptureSize(),
                        ImageFormat.YUV_420_888);
                imageSaverBuilder = new YuvImageBackendImageSaver(imageRotationCalculator,
                        imageBackend, configuration.getPostCaptureCrop());
                break;
        }

        Log.i(TAG, "Picture Size Configuration: " + configuration);

        return cameraFactory.createOneCamera(new AndroidCameraDeviceProxy(device),
                new OneCameraCharacteristicsImpl(characteristics),
                captureSupportLevel,
                mainThread,
                configuration.getNativeOutputSize(),
                imageSaverBuilder,
                captureSetting.getFlashSetting(),
                captureSetting.getExposureSetting(),
                captureSetting.getHdrSceneSetting(),
                burstController,
                fatalErrorHandler);
    }
}
