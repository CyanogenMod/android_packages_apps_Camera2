/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.one.v2.sharedimagereader.ringbuffer;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.async.ConcurrentState;
import com.android.camera.async.CountableBufferQueue;
import com.android.camera.async.Observable;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.sharedimagereader.ticketpool.Ticket;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketPool;
import com.android.camera.one.v2.sharedimagereader.util.ImageCloser;
import com.android.camera.one.v2.sharedimagereader.util.TicketImageProxy;
import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A dynamically-sized ring-buffer, implementing BufferQueue (output) and
 * BufferQueueController (input).
 * <p>
 * The size of the buffer is implicitly defined by the number of "Tickets"
 * available from the parent {@link TicketPool} at any given time. When the
 * number of available tickets decreases, the buffer shrinks, discarding old
 * elements. When the number of available tickets increases, the buffer expands,
 * retaining old elements when new elements are added.
 * <p>
 * The ring-buffer is also a TicketPool, which allows higher-priority requests
 * to reserve "Tickets" (representing ImageReader capacity) to evict images from
 * the ring-buffer.
 * <p>
 * See docs for {@link DynamicRingBufferFactory} for more information.
 */
@ThreadSafe
@ParametersAreNonnullByDefault
final class DynamicRingBuffer implements TicketPool, BufferQueue<ImageProxy>,
        BufferQueueController<ImageProxy> {
    private final CountableBufferQueue<ImageProxy> mQueue;
    private final TicketPool mTicketPool;
    private final AtomicInteger mTicketWaiterCount;
    private final AvailableTicketCounter mAvailableTicketCount;
    private final AtomicInteger mMaxSize;
    private final ConcurrentState<Integer> mQueueSize;

    /**
     * @param parentTickets The parent ticket pool which implicitly determines
     *            how much capacity is available at any given time.
     */
    DynamicRingBuffer(TicketPool parentTickets) {
        mQueueSize = new ConcurrentState<>(0);
        mQueue = new CountableBufferQueue<>(mQueueSize, new ImageCloser());
        mAvailableTicketCount = new AvailableTicketCounter(Arrays.asList(mQueueSize, parentTickets
                .getAvailableTicketCount()));
        mTicketPool = parentTickets;
        mTicketWaiterCount = new AtomicInteger(0);
        mMaxSize = new AtomicInteger(Integer.MAX_VALUE);
    }

    @Override
    public void update(@Nonnull ImageProxy image) {
        // Try to acquire a ticket to expand the ring-buffer and save the image.
        Ticket ticket = null;

        // Counting is hard. {@link mAvailableTicketCount} must reflect the sum
        // of mTicketPool.getAvailableTicketCount() and the number of images in
        // mQueue. However, for a brief moment, we acquire a ticket from
        // mTicketPool, but have yet added it to mQueue. During this period,
        // mAvailableTicketCount would appear to be 1 less than it should.
        // To fix this, we must lock it to the current value, perform the
        // transaction, and then unlock it, marking it as "valid" again, which
        // also notifies listeners of the change.
        mAvailableTicketCount.freeze();
        try {
            ticket = tryAcquireLowPriorityTicket();
            if (ticket == null) {
                // If we cannot expand the ring-buffer, remove the last element
                // (decreasing the size), and then try to increase the size
                // again.
                mQueue.discardNext();
                ticket = tryAcquireLowPriorityTicket();
            }
            if (ticket != null) {
                mQueue.update(new TicketImageProxy(image, ticket));
            } else {
                image.close();
            }
            shrinkToFitMaxSize();
        } finally {
            mAvailableTicketCount.unfreeze();
        }
    }

    @Nullable
    private Ticket tryAcquireLowPriorityTicket() {
        if (mTicketWaiterCount.get() != 0) {
            return null;
        }
        return mTicketPool.tryAcquire();
    }

    @Override
    public void close() {
        mQueue.close();
    }

    @Override
    public ImageProxy getNext() throws InterruptedException, BufferQueueClosedException {
        return mQueue.getNext();
    }

    @Override
    public ImageProxy getNext(long timeout, TimeUnit unit) throws InterruptedException,
            TimeoutException, BufferQueueClosedException {
        return mQueue.getNext(timeout, unit);
    }

    @Override
    public ImageProxy peekNext() {
        return mQueue.peekNext();
    }

    @Override
    public void discardNext() {
        mQueue.discardNext();
    }

    @Override
    public boolean isClosed() {
        return mQueue.isClosed();
    }

    @Nonnull
    @Override
    public Collection<Ticket> acquire(int tickets) throws InterruptedException,
            NoCapacityAvailableException {
        mTicketWaiterCount.incrementAndGet();
        try {
            while (mQueue.peekNext() != null) {
                mQueue.discardNext();
            }
            return mTicketPool.acquire(tickets);
        } finally {
            mTicketWaiterCount.decrementAndGet();
        }
    }

    @Nonnull
    @Override
    public Observable<Integer> getAvailableTicketCount() {
        return mAvailableTicketCount;
    }

    @Nullable
    @Override
    public Ticket tryAcquire() {
        mTicketWaiterCount.incrementAndGet();
        try {
            while (mQueue.peekNext() != null) {
                mQueue.discardNext();
            }
            return mTicketPool.tryAcquire();
        } finally {
            mTicketWaiterCount.decrementAndGet();
        }
    }

    public void setMaxSize(int newMaxSize) {
        Preconditions.checkArgument(newMaxSize >= 0);
        mMaxSize.set(newMaxSize);
        // Shrink the queue to meet this new constraint.
        shrinkToFitMaxSize();
    }

    private void shrinkToFitMaxSize() {
        // To ensure that the available ticket count never "flickers" when we
        // logically move the ticket from the queue into the parent ticket pool,
        // lock the available ticket count.
        mAvailableTicketCount.freeze();
        try {
            // Note that to maintain the invariant of eventual-consistency
            // (since this class is inherently shared between multiple threads),
            // we must repeatedly poll these values each time.
            while (mQueueSize.get() > mMaxSize.get()) {
                mQueue.discardNext();
            }
        } finally {
            mAvailableTicketCount.unfreeze();
        }
    }
}
