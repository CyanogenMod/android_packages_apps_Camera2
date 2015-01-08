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

package com.android.camera.one.v2.photo.zsl;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.imagesaver.ImageSaver;
import com.android.camera.one.v2.photo.ImageCaptureCommand;
import com.android.camera.one.v2.sharedimagereader.metadatasynchronizer.MetadataPool;
import com.google.common.base.Predicate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Captures images by first looking to the zsl ring buffer for acceptable (based
 * on metadata) images. If no such images are available, a fallback
 * ImageCaptureCommand is used instead.
 */
@ParametersAreNonnullByDefault
class ZslImageCaptureCommand implements ImageCaptureCommand {
    private final BufferQueue<ImageProxy> mZslRingBuffer;
    private final MetadataPool mZslMetadataPool;
    private final ImageCaptureCommand mFallbackCommand;
    private final Predicate<TotalCaptureResultProxy> mMetadataFilter;

    public ZslImageCaptureCommand(BufferQueue<ImageProxy> zslRingBuffer, MetadataPool
            zslMetadataPool, ImageCaptureCommand fallbackCommand,
            Predicate<TotalCaptureResultProxy> metadataFilter) {
        mZslRingBuffer = zslRingBuffer;
        mZslMetadataPool = zslMetadataPool;
        mFallbackCommand = fallbackCommand;
        mMetadataFilter = metadataFilter;
    }

    /**
     * @return All images currently in the ring-buffer, ordered from oldest to
     *         most recent.
     */
    private List<ImageProxy> getAllAvailableImages() throws InterruptedException,
            BufferQueue.BufferQueueClosedException {
        List<ImageProxy> images = new ArrayList<>();
        try {
            // Keep grabbing images until there are no more immediately
            // available in the ring buffer.
            while (true) {
                try {
                    images.add(mZslRingBuffer.getNext(0, TimeUnit.SECONDS));
                } catch (TimeoutException e) {
                    break;
                }
            }
        } catch (Exception e) {
            // Close the images to avoid leaking them, since they will not be
            // returned to the caller.
            for (ImageProxy image : images) {
                image.close();
            }
            throw e;
        }
        return images;
    }

    @Nullable
    private ImageProxy tryGetZslImage() throws InterruptedException,
            BufferQueue.BufferQueueClosedException {
        List<ImageProxy> images = getAllAvailableImages();
        ImageProxy imageToSave = null;
        try {
            for (ImageProxy image : images) {
                Future<TotalCaptureResultProxy> metadataFuture =
                        mZslMetadataPool.removeMetadataFuture(image.getTimestamp());
                try {
                    if (mMetadataFilter.apply(metadataFuture.get())) {
                        imageToSave = image;
                    }
                } catch (ExecutionException | CancellationException e) {
                    // If we cannot get metadata for an image, for whatever
                    // reason, assume it is not acceptable for capture.
                }
            }
        } catch (Exception e) {
            if (imageToSave != null) {
                imageToSave.close();
            }
            throw e;
        } finally {
            for (ImageProxy image : images) {
                if (image != imageToSave) {
                    image.close();
                }
            }
        }
        return imageToSave;
    }

    @Override
    public void run(Updatable<Void> imageExposeCallback, ImageSaver imageSaver)
            throws InterruptedException, CameraAccessException,
            CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
        boolean mustCloseImageSaver = true;
        try {
            ImageProxy image = tryGetZslImage();
            if (image != null) {
                imageExposeCallback.update(null);
                imageSaver.addFullSizeImage(image);
            } else {
                mustCloseImageSaver = false;
                mFallbackCommand.run(imageExposeCallback, imageSaver);
            }
        } catch (BufferQueue.BufferQueueClosedException e) {
            // The zsl ring buffer has been closed, so do nothing since the
            // system is shutting down.
        } finally {
            if (mustCloseImageSaver) {
                imageSaver.close();
            }
        }
    }
}
