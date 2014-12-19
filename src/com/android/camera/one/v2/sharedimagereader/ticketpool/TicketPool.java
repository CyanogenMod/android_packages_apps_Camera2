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

import java.util.Collection;

/**
 * Stores a collection of {@link Ticket}s. Tickets may be acquired from the
 * pool. When closed, tickets return themselves to the pool.
 */
public interface TicketPool extends TicketProvider {
    /**
     * Indicates that the requested number of tickets will never be available,
     * possibly because the Pool has been closed, or because the request exceeds
     * the maximum number of tickets which exist in the context of the pool.
     */
    public static class NoCapacityAvailableException extends Exception {
    }

    /**
     * Acquires and returns the specified number of tickets.
     * <p>
     * Note: Implementations MUST be fair!
     * </p>
     */
    public Collection<Ticket> acquire(int tickets) throws InterruptedException,
            NoCapacityAvailableException;

    /**
     * Provides a hint as to whether or not the specified number of tickets can
     * be acquired immediately, or will probably block.
     *
     * @return True if a subsequent call to {@link #acquire} will probably not
     *         block.
     */
    public boolean canAcquire(int tickets);

    /**
     * Attempts to acquire and return a ticket.
     * <p>
     * Note: Implementations MUST be fair w.r.t. {@link #acquire}.
     * </p>
     *
     * @return The acquired ticket, or null if no ticket is readily available.
     */
    @Override
    public Ticket tryAcquire();
}
