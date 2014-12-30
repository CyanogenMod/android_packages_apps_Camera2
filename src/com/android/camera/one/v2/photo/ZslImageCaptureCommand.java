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

import com.android.camera.async.BufferQueue;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class ZslImageCaptureCommand implements ImageCaptureCommand {
    private final BufferQueue<ImageProxy> mZslRingBuffer;
    private final ImageCaptureCommand mFallbackCommand;

    public ZslImageCaptureCommand(BufferQueue<ImageProxy> zslRingBuffer,
            ImageCaptureCommand fallbackCommand) {
        mZslRingBuffer = zslRingBuffer;
        mFallbackCommand = fallbackCommand;
    }

    @Override
    public void run(Updatable<Void> imageExposeCallback, ImageSaver imageSaver)
            throws InterruptedException, CameraAccessException,
            CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        boolean zslImageCaptured = false;
        try {
            // FIXME TODO This should look at metadata related to this frame and
            // only use it if 3A is converged.
            ImageProxy image = mZslRingBuffer.getNext(0, TimeUnit.SECONDS);
            imageExposeCallback.update(null);
            imageSaver.saveAndCloseImage(image);
            zslImageCaptured = true;
        } catch (TimeoutException timeout) {
            if (!zslImageCaptured) {
                mFallbackCommand.run(imageExposeCallback, imageSaver);
            }
        } catch (BufferQueue.BufferQueueClosedException e) {
            // The zsl ring buffer has been closed, so do nothing since the
            // system is shutting down.
        }
    }
}
