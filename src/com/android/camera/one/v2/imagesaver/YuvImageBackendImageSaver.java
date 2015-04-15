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
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;

import com.android.camera.app.OrientationManager;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.processing.imagebackend.ImageBackend;
import com.android.camera.processing.imagebackend.ImageConsumer;
import com.android.camera.processing.imagebackend.ImageProcessorListener;
import com.android.camera.processing.imagebackend.ImageToProcess;
import com.android.camera.processing.imagebackend.TaskImageContainer;
import com.android.camera.session.CaptureSession;
import com.android.camera2.R;

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
 * Wires up the ImageBackend task submission process to save Yuv images.
 */
public class YuvImageBackendImageSaver implements ImageSaver.Builder {
    /** Progress for JPEG saving once the intermediate thumbnail is done. */
    private static final int PERCENTAGE_INTERMEDIATE_THUMBNAIL_DONE = 25;
    /** Progress for JPEG saving after compression, before writing to disk. */
    private static final int PERCENTAGE_COMPRESSION_DONE = 95;


    @ParametersAreNonnullByDefault
    private final class ImageSaverImpl implements SingleImageSaver {
        private final CaptureSession mSession;
        private final OrientationManager.DeviceOrientation mImageRotation;
        private final ImageProcessorListener mImageProcessorListener;

        public ImageSaverImpl(CaptureSession session,
                OrientationManager.DeviceOrientation imageRotation,
                ImageProcessorListener imageProcessorListener) {
            mSession = session;
            mImageRotation = imageRotation;
            mImageProcessorListener = imageProcessorListener;
        }

        @Override
        public void saveAndCloseImage(ImageProxy image, Optional<ImageProxy> thumbnail,
                ListenableFuture<TotalCaptureResultProxy> metadata) {
            // TODO Use thumbnail to speedup RGB thumbnail creation whenever
            // possible.
            if (thumbnail.isPresent()) {
                thumbnail.get().close();
            }

            Set<ImageConsumer.ImageTaskFlags> taskFlagsSet = new HashSet<>();
            taskFlagsSet.add(ImageConsumer.ImageTaskFlags.CREATE_EARLY_FILMSTRIP_PREVIEW);
            taskFlagsSet.add(ImageConsumer.ImageTaskFlags.CONVERT_TO_RGB_PREVIEW);
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

    private static class YuvImageProcessorListener implements ImageProcessorListener {
        private final CaptureSession mSession;
        private final OrientationManager.DeviceOrientation mImageRotation;
        private final OneCamera.PictureSaverCallback mPictureSaverCallback;

        private YuvImageProcessorListener(CaptureSession session,
                OrientationManager.DeviceOrientation imageRotation,
                OneCamera.PictureSaverCallback pictureSaverCallback) {
            mSession = session;
            mImageRotation = imageRotation;
            mPictureSaverCallback = pictureSaverCallback;
        }

        @Override
        public void onStart(TaskImageContainer.TaskInfo task) {
            switch (task.destination) {
                case FAST_THUMBNAIL:
                    // Signal start of processing
                    break;
                case INTERMEDIATE_THUMBNAIL:
                    // Do nothing
                    break;
            }
        }

        @Override
        public void onResultCompressed(TaskImageContainer.TaskInfo task,
                TaskImageContainer.CompressedPayload payload) {
            if (task.destination == TaskImageContainer.TaskInfo.Destination.FINAL_IMAGE) {
                mSession.setProgress(PERCENTAGE_COMPRESSION_DONE);
                mPictureSaverCallback.onRemoteThumbnailAvailable(payload.data);
            }
        }

        @Override
        public void onResultUncompressed(TaskImageContainer.TaskInfo task,
                TaskImageContainer.UncompressedPayload payload) {
            // Load bitmap into CameraAppUI
            switch (task.destination) {
                case FAST_THUMBNAIL:
                    final Bitmap bitmap = Bitmap.createBitmap(payload.data,
                            task.result.width,
                            task.result.height, Bitmap.Config.ARGB_8888);
                    mSession.updateCaptureIndicatorThumbnail(bitmap, mImageRotation.getDegrees());
                    break;
                case INTERMEDIATE_THUMBNAIL:
                    final Bitmap bitmapIntermediate = Bitmap.createBitmap(payload.data,
                            task.result.width,
                            task.result.height, Bitmap.Config.ARGB_8888);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(mImageRotation.getDegrees());
                    final Bitmap bitmapIntermediateRotated = Bitmap.createBitmap(
                            bitmapIntermediate, 0, 0, bitmapIntermediate.getWidth(),
                            bitmapIntermediate.getHeight(), matrix, true);
                    mSession.updateThumbnail(bitmapIntermediateRotated);
                    mSession.setProgressMessage(R.string.session_saving_image);
                    mSession.setProgress(PERCENTAGE_INTERMEDIATE_THUMBNAIL_DONE);
                    break;
            }
        }

        @Override
        public void onResultUri(TaskImageContainer.TaskInfo task, Uri uri) {
            // Do Nothing
        }
    }

    private final ImageRotationCalculator mImageRotationCalculator;
    private final ImageBackend mImageBackend;
    private final Rect mCrop;
    private final Executor mExecutor;

    /**
     * Constructor
     *
     * @param imageRotationCalculator the image rotation calculator to determine
     * @param imageBackend ImageBackend to run the image tasks
     * @param crop the crop to apply. Note that crop must be done *before* any
     *            rotation of the images.
     */
    public YuvImageBackendImageSaver(ImageRotationCalculator imageRotationCalculator,
            ImageBackend imageBackend, Rect crop) {
        mImageRotationCalculator = imageRotationCalculator;
        mImageBackend = imageBackend;
        mCrop = crop;
        mExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Constructor for dependency injection/ testing.
     *
     * @param imageRotationCalculator the image rotation calculator to determine
     * @param imageBackend ImageBackend to run the image tasks
     * @param crop the crop to apply. Note that crop must be done *before* any
     *            rotation of the images.
     * @param executor Executor to be used for listener events in ImageBackend.
     */
    @VisibleForTesting
    public YuvImageBackendImageSaver(ImageRotationCalculator imageRotationCalculator,
            ImageBackend imageBackend, Rect crop, Executor executor) {
        mImageRotationCalculator = imageRotationCalculator;
        mImageBackend = imageBackend;
        mCrop = crop;
        mExecutor = executor;
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

        YuvImageProcessorListener yuvImageProcessorListener = new YuvImageProcessorListener(
                session, imageRotation, pictureSaverCallback);
        return new MostRecentImageSaver(new ImageSaverImpl(session, imageRotation,
                yuvImageProcessorListener));
    }
}
