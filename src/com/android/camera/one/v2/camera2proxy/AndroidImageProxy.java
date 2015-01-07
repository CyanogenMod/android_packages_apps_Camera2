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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link ImageProxy} backed by an {@link android.media.Image}.
 */
public class AndroidImageProxy implements ImageProxy {

    /**
     * An {@link ImageProxy.Plane} backed by an
     * {@link android.media.Image.Plane}.
     */
    public class Plane implements ImageProxy.Plane {
        private final Image.Plane mPlane;

        public Plane(Image.Plane imagePlane) {
            mPlane = imagePlane;
        }

        /**
         * @see {@link android.media.Image.Plane#getRowStride}
         */
        @Override
        public int getRowStride() {
            return mPlane.getRowStride();
        }

        /**
         * @see {@link android.media.Image.Plane#getPixelStride}
         */
        @Override
        public int getPixelStride() {
            return mPlane.getPixelStride();
        }

        /**
         * @see {@link android.media.Image.Plane#getBuffer}
         */
        @Override
        public ByteBuffer getBuffer() {
            return mPlane.getBuffer();
        }

    }

    private final android.media.Image mImage;

    public AndroidImageProxy(android.media.Image image) {
        mImage = image;
    }

    /**
     * @see {@link android.media.Image#getCropRect}
     */
    @Override
    public Rect getCropRect() {
        return mImage.getCropRect();
    }

    /**
     * @see {@link android.media.Image#setCropRect}
     */
    @Override
    public void setCropRect(Rect cropRect) {
        mImage.setCropRect(cropRect);
    }

    /**
     * @see {@link android.media.Image#getFormat}
     */
    @Override
    public int getFormat() {
        return mImage.getFormat();
    }

    /**
     * @see {@link android.media.Image#getHeight}
     */
    @Override
    public int getHeight() {
        return mImage.getHeight();
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
     * @see {@link android.media.Image#getPlanes}
     */
    @Override
    public List<ImageProxy.Plane> getPlanes() {
        Image.Plane[] planes = mImage.getPlanes();

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
        return mImage.getTimestamp();
    }

    /**
     * @see {@link android.media.Image#getWidth}
     */
    @Override
    public int getWidth() {
        return mImage.getWidth();
    }

    /**
     * @see {@link android.media.Image#close}
     */
    @Override
    public void close() {
        mImage.close();
    }
}
