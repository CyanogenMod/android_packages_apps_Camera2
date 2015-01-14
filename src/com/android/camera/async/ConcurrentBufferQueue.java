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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

/**
 * A {@link BufferQueue} implementation useful for thread-safe producer-consumer
 * interactions.<br>
 * Unlike a regular {@link java.util.concurrent.BlockingQueue}, this allows
 * closing the queue from either the producer or consumer side and enables
 * precise accounting of objects which are never read by the consumer. Notably,
 * this enables cleanly shutting down producer-consumer interactions without
 * leaking managed resources which might otherwise be left dangling in the
 * queue.
 */
public class ConcurrentBufferQueue<T> implements BufferQueue<T>, BufferQueueController<T>,
        SafeCloseable {
    /**
     * A callback to be invoked with all of the elements of the sequence which
     * are added but never retrieved via {@link #getNext}.
     */
    public static interface UnusedElementProcessor<T> {
        /**
         * Implementations should properly close the discarded element, if
         * necessary.
         */
        public void process(T element);
    }

    /**
     * An entry can either be a {@link T} or a special "poison-pill" marker
     * indicating that the sequence has been closed.
     */
    private static class Entry<T> {
        private final T mValue;
        private final boolean mClosing;

        private Entry(T value, boolean closing) {
            mValue = value;
            mClosing = closing;
        }

        public boolean isClosingMarker() {
            return mClosing;
        }

        public T getValue() {
            return mValue;
        }
    }
    /**
     * Lock used for mQueue modification and mClosed.
     */
    private final Object mLock;
    /**
     * The queue in which to store elements of the sequence as they arrive.
     */
    private final BlockingQueue<Entry<T>> mQueue;
    /**
     * Whether this sequence is closed.
     */
    private final AtomicBoolean mClosed;
    /**
     * The callback to use to process all elements which are discarded by the
     * queue.
     */
    private final UnusedElementProcessor<T> mUnusedElementProcessor;

    public ConcurrentBufferQueue(UnusedElementProcessor<T> unusedElementProcessor) {
        mUnusedElementProcessor = unusedElementProcessor;
        mLock = new Object();
        mQueue = new LinkedBlockingQueue<>();
        mClosed = new AtomicBoolean();
    }

    public ConcurrentBufferQueue() {
        // Instantiate with a DiscardedElementProcessor which does nothing.
        this(new UnusedElementProcessor<T>() {
            @Override
            public void process(T element) {
            }
        });
    }

    @Override
    public void close() {
        List<Entry<T>> remainingElements = new ArrayList<>();
        synchronized (mLock) {
            // Mark as closed so that no more threads wait in getNext().
            // Any additional calls to close() will return immediately.
            boolean alreadyClosed = mClosed.getAndSet(true);
            if (alreadyClosed) {
                return;
            }

            mQueue.drainTo(remainingElements);

            // Keep feeding any currently-waiting consumer threads "poison pill"
            // {@link Entry}s indicating that the sequence has ended so they
            // wake up. When no more threads are waiting for another value from
            // mQueue, the call to peek() from this thread will see a value.
            // Note that this also ensures that there is a poison pill in the
            // queue
            // to keep waking-up any threads which manage to block in getNext()
            // even after marking mClosed.
            while (mQueue.peek() == null) {
                mQueue.add(makeClosingMarker());
            }
        }

        for (Entry<T> entry : remainingElements) {
            if (!entry.isClosingMarker()) {
                mUnusedElementProcessor.process(entry.getValue());
            }
        }
    }

    @Override
    public void update(@Nonnull T element) {
        boolean closed = false;
        synchronized (mLock) {
            closed = mClosed.get();
            if (!closed) {
                mQueue.add(makeEntry(element));
            }
        }
        if (closed) {
            mUnusedElementProcessor.process(element);
        }
    }

    private T doWithNextEntry(Entry<T> nextEntry) throws BufferQueueClosedException {
        if (nextEntry.isClosingMarker()) {
            // Always keep a poison-pill in the queue to avoid a race condition
            // in which a thread reaches the mQueue.take() call after close().
            mQueue.add(nextEntry);
            throw new BufferQueueClosedException();
        } else {
            return nextEntry.getValue();
        }
    }

    @Override
    public T getNext() throws InterruptedException, BufferQueueClosedException {
        Entry<T> nextEntry = mQueue.take();
        return doWithNextEntry(nextEntry);
    }

    @Override
    public T getNext(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException,
            BufferQueueClosedException {
        Entry<T> nextEntry = mQueue.poll(timeout, unit);
        if (nextEntry == null) {
            throw new TimeoutException();
        }
        return doWithNextEntry(nextEntry);
    }

    @Override
    public T peekNext() {
        Entry<T> nextEntry = mQueue.peek();
        if (nextEntry == null) {
            return null;
        } else if (nextEntry.isClosingMarker()) {
            return null;
        } else {
            return nextEntry.getValue();
        }
    }

    @Override
    public void discardNext() {
        try {
            Entry<T> nextEntry = mQueue.remove();
            if (nextEntry.isClosingMarker()) {
                // Always keep a poison-pill in the queue to avoid a race
                // condition in which a thread reaches the mQueue.take() call
                // after close().
                mQueue.add(nextEntry);
            } else {
                mUnusedElementProcessor.process(nextEntry.getValue());
            }
        } catch (NoSuchElementException e) {
            // If the queue is already empty, do nothing.
            return;
        }
    }

    @Override
    public boolean isClosed() {
        return mClosed.get();
    }

    private Entry makeEntry(T value) {
        return new Entry(value, false);
    }

    private Entry makeClosingMarker() {
        return new Entry(null, true);
    }
}
