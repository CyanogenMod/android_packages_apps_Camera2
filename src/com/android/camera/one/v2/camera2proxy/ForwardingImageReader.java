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

package com.android.camera.one.v2.camera2proxy;

import android.os.Handler;
import android.view.Surface;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class ForwardingImageReader implements ImageReaderProxy {
    private final ImageReaderProxy mDelegate;

    public ForwardingImageReader(ImageReaderProxy delegate) {
        mDelegate = delegate;
    }

    @Override
    public int getWidth() {
        return mDelegate.getWidth();
    }

    @Override
    public int getHeight() {
        return mDelegate.getHeight();
    }

    @Override
    public int getImageFormat() {
        return mDelegate.getImageFormat();
    }

    @Override
    public int getMaxImages() {
        return mDelegate.getMaxImages();
    }

    @Override
    @Nonnull
    public Surface getSurface() {
        return mDelegate.getSurface();
    }

    @Override
    @Nullable
    public ImageProxy acquireLatestImage() {
        return mDelegate.acquireLatestImage();
    }

    @Override
    @Nullable
    public ImageProxy acquireNextImage() {
        return mDelegate.acquireNextImage();
    }

    @Override
    public void setOnImageAvailableListener(@Nonnull
    final OnImageAvailableListener listener,
            @Nullable Handler handler) {
        mDelegate.setOnImageAvailableListener(listener, handler);
    }

    @Override
    public void close() {
        mDelegate.close();
    }

    @Override
    public String toString() {
        return mDelegate.toString();
    }
}
