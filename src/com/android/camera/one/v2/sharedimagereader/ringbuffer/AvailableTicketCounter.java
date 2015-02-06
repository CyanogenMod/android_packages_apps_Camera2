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

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Observable;
import com.android.camera.async.SafeCloseable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides a listenable count of the total number of image capacity which are
 * readily-available at any given time.
 * <p>
 * The total count is the sum of all inputs.
 */
@ThreadSafe
@ParametersAreNonnullByDefault
final class AvailableTicketCounter implements Observable<Integer> {
    private final List<Observable<Integer>> mInputs;
    private final ConcurrentState<Integer> mCount;
    private final AtomicInteger mCounterLocked;

    public AvailableTicketCounter(List<Observable<Integer>> inputs) {
        mInputs = new ArrayList<>(inputs);
        mCount = new ConcurrentState<>(0);
        mCounterLocked = new AtomicInteger(0);
    }

    @Nonnull
    @Override
    public SafeCloseable addCallback(final Runnable callback, final Executor executor) {
        Lifetime callbackLifetime = new Lifetime();
        for (Observable<Integer> input : mInputs) {
            callbackLifetime.add(input.addCallback(callback, executor));
        }
        return callbackLifetime;
    }

    private int compute() {
        int sum = 0;
        for (Observable<Integer> input : mInputs) {
            sum += input.get();
        }
        return sum;
    }

    @Nonnull
    @Override
    public Integer get() {
        int value = mCount.get();
        if (mCounterLocked.get() == 0) {
            value = compute();
        }
        return value;
    }

    /**
     * Locks the counter to the current value. Changes to the value, resulting
     * from changes to the inputs, will not be reflected by {@link #get} or be
     * propagated to callbacks until a matching call to {@link #unfreeze}.
     */
    public void freeze() {
        int value = compute();
        // Update the count with the current, valid value before freezing it, if
        // it was not already frozen.
        mCounterLocked.incrementAndGet();
        mCount.update(value);
    }

    /**
     * @see {@link #freeze}
     */
    public void unfreeze() {
        // If this invocation released the last logical "lock" on the counter,
        // then update with the latest value.
        // Note that the value used *must* be that recomputed before
        // decrementing the lock counter to guarantee that we only update
        // listeners with a valid value.
        int newValue = compute();
        int numLocksHeld = mCounterLocked.decrementAndGet();
        if (numLocksHeld == 0) {
            mCount.update(newValue);
        }
    }
}
