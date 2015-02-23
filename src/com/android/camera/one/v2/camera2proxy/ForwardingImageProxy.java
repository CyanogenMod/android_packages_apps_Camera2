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

import java.util.List;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Forwards all {@link ImageProxy} methods.
 */
@ThreadSafe
public abstract class ForwardingImageProxy implements ImageProxy {
    private final ImageProxy mImpl;

    public ForwardingImageProxy(ImageProxy proxy) {
        mImpl = proxy;
    }

    /**
     * @see {@link android.media.Image#getCropRect}
     */
    @Override
    public Rect getCropRect() {
        return mImpl.getCropRect();
    }

    /**
     * @see {@link android.media.Image#setCropRect}
     */
    @Override
    public void setCropRect(Rect cropRect) {
        mImpl.setCropRect(cropRect);
    }

    /**
     * @see {@link android.media.Image#getFormat}
     */
    @Override
    public int getFormat() {
        return mImpl.getFormat();
    }

    /**
     * @see {@link android.media.Image#getHeight}
     */
    @Override
    public int getHeight() {
        return mImpl.getHeight();
    }

    /**
     * @see {@link android.media.Image#getPlanes}
     */
    @Override
    public List<Plane> getPlanes() {
        return mImpl.getPlanes();
    }

    /**
     * @see {@link android.media.Image#getTimestamp}
     */
    @Override
    public long getTimestamp() {
        return mImpl.getTimestamp();
    }

    /**
     * @see {@link android.media.Image#getWidth}
     */
    @Override
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

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
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
