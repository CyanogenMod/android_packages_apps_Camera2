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

package com.android.camera.one.v2.imagesaver;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;

import com.android.camera.Exif;
import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.processing.imagebackend.ImageBackend;
import com.android.camera.processing.imagebackend.ImageConsumer;
import com.android.camera.processing.imagebackend.ImageProcessorListener;
import com.android.camera.processing.imagebackend.ImageProcessorProxyListener;
import com.android.camera.processing.imagebackend.ImageToProcess;
import com.android.camera.processing.imagebackend.TaskImageContainer;
import com.android.camera.session.CaptureSession;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Wires up the ImageBackend task submission process to save JPEG images. Camera
 * delivers a JPEG-compressed full-size image. This class does very little work
 * and just routes this image artifact as the thumbnail and to remote devices.
 */
public class JpegImageBackendImageSaver implements ImageSaver.Builder {

    @ParametersAreNonnullByDefault
    private final class ImageSaverImpl implements SingleImageSaver {
        private final CaptureSession mSession;
        private final OrientationManager.DeviceOrientation mImageRotation;
        private final ImageBackend mImageBackend;
        private final ImageProcessorListener mImageProcessorListener;

        public ImageSaverImpl(CaptureSession session,
                OrientationManager.DeviceOrientation imageRotation,
                ImageBackend imageBackend, ImageProcessorListener imageProcessorListener) {
            mSession = session;
            mImageRotation = imageRotation;
            mImageBackend = imageBackend;
            mImageProcessorListener = imageProcessorListener;
        }

        @Override
        public void saveAndCloseImage(ImageProxy image, Optional<ImageProxy> thumbnail,
                ListenableFuture<TotalCaptureResultProxy> metadata) {
            // TODO: Use thumbnail to speed up RGB thumbnail creation whenever
            // possible. For now, just close it.
            if (thumbnail.isPresent()) {
                thumbnail.get().close();
            }

            Set<ImageConsumer.ImageTaskFlags> taskFlagsSet = new HashSet<>();
            taskFlagsSet.add(ImageConsumer.ImageTaskFlags.COMPRESS_TO_JPEG_AND_WRITE_TO_DISK);
            taskFlagsSet.add(ImageConsumer.ImageTaskFlags.CLOSE_ON_ALL_TASKS_RELEASE);

            try {
                mImageBackend.receiveImage(new ImageToProcess(image, mImageRotation, metadata,
                        mCrop), mExecutor, taskFlagsSet, mSession,
                        Optional.of(mImageProcessorListener));
            } catch (InterruptedException e) {
                // Impossible exception because receiveImage is nonblocking
                throw new RuntimeException(e);
            }
        }
    }

    private static class JpegImageProcessorListener implements ImageProcessorListener {
        private final ImageProcessorProxyListener mListenerProxy;
        private final CaptureSession mSession;
        private final OrientationManager.DeviceOrientation mImageRotation;
        private final OneCamera.PictureSaverCallback mPictureSaverCallback;

        private JpegImageProcessorListener(ImageProcessorProxyListener listenerProxy,
                CaptureSession session,
                OrientationManager.DeviceOrientation imageRotation,
                OneCamera.PictureSaverCallback pictureSaverCallback) {
            mListenerProxy = listenerProxy;
            mSession = session;
            mImageRotation = imageRotation;
            mPictureSaverCallback = pictureSaverCallback;
        }

        @Override
        public void onStart(TaskImageContainer.TaskInfo task) {
        }

        @Override
        public void onResultCompressed(TaskImageContainer.TaskInfo task,
                TaskImageContainer.CompressedPayload payload) {
            if (task.destination == TaskImageContainer.TaskInfo.Destination.FINAL_IMAGE) {
                // Just start the thumbnail now, since there's no earlier event.

                // Downsample and convert the JPEG payload to a reasonably-sized
                // Bitmap
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = JPEG_DOWNSAMPLE_FOR_FAST_INDICATOR;
                final Bitmap bitmap = BitmapFactory.decodeByteArray(payload.data, 0,
                        payload.data.length, options);

                // If the rotation is implemented as an EXIF flag, we need to
                // pass this information onto the UI call, since the rotation is
                // NOT applied to the bitmap directly.
                int rotation = Exif.getOrientation(payload.data);
                mSession.updateCaptureIndicatorThumbnail(bitmap, rotation);
                // Send image to remote devices
                mPictureSaverCallback.onRemoteThumbnailAvailable(payload.data);
            }

        }

        @Override
        public void onResultUncompressed(TaskImageContainer.TaskInfo task,
                TaskImageContainer.UncompressedPayload payload) {
            // Do Nothing
        }

        @Override
        public void onResultUri(TaskImageContainer.TaskInfo task, Uri uri) {
            // Do Nothing
        }
    }

    /** Factor to downsample full-size JPEG image for use in thumbnail bitmap. */
    private static final int JPEG_DOWNSAMPLE_FOR_FAST_INDICATOR = 4;
    private static Log.Tag TAG = new Log.Tag("JpegImgBESaver");
    private final ImageRotationCalculator mImageRotationCalculator;
    private final ImageBackend mImageBackend;
    private final Executor mExecutor;
    private final Rect mCrop;


    /**
     * Constructor Instantiate a local instance executor for all JPEG ImageSaver
     * factory requests via constructor.
     *
     * @param imageRotationCalculator the image rotation calculator to determine
     * @param imageBackend ImageBackend to run the image tasks
     */
    public JpegImageBackendImageSaver(
            ImageRotationCalculator imageRotationCalculator,
            ImageBackend imageBackend, Rect crop) {
        mImageRotationCalculator = imageRotationCalculator;
        mImageBackend = imageBackend;
        mExecutor = Executors.newSingleThreadExecutor();
        mCrop = crop;
    }

    /**
     * Constructor for dependency injection/ testing.
     *
     * @param imageRotationCalculator the image rotation calculator to determine
     * @param imageBackend ImageBackend to run the image tasks
     * @param executor Executor to be used for listener events in ImageBackend.
     */
    @VisibleForTesting
    public JpegImageBackendImageSaver(
            ImageRotationCalculator imageRotationCalculator,
            ImageBackend imageBackend, Executor executor, Rect crop) {
        mImageRotationCalculator = imageRotationCalculator;
        mImageBackend = imageBackend;
        mExecutor = executor;
        mCrop = crop;
    }

    /**
     * Builder for the Zsl/ImageBackend Interface
     *
     * @return Instantiated interface object
     */
    @Override
    public ImageSaver build(
            @Nonnull OneCamera.PictureSaverCallback pictureSaverCallback,
            @Nonnull OrientationManager.DeviceOrientation orientation,
            @Nonnull CaptureSession session) {
        final OrientationManager.DeviceOrientation imageRotation = mImageRotationCalculator
                .toImageRotation();

        ImageProcessorProxyListener proxyListener = mImageBackend.getProxyListener();

        JpegImageProcessorListener jpegImageProcessorListener = new JpegImageProcessorListener(
                proxyListener, session, imageRotation, pictureSaverCallback);
        return new MostRecentImageSaver(new ImageSaverImpl(session,
                imageRotation, mImageBackend, jpegImageProcessorListener));
    }
}
