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

import javax.annotation.Nonnull;

/**
 * Like {@link ConcurrentBufferQueue}, but also tracks the number of objects
 * currently in the queue.
 */
public class CountableBufferQueue<T> implements BufferQueueController<T>, BufferQueue<T> {
    private class DecrementingProcessor<T> implements
            ConcurrentBufferQueue.UnusedElementProcessor<T> {
        private final ConcurrentBufferQueue.UnusedElementProcessor mProcessor;

        private DecrementingProcessor(ConcurrentBufferQueue.UnusedElementProcessor<T> processor) {
            mProcessor = processor;
        }

        @Override
        public void process(T element) {
            mProcessor.process(element);
            decrementSize();
        }
    }

    private final ConcurrentBufferQueue<T> mBufferQueue;
    private final Object mCountLock;
    private final Updatable<Integer> mSizeCallback;
    private int mCount;

    /**
     * @param sizeCallback A thread-safe callback to be updated with the size
     *            of the queue.
     * @param processor The callback for processing elements discarded from the
     *            queue.
     */
    public CountableBufferQueue(Updatable<Integer> sizeCallback, ConcurrentBufferQueue
            .UnusedElementProcessor<T> processor) {
        mBufferQueue = new ConcurrentBufferQueue<T>(new DecrementingProcessor<T>(processor));
        mCountLock = new Object();
        mCount = 0;
        mSizeCallback = sizeCallback;
    }

    public CountableBufferQueue(ConcurrentState<Integer> sizeCallback) {
        this(sizeCallback, new ConcurrentBufferQueue.UnusedElementProcessor<T>() {
            @Override
            public void process(T element) {
                // Do nothing by default.
            }
        });
    }

    private void decrementSize() {
        int count;
        synchronized (mCountLock) {
            mCount--;
            count = mCount;
        }
        mSizeCallback.update(count);
    }

    @Override
    public T getNext() throws InterruptedException, BufferQueueClosedException {
        T result = mBufferQueue.getNext();
        decrementSize();
        return result;
    }

    @Override
    public T getNext(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException,
            BufferQueueClosedException {
        T result = mBufferQueue.getNext(timeout, unit);
        decrementSize();
        return result;
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
    public void update(@Nonnull T element) {
        // This is tricky since mBufferQueue.update() may immediately discard
        // the element if the queue is closed. Sending redundant updates for 0
        // size is acceptable, but sending updates indicating that the size has
        // increased and then decreased, even after the queue is closed, would
        // be bad. Thus, the following will filter these out.
        int preCount;
        int postCount;
        synchronized (mCountLock) {
            preCount = mCount;
            mCount++;
            mBufferQueue.update(element);
            postCount = mCount;
        }
        if (preCount != postCount) {
            mSizeCallback.update(postCount);
        }
    }

    @Override
    public void close() {
        mBufferQueue.close();
    }

    @Override
    public boolean isClosed() {
        return mBufferQueue.isClosed();
    }
}
