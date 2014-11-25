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

package com.android.camera.async;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An input stream of objects which can be closed from either the producer or
 * the consumer.
 */
public interface BufferQueue<T> extends SafeCloseable {
    @Override
    public void close();

    /**
     * Blocks, returning the next available value.
     *
     * @return The next available value.
     * @throws InterruptedException If interrupted while waiting for the next
     *             value.
     * @throws com.android.camera.async.BufferQueue.BufferQueueClosedException If the stream is closed and no more values
     *             will be available.
     */
    public T getNext() throws InterruptedException, BufferQueueClosedException;

    /**
     * Blocks, returning the next available value.
     *
     * @param timeout The maximum amount of time to wait.
     * @param unit The unit associated with the timeout.
     * @return The next available value.
     * @throws InterruptedException If interrupted while waiting for the next
     *             value.
     * @throws com.android.camera.async.BufferQueue.BufferQueueClosedException If the stream is closed and no more values
     *             will be available.
     * @throws TimeoutException If no new value is made available within the
     *             specified time limit.
     */
    public T getNext(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException,
            BufferQueueClosedException;

    /**
     * Immediately returns the next available value without removing it from the
     * stream.
     *
     * @return The next available value if one exists, or null if no value
     *         exists yet or the stream is closed.
     */
    public T peekNext();

    /**
     * Immediately discards the next available value, if one exists.
     */
    public void discardNext();

    /**
     * @return True if the stream has been closed by either the producer or the
     *         consumer.
     */
    public boolean isClosed();

    /**
     * Indicates that the stream is closed and no more results are available.
     */
    public static class BufferQueueClosedException extends Exception {
    }
}
