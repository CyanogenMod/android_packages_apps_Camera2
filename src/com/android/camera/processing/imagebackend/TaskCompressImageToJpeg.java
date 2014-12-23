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

package com.android.camera.processing.imagebackend;

import android.graphics.ImageFormat;

import com.android.camera.app.OrientationManager.DeviceOrientation;
import com.android.camera.exif.ExifInterface;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.JpegUtilNative;
import com.android.camera.util.Size;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

/**
 * Implements the conversion of a YUV_420_888 image to compressed JPEG byte
 * array, using the native implementation of the Camera Application.
 * <p>
 * TODO: Instead of setting the orientation in EXIF, actually rotate the image
 * here and set a rotation of 0.
 */
public class TaskCompressImageToJpeg extends TaskJpegEncode {
    private static final int DEFAULT_JPEG_COMPRESSION_QUALITY = 90;

    /**
     * Constructor
     *
     * @param image Image required for computation
     * @param executor Executor to run events
     * @param imageBackend Link to ImageBackend for reference counting
     * @param captureSession Handler for UI/Disk events
     */
    TaskCompressImageToJpeg(ImageToProcess image, Executor executor, ImageBackend imageBackend,
            CaptureSession captureSession) {
        super(image, executor, imageBackend, ProcessingPriority.SLOW, captureSession);
    }

    private void logWrapper(String message) {
        // Do nothing.
    }

    @Override
    public void run() {
        ImageToProcess img = mImage;

        final TaskImage inputImage = new TaskImage(
                img.rotation, img.proxy.getWidth(), img.proxy.getHeight(), img.proxy.getFormat());

        // Resulting image will be rotated so that viewers won't have to rotate.
        // That's why the resulting image will have 0 rotation.
        Size resultSize = getImageSizeForOrientation(img.proxy.getWidth(), img.proxy.getHeight(),
                img.rotation);
        final TaskImage resultImage = new TaskImage(
                DeviceOrientation.CLOCKWISE_0, resultSize.getWidth(), resultSize.getHeight(),
                ImageFormat.JPEG);

        onStart(mId, inputImage, resultImage);
        logWrapper("TIMER_END Full-size YUV buffer available, w=" + img.proxy.getWidth() + " h="
                + img.proxy.getHeight() + " of format " + img.proxy.getFormat()
                + " (35==YUV_420_888)");

        ByteBuffer compressedData = ByteBuffer.allocateDirect(3 * resultImage.width
                * resultImage.height);

        int numBytes = JpegUtilNative.compressJpegFromYUV420Image(img.proxy, compressedData,
                DEFAULT_JPEG_COMPRESSION_QUALITY, inputImage.orientation.getDegrees());

        if (numBytes < 0) {
            throw new RuntimeException("Error compressing jpeg.");
        }
        compressedData.limit(numBytes);
        byte[] writeOut = new byte[numBytes];
        compressedData.get(writeOut);
        compressedData.rewind();

        // Release the image now that you have a usable copy
        mImageBackend.releaseSemaphoreReference(img, mExecutor);

        mSession.saveAndFinish(writeOut, resultImage.width, resultImage.height,
                resultImage.orientation.getDegrees(), createExif(resultImage), null);
        onJpegEncodeDone(mId, inputImage, resultImage, writeOut);
    }

    private static ExifInterface createExif(TaskImage image) {
        ExifInterface exif = new ExifInterface();
        exif.setTag(exif.buildTag(ExifInterface.TAG_PIXEL_X_DIMENSION, image.width));
        exif.setTag(exif.buildTag(ExifInterface.TAG_PIXEL_Y_DIMENSION, image.height));
        exif.setTag(exif.buildTag(ExifInterface.TAG_ORIENTATION,
                ExifInterface.getOrientationValueForRotation(image.orientation.getDegrees())));
        return exif;
    }

    /**
     * @param originalWidth the width of the original image captured from the
     *            camera
     * @param originalHeight the height of the original image captured from the
     *            camera
     * @param orientation the rotation to apply, in degrees.
     * @return The size of the final rotated image
     */
    private Size getImageSizeForOrientation(int originalWidth, int originalHeight,
            DeviceOrientation orientation) {
        if (orientation == DeviceOrientation.CLOCKWISE_0
                || orientation == DeviceOrientation.CLOCKWISE_180) {
            return new Size(originalWidth, originalHeight);
        } else if (orientation == DeviceOrientation.CLOCKWISE_90
                || orientation == DeviceOrientation.CLOCKWISE_270) {
            return new Size(originalHeight, originalWidth);
        } else {
            // Unsupported orientation. Get rid of this once UNKNOWN is gone.
            return new Size(originalWidth, originalHeight);
        }
    }
}
