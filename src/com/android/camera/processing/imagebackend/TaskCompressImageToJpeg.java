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
import android.net.Uri;

import com.android.camera.app.MediaSaver;
import com.android.camera.app.OrientationManager.DeviceOrientation;
import com.android.camera.exif.ExifInterface;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.JpegUtilNative;
import com.android.camera.util.Size;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

/**
 * Implements the conversion of a YUV_420_888 image to compressed JPEG byte
 * array, using the native implementation of the Camera Application. If the
 * image is already JPEG, then it passes it through properly with the assumption
 * that the JPEG is already encoded in the proper orientation.
 */
public class TaskCompressImageToJpeg extends TaskJpegEncode {

    private static final int DEFAULT_JPEG_COMPRESSION_QUALITY = 90;

    /**
     * Constructor
     *
     * @param image Image required for computation
     * @param executor Executor to run events
     * @param imageTaskManager Link to ImageBackend for reference counting
     * @param captureSession Handler for UI/Disk events
     */
    TaskCompressImageToJpeg(ImageToProcess image, Executor executor,
            ImageTaskManager imageTaskManager,
            CaptureSession captureSession) {
        super(image, executor, imageTaskManager, ProcessingPriority.SLOW, captureSession);
    }

    /**
     * Wraps the static call to JpegUtilNative for testability. {@see
     * JpegUtilNative#compressJpegFromYUV420Image}
     */
    public int compressJpegFromYUV420Image(ImageProxy img, ByteBuffer outBuf, int quality,
            int degrees) {
        return JpegUtilNative.compressJpegFromYUV420Image(img, outBuf, quality, degrees);
    }

    @Override
    public void run() {
        ImageToProcess img = mImage;
        if (img.rotation != DeviceOrientation.CLOCKWISE_0
                && img.proxy.getFormat() == ImageFormat.JPEG) {
            // TODO: Ensure the capture for SimpleOneCameraFactory is always
            // at zero rotation
            // To avoid suboptimal rotation implementation, the JPEG should
            // come with zero orientation. Any post-rotation of JPEG would be
            // REALLY sub-optimal.

            // Release image references on error
            mImageTaskManager.releaseSemaphoreReference(img, mExecutor);

            throw new IllegalStateException(
                    "Image Rotation for JPEG should be zero, but is actually "
                            + img.rotation.getDegrees());
        }

        final TaskImage inputImage = new TaskImage(
                img.rotation, img.proxy.getWidth(), img.proxy.getHeight(),
                img.proxy.getFormat());
        Size resultSize = getImageSizeForOrientation(img.proxy.getWidth(),
                img.proxy.getHeight(),
                img.rotation);
        final TaskImage resultImage = new TaskImage(
                DeviceOrientation.CLOCKWISE_0, resultSize.getWidth(), resultSize.getHeight(),
                ImageFormat.JPEG);
        byte[] writeOut;
        int numBytes;

        try {
            // Resulting image will be rotated so that viewers won't have to
            // rotate. That's why the resulting image will have 0 rotation.

            onStart(mId, inputImage, resultImage, TaskInfo.Destination.FINAL_IMAGE);
            logWrapper("TIMER_END Full-size YUV buffer available, w=" + img.proxy.getWidth()
                    + " h="
                    + img.proxy.getHeight() + " of format " + img.proxy.getFormat()
                    + " (35==YUV_420_888)");

            ByteBuffer compressedData;
            switch (inputImage.format) {
                case ImageFormat.JPEG:
                    compressedData = img.proxy.getPlanes().get(0).getBuffer();
                    numBytes = compressedData.capacity();
                    break;
                case ImageFormat.YUV_420_888:
                    compressedData = ByteBuffer.allocateDirect(3 * resultImage.width
                            * resultImage.height);

                    // If Orientation is UNKNOWN, treat input image orientation
                    // as CLOCKWISE_0.
                    numBytes = compressJpegFromYUV420Image(
                            img.proxy, compressedData, DEFAULT_JPEG_COMPRESSION_QUALITY,
                            inputImage.orientation.getDegrees());

                    if (numBytes < 0) {
                        throw new RuntimeException("Error compressing jpeg.");
                    }
                    compressedData.limit(numBytes);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported input image format for TaskCompressImageToJpeg");
            }

            writeOut = new byte[numBytes];
            compressedData.get(writeOut);
            compressedData.rewind();
        } finally {
            // Release the image now that you have a usable copy in local memory
            // Or you failed to process
            mImageTaskManager.releaseSemaphoreReference(img, mExecutor);
        }

        onJpegEncodeDone(mId, inputImage, resultImage, writeOut,
                TaskInfo.Destination.FINAL_IMAGE);

        // TODO: the app actually crashes here on a race condition:
        // TaskCompressImageToJpeg might complete before
        // TaskConvertImageToRGBPreview.
        mSession.saveAndFinish(writeOut, resultImage.width, resultImage.height,
                resultImage.orientation.getDegrees(), createExif(resultImage),
                new MediaSaver.OnMediaSavedListener() {
                    @Override
                    public void onMediaSaved(Uri uri) {
                        onUriResolved(mId, inputImage, resultImage, uri,
                                TaskInfo.Destination.FINAL_IMAGE);
                    }
                });
    }

    /**
     * Wraps a possible log message to be overridden for testability purposes.
     *
     * @param message
     */
    protected void logWrapper(String message) {
        // Do nothing.
    }

    /**
     * Wraps EXIF Interface for JPEG Metadata creation. Can be overridden for
     * testing
     *
     * @param image Metadata for a jpeg image to create EXIF Interface
     * @return the created Exif Interface
     */
    protected ExifInterface createExif(TaskImage image) {
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
