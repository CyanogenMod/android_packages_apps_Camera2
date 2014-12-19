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

package com.android.camera.processing;

import android.graphics.ImageFormat;

import com.android.camera.app.OrientationManager;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.JpegUtilNative;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

/**
 * Implements the conversion of a YUV_420_888 image to compressed JPEG byte
 * array, using the native implementation of the Camera Application.
 */
public class TaskCompressImageToJpeg extends TaskJpegEncode {
    private static final int DEFAULT_JPEG_COMPRESSION_QUALITY = 90;

    /**
     * Constructor
     *
     * @param imageProxy Image required for computation
     * @param executor Executor to run events
     * @param imageBackend Link to ImageBackend for reference counting
     * @param captureSession Handler for UI/Disk events
     */
    TaskCompressImageToJpeg(ImageProxy imageProxy, Executor executor, ImageBackend imageBackend,
            CaptureSession captureSession) {
        super(imageProxy, executor, imageBackend, ProcessingPriority.SLOW, captureSession);
    }

    private void logWrapper(String message) {
        // Do nothing.
    }

    @Override
    public void run() {
        ImageProxy img = mImageProxy;

        // TODO: Pass in the orientation for processing as well.
        final TaskImage inputImage = new TaskImage(
                OrientationManager.DeviceOrientation.CLOCKWISE_0,
                img.getWidth(), img.getHeight(), img.getFormat());
        final TaskImage resultImage = new TaskImage(
                OrientationManager.DeviceOrientation.CLOCKWISE_0,
                img.getWidth(), img.getHeight(), ImageFormat.JPEG);

        onStart(mId, inputImage, resultImage);

        logWrapper("TIMER_END Full-size YUV buffer available, w=" + img.getWidth() + " h="
                + img.getHeight() + " of format " + img.getFormat() + " (35==YUV_420_888)");

        ByteBuffer compressedData = ByteBuffer.allocateDirect(3 * resultImage.width
                * resultImage.height);

        int numBytes = JpegUtilNative.compressJpegFromYUV420Image(img, compressedData,
                DEFAULT_JPEG_COMPRESSION_QUALITY);

        if (numBytes < 0) {
            throw new RuntimeException("Error compressing jpeg.");
        }
        compressedData.limit(numBytes);
        byte[] writeOut = new byte[numBytes];
        compressedData.get(writeOut);
        compressedData.rewind();
        // Release the image now that you have a usable copy
        mImageBackend.releaseSemaphoreReference(img, mExecutor);

        mSession.saveAndFinish(writeOut, resultImage.width, resultImage.height, 0, null, null);
        onJpegEncodeDone(mId, inputImage, resultImage, writeOut);
    }

}
