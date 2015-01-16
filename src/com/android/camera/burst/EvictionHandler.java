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

package com.android.camera.burst;

import android.hardware.camera2.TotalCaptureResult;

/**
 * The eviction strategy of the internal Camera image buffer.
 * <p/>
 * For a burst the Camera maintains an internal image buffer. This image buffer
 * has limited memory and old images need to be evicted for storing new images.
 * The eviction handler encapsulates the eviction strategy that the Camera uses
 * to evict frames.
 */
public interface EvictionHandler {
    /**
     * Return the timestamp of the image that should be dropped.
     * <p/>
     * This method is called when the internal image buffer is out of capacity
     * and needs to drop an image from the buffer.
     * <p/>
     * This should return one of the timestamps passed into
     * {@link #onFrameInserted(long)}, and which has not yet
     * been dropped.
     *
     * @return the timestamp of the frame to drop.
     */
    long selectFrameToDrop();

    /**
     * Called when the capture result for a frame is available.
     *
     * @param timestamp the timestamp of the frame, this frame may or may not be
     *            present in the image buffer.
     * @param captureResult the capture result of the image.
     */
    void onFrameCaptureResultAvailable(long timestamp,
            TotalCaptureResult captureResult);

    /**
     * Called when an image is inserted in the image buffer.
     *
     * @param timestamp the timestamp of the inserted frame in the image buffer.
     */
    void onFrameInserted(long timestamp);

    /**
     * Called when a frame is dropped from the image buffer.
     *
     * @param timestamp the timestamp of the dropped frame.
     */
    void onFrameDropped(long timestamp);
}
