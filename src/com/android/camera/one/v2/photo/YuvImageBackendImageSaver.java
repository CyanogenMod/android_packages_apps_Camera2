/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.one.v2.photo;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.MainThreadExecutor;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.processing.ProcessingServiceManager;
import com.android.camera.processing.imagebackend.ImageBackend;
import com.android.camera.processing.imagebackend.ImageConsumer;
import com.android.camera.processing.imagebackend.ImageProcessorListener;
import com.android.camera.processing.imagebackend.ImageProcessorProxyListener;
import com.android.camera.processing.imagebackend.ImageToProcess;
import com.android.camera.processing.imagebackend.TaskImageContainer;
import com.android.camera.session.CaptureSession;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Wires up the ImageBackend task submission process to save Yuv images.
 */
public class YuvImageBackendImageSaver implements ImageSaver.Builder {
    private final Executor mExecutor;
    private final ImageRotationCalculator mImageRotationCalculator;

    /**
     * Constructor
     *
     * @param executor Executor to run listener events on the ImageBackend
     * @param imageRotationCalculator the image rotation calculator to determine
     *            the final image rotation with
     */
    public YuvImageBackendImageSaver(MainThreadExecutor executor,
            ImageRotationCalculator imageRotationCalculator) {
        mExecutor = executor;
        mImageRotationCalculator = imageRotationCalculator;
    }

    /**
     * Builder for the Zsl/ImageBackend Interface
     *
     * @return Instantiated interface object
     */
    @Override
    public ImageSaver build(
            final OneCamera.PictureSaverCallback pictureSaverCallback,
            final OrientationManager.DeviceOrientation orientation,
            final CaptureSession session) {
        final OrientationManager.DeviceOrientation imageRotation = mImageRotationCalculator
                .toImageRotation(orientation);

        return new ImageSaver() {

            /**
             * Instantiates all the tasks to be run on this image as well as the
             * listener that fires the UI events.
             *
             * @param imageProxy Image that is required by spawned tasks
             */
            @Override
            public void saveAndCloseImage(ImageProxy imageProxy) {
                final ImageBackend imageBackend = ProcessingServiceManager
                        .getImageBackendInstance();
                final ImageProcessorProxyListener listenerProxy = imageBackend.getProxyListener();

                final ImageProcessorListener previewListener = new ImageProcessorListener() {
                    @Override
                    public synchronized void onStart(TaskImageContainer.TaskInfo task) {
                        switch (task.destination) {
                            case FAST_THUMBNAIL:
                                // Start Animation
                                if (task.result.format
                                == TaskImageContainer.TaskImage.EXTRA_USER_DEFINED_FORMAT_ARGB_8888) {
                                    pictureSaverCallback.onThumbnailProcessingBegun();
                                }
                                break;
                            case INTERMEDIATE_THUMBNAIL:
                                // Do nothing
                                break;
                        }
                    }

                    @Override
                    public void onResultCompressed(TaskImageContainer.TaskInfo task,
                            TaskImageContainer.CompressedPayload payload) {
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
                                pictureSaverCallback.onThumbnailAvailable(bitmap,
                                        imageRotation.getDegrees());
                                break;
                            case INTERMEDIATE_THUMBNAIL:
                                final Bitmap bitmapIntermediate = Bitmap.createBitmap(payload.data,
                                        task.result.width,
                                        task.result.height, Bitmap.Config.ARGB_8888);
                                Matrix matrix = new Matrix();
                                matrix.postRotate(imageRotation.getDegrees());
                                final Bitmap bitmapIntermediateRotated = Bitmap.createBitmap(
                                        bitmapIntermediate, 0, 0, bitmapIntermediate.getWidth(),
                                        bitmapIntermediate.getHeight(), matrix, true);
                                mExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        // TODO: Put proper I18n string message here.
                                        session.startSession(bitmapIntermediateRotated,
                                                "Saving rotated image ...");
                                        session.setProgress(20);
                                    }
                                });
                                break;
                        }

                    }

                    @Override
                    public void onResultUri(TaskImageContainer.TaskInfo task, Uri uri) {
                        // Remove yourself from the listener after JPEG save.
                        // TODO: This should really be done by the ImageBackend to guarantee
                        // ordering, since technically this could happen out of order.
                        listenerProxy.unregisterListener(this);
                    }
                };

                listenerProxy.registerListener(previewListener, imageProxy);

                Set<ImageConsumer.ImageTaskFlags> taskFlagsSet = new HashSet<>();
                taskFlagsSet.add(ImageConsumer.ImageTaskFlags.CONVERT_IMAGE_TO_RGB_PREVIEW);
                taskFlagsSet.add(ImageConsumer.ImageTaskFlags.COMPRESS_IMAGE_TO_JPEG);
                taskFlagsSet.add(ImageConsumer.ImageTaskFlags.CLOSE_IMAGE_ON_RELEASE);

                try {
                    imageBackend.receiveImage(new ImageToProcess(imageProxy, imageRotation),
                            mExecutor, taskFlagsSet, session);
                } catch (InterruptedException e) {
                    // TODO: Fire error here, since we are non-blocking.
                }
            }
        };
    }
};
