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

import android.hardware.camera2.CameraDevice;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.MainThread;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.photo.zsl.AcceptableZslImageFilter;
import com.android.camera.one.v2.photo.zsl.ZslImageCaptureCommand;
import com.android.camera.one.v2.sharedimagereader.ImageStreamFactory;
import com.android.camera.one.v2.sharedimagereader.metadatasynchronizer.MetadataPool;
import com.google.common.base.Supplier;

import java.util.Arrays;

/**
 * Wires together a PictureTaker with zero shutter lag.
 */
public class ZslPictureTakerFactory {
    private final PictureTakerImpl mPictureTaker;

    public ZslPictureTakerFactory(MainThread mainExecutor,
            CameraCommandExecutor commandExecutor,
            ImageSaver.Builder imageSaverBuilder,
            FrameServer frameServer,
            RequestBuilder.Factory rootRequestBuilder,
            ImageStreamFactory sharedImageReader,
            BufferQueue<ImageProxy> ringBuffer,
            MetadataPool metadataPool,
            Supplier<OneCamera.PhotoCaptureParameters.Flash> flashMode) {
        // The fallback command is used whenever no acceptable ZSL images are
        // available for capture.
        // This command will perform a full AF & AE sequence.
        ImageCaptureCommand fallbackCommand = new ConvergedImageCaptureCommand(
                sharedImageReader, frameServer, rootRequestBuilder,
                CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG, CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG,
                Arrays.asList(rootRequestBuilder));

        // When flash is ON, always use the ConvergedImageCaptureCommand which
        // performs the AF & AE precapture sequence.
        ImageCaptureCommand flashOnCommand =
                fallbackCommand;
        // When flash is OFF, use ZSL and filter images to require AF
        // convergence, but not AE convergence (AE can take a long time to
        // converge, making capture feel slow).
        ImageCaptureCommand flashOffCommand =
                new ZslImageCaptureCommand(ringBuffer, metadataPool, fallbackCommand,
                        new AcceptableZslImageFilter(true, false));
        // When flash is Auto, use ZSL and filter images to require AF
        // convergence, and AE convergence.
        ImageCaptureCommand flashAutoCommand =
                new ZslImageCaptureCommand(ringBuffer, metadataPool, fallbackCommand,
                        new AcceptableZslImageFilter(true, true));

        ImageCaptureCommand flashBasedCommand = new FlashBasedPhotoCommand(flashMode,
                flashOnCommand, flashAutoCommand, flashOffCommand);
        mPictureTaker = new PictureTakerImpl(mainExecutor, commandExecutor, imageSaverBuilder,
                flashBasedCommand);
    }

    public PictureTaker providePictureTaker() {
        return mPictureTaker;
    }
}
