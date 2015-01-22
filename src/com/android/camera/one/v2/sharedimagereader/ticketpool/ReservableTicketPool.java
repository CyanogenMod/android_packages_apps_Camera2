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

package com.android.camera.one.v2.sharedimagereader.ticketpool;

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.Observable;
import com.android.camera.async.SafeCloseable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.GuardedBy;

/**
 * A TicketPool which reserves a capacity of tickets ahead of time from its
 * parent.
 * <p>
 * The capacity of the pool is the total number of tickets which may be
 * simultaneously acquired and not closed from this pool.
 * <p>
 * Increases in capacity result in tickets being requested from the parent pool.
 * <p>
 * Decreases in capacity result in the returning of tickets to the parent pool
 * as soon as possible, which may depend on consumers of this ticket pool
 * closing tickets which had previously been acquired.
 */
@ParametersAreNonnullByDefault
public class ReservableTicketPool implements TicketPool, SafeCloseable {
    private static class Waiter {
        private final Condition mDoneCondition;
        private final int mRequestedTicketCount;

        private Waiter(Condition doneCondition, int requestedTicketCount) {
            mDoneCondition = doneCondition;
            mRequestedTicketCount = requestedTicketCount;
        }

        public Condition getDoneCondition() {
            return mDoneCondition;
        }

        public int getRequestedTicketCount() {
            return mRequestedTicketCount;
        }
    }

    /**
     * Wraps tickets from the parent ticket pool with logic to either release
     * them back to the parent pool, or hold on to them, depending on the
     * current capacity.
     */
    private class TicketImpl implements Ticket {
        private final Ticket mParentTicket;
        private final AtomicBoolean mClosed;

        private TicketImpl(Ticket parentTicket) {
            mParentTicket = parentTicket;
            mClosed = new AtomicBoolean(false);
        }

        @Override
        public void close() {
            if (mClosed.getAndSet(true)) {
                return;
            }
            boolean releaseToParent;
            mLock.lock();
            try {
                // If mParentTickets is already at capacity, then we "overflow"
                // and return the ticket to the parent by closing it.
                // Otherwise, add it back to the local pool (mParentTickets) and
                // update any waiters which may want it.
                releaseToParent = (mParentTickets.size() == mCapacity);
                if (!releaseToParent) {
                    mParentTickets.add(mParentTicket);
                    updateCurrentTicketCount();
                    releaseWaitersOnTicketAvailability();
                }
            } finally {
                mLock.unlock();
            }

            if (releaseToParent) {
                mParentTicket.close();
            }
        }
    }

    /**
     * The pool from which capacity is acquired and released. When capacity is
     * acquired, tickets are taken from the parent pool and stored in
     * {@link #mParentTickets}.
     */
    private final TicketPool mParentPool;
    /**
     * Lock for mutable state: {@link #mParentTickets}, {@link #mCapacity}, and
     * {@link #mTicketWaiters}.
     */
    private final ReentrantLock mLock;
    /**
     * A Queue containing the number of tickets requested by each thread
     * currently blocked in {@link #acquire}.
     */
    @GuardedBy("mLock")
    private final ArrayDeque<Waiter> mTicketWaiters;
    /**
     * Tickets from mParentPool which have not been given to clients via
     * {@link #acquire}.
     */
    @GuardedBy("mLock")
    private final ArrayDeque<Ticket> mParentTickets;
    /**
     * Maintains an observable count of the number of tickets which are readily
     * available at any time.
     */
    private final ConcurrentState<Integer> mAvailableTicketCount;
    /**
     * The total number of tickets available and outstanding (acquired but not
     * closed).
     */
    @GuardedBy("mLock")
    private int mCapacity;

    public ReservableTicketPool(TicketPool parentPool) {
        mParentPool = parentPool;
        mLock = new ReentrantLock(true);
        mTicketWaiters = new ArrayDeque<>();
        mParentTickets = new ArrayDeque<>();
        mCapacity = 0;
        mAvailableTicketCount = new ConcurrentState<>(0);
    }

    @GuardedBy("mLock")
    private void updateCurrentTicketCount() {
        mLock.lock();
        try {
            if (mTicketWaiters.size() != 0) {
                mAvailableTicketCount.update(0);
            } else {
                mAvailableTicketCount.update(mParentTickets.size());
            }
        } finally {
            mLock.unlock();
        }
    }

    @Nonnull
    @Override
    public Collection<Ticket> acquire(int tickets) throws InterruptedException,
            NoCapacityAvailableException {
        Collection<Ticket> acquiredParentTickets = acquireParentTickets(tickets);

        List<Ticket> wrappedTicketList = new ArrayList<>();
        for (Ticket parentTicket : acquiredParentTickets) {
            wrappedTicketList.add(new TicketImpl(parentTicket));
        }
        return wrappedTicketList;
    }

    @Nonnull
    @Override
    public Observable<Integer> getAvailableTicketCount() {
        return mAvailableTicketCount;
    }

    @Override
    public Ticket tryAcquire() {
        Ticket parentTicket;
        mLock.lock();
        try {
            if (mParentTickets.isEmpty() || mTicketWaiters.size() > 0) {
                return null;
            }
            parentTicket = mParentTickets.remove();
            updateCurrentTicketCount();
        } finally {
            mLock.unlock();
        }

        return new TicketImpl(parentTicket);
    }

