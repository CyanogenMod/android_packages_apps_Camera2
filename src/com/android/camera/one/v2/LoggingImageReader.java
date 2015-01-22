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

package com.android.camera.one.v2;

import com.android.camera.debug.Log;
import com.android.camera.debug.Logger;
import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ForwardingImageReader;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

final class LoggingImageReader extends ForwardingImageReader {
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
    private final Logger mLogger;
    private final AtomicInteger mNumOpenImages;

    LoggingImageReader(ImageReaderProxy delegate, Logger logger) {
        super(delegate);
        mLogger = logger;
        mNumOpenImages = new AtomicInteger(0);
    }

    public static LoggingImageReader create(ImageReaderProxy imageReader) {
        return new LoggingImageReader(imageReader, Logger.create("LoggingImageReader"));
    }

    @Override
    @Nullable
    public ImageProxy acquireNextImage() {
        return decorateNewImage(super.acquireNextImage());
    }

    @Override
    @Nullable
    public ImageProxy acquireLatestImage() {
        return decorateNewImage(super.acquireLatestImage());
    }

    @Nullable
    private ImageProxy decorateNewImage(@Nullable ImageProxy image) {
        if (image == null) {
            return null;
        }
        incrementOpenImageCount();
        return new LoggingImageProxy(image);
    }

    @Override
    public void close() {
        mLogger.d("Closing: " + toString());
        super.close();
    }

    private void incrementOpenImageCount() {
        int numOpenImages = mNumOpenImages.incrementAndGet();
        if (numOpenImages >= getMaxImages()) {
            mLogger.e(String.format("Open Image Count (%d) exceeds maximum (%d)!",
                    numOpenImages, getMaxImages()));
        }
    }

    private void decrementOpenImageCount() {
        int numOpenImages = mNumOpenImages.decrementAndGet();
        mLogger.v("Open Image Count = " + numOpenImages);
    }
}
