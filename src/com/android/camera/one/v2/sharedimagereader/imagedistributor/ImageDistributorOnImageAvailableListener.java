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

import com.android.camera.debug.Log;
import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Connects an {@link ImageReaderProxy} to an {@link ImageDistributor} with a new
 * thread to handle image availability callbacks.
 */
@ParametersAreNonnullByDefault
class ImageDistributorOnImageAvailableListener implements
        ImageReaderProxy.OnImageAvailableListener {
    private final ImageDistributorImpl mImageDistributor;

    /**
     * @param imageDistributor The image distributor to send images to.
     */
    public ImageDistributorOnImageAvailableListener(ImageDistributorImpl imageDistributor) {
        mImageDistributor = imageDistributor;
    }

    @Override
    public void onImageAvailable(@Nonnull ImageReaderProxy imageReader) {
        ImageProxy nextImage = imageReader.acquireNextImage();
        if (nextImage != null) {
            mImageDistributor.distributeImage(new SingleCloseImageProxy(nextImage));
        }
    }
}
