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
 * A BufferQueue which forwards all methods to another.
 */
public class ForwardingBufferQueue<T> implements BufferQueue<T> {
    private final BufferQueue<T> mBufferQueue;

    public ForwardingBufferQueue(BufferQueue<T> bufferQueue) {
        mBufferQueue = bufferQueue;
    }

    @Override
    public void close() {
        mBufferQueue.close();
    }

    @Override
    public T getNext() throws InterruptedException, BufferQueueClosedException {
        return mBufferQueue.getNext();
    }

    @Override
    public T getNext(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException,
            BufferQueueClosedException {
        return mBufferQueue.getNext(timeout, unit);
    }

    @Override
    public T peekNext() {
        return mBufferQueue.peekNext();
    }

    @Override
    public void discardNext() {
        mBufferQueue.discardNext();
    }

    @Override
    public boolean isClosed() {
        return mBufferQueue.isClosed();
    }
}