    /**
     * Reserves tickets from the parent pool.
     *
     * @param additionalCapacity The additional capacity to acquire.
     * @throws InterruptedException If interrupted while trying to acquire the
     *             necessary number of tickets.
     */
    public void reserveCapacity(int additionalCapacity) throws InterruptedException,
            NoCapacityAvailableException {
        Collection<Ticket> tickets = mParentPool.acquire(additionalCapacity);

        mLock.lock();
        try {
            mCapacity += additionalCapacity;

            for (Ticket ticket : tickets) {
                mParentTickets.add(ticket);
            }

            releaseWaitersOnTicketAvailability();
        } finally {
            mLock.unlock();
        }

        updateCurrentTicketCount();
    }

    /**
     * Releases the capacity to the parent ticket pool. Note that the tickets
     * will be released as soon as possible. However, this is not necessarily
     * immediately if there are tickets which have been acquired() by a user of
     * this class, but not yet released.
     *
     * @param capacityToRelease The amount of capacity to release.
     */
    public void releaseCapacity(int capacityToRelease) {
        if (capacityToRelease <= 0) {
            return;
        }
        List<Ticket> parentTicketsToRelease = new ArrayList<>();

        mLock.lock();
        try {
            if (capacityToRelease > mCapacity) {
                capacityToRelease = mCapacity;
            }

            mCapacity -= capacityToRelease;

            // Release as many tickets as necessary, immediately.
            int numParentTicketsToRelease = Math.min(mParentTickets.size(), capacityToRelease);
            for (int i = 0; i < numParentTicketsToRelease; i++) {
                parentTicketsToRelease.add(mParentTickets.remove());
            }

            abortWaitersOnCapacityDecrease();
        } finally {
            mLock.unlock();
        }

        for (Ticket ticket : parentTicketsToRelease) {
            ticket.close();
        }

        updateCurrentTicketCount();
    }

    /**
     * Releases all remaining capacity to the parent ticket pool.
     */
    private void releaseAllCapacity() {
        mLock.lock();
        try {
            releaseCapacity(mCapacity);
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public void close() {
        releaseAllCapacity();
    }

    /**
     * Acquires the specified number of tickets from mParentTickets atomically,
     * blocking until released by {@link #releaseWaitersOnTicketAvailability}.
     *
     * @param tickets The number of tickets to acquire.
     */
    private Collection<Ticket> acquireParentTickets(int tickets) throws InterruptedException,
            NoCapacityAvailableException {
        // The list of tickets from mTicketList to acquire.
        // Try to acquire these immediately, if there are no threads already
        // waiting for tickets.
        List<Ticket> acquiredParentTickets = null;
        mLock.lock();
        try {
            if (mTicketWaiters.isEmpty()) {
                acquiredParentTickets = tryAcquireAtomically(tickets);
            }
            Waiter thisWaiter = new Waiter(mLock.newCondition(), tickets);
            mTicketWaiters.add(thisWaiter);
            updateCurrentTicketCount();
            try {
                while (acquiredParentTickets == null) {
                    thisWaiter.getDoneCondition().await();
                    acquiredParentTickets = tryAcquireAtomically(tickets);
                }
            } finally {
                mTicketWaiters.remove(thisWaiter);
            }
            updateCurrentTicketCount();
        } finally {
            mLock.unlock();
        }
        return acquiredParentTickets;
    }

    /**
     * Atomically attempt to remove the necessary number of tickets. This must
     * be an all-or-nothing attempt to avoid multiple acquire() calls from
     * deadlocking by each partially acquiring the necessary number of tickets.
     *
     * @return The tickets acquired from the parent ticket pool, or null if they
     *         could not be acquired.
     */
    @Nullable
    @CheckReturnValue
    private List<Ticket> tryAcquireAtomically(int tickets) throws NoCapacityAvailableException {
        List<Ticket> acquiredParentTickets = new ArrayList<>();
        mLock.lock();
        try {
            if (tickets > mCapacity) {
                throw new NoCapacityAvailableException();
            }
            if (mParentTickets.size() >= tickets) {
                for (int i = 0; i < tickets; i++) {
                    acquiredParentTickets.add(mParentTickets.remove());
                }
                updateCurrentTicketCount();
                return acquiredParentTickets;
            }
        } finally {
            mLock.unlock();
        }
        return null;
    }

    private void releaseWaitersOnTicketAvailability() {
        mLock.lock();
        try {
            // Release waiters, in order, so long as their requests can be
            // fulfilled.
            int numTicketsReadilyAvailable = mParentTickets.size();
            while (mTicketWaiters.size() > 0) {
                Waiter nextWaiter = mTicketWaiters.peek();
                if (nextWaiter.getRequestedTicketCount() <= numTicketsReadilyAvailable) {
                    numTicketsReadilyAvailable -= nextWaiter.getRequestedTicketCount();
                    nextWaiter.getDoneCondition().signal();
                    mTicketWaiters.poll();
                } else {
                    return;
                }
            }
        } finally {
            mLock.unlock();
        }
    }

    private void abortWaitersOnCapacityDecrease() {
        mLock.lock();
        try {
            // Release all waiters requesting more tickets than the current
            // capacity
            List<Waiter> toRemove = new ArrayList<>();
            for (Waiter waiter : mTicketWaiters) {
                if (waiter.getRequestedTicketCount() > mCapacity) {
                    toRemove.add(waiter);
                }
            }
            for (Waiter waiter : toRemove) {
                waiter.getDoneCondition().signal();
            }
        } finally {
            mLock.unlock();
        }
    }
}
