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

import android.graphics.Rect;

import com.android.camera.app.OrientationManager;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * An image to be processed by the image backend. Contains an ImageProxy and
 * parameters required to process the image.
 */
public class ImageToProcess {
    public final ImageProxy proxy;
    public final OrientationManager.DeviceOrientation rotation;
    public final ListenableFuture<TotalCaptureResultProxy> metadata;
    public final Rect crop;

    /**
     * @param proxy The underlying image to process.
     * @param imageRotation The amount to rotate the image (after cropping).
     * @param metadata The capture result metadata associated with this image.
     * @param crop The crop region of the image to save. Note that this is in
     *            the coordinate-space of the original image, so the crop should
     *            be performed *before* any rotation, and a crop rectangle of
     *            (0, 0)-(proxy.width, proxy.height) is a no-op.
     */
    public ImageToProcess(ImageProxy proxy, OrientationManager.DeviceOrientation imageRotation,
                          ListenableFuture<TotalCaptureResultProxy> metadata, Rect crop) {
        this.proxy = proxy;
        this.rotation = imageRotation;
        this.metadata = metadata;
        this.crop = crop;
    }

    /**
     * No crop.
     *
     * @param proxy The underlying image to process.
     * @param imageRotation The amount to rotate the image (after cropping).
     * @param metadata The capture result metadata associated with this image.
     */
    public ImageToProcess(ImageProxy proxy, OrientationManager.DeviceOrientation imageRotation,
                          ListenableFuture<TotalCaptureResultProxy> metadata) {
        this(proxy, imageRotation, metadata, new Rect(0, 0, proxy.getWidth(), proxy.getHeight()));
    }
}
