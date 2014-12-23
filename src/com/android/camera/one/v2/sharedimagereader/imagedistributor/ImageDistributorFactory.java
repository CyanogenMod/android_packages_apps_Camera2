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

import android.media.ImageReader;

import com.android.camera.async.CloseableHandlerThread;
import com.android.camera.async.ConcurrentBufferQueue;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Updatable;

public class ImageDistributorFactory {
    private final ImageDistributorImpl mImageDistributor;
    private final Updatable<Long> mTimestampStream;

    /**
     * Creates an ImageDistributor from the given ImageReader.
     */
    public ImageDistributorFactory(Lifetime lifetime, ImageReader imageReader) {
        ConcurrentBufferQueue<Long> globalTimestampStream = new ConcurrentBufferQueue<>();
        mTimestampStream = globalTimestampStream;
        mImageDistributor = new ImageDistributorImpl(globalTimestampStream);

        CloseableHandlerThread imageReaderHandler = new CloseableHandlerThread("ImageDistributor");
        lifetime.add(imageReaderHandler);

        imageReader.setOnImageAvailableListener(
                new ImageDistributorOnImageAvailableListener(mImageDistributor, imageReader
                        .getMaxImages()),
                imageReaderHandler.get());
    }

    public ImageDistributor provideImageDistributor() {
        return mImageDistributor;
    }

    public Updatable<Long> provideGlobalTimestampCallback() {
        return mTimestampStream;
    }
}
