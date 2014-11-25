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

/**
 * Wraps {@link android.media.Image} with a mockable interface.
 */
public class ImageProxy implements SafeCloseable {
    private final ImageProxy mImpl;

    public ImageProxy(ImageProxy proxy) {
        mImpl = proxy;
    }

    public ImageProxy(android.media.Image image) {
        mImpl = new Impl(image);
    }

    private ImageProxy() {
        mImpl = null;
    }

    /**
     * @see {@link android.media.Image#getCropRect}
     */
    public Rect getCropRect() {
        return mImpl.getCropRect();
    }

    /**
     * @see {@link android.media.Image#setCropRect}
     */
    public void setCropRect(Rect cropRect) {
        mImpl.setCropRect(cropRect);
    }

    /**
     * @see {@link android.media.Image#getFormat}
     */
    public int getFormat() {
        return mImpl.getFormat();
    }

    /**
     * @see {@link android.media.Image#getHeight}
     */
    public int getHeight() {
        return mImpl.getHeight();
    }

    /**
     * @see {@link android.media.Image#getPlanes}
     */
    public android.media.Image.Plane[] getPlanes() {
        return mImpl.getPlanes();
    }

    /**
     * @see {@link android.media.Image#getTimestamp}
     */
    public long getTimestamp() {
        return mImpl.getTimestamp();
    }

    /**
     * @see {@link android.media.Image#getWidth}
     */
    public int getWidth() {
        return mImpl.getWidth();
    }

    /**
     * @see {@link android.media.Image#close}
     */
    @Override
    public void close() {
        mImpl.close();
    }

    private static class Impl extends ImageProxy {
        private final android.media.Image mImage;

        public Impl(android.media.Image image) {
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
         * @see {@link android.media.Image#getPlanes}
         */
        @Override
        public android.media.Image.Plane[] getPlanes() {
            return mImage.getPlanes();
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
}
