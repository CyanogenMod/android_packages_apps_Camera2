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

package com.android.camera.one.v2.sharedimagereader;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.camera.async.BoundedBufferQueue;
import com.android.camera.async.ConcurrentBufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

/**
 * A {@link BoundedBufferQueue} of {@link ImageProxy}s for which only a finite
 * number of {@link ImageProxy}s may be retrieved before further requests, via
 * {@link com.android.camera.async.BufferQueue#getNext()}, will block for
 * the closing of previously-retrieved {@link ImageProxy}s.
 * <p/>
 * The ability to acquire another image is represented by a logical "ticket".
 * When constructed, the stream has 0 tickets. More may be added via
 * {@link #addCapacity}. All tickets are eventually returned to the parent
 * {@link ImageTicketSink}. When a consumer retrieves an {@link ImageProxy}, it
 * gains control of the associated ticket and must release it by calling
 * {@link com.android.camera.one.v2.camera2proxy.ImageProxy#close}. When the
 * BoundedImageStream is closed, all tickets currently owned by the stream are
 * returned to the {@link ImageTicketSink}. Tickets later released by consumers
 * closing previously-acquired Images are also returned to the
 * {@link ImageTicketSink}.
 * <p/>
 * Allowing consumers to release tickets one-at-a-time by asynchronously closing
 * Images in this way enables optimal fine-grain accounting of logical image
 * tickets.
 * <p/>
 * Note that it is the stream consumer's responsibility to explicitly close()
 * Images to avoid deadlocking when calling
 * {@link com.android.camera.async.BufferQueue#getNext}.
 *
 * TODO Refactor ticket-pool logic into a separate class.
 */
public class BoundedImageBufferQueue implements BoundedBufferQueue<ImageProxy>,
        BufferQueueController<ImageProxy> {
    public static interface ImageTicketSink {
        /**
         * Called whenever a logical ticket is released from the stream forever.<br>
         * Note: This may be called on any thread and must execute quickly!
         */
        public void onImageTicketReleased();
    }

    /**
     * An {@link ImageProxy} which overrides close() to release the logical
     * ticket associated with the image.
     */
    private class TicketReleasingImageProxy extends ImageProxy {
        private final AtomicBoolean mClosed;

        public TicketReleasingImageProxy(ImageProxy image) {
            super(image);
            mClosed = new AtomicBoolean(false);
        }

        @Override
        public void close() {
            if (!mClosed.getAndSet(true)) {
                releaseTicket();
            }
            super.close();
        }
    }

    private final ImageTicketSink mImageTicketSink;
    private final ConcurrentBufferQueue<ImageProxy> mImageSequence;
    /**
     * Lock for {@link #mTicketsAvailable}, {@link #mClosed}, and
     * {@link #mTotalTickets}.
     */
    private final Object mLock;
    /**
     * The maximum number of images which may be acquired and not closed at any
     * given time.
     */
    private int mTotalTickets;
    /**
     * The number of tickets currently owned by this object. This excludes
     * tickets owned by each {@link ImageProxy} added to the sequence.
     */
    private int mTicketsAvailable;
    private boolean mClosed;

    public BoundedImageBufferQueue(ImageTicketSink imageTicketSink) {
        mImageSequence = new ConcurrentBufferQueue<>(
                new ConcurrentBufferQueue.UnusedElementProcessor<ImageProxy>() {
                    @Override
                    public void process(ImageProxy element) {
                        // Always close images which are never retrieved by the
                        // consumer.
                        element.close();
                    }
                });
        mLock = new Object();
        mTicketsAvailable = 0;
        mClosed = false;
        mImageTicketSink = imageTicketSink;
    }

    /**
     * Adds to the capacity of the image stream. When the stream is closed, all
     * capacity is (eventually, once all {@link ImageProxy}s retrieved from the
     * stream are closed) returned to the caller via the {@link ImageTicketSink}
     *
     * @param tickets The capacity to add to the stream.
     */
    public void addCapacity(int tickets) {
        if (tickets < 0) {
            tickets = 0;
        }
        synchronized (mLock) {
            if (mClosed) {
                for (int i = 0; i < tickets; i++) {
                    mImageTicketSink.onImageTicketReleased();
                }
                return;
            } else {
                mTotalTickets += tickets;
                mTicketsAvailable += tickets;
            }
        }
    }

    @Override
    public int getCapacity() {
        return mTotalTickets;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }

            mClosed = true;
            mImageSequence.close();
            for (int i = 0; i < mTicketsAvailable; i++) {
                mImageTicketSink.onImageTicketReleased();
            }
            mTicketsAvailable = 0;
        }
    }

    @Override
    public ImageProxy getNext() throws InterruptedException,
            BufferQueueClosedException {
        return mImageSequence.getNext();
    }

    @Override
    public ImageProxy getNext(long timeout, TimeUnit unit)
            throws InterruptedException,
            TimeoutException, BufferQueueClosedException {
        return mImageSequence.getNext(timeout, unit);
    }

    @Override
    public ImageProxy peekNext() {
        return mImageSequence.peekNext();
    }

    @Override
    public void discardNext() {
        mImageSequence.discardNext();
    }

    @Override
    public void append(ImageProxy image) {
        synchronized (mLock) {
            if (mClosed) {
                image.close();
                return;
            }

            if (mTicketsAvailable == 0) {
                image.close();
                return;
            }

            mTicketsAvailable--;
            mImageSequence.append(new TicketReleasingImageProxy(image));
        }
    }

    @Override
    public boolean isClosed() {
        boolean closed;
        synchronized (mLock) {
            closed = mClosed;
        }
        return closed;
    }

    private void releaseTicket() {
        synchronized (mLock) {
            if (BoundedImageBufferQueue.this.mClosed) {
                mImageTicketSink.onImageTicketReleased();
            } else {
                mTicketsAvailable++;
            }
        }
    }
}
