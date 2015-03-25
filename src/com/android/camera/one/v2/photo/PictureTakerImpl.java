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
import com.google.common.base.Objects;

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

    private final class PictureTakerCommand implements CameraCommand {
        private final Updatable<Void> mImageExposureCallback;
        private final ImageSaver mImageSaver;
        private final CaptureSession mSession;

        private PictureTakerCommand(Updatable<Void> imageExposureCallback,
                ImageSaver imageSaver,
                CaptureSession session) {
            mImageExposureCallback = imageExposureCallback;
            mImageSaver = imageSaver;
            mSession = session;
        }

        @Override
        public void run() throws InterruptedException, CameraAccessException,
                CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
            try {
                mCommand.run(mImageExposureCallback, mImageSaver);
            } catch (Exception e) {
                mSession.cancel();
                throw e;
            }
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("command", mCommand)
                    .toString();
        }
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

        final ImageSaver imageSaver = mImageSaverBuilder.build(
                params.saverCallback,
                OrientationManager.DeviceOrientation.from(params.orientation),
                session);

        mCameraCommandExecutor.execute(new PictureTakerCommand(
                imageExposureCallback, imageSaver, session));
    }
}
