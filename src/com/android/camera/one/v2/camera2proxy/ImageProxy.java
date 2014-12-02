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
public interface ImageProxy extends SafeCloseable {
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
    public android.media.Image.Plane[] getPlanes();

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
