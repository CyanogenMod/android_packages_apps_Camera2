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

import android.media.Image;
import android.media.ImageReader;

import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.AndroidImageProxy;
import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connects an {@link ImageReader} to an {@link ImageDistributor} with a new
 * thread to handle image availability callbacks.
 */
class ImageDistributorOnImageAvailableListener implements
        ImageReader.OnImageAvailableListener {
    private class LoggingImageProxy extends ForwardingImageProxy {
        public LoggingImageProxy(ImageProxy proxy) {
            super(proxy);
        }

        @Override
        public void close() {
            super.close();
            decrementOpenImageCount();
        }
    }

    private static final Log.Tag TAG = new Log.Tag("ImageDistributor");
    private final ImageDistributorImpl mImageDistributor;
    private final AtomicInteger mNumOpenImages;
    private final int mImageReaderSize;

    /**
     * @param imageDistributor The image distributor to send images to.
     * @param imageReaderSize The maximum number of images which can be open
     *            simultaneously
     */
    public ImageDistributorOnImageAvailableListener(ImageDistributorImpl imageDistributor,
            int imageReaderSize) {
        mImageDistributor = imageDistributor;
        mImageReaderSize = imageReaderSize;
        mNumOpenImages = new AtomicInteger(0);
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        incrementOpenImageCount();
        Image androidImage = imageReader.acquireNextImage();
        mImageDistributor.distributeImage(
                new SingleCloseImageProxy(
                        new LoggingImageProxy(
                                new AndroidImageProxy(androidImage))));
    }

    private void incrementOpenImageCount() {
        int numOpenImages = mNumOpenImages.incrementAndGet();
        if (numOpenImages >= mImageReaderSize) {
            Log.e(TAG, "Open Image Count exceeds maximum! Open Image Count = " + numOpenImages);
        }
    }

    private void decrementOpenImageCount() {
        int numOpenImages = mNumOpenImages.decrementAndGet();
        // Log.d(TAG, "Open Image Count = " + numOpenImages);
    }
}
