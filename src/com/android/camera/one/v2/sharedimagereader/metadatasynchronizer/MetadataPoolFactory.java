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

package com.android.camera.one.v2.sharedimagereader.metadatasynchronizer;

import com.android.camera.async.BufferQueueController;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.google.common.annotations.VisibleForTesting;

public class MetadataPoolFactory {
    private final BufferQueueController<ImageProxy> mImageQueue;
    private final MetadataPoolImpl mMetadataPool;

    public MetadataPoolFactory(BufferQueueController<ImageProxy> imageQueue) {
        mMetadataPool = new MetadataPoolImpl();
        mImageQueue = new MetadataReleasingImageQueue(imageQueue, mMetadataPool);
    }

    public MetadataPool provideMetadataPool() {
        return mMetadataPool;
    }

    public Updatable<TotalCaptureResultProxy> provideMetadataCallback() {
        return mMetadataPool;
    }

    public BufferQueueController<ImageProxy> provideImageQueue() {
        return mImageQueue;
    }

    @VisibleForTesting
    public MetadataPoolImpl provideMetadataPoolImpl() {
        return mMetadataPool;
    }
}
