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

package com.android.camera.one.v2.sharedimagereader;

import android.view.Surface;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.async.ForwardingBufferQueue;
import com.android.camera.async.Lifetime;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageDistributor;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageStream;

/**
 * An ImageQueueCaptureStream which registers itself with the ImageDistributor
 * when bound to begin receiving images.
 */
class ImageStreamImpl extends ForwardingBufferQueue<ImageProxy> implements
        ImageStream {
    private final ImageDistributor mImageDistributor;
    private final BufferQueueController<ImageProxy> mImageStreamController;
    private final Surface mSurface;
    private final Lifetime mLifetime;

    public ImageStreamImpl(BufferQueue<ImageProxy> imageStream,
            BufferQueueController<ImageProxy> imageStreamController,
            ImageDistributor imageDistributor, Surface surface) {
        super(imageStream);
        mSurface = surface;
        mImageDistributor = imageDistributor;
        mImageStreamController = imageStreamController;
        mLifetime = new Lifetime();
        mLifetime.add(imageStream);
        mLifetime.add(mImageStreamController);
    }

    @Override
    public Surface bind(BufferQueue<Long> timestamps) throws InterruptedException,
            ResourceAcquisitionFailedException {
        mLifetime.add(timestamps);
        mImageDistributor.addRoute(timestamps, mImageStreamController);
        return mSurface;
    }

    @Override
    public void close() {
        mLifetime.close();
    }
}
