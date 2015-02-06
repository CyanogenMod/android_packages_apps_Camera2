/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.hardware.camera2.CameraCharacteristics;

import com.android.camera.async.MainThread;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.sharedimagereader.ManagedImageReader;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Creates a {@link PictureTaker} for devices which only support
 * {@link CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY}.
 */
@ParametersAreNonnullByDefault
public final class LegacyPictureTakerFactory {
    private final PictureTaker mPictureTaker;

    public LegacyPictureTakerFactory(ImageSaver.Builder imageSaverBuilder,
            CameraCommandExecutor cameraCommandExecutor, MainThread mainExecutor, FrameServer
            frameServer, RequestBuilder.Factory rootRequestBuilder,
            ManagedImageReader imageReader) {
        SimpleImageCaptureCommand imageCaptureCommand = new SimpleImageCaptureCommand(frameServer,
                rootRequestBuilder, imageReader);
        mPictureTaker = new PictureTakerImpl(mainExecutor, cameraCommandExecutor,
                imageSaverBuilder, imageCaptureCommand);
    }

    @Nonnull
    public PictureTaker providePictureTaker() {
        return mPictureTaker;
    }
}
