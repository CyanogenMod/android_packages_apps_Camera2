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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

/**
 * Wraps {@link ImageProxy} to filter out multiple calls to {@link #close}.
 */
class SingleCloseImageProxy extends ForwardingImageProxy {
    private final AtomicBoolean mClosed;

    /**
     * @param image The image to wrap
     */
    public SingleCloseImageProxy(ImageProxy image) {
        super(image);
        mClosed = new AtomicBoolean(false);
    }

    @Override
    public void close() {
        if (!mClosed.getAndSet(true)) {
            super.close();
        }
    }
}
