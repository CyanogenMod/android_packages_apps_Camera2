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

package com.android.camera.one.v2.sharedimagereader.imagedistributor;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public interface ImageDistributor {
    /**
     * Begins routing new images with timestamps matching those found in
     * inputTimestampQueue to outputStream.
     * <p>
     * The route is removed when either inputTimestampQueue is closed, or
     * outputStream is closed.
     * <p>
     * If multiple routes request the same image, they will both receive a
     * reference-counted "copy".
     *
     * @param inputTimestampQueue A queue containing timestamps of all images to
     *            be routed to outputStream.
     * @param outputStream The output queue in which to add images.
     */
    void addRoute(BufferQueue<Long> inputTimestampQueue,
            BufferQueueController<ImageProxy> outputStream);
}
