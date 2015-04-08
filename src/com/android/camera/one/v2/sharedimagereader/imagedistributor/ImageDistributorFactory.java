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

package com.android.camera.one.v2.sharedimagereader.imagedistributor;

import android.os.Handler;

import com.android.camera.async.ConcurrentBufferQueue;
import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Updatable;
import com.android.camera.debug.Loggers;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;

public class ImageDistributorFactory {
    private final ImageDistributorImpl mImageDistributor;
    private final Updatable<Long> mTimestampStream;

    /**
     * Creates an ImageDistributor from the given ImageReader.
     * 
     * @param lifetime The lifetime of the image distributor. Images will stop
     *            being distributed when the lifetime closes.
     * @param imageReader The ImageReader from which to distribute images.
     * @param handlerFactory Used for creating handler threads for callbacks
     *            registered with the platform.
     */
    public ImageDistributorFactory(Lifetime lifetime, ImageReaderProxy imageReader,
            HandlerFactory handlerFactory) {
        ConcurrentBufferQueue<Long> globalTimestampStream = new ConcurrentBufferQueue<>();
        mTimestampStream = globalTimestampStream;
        lifetime.add(globalTimestampStream);
        mImageDistributor = new ImageDistributorImpl(Loggers.tagFactory(), globalTimestampStream);

        // This imageReaderHandler will be created with a very very high thread
        // priority because missing any input event potentially stalls the
        // camera preview and HAL.
        Handler imageReaderHandler = handlerFactory.create(lifetime, "ImageDistributor",
              Thread.MAX_PRIORITY);

        imageReader.setOnImageAvailableListener(
                new ImageDistributorOnImageAvailableListener(imageReader, mImageDistributor),
                imageReaderHandler);
    }

    public ImageDistributor provideImageDistributor() {
        return mImageDistributor;
    }

    public Updatable<Long> provideGlobalTimestampCallback() {
        return mTimestampStream;
    }
}
