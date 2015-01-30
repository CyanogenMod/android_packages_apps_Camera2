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

import android.media.ImageReader;
import android.os.Handler;
import android.view.Surface;

import com.android.camera.async.SafeCloseable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Interface for {@link android.media.ImageReader}.
 */
@ThreadSafe
public interface ImageReaderProxy extends SafeCloseable {
    /**
     * See {@link ImageReader.OnImageAvailableListener}
     */
    public interface OnImageAvailableListener {
        /**
         * See {@link ImageReader.OnImageAvailableListener#onImageAvailable}
         */
        public void onImageAvailable();
    }

    /**
     * @See {@link ImageReader#getWidth}.
     */
    public int getWidth();

    /**
     * @See {@link ImageReader#getHeight}.
     */
    public int getHeight();

    /**
     * @See {@link ImageReader#getImageFormat}.
     */
    public int getImageFormat();

    /**
     * @See {@link ImageReader#getMaxImages}.
     */
    public int getMaxImages();

    /**
     * @See {@link ImageReader#getSurface}.
     */
    @Nonnull
    public Surface getSurface();

    /**
     * @See {@link ImageReader#acquireLatestImage}.
     */
    @Nullable
    public ImageProxy acquireLatestImage();

    /**
     * @See {@link ImageReader#acquireNextImage}.
     */
    @Nullable
    public ImageProxy acquireNextImage();

    /**
     * @See {@link ImageReader#setOnImageAvailableListener}.
     */
    public void setOnImageAvailableListener(
            @Nonnull ImageReaderProxy.OnImageAvailableListener listener,
            @Nullable Handler handler);

    /**
     * @See {@link ImageReader#close}.
     */
    @Override
    public void close();
}
