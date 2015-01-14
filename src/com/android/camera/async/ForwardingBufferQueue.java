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
public abstract class ForwardingBufferQueue<T> implements BufferQueue<T> {
    private final BufferQueue<T> mDelegate;

    public ForwardingBufferQueue(BufferQueue<T> delegate) {
        mDelegate = delegate;
    }

    @Override
    public void close() {
        mDelegate.close();
    }

    @Override
    public T getNext() throws InterruptedException, BufferQueueClosedException {
        return mDelegate.getNext();
    }

    @Override
    public T getNext(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException,
            BufferQueueClosedException {
        return mDelegate.getNext(timeout, unit);
    }

    @Override
    public T peekNext() {
        return mDelegate.peekNext();
    }

    @Override
    public void discardNext() {
        mDelegate.discardNext();
    }

    @Override
    public boolean isClosed() {
        return mDelegate.isClosed();
    }

    @Override
    public String toString() {
        return mDelegate.toString();
    }
}
