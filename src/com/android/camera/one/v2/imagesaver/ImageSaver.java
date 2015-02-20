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

import com.android.camera.app.OrientationManager;
import com.android.camera.async.SafeCloseable;
import com.android.camera.one.OneCamera;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.session.CaptureSession;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An ImageSaver represents a single transaction which may involve processing
 * one or more full-size and thumbnail images of varying formats resulting from
 * a burst.
 * <p>
 * Methods will be called on the same thread according to the following state
 * machine:<br>
 * (addThumbnail | addFullSizeImage)* close
 * <p>
 * ImageSaver instances will never be reused after close.
 * <p>
 * ImageSaver does not have to be thread-safe because instances are confined to
 * the thread they are created on.
 */
@ParametersAreNonnullByDefault
@NotThreadSafe
public interface ImageSaver extends SafeCloseable {
    /**
     * Creates ImageSaver instances.
     * <p>
     * The same builder may be used simultaneously on multiple threads, so it
     * must be thread-safe. However, ImageSaver instances are confined to the
     * thread they are created on, so they do not need to be thread-safe.
     */
    @ThreadSafe
    public interface Builder {
        /**
         * Creates a new ImageSaver which will be used to process and save a
         * single set of images.
         */
        public ImageSaver build(
                OneCamera.PictureSaverCallback pictureSaverCallback,
                OrientationManager.DeviceOrientation orientation,
                CaptureSession session);
    }

    /**
     * Adds a thumbnail image to be processed.
     * <p>
     * Implementations must eventually close the image and must tolerate
     * duplicate and out-of-order images.
     */
    public void addThumbnail(ImageProxy imageProxy);

    /**
     * Adds a full-size image to be processed along with a future to its
     * metadata. Note that the metadata future may be cancelled or result in an
     * exception if the camera system is being closed or the hardware reports an
     * error.
     * <p>
     * Implementations must eventually close the image and must tolerate
     * duplicate and out-of-order images.
     */
    public void addFullSizeImage(ImageProxy imageProxy, ListenableFuture<TotalCaptureResultProxy>
            metadata);

    /**
     * Indicates that no more images will be added.
     */
    public void close();
}
