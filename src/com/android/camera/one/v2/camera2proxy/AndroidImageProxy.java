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
import com.google.common.collect.ImmutableList;

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
        private final int mPixelStride;
        private final int mRowStride;
        private final ByteBuffer mBuffer;

        public Plane(Image.Plane imagePlane) {
            // Copying out the contents of the Image.Plane means that this Plane
            // implementation can be thread-safe (without requiring any locking)
            // and can have getters which do not throw a RuntimeException if
            // the underlying Image is closed.
            mPixelStride = imagePlane.getPixelStride();
            mRowStride = imagePlane.getRowStride();
            mBuffer = imagePlane.getBuffer();
        }

        /**
         * @see {@link android.media.Image.Plane#getRowStride}
         */
        @Override
        public int getRowStride() {
            return mRowStride;
        }

        /**
         * @see {@link android.media.Image.Plane#getPixelStride}
         */
        @Override
        public int getPixelStride() {
            return mPixelStride;
        }

        /**
         * @see {@link android.media.Image.Plane#getBuffer}
         */
        @Override
        public ByteBuffer getBuffer() {
            return mBuffer;
        }
    }

    private final Object mLock;
    /**
     * {@link android.media.Image} is not thread-safe, so all interactions must
     * be guarded by {@link #mLock}.
     */
    @GuardedBy("mLock")
    private final android.media.Image mImage;
    private final int mFormat;
    private final int mWidth;
    private final int mHeight;
    private final long mTimestamp;
    private final ImmutableList<ImageProxy.Plane> mPlanes;
    @GuardedBy("mLock")
    private Rect mCropRect;

    public AndroidImageProxy(android.media.Image image) {
        mLock = new Object();

        mImage = image;
        // Copying out the contents of the Image means that this Image
        // implementation can be thread-safe (without requiring any locking)
        // and can have getters which do not throw a RuntimeException if
        // the underlying Image is closed.
        mFormat = mImage.getFormat();
        mWidth = mImage.getWidth();
        mHeight = mImage.getHeight();
        mTimestamp = mImage.getTimestamp();

        Image.Plane[] planes;
        planes = mImage.getPlanes();
        if (planes == null) {
            mPlanes = ImmutableList.of();
        } else {
            List<ImageProxy.Plane> wrappedPlanes = new ArrayList<>(planes.length);
            for (int i = 0; i < planes.length; i++) {
                wrappedPlanes.add(new Plane(planes[i]));
            }
            mPlanes = ImmutableList.copyOf(wrappedPlanes);
        }
    }

    /**
     * @see {@link android.media.Image#getCropRect}
     */
    @Override
    public Rect getCropRect() {
        synchronized (mLock) {
            try {
                mCropRect = mImage.getCropRect();
            } catch (IllegalStateException imageClosedException) {
                // If the image is closed, then just return the cached CropRect.
                return mCropRect;
            }
            return mCropRect;
        }
    }

    /**
     * @see {@link android.media.Image#setCropRect}
     */
    @Override
    public void setCropRect(Rect cropRect) {
        synchronized (mLock) {
            mCropRect = cropRect;
            try {
                mImage.setCropRect(cropRect);
            } catch (IllegalStateException imageClosedException) {
                // Ignore.
            }
        }
    }

    /**
     * @see {@link android.media.Image#getFormat}
     */
    @Override
    public int getFormat() {
        return mFormat;
    }

    /**
     * @see {@link android.media.Image#getHeight}
     */
    @Override
    public int getHeight() {
        return mHeight;
    }

    /**
     * @see {@link android.media.Image#getPlanes}
     */
    @Override
    public List<ImageProxy.Plane> getPlanes() {
        return mPlanes;
    }

    /**
     * @see {@link android.media.Image#getTimestamp}
     */
    @Override
    public long getTimestamp() {
        return mTimestamp;
    }

    /**
     * @see {@link android.media.Image#getWidth}
     */
    @Override
    public int getWidth() {
        return mWidth;
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
        return Objects.toStringHelper(this)
                .add("format", getFormat())
                .add("timestamp", getTimestamp())
                .add("width", getWidth())
                .add("height", getHeight())
                .toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (!(other instanceof ImageProxy)) {
            return false;
        }
        ImageProxy otherImage = (ImageProxy) other;
        return otherImage.getFormat() == getFormat() &&
                otherImage.getWidth() == getWidth() &&
                otherImage.getHeight() == getHeight() &&
                otherImage.getTimestamp() == getTimestamp();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getFormat(), getWidth(), getHeight(), getTimestamp());
    }
}
