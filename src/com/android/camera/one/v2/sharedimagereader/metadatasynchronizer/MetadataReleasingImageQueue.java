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
import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Wraps an output queue of images by wrapping each image to track when they are
 * closed. When images are closed, their associated metadata entry is freed to
 * not leak memory.
 */
@ThreadSafe
@ParametersAreNonnullByDefault
public class MetadataReleasingImageQueue implements BufferQueueController<ImageProxy> {
    private class MetadataReleasingImageProxy extends ForwardingImageProxy {
        public MetadataReleasingImageProxy(ImageProxy proxy) {
            super(proxy);
        }

        @Override
        public void close() {
            // Retrieve the timestamp before the image is closed.
            long timestamp = getTimestamp();
            super.close();
            // Free the metadata when the image is closed to not leak
            // memory.
            mMetadataPool.removeMetadataFuture(timestamp);
        }
    }

    private final BufferQueueController<ImageProxy> mOutputQueue;
    private final MetadataPool mMetadataPool;

    public MetadataReleasingImageQueue(BufferQueueController<ImageProxy> outputQueue,
            MetadataPool metadataPool) {
        mOutputQueue = outputQueue;
        mMetadataPool = metadataPool;
    }

    @Override
    public void update(@Nonnull ImageProxy element) {
        mOutputQueue.update(new MetadataReleasingImageProxy(element));
    }

    @Override
    public void close() {
        mOutputQueue.close();
    }

    @Override
    public boolean isClosed() {
        return mOutputQueue.isClosed();
    }
}
