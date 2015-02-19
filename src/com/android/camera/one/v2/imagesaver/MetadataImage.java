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

import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * An ImageProxy decorator that attaches a TotalCaptureResultProxy metadata to
 * an ImageProxy
 */
public final class MetadataImage extends ForwardingImageProxy {
    private final ListenableFuture<TotalCaptureResultProxy> mMetadata;

    /**
     * Create a new MetadataImage
     *
     * @param image The image.
     * @param metadata The TotalCaptureResultProxy metadata associated with image.
     */
    public MetadataImage(ImageProxy image, ListenableFuture<TotalCaptureResultProxy> metadata) {
        super(image);
        mMetadata = metadata;
    }

    /**
     * @return The TotalCaptureResultProxy metadata associated with image.
     */
    public ListenableFuture<TotalCaptureResultProxy> getMetadata() {
        return mMetadata;
    }
}
