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
import com.android.camera.async.MainThread;
import com.android.camera.async.Updatable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.commands.CameraCommand;
import com.android.camera.one.v2.commands.CameraCommandExecutor;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.session.CaptureSession;
import com.android.camera2.R;

class PictureTakerImpl implements PictureTaker {
    private final MainThread mMainExecutor;
    private final CameraCommandExecutor mCameraCommandExecutor;
    private final ImageSaver.Builder mImageSaverBuilder;
    private final ImageCaptureCommand mCommand;

    public PictureTakerImpl(MainThread mainExecutor, CameraCommandExecutor cameraCommandExecutor,
            ImageSaver.Builder imageSaverBuilder, ImageCaptureCommand command) {
        mMainExecutor = mainExecutor;
        mCameraCommandExecutor = cameraCommandExecutor;
        mImageSaverBuilder = imageSaverBuilder;
        mCommand = command;
    }

    @Override
    public void takePicture(OneCamera.PhotoCaptureParameters params, final CaptureSession session) {
        OneCamera.PictureCallback pictureCallback = params.callback;

        // Wrap the pictureCallback with a thread-safe adapter which guarantees
        // that they are always invoked on the main thread.
        PictureCallbackAdapter pictureCallbackAdapter =
                new PictureCallbackAdapter(pictureCallback, mMainExecutor);

        final Updatable<Void> imageExposureCallback =
                pictureCallbackAdapter.provideQuickExposeUpdatable();

        final Updatable<Void> failureCallback =
                pictureCallbackAdapter.providePictureTakingFailedUpdatable();

        final ImageSaver imageSaver = mImageSaverBuilder.build(
                params.saverCallback,
                OrientationManager.DeviceOrientation.from(params.orientation),
                session);

        mCameraCommandExecutor.execute(new CameraCommand() {
            @Override
            public void run() throws InterruptedException, CameraAccessException,
                    CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
                boolean failed = true;
                try {
                    mCommand.run(imageExposureCallback, imageSaver);
                    failed = false;
                } catch (Exception e) {
                    failureCallback.update(null);
                    session.finishWithFailure(R.string.error_cannot_connect_camera,
                            true /* remove from filmstrip */);
                    throw e;
                }
            }
        });
    }
}
