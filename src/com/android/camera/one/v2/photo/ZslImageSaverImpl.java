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
import android.location.Location;
import android.net.Uri;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.Updatable;
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
 * Wires up the ImageBackend task submission process to the ZSL backend. This
 * object is created on a per CaptureSession basis.
 */
public class ZslImageSaverImpl implements ImageSaver.Builder {
    private String mTitle;
    private OrientationManager.DeviceOrientation mOrientation;
    private Location mLocation;
    private Updatable<byte[]> mThumbnailCallback;
    private CaptureSession mSession;

    final Executor mExecutor;
    final ImageRotationCalculator mImageRotationCalculator;

    /**
     * Constructor
     *
     * @param executor Executor to run listener events on the ImageBackend
     * @param imageRotationCalculator the image rotation calculator to determine
     *            the final image rotation with
     */
    public ZslImageSaverImpl(Executor executor, ImageRotationCalculator imageRotationCalculator) {
        mExecutor = executor;
        mImageRotationCalculator = imageRotationCalculator;
    }

    /**
     * Saves title for the session. TODO: See whether some of these setters can
     * be derived from the session.
     *
     * @param title Title to be saved
     */
    @Override
    public void setTitle(String title) {
        mTitle = title;
    }

    /**
     * Saves Orientation of the current session
     *
     * @param orientation Orientation to be saved
     */
    @Override
    public void setOrientation(OrientationManager.DeviceOrientation orientation) {
        mOrientation = orientation;
    }

    /**
     * Saves location for this session
     *
     * @param location Location to be saved
     */
    @Override
    public void setLocation(Location location) {
        mLocation = location;
    }

    /**
     * Associates the CaptureSession with the tasks to be run
     *
     * @param session
     */
    @Override
    public void setSession(CaptureSession session) {
        mSession = session;
    }

    /**
     * Associates an updateable thumbnail for the Android Wear. MUST be in
     * compressed JPEG.
     *
     * @param callback
     */
    @Override
    public void setThumbnailCallback(Updatable<byte[]> callback) {
        mThumbnailCallback = callback;
    }

    /**
     * Builder for the Zsl/ImageBackend Interface
     *
     * @return Instantiated interface object
     */
    @Override
    public ImageSaver build() {
        final OrientationManager.DeviceOrientation imageRotation = mImageRotationCalculator
                .toImageRotation(mOrientation);

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
                    public void onStart(TaskImageContainer.TaskInfo task) {
                        // Start Animation
                        if (task.result.format
                            == TaskImageContainer.TaskImage.EXTRA_USER_DEFINED_FORMAT_ARGB_8888) {
                            if (imageBackend.hasValidUI()) {
                                mExecutor.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        imageBackend.getAppUI()
                                                .startCaptureIndicatorRevealAnimation(
                                                        "PUT STRING FOR REVEAL"); // FIXME
                                                                                  // TODO
                                    }
                                });
                            }
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
                        if (imageBackend.hasValidUI()) {
                            final Bitmap bitmap = Bitmap.createBitmap(payload.data,
                                    task.result.width,
                                    task.result.height, Bitmap.Config.ARGB_8888);
                            mExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    // TODO: Finalize and I18N string.
                                    mSession.startSession(bitmap, "Saving image ...");
                                    mSession.setProgress(42);
                                    imageBackend.getAppUI().updateCaptureIndicatorThumbnail(bitmap,
                                            imageRotation.getDegrees());
                                }
                            });
                        }

                        // Remove yourself from the listener
                        listenerProxy.unregisterListener(this);
                    }

                    @Override
                    public void onResultUri(TaskImageContainer.TaskInfo task, Uri uri) {
                    }
                };

                listenerProxy.registerListener(previewListener, imageProxy);

                Set<ImageConsumer.ImageTaskFlags> taskFlagsSet = new HashSet<>();
                taskFlagsSet.add(ImageConsumer.ImageTaskFlags.CONVERT_IMAGE_TO_RGB_PREVIEW);
                taskFlagsSet.add(ImageConsumer.ImageTaskFlags.COMPRESS_IMAGE_TO_JPEG);
                taskFlagsSet.add(ImageConsumer.ImageTaskFlags.CLOSE_IMAGE_ON_RELEASE);

                try {
                    imageBackend.receiveImage(new ImageToProcess(imageProxy, imageRotation),
                            mExecutor, taskFlagsSet, mSession);
                } catch (InterruptedException e) {
                    // TODO: Fire error here, since we are non-blocking.
                }
            }
        };
    }
};
