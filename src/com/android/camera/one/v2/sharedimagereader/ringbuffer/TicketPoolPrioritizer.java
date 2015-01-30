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

package com.android.camera.one.v2.sharedimagereader.ringbuffer;

import com.android.camera.async.Observable;
import com.android.camera.async.Observables;
import com.android.camera.one.v2.sharedimagereader.ticketpool.Ticket;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketPool;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketProvider;
import com.google.common.base.Function;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Splits a {@link TicketPool} into a high-priority lane and a low-priority,
 * semi-preemptable lane.
 * <p>
 * Requests for high-priority tickets result in all low-priority tickets being
 * released immediately (via the {@link LowPriorityTicketReleaser}).
 */
class TicketPoolPrioritizer {
    public interface LowPriorityTicketReleaser {
        /**
         * Provides a signal that a high-priority ticket has been requested, so
         * all low-priority tickets which can be released immediately are
         * released immediately.
         */
        public void releaseLowPriorityTickets();
    }

    private class LowPriorityTicketPool implements TicketProvider {
        private LowPriorityTicketPool() {
        }

        @Nullable
        @CheckReturnValue
        @Override
        public Ticket tryAcquire() {
            if (mHighPriorityWaiters.get() > 0) {
                return null;
            } else {
                return mRootTicketPool.tryAcquire();
            }
        }
    }

    private class HighPriorityTicketPool implements TicketPool {
        @Nonnull
        @Override
        public Collection<Ticket> acquire(int tickets) throws InterruptedException,
                NoCapacityAvailableException {
            Collection<Ticket> result;
            // This over-aggressively flushes all low-priority tickets every
            // time high-priority tickets are requested. It could, instead,
            // precisely flush only those which are necessary. However, for
            // current requirements, this is an acceptable simplification with
            // no drawbacks.
            mLowPriorityTicketReleaser.releaseLowPriorityTickets();
            mHighPriorityWaiters.incrementAndGet();
            try {
                result = mRootTicketPool.acquire(tickets);
            } finally {
                mHighPriorityWaiters.decrementAndGet();
            }
            return result;
        }

        @Nonnull
        @Override
        public Observable<Integer> getAvailableTicketCount() {
            return mAvailableHighPriorityTicketCount;
        }

        @Nullable
        @CheckReturnValue
        @Override
        public Ticket tryAcquire() {
            // This over-aggressively flushes all low-priority tickets every
            // time high-priority tickets are requested. It could, instead,
            // precisely flush only those which are necessary. However, for
            // current requirements, this is an acceptable simplification with
            // no drawbacks.
            mLowPriorityTicketReleaser.releaseLowPriorityTickets();
            return mRootTicketPool.tryAcquire();
        }
    }

    private final LowPriorityTicketReleaser mLowPriorityTicketReleaser;
    private final TicketPool mRootTicketPool;
    private final AtomicInteger mHighPriorityWaiters;
    private final HighPriorityTicketPool mHighPriority;
    private final LowPriorityTicketPool mLowPriority;

    private final Observable<Integer> mAvailableHighPriorityTicketCount;

    public TicketPoolPrioritizer(LowPriorityTicketReleaser lowPriorityTicketReleaser,
            Observable<Integer> releasableLowPriorityTicketCount, TicketPool rootTicketPool) {
        mLowPriorityTicketReleaser = lowPriorityTicketReleaser;
        mRootTicketPool = rootTicketPool;
        mHighPriorityWaiters = new AtomicInteger(0);
        mHighPriority = new HighPriorityTicketPool();
        mLowPriority = new LowPriorityTicketPool();

        final Observable<Integer> parentPoolTicketCount = rootTicketPool.getAvailableTicketCount();
        Function<List<Integer>, Integer> sum = new Function<List<Integer>, Integer>() {
            @Override
            public Integer apply(@Nullable List<Integer> integers) {
                int total = 0;
                for (Integer i : integers) {
                    total += i;
                }
                return total;
            }
        };

        // The number of high-priority tickets available at any time is the sum
        // of parentPoolTicketCount and releaseableLowPriorityTicketCount.
        mAvailableHighPriorityTicketCount = Observables.transform(
                Arrays.asList(parentPoolTicketCount, releasableLowPriorityTicketCount),
                sum);
    }

    public TicketProvider getLowPriorityTicketProvider() {
        return mLowPriority;
    }

    public TicketPool getHighPriorityTicketPool() {
        return mHighPriority;
    }
}
