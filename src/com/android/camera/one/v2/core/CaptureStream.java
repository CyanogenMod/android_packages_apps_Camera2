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

package com.android.camera.one.v2.core;

import android.view.Surface;

import com.android.camera.async.BufferQueue;

/**
 * A generic Surface-holding object which can be attached to a {@link Request},
 * when build via {@link RequestBuilder}. The simplest implementation may simply
 * provide a {@link Surface} on which to send frames from the camera. However,
 * other implementations may wish to filter images sent to the {@link Surface}
 * in response to different {@link Request}s. To enable this, {@link #bind} is
 * called for each Request with a queue which will contain the timestamps of
 * every image captured for that request.
 * <p>
 * Implementations must provide a {@link Surface} and (optionally) implement
 * logic to filter images added to the surface according to a stream of image
 * timestamps.
 * </p>
 * <p>
 * Implementations should use the {@link CaptureStream#bind} method to kick off
 * a process of taking, as input, a {@link com.android.camera.async.BufferQueue}
 * of image timestamps as well as the images added to the {@link Surface}, and
 * producing, as output, a stream of useful handles to the image data.
 * </p>
 */
public interface CaptureStream {

    /**
     * Implementations should use this method to allocate all resources
     * necessary to ensure that the requested images can be saved.
     *
     * @param timestamps A stream of monotonically-increasing timestamps of
     *            images which correspond to the request to which the surface
     *            will be bound Images with timestamps not present in the queue
     *            should typically be ignored/discarded by the implementation.
     *            Note that for non-repeating requests, this will only be a
     *            single timestamp.
     * @return The stream which clients may use to interact with the returned
     *         images.
     * @throws InterruptedException if interrupted while waiting to allocate
     *             resources necessary to begin accepting new images.
     */
    public Surface bind(BufferQueue<Long> timestamps)
            throws InterruptedException, ResourceAcquisitionFailedException;
}
