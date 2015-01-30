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
import android.media.Image;

import com.google.common.base.Objects;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An {@link ImageProxy} backed by an {@link android.media.Image}.
 */
@ThreadSafe
public class AndroidImageProxy implements ImageProxy {

    /**
     * An {@link ImageProxy.Plane} backed by an
     * {@link android.media.Image.Plane}.
     */
    public class Plane implements ImageProxy.Plane {
        /**
         * {@link android.media.Image} is not thread-safe, and the resulting
         * {@link Image.Plane} objects are not-necessarily thread-safe either,
         * so all interaction must be guarded by {@link #mLock}.
         */
        @GuardedBy("mLock")
        private final Image.Plane mPlane;

        public Plane(Image.Plane imagePlane) {
            mPlane = imagePlane;
        }

        /**
         * @see {@link android.media.Image.Plane#getRowStride}
         */
        @Override
        public int getRowStride() {
            synchronized (mLock) {
                return mPlane.getRowStride();
            }
        }

        /**
         * @see {@link android.media.Image.Plane#getPixelStride}
         */
        @Override
        public int getPixelStride() {
            synchronized (mLock) {
                return mPlane.getPixelStride();
            }
        }

        /**
         * @see {@link android.media.Image.Plane#getBuffer}
         */
        @Override
        public ByteBuffer getBuffer() {
            synchronized (mLock) {
                return mPlane.getBuffer();
            }
        }

    }

    private final Object mLock;
    /**
     * {@link android.media.Image} is not thread-safe, so all interactions must
     * be guarded by {@link #mLock}.
     */
    @GuardedBy("mLock")
    private final android.media.Image mImage;

    public AndroidImageProxy(android.media.Image image) {
        mLock = new Object();
        mImage = image;
    }

    /**
     * @see {@link android.media.Image#getCropRect}
     */
    @Override
    public Rect getCropRect() {
        synchronized (mLock) {
            return mImage.getCropRect();
        }
    }

    /**
     * @see {@link android.media.Image#setCropRect}
     */
    @Override
    public void setCropRect(Rect cropRect) {
        synchronized (mLock) {
            mImage.setCropRect(cropRect);
        }
    }

    /**
     * @see {@link android.media.Image#getFormat}
     */
    @Override
    public int getFormat() {
        synchronized (mLock) {
            return mImage.getFormat();
        }
    }

    /**
     * @see {@link android.media.Image#getHeight}
     */
    @Override
    public int getHeight() {
        synchronized (mLock) {
            return mImage.getHeight();
        }
    }

    /**
     * <p>
     * NOTE:This wrapper is functionally correct, but has some performance
     * implications: it dynamically allocates a small array (usually 1-3
     * elements) and iteratively constructs each element of this array every
     * time it is called. This function definitely should <b>NOT</b> be called
     * within an tight inner loop, as it may litter the GC with lots of little
     * allocations. However, a proper caching of this object needs to be tied to
     * the Android Image updates, which would be a little more complex than this
     * object needs to be. So, just consider the performance when using this
     * function wrapper.
     * </p>
     *
     * @see {@link android.media.Image#getPlanes}
     */
    @Override
    public List<ImageProxy.Plane> getPlanes() {
        Image.Plane[] planes;

        synchronized (mLock) {
            planes = mImage.getPlanes();
        }

        List<ImageProxy.Plane> wrappedPlanes = new ArrayList<>(planes.length);

        for (int i = 0; i < planes.length; i++) {
            wrappedPlanes.add(new Plane(planes[i]));
        }
        return wrappedPlanes;
    }

    /**
     * @see {@link android.media.Image#getTimestamp}
     */
    @Override
    public long getTimestamp() {
        synchronized (mLock) {
            return mImage.getTimestamp();
        }
    }

    /**
     * @see {@link android.media.Image#getWidth}
     */
    @Override
    public int getWidth() {
        synchronized (mLock) {
            return mImage.getWidth();
        }
    }

    /**
     * @see {@link android.media.Image#close}
     */
    @Override
    public void close() {
        synchronized (mLock) {
            mImage.close();
        }
    }

    @Override
    public String toString() {
        Objects.ToStringHelper tsh;

        synchronized (mImage) {
            tsh = Objects.toStringHelper(mImage);
        }
        return tsh.add("timestamp", getTimestamp())
                .toString();
    }
}
