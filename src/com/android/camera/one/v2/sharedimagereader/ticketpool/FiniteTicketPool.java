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

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.Observable;
import com.android.camera.async.SafeCloseable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

/**
 * A ticket pool with a finite number of tickets.
 */
public final class FiniteTicketPool implements TicketPool, SafeCloseable {
    private class TicketImpl implements Ticket {
        private final AtomicBoolean mClosed;

        public TicketImpl() {
            mClosed = new AtomicBoolean(false);
        }

        @Override
        public void close() {
            boolean alreadyClosed = mClosed.getAndSet(true);
            if (!alreadyClosed) {
                synchronized (mLock) {
                    releaseTicket();
                    updateAvailableTicketCount();
                }
            }
        }
    }

    private class Waiter {
        private final int mTicketsRequested;
        private final Condition mCondition;

        private Waiter(int ticketsRequested, Condition condition) {
            mTicketsRequested = ticketsRequested;
            mCondition = condition;
        }

        public Condition getCondition() {
            return mCondition;
        }

        public int getTicketsRequested() {
            return mTicketsRequested;
        }
    }

    private final int mMaxCapacity;
    private final ReentrantLock mLock;
    @GuardedBy("mLock")
    private final LinkedList<Waiter> mWaiters;
    private final ConcurrentState<Integer> mAvailableTicketCount;
    @GuardedBy("mLock")
    private int mTickets;
    @GuardedBy("mLock")
    private boolean mClosed;

    public FiniteTicketPool(int capacity) {
        mMaxCapacity = capacity;
        mLock = new ReentrantLock(true);
        mTickets = capacity;
        mWaiters = new LinkedList<>();
        mClosed = false;
        mAvailableTicketCount = new ConcurrentState<>(capacity);
    }

    @GuardedBy("mLock")
    private void releaseTicket() {
        mLock.lock();
        try {
            mTickets++;

            // Wake up waiters in order, so long as their requested number of
            // tickets can be satisfied.
            int ticketsRemaining = mTickets;
            Waiter nextWaiter = mWaiters.peekFirst();
            while (nextWaiter != null && nextWaiter.getTicketsRequested() <= ticketsRemaining) {
                ticketsRemaining -= nextWaiter.getTicketsRequested();
                nextWaiter.getCondition().signal();

                mWaiters.removeFirst();
                nextWaiter = mWaiters.peekFirst();
            }
        } finally {
            mLock.unlock();
        }
    }

    @Nonnull
    @Override
    public Collection<Ticket> acquire(int tickets) throws InterruptedException,
            NoCapacityAvailableException {
        mLock.lock();
        try {
            if (tickets > mMaxCapacity || tickets < 0) {
                throw new NoCapacityAvailableException();
            }
            Waiter thisWaiter = new Waiter(tickets, mLock.newCondition());
            mWaiters.addLast(thisWaiter);
            updateAvailableTicketCount();
            try {
                while (mTickets < tickets && !mClosed) {
                    thisWaiter.getCondition().await();
                }
                if (mClosed) {
                    throw new NoCapacityAvailableException();
                }

                mTickets -= tickets;

                updateAvailableTicketCount();

                List<Ticket> ticketList = new ArrayList<>();
                for (int i = 0; i < tickets; i++) {
                    ticketList.add(new TicketImpl());
                }
                return ticketList;
            } finally {
                mWaiters.remove(thisWaiter);
                updateAvailableTicketCount();
            }
        } finally {
            mLock.unlock();
        }
    }

    @GuardedBy("mLock")
    private void updateAvailableTicketCount() {
        if (mClosed || !mWaiters.isEmpty()) {
            mAvailableTicketCount.update(0);
        } else {
            mAvailableTicketCount.update(mTickets);
        }
    }

    @Nonnull
    @Override
    public Observable<Integer> getAvailableTicketCount() {
        return mAvailableTicketCount;
    }

    @Override
    public Ticket tryAcquire() {
        mLock.lock();
        try {
            if (!mClosed && mWaiters.isEmpty() && mTickets >= 1) {
                mTickets--;
                updateAvailableTicketCount();
                return new TicketImpl();
            } else {
                return null;
            }
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void close() {
        mLock.lock();
        try {
            if (mClosed) {
                return;
            }

            mClosed = true;

            for (Waiter waiter : mWaiters) {
                waiter.getCondition().signal();
            }

            updateAvailableTicketCount();
        } finally {
            mLock.unlock();
        }
    }
}
