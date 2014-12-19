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

package com.android.camera.one.v2.sharedimagereader.ticketpool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.camera.async.SafeCloseable;

/**
 * A ticket pool with a finite number of tickets.
 */
public class FiniteTicketPool implements TicketPool, SafeCloseable {
    private class TicketImpl implements Ticket {
        private final AtomicBoolean mClosed;

        public TicketImpl() {
            mClosed = new AtomicBoolean(false);
        }

        @Override
        public void close() {
            boolean alreadyClosed = mClosed.getAndSet(true);
            if (!alreadyClosed) {
                mTickets.release();
            }
        }
    }

    private final int mMaxCapacity;
    private final Semaphore mTickets;
    private final AtomicBoolean mClosed;

    public FiniteTicketPool(int capacity) {
        mMaxCapacity = capacity;
        mTickets = new Semaphore(capacity);
        mClosed = new AtomicBoolean(false);
    }

    @Override
    public Collection<Ticket> acquire(int tickets) throws InterruptedException,
            NoCapacityAvailableException {
        if (tickets > mMaxCapacity || tickets < 0) {
            throw new NoCapacityAvailableException();
        }
        mTickets.acquire(tickets);
        if (mClosed.get()) {
            // If the pool was closed while we were waiting to acquire the
            // tickets, release them (they may be fake) and throw because no
            // capacity is available.
            mTickets.release(tickets);
            throw new NoCapacityAvailableException();
        }
        List<Ticket> ticketList = new ArrayList<Ticket>();
        for (int i = 0; i < tickets; i++) {
            ticketList.add(new TicketImpl());
        }
        return ticketList;
    }

    @Override
    public boolean canAcquire(int tickets) {
        if (tickets < 0) {
            return false;
        }
        if (tickets == 0) {
            return true;
        }
        return !mClosed.get() &&
                !mTickets.hasQueuedThreads() &&
                mTickets.availablePermits() >= tickets;
    }

    @Override
    public Ticket tryAcquire() {
        if (mTickets.tryAcquire()) {
            // Release the ticket immediately if...
            // 1. The pool is closed, and tryAcquire may have received a fake,
            // "poisson pill" ticket, which must be released in order to enable
            // other calls to acquire() to not block.
            // 2. Or, mTickets has threads queued on it because of blocked calls
            // to {@link #acquire}. It would not be fair to acquire this ticket
            // if there are pending requests for it already.
            boolean releaseTicketImmediately = mClosed.get() || mTickets.hasQueuedThreads();
            if (releaseTicketImmediately) {
                mTickets.release();
                return null;
            } else {
                return new TicketImpl();
            }
        } else {
            return null;
        }
    }

    @Override
    public void close() {
        if (mClosed.getAndSet(true)) {
            // If already closed, just return.
            return;
        }

        // Threads may be waiting in acquire() for tickets to be available, so
        // wake them up by adding fake, "poisson pill" tickets.
        // Adding mMaxCapacity permits to the semaphore is sufficient to
        // guarantee that any/all
        // waiting threads wake up and detect that the pool is closed (so long
        // as all waiting
        // threads release the fake tickets they acquired while waking up).
        mTickets.release(mMaxCapacity);
    }
}
