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

import com.android.camera.debug.Log.Tag;
import com.android.camera.debug.Logger;
import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ForwardingImageReader;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

final class LoggingImageReader extends ForwardingImageReader {
    private class LoggingImageProxy extends ForwardingImageProxy {
        private final AtomicBoolean mClosed;

        public LoggingImageProxy(ImageProxy proxy) {
            super(proxy);
            mClosed = new AtomicBoolean(false);
        }

        @Override
        public void close() {
            if (!mClosed.getAndSet(true)) {
                super.close();
                decrementOpenImageCount();
            }
        }
    }

    private final Logger mLog;
    private final AtomicInteger mNumOpenImages;

    public LoggingImageReader(ImageReaderProxy delegate, Logger.Factory logFactory) {
        super(delegate);
        mLog = logFactory.create(new Tag("LoggingImageReader"));
        mNumOpenImages = new AtomicInteger(0);
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
        mLog.d("Closing: " + toString());
        super.close();
    }

    private void incrementOpenImageCount() {
        int numOpenImages = mNumOpenImages.incrementAndGet();
        if (numOpenImages >= getMaxImages()) {
            mLog.e(String.format("Open Image Count (%d) exceeds maximum (%d)!",
                    numOpenImages, getMaxImages()));
        }
    }

    private void decrementOpenImageCount() {
        int numOpenImages = mNumOpenImages.decrementAndGet();
    }
}
