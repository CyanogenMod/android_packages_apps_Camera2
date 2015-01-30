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

import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Handler;
import android.view.Surface;

import com.google.common.base.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * A replacement for {@link android.media.ImageReader}.
 */
public final class AndroidImageReaderProxy implements ImageReaderProxy {
    private final Object mLock;
    @GuardedBy("mLock")
    private final android.media.ImageReader mDelegate;

    public AndroidImageReaderProxy(android.media.ImageReader delegate) {
        mLock = new Object();
        mDelegate = delegate;
    }

    /**
     * @See {@link android.media.ImageReader}
     */
    public static ImageReaderProxy newInstance(int width, int height, int format, int maxImages) {
        return new AndroidImageReaderProxy(android.media.ImageReader.newInstance(width, height,
                format, maxImages));
    }

    private static String imageFormatToString(int imageFormat) {
        switch (imageFormat) {
            case ImageFormat.JPEG:
                return "JPEG";
            case ImageFormat.NV16:
                return "NV16";
            case ImageFormat.NV21:
                return "NV21";
            case ImageFormat.RAW10:
                return "RAW10";
            case ImageFormat.RAW_SENSOR:
                return "RAW_SENSOR";
            case ImageFormat.RGB_565:
                return "RGB_565";
            case ImageFormat.UNKNOWN:
                return "UNKNOWN";
            case ImageFormat.YUV_420_888:
                return "YUV_420_888";
            case ImageFormat.YUY2:
                return "YUY2";
            case ImageFormat.YV12:
                return "YV12";
        }
        return Integer.toString(imageFormat);
    }

    @Override
    public int getWidth() {
        synchronized (mLock) {
            return mDelegate.getWidth();
        }
    }

    @Override
    public int getHeight() {
        synchronized (mLock) {
            return mDelegate.getHeight();
        }
    }

    @Override
    public int getImageFormat() {
        synchronized (mLock) {
            return mDelegate.getImageFormat();
        }
    }

    @Override
    public int getMaxImages() {
        synchronized (mLock) {
            return mDelegate.getMaxImages();
        }
    }

    @Override
    @Nonnull
    public Surface getSurface() {
        synchronized (mLock) {
            return mDelegate.getSurface();
        }
    }

    @Override
    @Nullable
    public ImageProxy acquireLatestImage() {
        synchronized (mLock) {
            Image image = mDelegate.acquireLatestImage();
            if (image == null) {
                return null;
            } else {
                return new AndroidImageProxy(image);
            }
        }
    }

    @Override
    @Nullable
    public ImageProxy acquireNextImage() {
        synchronized (mLock) {
            Image image = mDelegate.acquireNextImage();
            if (image == null) {
                return null;
            } else {
                return new AndroidImageProxy(image);
            }
        }
    }

    @Override
    public void setOnImageAvailableListener(@Nonnull
    final ImageReaderProxy.OnImageAvailableListener listener,
            Handler handler) {
        synchronized (mLock) {
            mDelegate.setOnImageAvailableListener(
                    new android.media.ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(android.media.ImageReader imageReader) {
                            listener.onImageAvailable();
                        }
                    }, handler);
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            mDelegate.close();
        }
    }

    @Override
    public String toString() {
        Objects.ToStringHelper tsh;
        synchronized (mLock) {
            tsh = Objects.toStringHelper(mDelegate);
        }
        return tsh.add("width", getWidth())
                .add("height", getHeight())
                .add("format", imageFormatToString(getImageFormat()))
                .toString();
    }
}
