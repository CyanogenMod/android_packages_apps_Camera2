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

import android.hardware.camera2.CameraAccessException;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.commands.LoggingCameraCommand;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.session.CaptureSession;

import java.util.concurrent.Executor;

class PictureTakerImpl implements PictureTaker {
    private final Executor mMainExecutor;
    private final CameraCommandExecutor mCameraCommandExecutor;
    private final ImageSaver.Builder mImageSaverBuilder;
    private final ImageCaptureCommand mFlashOffCommand;
    private final ImageCaptureCommand mFlashOnCommand;
    private final ImageCaptureCommand mFlashAutoCommand;

    public PictureTakerImpl(Executor mainExecutor,
            CameraCommandExecutor cameraCommandExecutor,
            ImageSaver.Builder imageSaverBuilder,
            ImageCaptureCommand flashOffCommand,
            ImageCaptureCommand flashOnCommand,
            ImageCaptureCommand flashAutoCommand) {
        mMainExecutor = mainExecutor;
        mCameraCommandExecutor = cameraCommandExecutor;
        mImageSaverBuilder = imageSaverBuilder;
        mFlashOffCommand = flashOffCommand;
        mFlashOnCommand = flashOnCommand;
        mFlashAutoCommand = flashAutoCommand;
    }

    @Override
    public void takePicture(OneCamera.PhotoCaptureParameters params, CaptureSession session) {
        OneCamera.PhotoCaptureParameters.Flash flashMode = params.flashMode;
        OneCamera.PictureCallback pictureCallback = params.callback;

        // Wrap the pictureCallback with a thread-safe adaptor which guarantees
        // that they are always invoked on the main thread.
        PictureCallbackAdaptor pictureCallbackAdaptor =
                new PictureCallbackAdaptor(pictureCallback, mMainExecutor);

        final Updatable<Void> imageExposureCallback =
                pictureCallbackAdaptor.provideQuickExposeUpdatable();

        final Updatable<Void> failureCallback =
                pictureCallbackAdaptor.providePictureTakingFailedUpdatable();

        final Updatable<byte[]> thumbnailCallback =
                pictureCallbackAdaptor.provideThumbnailUpdatable();

        final ImageSaver imageSaver = mImageSaverBuilder.build(OrientationManager
                .DeviceOrientation.from(params.orientation), session);

        ImageCaptureCommand imageCommand;
        if (flashMode == OneCamera.PhotoCaptureParameters.Flash.ON) {
            imageCommand = mFlashOnCommand;
        } else if (flashMode == OneCamera.PhotoCaptureParameters.Flash.OFF) {
            imageCommand = mFlashOffCommand;
        } else {
            imageCommand = mFlashAutoCommand;
        }

        final ImageCaptureCommand finalImageCommand = imageCommand;

        mCameraCommandExecutor.execute(new LoggingCameraCommand(new CameraCommand() {
            @Override
            public void run() throws InterruptedException, CameraAccessException,
                    CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
                boolean failed = true;
                try {
                    finalImageCommand.run(imageExposureCallback, imageSaver);
                    failed = false;
                } catch (Exception e) {
                    failureCallback.update(null);
                    throw e;
                }
            }
        }, "Picture Command"));
    }
}
