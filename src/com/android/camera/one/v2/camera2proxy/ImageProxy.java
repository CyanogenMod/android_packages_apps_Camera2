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

package com.android.camera.one.v2.camera2proxy;

import android.graphics.Rect;

import com.android.camera.async.SafeCloseable;

import java.nio.ByteBuffer;
import java.util.List;

import javax.annotation.concurrent.ThreadSafe;

/**
 * An interface for {@link android.media.Image} with two convenient differences:
 * <ul>
 * <li>Implementations must be thread-safe.</li>
 * <li>Methods will never throw a RuntimeException, even if the underlying Image
 * has been closed.</li>
 * </ul>
 */
@ThreadSafe
public interface ImageProxy extends SafeCloseable {

    /**
     * An interface for {@link android.media.Image.Plane} with two convenient
     * differences:
     * <ul>
     * <li>Implementations must be thread-safe.</li>
     * <li>Getters must never throw, even if the underlying Image has been
     * closed.</li>
     * </ul>
     */
    public interface Plane {

        /**
         * @see {@link android.media.Image.Plane#getRowStride()}
         */
        public int getRowStride();

        /**
         * @see {@link android.media.Image.Plane#getPixelStride()}
         */
        public int getPixelStride();

        /**
         * @see {@link android.media.Image.Plane#getBuffer()}
         */
        public ByteBuffer getBuffer();
    }

    /**
     * @see {@link android.media.Image#getCropRect}
     */
    public Rect getCropRect();

    /**
     * @see {@link android.media.Image#setCropRect}
     */
    public void setCropRect(Rect cropRect);

    /**
     * @see {@link android.media.Image#getFormat}
     */
    public int getFormat();

    /**
     * @see {@link android.media.Image#getHeight}
     */
    public int getHeight();

    /**
     * @see {@link android.media.Image#getPlanes}
     */
    public List<Plane> getPlanes();

    /**
     * @see {@link android.media.Image#getTimestamp}
     */
    public long getTimestamp();

    /**
     * @see {@link android.media.Image#getWidth}
     */
    public int getWidth();

    /**
     * @see {@link android.media.Image#close}
     */
    @Override
    public void close();
}
