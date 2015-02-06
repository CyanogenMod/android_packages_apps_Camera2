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
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.CaptureStream;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An interface for {@link CaptureStream}s which route images into a
 * {@link BufferQueue}.
 * <p>
 * For example, an implementation of this may be added to a
 * {@link com.android.camera.one.v2.core.RequestBuilder} to then get Images
 * corresponding to that request as a queue.
 * <p>
 * Note that images always arrive in order. Different implementations may behave
 * differently with respect to how images are dropped. For example, some may act
 * like a ring-buffer and automatically drop the oldest image when a new image
 * arrives. Others may close new images immediately if no capacity is available.
 */
public interface ImageStream extends BufferQueue<ImageProxy>, CaptureStream {
    /**
     * Closes the ImageStream. After being closed, no more images will be
     * available and any images left in the stream are closed.
     */
    @Override
    public void close();

    /**
     * Blocks, returning the next available image.
     *
     * @return The next available image.
     * @throws InterruptedException If interrupted while waiting for the next
     *             image.
     * @throws com.android.camera.async.BufferQueue.BufferQueueClosedException
     *             If the stream is closed and no more images will be available.
     */
    @Override
    public ImageProxy getNext() throws InterruptedException, BufferQueueClosedException;

    /**
     * Blocks, returning the next available image.
     *
     * @param timeout The maximum amount of time to wait.
     * @param unit The unit associated with the timeout.
     * @return The next available image.
     * @throws InterruptedException If interrupted while waiting for the next
     *             image.
     * @throws com.android.camera.async.BufferQueue.BufferQueueClosedException
     *             If the stream is closed and no more images will be available.
     * @throws TimeoutException If no new image is made available within the
     *             specified time limit.
     */
    @Override
    public ImageProxy getNext(long timeout, TimeUnit unit) throws InterruptedException,
            TimeoutException,
            BufferQueueClosedException;

    /**
     * Immediately returns the next available image without removing it from the
     * stream. Note that the ImageStream still owns the image, so the caller
     * MUST NOT close it.
     *
     * @return The next available value if one exists, or null if no value
     *         exists yet or the stream is closed.
     */
    @Override
    public ImageProxy peekNext();

    /**
     * Immediately discards the next available image, if one exists.
     */
    @Override
    public void discardNext();

    /**
     * @return True if the stream has been closed by either the producer or the
     *         consumer.
     */
    @Override
    public boolean isClosed();
}
