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

package com.android.camera.one.v2.photo;

import static com.android.camera.one.v2.core.ResponseListeners.forPartialMetadata;

import android.hardware.camera2.CameraDevice;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.MainThread;
import com.android.camera.debug.Logger;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.ResponseManager;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.photo.zsl.AcceptableZslImageFilter;
import com.android.camera.one.v2.photo.zsl.AutoFlashZslImageFilter;
import com.android.camera.one.v2.photo.zsl.ZslImageCaptureCommand;
import com.android.camera.one.v2.sharedimagereader.ManagedImageReader;
import com.android.camera.one.v2.sharedimagereader.metadatasynchronizer.MetadataPool;
import com.google.common.base.Supplier;

import java.util.Arrays;

/**
 * Wires together a PictureTaker with zero shutter lag.
 */
public class ZslPictureTakerFactory {
    /**
     * The maximum amount of time (in nanoseconds) to look-back in the zsl
     * ring-buffer for an image with AE and/or AF convergence.
     */
    private static final long MAX_LOOKBACK_NANOS = 100000000; // 100 ms
    private final PictureTakerImpl mPictureTaker;

    private ZslPictureTakerFactory(PictureTakerImpl pictureTaker) {
        mPictureTaker = pictureTaker;
    }

    public static ZslPictureTakerFactory create(Logger.Factory logFactory,
            MainThread mainExecutor,
            CameraCommandExecutor commandExecutor,
            ImageSaver.Builder imageSaverBuilder,
            FrameServer frameServer,
            RequestBuilder.Factory rootRequestBuilder,
            ManagedImageReader sharedImageReader,
            BufferQueue<ImageProxy> ringBuffer,
            MetadataPool metadataPool,
            Supplier<OneCamera.PhotoCaptureParameters.Flash> flashMode,
            ResponseManager globalResponseManager) {
        // When flash is ON, always use the ConvergedImageCaptureCommand which
        // performs the AF & AE precapture sequence.
        ImageCaptureCommand flashOnCommand = new ConvergedImageCaptureCommand(
                sharedImageReader, frameServer, rootRequestBuilder,
                CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG, CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG,
                Arrays.asList(rootRequestBuilder), true, true);
        // When flash is OFF, use ZSL and filter images to require AF
        // convergence, but not AE convergence (AE can take a long time to
        // converge, making capture feel slow).
        ImageCaptureCommand flashOffFallback = new ConvergedImageCaptureCommand(
                sharedImageReader, frameServer, rootRequestBuilder,
                CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG, CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG,
                Arrays.asList(rootRequestBuilder), /* ae */false, /* af */true);
        ImageCaptureCommand flashOffCommand =
                new ZslImageCaptureCommand(logFactory, ringBuffer, metadataPool, flashOffFallback,
                        new AcceptableZslImageFilter(true, false), MAX_LOOKBACK_NANOS);
        // When flash is Auto, use ZSL and filter images to require AF
        // convergence, and AE convergence.
        AutoFlashZslImageFilter autoFlashZslImageFilter = AutoFlashZslImageFilter.create(
                logFactory, /* afConvergence */true);
        globalResponseManager.addResponseListener(forPartialMetadata(autoFlashZslImageFilter));
        ImageCaptureCommand flashAutoCommand =
                new ZslImageCaptureCommand(logFactory, ringBuffer, metadataPool, flashOnCommand,
                        autoFlashZslImageFilter, MAX_LOOKBACK_NANOS);

        ImageCaptureCommand flashBasedCommand = new FlashBasedPhotoCommand(logFactory, flashMode,
                flashOnCommand, flashAutoCommand, flashOffCommand);
        PictureTakerImpl pictureTaker = new PictureTakerImpl(mainExecutor, commandExecutor,
                imageSaverBuilder,
                flashBasedCommand);

        return new ZslPictureTakerFactory(pictureTaker);
    }

    public PictureTaker providePictureTaker() {
        return mPictureTaker;
    }
}
