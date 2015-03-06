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

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.Updatable;
import com.android.camera.debug.Log;
import com.android.camera.debug.Logger;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.google.common.base.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
final class FlashBasedPhotoCommand implements ImageCaptureCommand {
    private final Logger mLog;
    private final Supplier<OneCamera.PhotoCaptureParameters.Flash> mFlashMode;
    private final ImageCaptureCommand mFlashOnCommand;
    private final ImageCaptureCommand mFlashAutoCommand;
    private final ImageCaptureCommand mFlashOffCommand;

    FlashBasedPhotoCommand(Logger.Factory logFactory,
            Supplier<OneCamera.PhotoCaptureParameters.Flash> flashMode,
            ImageCaptureCommand flashOnCommand,
            ImageCaptureCommand flashAutoCommand,
            ImageCaptureCommand flashOffCommand) {
        mLog = logFactory.create(new Log.Tag("FlashBasedPhotoCmd"));
        mFlashMode = flashMode;
        mFlashOnCommand = flashOnCommand;
        mFlashAutoCommand = flashAutoCommand;
        mFlashOffCommand = flashOffCommand;
    }

    @Override
    public void run(Updatable<Void> imageExposeCallback, ImageSaver imageSaver)
            throws InterruptedException, CameraAccessException,
            CameraCaptureSessionClosedException,
            ResourceAcquisitionFailedException {
        OneCamera.PhotoCaptureParameters.Flash flashMode = mFlashMode.get();
        if (flashMode == OneCamera.PhotoCaptureParameters.Flash.ON) {
            mLog.i("running flash-on command: " + mFlashOnCommand);
            mFlashOnCommand.run(imageExposeCallback, imageSaver);
        } else if (flashMode == OneCamera.PhotoCaptureParameters.Flash.AUTO) {
            mLog.i("running flash-auto command: " + mFlashAutoCommand);
            mFlashAutoCommand.run(imageExposeCallback, imageSaver);
        } else {
            mLog.i("running flash-off command: " + mFlashOffCommand);
            mFlashOffCommand.run(imageExposeCallback, imageSaver);
        }
    }
}
