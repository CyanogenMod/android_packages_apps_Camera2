/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.burst;

import android.hardware.camera2.TotalCaptureResult;

/**
 * Holds image data and meta-data for a single image that is part of a burst.
 * <p/>
 * Bursts are an ordered collection of images. This class holds the data for one
 * of these images.
 */
public class BurstImage {
    /**
     * The bytes of image that can be decoded by
     * {@link android.graphics.BitmapFactory#decodeByteArray(byte[],
     * int, int, android.graphics.BitmapFactory.Options)}.
     */
    // TODO: Fix this and use a URI here for saved image.
    public byte[] data;

    /**
     * The timestamp of the image measured in nanoseconds.
     * <p/>
     * The timestamp is monotonically increasing. It is mostly useful for
     * determining time offsets between images in burst. The zero-point and the
     * value of the timestamp cannot be in general compared between two
     * different bursts.
     */
    public long timestamp;

    /**
     * The width of the image in pixels.
     */
    public int width;

    /**
     * The height of the image in pixels.
     */
    public int height;

    /**
     * The capture result associated with the image.
     */
    public TotalCaptureResult captureResult;
}
