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
import android.graphics.Rect;
import android.location.Location;
import android.media.CameraProfile;
import android.net.Uri;

import com.android.camera.Exif;
import com.android.camera.app.OrientationManager.DeviceOrientation;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.one.v2.camera2proxy.CaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.processing.memory.LruResourcePool;
import com.android.camera.processing.memory.LruResourcePool.Resource;
import com.android.camera.session.CaptureSession;
import com.android.camera.util.ExifUtil;
import com.android.camera.util.JpegUtilNative;
import com.android.camera.util.Size;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Implements the conversion of a YUV_420_888 image to compressed JPEG byte
 * array, using the native implementation of the Camera Application. If the
 * image is already JPEG, then it passes it through properly with the assumption
 * that the JPEG is already encoded in the proper orientation.
 */
public class TaskCompressImageToJpeg extends TaskJpegEncode {

    /**
     *  Loss-less JPEG compression  is usually about a factor of 5,
     *  and is a safe lower bound for this value to use to reduce the memory
     *  footprint for encoding the final jpg.
     */
    private static final int MINIMUM_EXPECTED_JPG_COMPRESSION_FACTOR = 2;
    private final LruResourcePool<Integer, ByteBuffer> mByteBufferDirectPool;

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
            CaptureSession captureSession,
            LruResourcePool<Integer, ByteBuffer> byteBufferResourcePool) {
        super(image, executor, imageTaskManager, ProcessingPriority.SLOW, captureSession);
        mByteBufferDirectPool = byteBufferResourcePool;
    }

    /**
     * Wraps the static call to JpegUtilNative for testability. {@see
     * JpegUtilNative#compressJpegFromYUV420Image}
     */
    public int compressJpegFromYUV420Image(ImageProxy img, ByteBuffer outBuf, int quality,
            Rect crop, int degrees) {
        return JpegUtilNative.compressJpegFromYUV420Image(img, outBuf, quality, crop, degrees);
    }

    /**
     * Encapsulates the required EXIF Tag parse for Image processing.
     *
     * @param exif EXIF data from which to extract data.
     * @return A Minimal Map from ExifInterface.Tag value to values required for Image processing
     */
    public Map<Integer, Integer> exifGetMinimalTags(ExifInterface exif) {
        Map<Integer, Integer> map = new HashMap<>();
        map.put(ExifInterface.TAG_ORIENTATION,
                ExifInterface.getRotationForOrientationValue((short) Exif.getOrientation(exif)));
        map.put(ExifInterface.TAG_PIXEL_X_DIMENSION, exif.getTagIntValue(
                ExifInterface.TAG_PIXEL_X_DIMENSION));
        map.put(ExifInterface.TAG_PIXEL_Y_DIMENSION, exif.getTagIntValue(
                ExifInterface.TAG_PIXEL_Y_DIMENSION));
        return map;
    }

    @Override
    public void run() {
        ImageToProcess img = mImage;
        mSession.getCollector().markProcessingTimeStart();
        final Rect safeCrop;

        // For JPEG, it is the capture devices responsibility to get proper
        // orientation.

        TaskImage inputImage, resultImage;
        byte[] writeOut;
        int numBytes;
        ByteBuffer compressedData;
        ExifInterface exifData = null;
        Resource<ByteBuffer> byteBufferResource = null;

        switch (img.proxy.getFormat()) {
            case ImageFormat.JPEG:
                try {
                    // In the cases, we will request a zero-oriented JPEG from
                    // the HAL; the HAL may deliver its orientation in the JPEG
                    // encoding __OR__ EXIF -- we don't know. We need to read
                    // the EXIF setting from byte payload and the EXIF reader
                    // doesn't work on direct buffers. So, we make a local
                    // copy in a non-direct buffer.
                    ByteBuffer origBuffer = img.proxy.getPlanes().get(0).getBuffer();
                    compressedData = ByteBuffer.allocate(origBuffer.limit());

                    // On memory allocation failure, fail gracefully.
                    if (compressedData == null) {
                        // TODO: Put memory allocation failure code here.
                        mSession.finishWithFailure(-1, true);
                        return;
                    }

                    origBuffer.rewind();
                    compressedData.put(origBuffer);
                    origBuffer.rewind();
                    compressedData.rewind();

                    // For JPEG, always use the EXIF orientation as ground
                    // truth on orientation, width and height.
                    Integer exifOrientation = null;
                    Integer exifPixelXDimension = null;
                    Integer exifPixelYDimension = null;

                    if (compressedData.array() != null) {
                        exifData = Exif.getExif(compressedData.array());
                        Map<Integer, Integer> minimalExifTags = exifGetMinimalTags(exifData);

                        exifOrientation = minimalExifTags.get(ExifInterface.TAG_ORIENTATION);
                        exifPixelXDimension = minimalExifTags
                                .get(ExifInterface.TAG_PIXEL_X_DIMENSION);
                        exifPixelYDimension = minimalExifTags
                                .get(ExifInterface.TAG_PIXEL_Y_DIMENSION);
                    }

                    final DeviceOrientation exifDerivedRotation;
                    if (exifOrientation == null) {
                        // No existing rotation value is assumed to be 0
                        // rotation.
                        exifDerivedRotation = DeviceOrientation.CLOCKWISE_0;
                    } else {
                        exifDerivedRotation = DeviceOrientation
                                .from(exifOrientation);
                    }

                    final int imageWidth;
                    final int imageHeight;
                    // Crop coordinate space is in original sensor coordinates.  We need
                    // to calculate the proper rotation of the crop to be applied to the
                    // final JPEG artifact.
                    final DeviceOrientation combinedRotationFromSensorToJpeg =
                            addOrientation(img.rotation, exifDerivedRotation);

                    if (exifPixelXDimension == null || exifPixelYDimension == null) {
                        Log.w(TAG,
                                "Cannot parse EXIF for image dimensions, passing 0x0 dimensions");
                        imageHeight = 0;
                        imageWidth = 0;
                        // calculate crop from exif info with image proxy width/height
                        safeCrop = guaranteedSafeCrop(img.proxy,
                                rotateBoundingBox(img.crop, combinedRotationFromSensorToJpeg));
                    } else {
                        imageWidth = exifPixelXDimension;
                        imageHeight = exifPixelYDimension;
                        // calculate crop from exif info with combined rotation
                        safeCrop = guaranteedSafeCrop(imageWidth, imageHeight,
                                rotateBoundingBox(img.crop, combinedRotationFromSensorToJpeg));
                    }

                    // Ignore the device rotation on ImageToProcess and use the EXIF from
                    // byte[] payload
                    inputImage = new TaskImage(
                            exifDerivedRotation,
                            imageWidth,
                            imageHeight,
                            img.proxy.getFormat(), safeCrop);

                    if(requiresCropOperation(img.proxy, safeCrop)) {
                        // Crop the image
                        resultImage = new TaskImage(
                                exifDerivedRotation,
                                safeCrop.width(),
                                safeCrop.height(),
                                img.proxy.getFormat(), null);

                        byte[] croppedResult = decompressCropAndRecompressJpegData(
                                compressedData.array(), safeCrop,
                                getJpegCompressionQuality());

                        compressedData = ByteBuffer.allocate(croppedResult.length);
                        compressedData.put(ByteBuffer.wrap(croppedResult));
                        compressedData.rewind();
                    } else {
                        // Pass-though the JPEG data
                        resultImage = inputImage;
                    }
                } finally {
                    // Release the image now that you have a usable copy in
                    // local memory
                    // Or you failed to process
                    mImageTaskManager.releaseSemaphoreReference(img, mExecutor);
                }

                onStart(mId, inputImage, resultImage, TaskInfo.Destination.FINAL_IMAGE);

                numBytes = compressedData.limit();
                break;
            case ImageFormat.YUV_420_888:
                safeCrop = guaranteedSafeCrop(img.proxy, img.crop);
                try {
                    inputImage = new TaskImage(img.rotation, img.proxy.getWidth(),
                            img.proxy.getHeight(),
                            img.proxy.getFormat(), safeCrop);
                    Size resultSize = getImageSizeForOrientation(img.crop.width(),
                            img.crop.height(),
                            img.rotation);

                    // Resulting image will be rotated so that viewers won't
                    // have to rotate. That's why the resulting image will have 0
                    // rotation.
                    resultImage = new TaskImage(
                            DeviceOrientation.CLOCKWISE_0, resultSize.getWidth(),
                            resultSize.getHeight(),
                            ImageFormat.JPEG, null);
                    // Image rotation is already encoded into the bytes.

                    onStart(mId, inputImage, resultImage, TaskInfo.Destination.FINAL_IMAGE);

                    // WARNING:
                    // This reduces the size of the buffer that is created
                    // to hold the final jpg. It is reduced by the "Minimum expected
                    // jpg compression factor" to reduce memory allocation consumption.
                    // If the final jpg is more than this size the image will be
                    // corrupted. The maximum size of an image is width * height *
                    // number_of_channels. We artificially reduce this number based on
                    // what we expect the compression ratio to be to reduce the
                    // amount of memory we are required to allocate.
                    int maxPossibleJpgSize = 3 * resultImage.width * resultImage.height;
                    int jpgBufferSize = maxPossibleJpgSize /
                          MINIMUM_EXPECTED_JPG_COMPRESSION_FACTOR;

                    byteBufferResource = mByteBufferDirectPool.acquire(jpgBufferSize);
                    compressedData = byteBufferResource.get();

                    // On memory allocation failure, fail gracefully.
                    if (compressedData == null) {
                        // TODO: Put memory allocation failure code here.
                        mSession.finishWithFailure(-1, true);
                        byteBufferResource.close();
                        return;
                    }

                    // Do the actual compression here.
                    numBytes = compressJpegFromYUV420Image(
                            img.proxy, compressedData, getJpegCompressionQuality(),
                            img.crop, inputImage.orientation.getDegrees());

                    // If the compression overflows the size of the buffer, the
                    // actual number of bytes will be returned.
                    if (numBytes > jpgBufferSize) {
                        byteBufferResource.close();
                        mByteBufferDirectPool.acquire(maxPossibleJpgSize);
                        compressedData = byteBufferResource.get();

                        // On memory allocation failure, fail gracefully.
                        if (compressedData == null) {
                            // TODO: Put memory allocation failure code here.
                            mSession.finishWithFailure(-1, true);
                            byteBufferResource.close();
                            return;
                        }

                        numBytes = compressJpegFromYUV420Image(
                              img.proxy, compressedData, getJpegCompressionQuality(),
                              img.crop, inputImage.orientation.getDegrees());
                    }

                    if (numBytes < 0) {
                        byteBufferResource.close();
                        throw new RuntimeException("Error compressing jpeg.");
                    }
                    compressedData.limit(numBytes);
                } finally {
                    // Release the image now that you have a usable copy in local memory
                    // Or you failed to process
                    mImageTaskManager.releaseSemaphoreReference(img, mExecutor);
                }
                break;
            default:
                mImageTaskManager.releaseSemaphoreReference(img, mExecutor);
                throw new IllegalArgumentException(
                        "Unsupported input image format for TaskCompressImageToJpeg");
        }

        writeOut = new byte[numBytes];
        compressedData.get(writeOut);
        compressedData.rewind();

        if (byteBufferResource != null) {
            byteBufferResource.close();
        }

        onJpegEncodeDone(mId, inputImage, resultImage, writeOut,
                TaskInfo.Destination.FINAL_IMAGE);

        // In rare cases, TaskCompressImageToJpeg might complete before
        // TaskConvertImageToRGBPreview. However, session should take care
        // of out-of-order completion.
        // EXIF tags are rewritten so that output from this task is normalized.
        final TaskImage finalInput = inputImage;
        final TaskImage finalResult = resultImage;

        final ExifInterface exif = createExif(Optional.fromNullable(exifData), resultImage,
                img.metadata);
        mSession.getCollector().decorateAtTimeWriteToDisk(exif);
        ListenableFuture<Optional<Uri>> futureUri = mSession.saveAndFinish(writeOut,
                resultImage.width, resultImage.height, resultImage.orientation.getDegrees(), exif);
        Futures.addCallback(futureUri, new FutureCallback<Optional<Uri>>() {
            @Override
            public void onSuccess(Optional<Uri> uriOptional) {
                if (uriOptional.isPresent()) {
                    onUriResolved(mId, finalInput, finalResult, uriOptional.get(),
                            TaskInfo.Destination.FINAL_IMAGE);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        });

        final ListenableFuture<TotalCaptureResultProxy> requestMetadata = img.metadata;
        // If TotalCaptureResults are available add them to the capture event.
        // Otherwise, do NOT wait for them, since we'd be stalling the ImageBackend
        if (requestMetadata.isDone()) {
            try {
                mSession.getCollector()
                        .decorateAtTimeOfCaptureRequestAvailable(requestMetadata.get());
            } catch (InterruptedException e) {
                Log.e(TAG,
                        "CaptureResults not added to photoCaptureDoneEvent event due to Interrupted Exception.");
            } catch (ExecutionException e) {
                Log.w(TAG,
                        "CaptureResults not added to photoCaptureDoneEvent event due to Execution Exception.");
            } finally {
                mSession.getCollector().photoCaptureDoneEvent();
            }
        } else {
            Log.w(TAG, "CaptureResults unavailable to photoCaptureDoneEvent event.");
            mSession.getCollector().photoCaptureDoneEvent();
        }
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
    protected ExifInterface createExif(Optional<ExifInterface> exifData, TaskImage image,
                                       ListenableFuture<TotalCaptureResultProxy> totalCaptureResultProxyFuture) {
        ExifInterface exif;
        if (exifData.isPresent()) {
            exif = exifData.get();
        } else {
            exif = new ExifInterface();
        }
        Optional<Location> location = Optional.fromNullable(mSession.getLocation());

        try {
            new ExifUtil(exif).populateExif(Optional.of(image),
                    Optional.<CaptureResultProxy>of(totalCaptureResultProxyFuture.get()), location);
        } catch (InterruptedException | ExecutionException e) {
            new ExifUtil(exif).populateExif(Optional.of(image),
                    Optional.<CaptureResultProxy>absent(), location);
        }

        return exif;
    }

    /**
     * @return Quality level to use for JPEG compression.
     */
    protected int getJpegCompressionQuality () {
        return CameraProfile.getJpegEncodingQualityParameter(CameraProfile.QUALITY_HIGH);
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
