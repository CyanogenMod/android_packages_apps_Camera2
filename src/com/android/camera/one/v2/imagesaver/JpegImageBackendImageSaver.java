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
import android.net.Uri;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.MainThread;
import com.android.camera.debug.Log;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.processing.imagebackend.ImageBackend;
import com.android.camera.processing.imagebackend.ImageConsumer;
import com.android.camera.processing.imagebackend.ImageProcessorListener;
import com.android.camera.processing.imagebackend.ImageProcessorProxyListener;
import com.android.camera.processing.imagebackend.ImageToProcess;
import com.android.camera.processing.imagebackend.TaskImageContainer;
import com.android.camera.session.CaptureSession;

import com.google.common.base.Optional;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Wires up the ImageBackend task submission process to save JPEG images. Camera
 * delivers a JPEG-compressed full-size image. This class does very little work
 * and just routes this image artifact as the thumbnail and to remote devices.
 */
public class JpegImageBackendImageSaver implements ImageSaver.Builder {
    private static Log.Tag TAG = new Log.Tag("JpegImgBESaver");

    @ParametersAreNonnullByDefault
    private static class ImageSaverImpl implements SingleImageSaver {
        private final MainThread mExecutor;
        private final CaptureSession mSession;
        private final OrientationManager.DeviceOrientation mImageRotation;
        private final ImageBackend mImageBackend;
        private final ImageProcessorListener mPreviewListener;

        public ImageSaverImpl(MainThread executor,
                CaptureSession session, OrientationManager.DeviceOrientation imageRotation,
                ImageBackend imageBackend, ImageProcessorListener previewListener) {
            mExecutor = executor;
            mSession = session;
            mImageRotation = imageRotation;
            mImageBackend = imageBackend;
            mPreviewListener = previewListener;
        }

        @Override
        public void saveAndCloseImage(ImageProxy image, Optional<ImageProxy> thumbnail) {
            // TODO: Use thumbnail to speed up RGB thumbnail creation whenever
            // possible. For now, just close it.
            if (thumbnail.isPresent()) {
                thumbnail.get().close();
            }
            final ImageProcessorProxyListener listenerProxy = mImageBackend.getProxyListener();

            listenerProxy.registerListener(mPreviewListener, image);

            Set<ImageConsumer.ImageTaskFlags> taskFlagsSet = new HashSet<>();
            taskFlagsSet.add(ImageConsumer.ImageTaskFlags.COMPRESS_TO_JPEG_AND_WRITE_TO_DISK);
            taskFlagsSet.add(ImageConsumer.ImageTaskFlags.CLOSE_ON_ALL_TASKS_RELEASE);

            try {
                mImageBackend.receiveImage(new ImageToProcess(image, mImageRotation),
                        mExecutor, taskFlagsSet, mSession);
            } catch (InterruptedException e) {
                // Impossible exception because receiveImage is nonblocking
                throw new RuntimeException(e);
            }
        }
    }

    private static class PreviewListener implements ImageProcessorListener {
        private final MainThread mExecutor;
        private final ImageProcessorProxyListener mListenerProxy;
        private final CaptureSession mSession;
        private final OrientationManager.DeviceOrientation mImageRotation;
        private final OneCamera.PictureSaverCallback mPictureSaverCallback;

        private PreviewListener(MainThread executor,
                ImageProcessorProxyListener listenerProxy, CaptureSession session,
                OrientationManager.DeviceOrientation imageRotation,
                OneCamera.PictureSaverCallback pictureSaverCallback) {
            mExecutor = executor;
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
                final Bitmap bitmap = BitmapFactory.decodeByteArray(payload.data, 0,
                        payload.data.length);
                // Send image to disk saver.
                mPictureSaverCallback.onThumbnailAvailable(bitmap, mImageRotation.getDegrees());
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
            // Remove yourself from the listener after JPEG save.
            // TODO: This should really be done by the ImageBackend to guarantee
            // ordering, since technically this could happen out of order.
            mListenerProxy.unregisterListener(this);
        }
    }

    private final MainThread mExecutor;
    private final ImageRotationCalculator mImageRotationCalculator;
    private final ImageBackend mImageBackend;

    /**
     * Constructor
     *
     * @param executor Executor to run listener events on the ImageBackend
     * @param imageRotationCalculator the image rotation calculator to determine
     */
    public JpegImageBackendImageSaver(MainThread executor,
            ImageRotationCalculator imageRotationCalculator,
            ImageBackend imageBackend) {
        mExecutor = executor;
        mImageRotationCalculator = imageRotationCalculator;
        mImageBackend = imageBackend;
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
                .toImageRotation(orientation);

        ImageProcessorProxyListener proxyListener = mImageBackend.getProxyListener();

        PreviewListener previewListener = new PreviewListener(mExecutor,
                proxyListener, session, imageRotation, pictureSaverCallback);
        return new MostRecentImageSaver(new ImageSaverImpl(mExecutor, session,
                imageRotation, mImageBackend, previewListener));
    }
}
