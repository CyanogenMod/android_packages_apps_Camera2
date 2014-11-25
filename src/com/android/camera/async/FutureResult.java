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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Future which can be used to asynchronously return a result.
 */
public class FutureResult<V> implements Future<V> {
    /**
     * True if "done" or in the process of being marked "done".
     */
    private final AtomicBoolean mDone;
    /**
     * 0 if "done" and the appropriate one of {@link #mValue},
     * {@link #mCancelled}, and {@link #mException} has been set.
     */
    private final CountDownLatch mDoneCondition;
    private V mValue;
    private boolean mCancelled;
    private Exception mException;

    public FutureResult() {
        mDone = new AtomicBoolean();
        mDoneCondition = new CountDownLatch(1);
        mValue = null;
        mCancelled = false;
    }

    /**
     * See {@link Future#cancel}. This Future implementation only supports
     * cancellation from the producer-side via {@link #setCancelled}.
     */
    @Override
    public boolean cancel(boolean b) {
        return false;
    }

    /**
     * See {@link Future#isCancelled}.
     */
    @Override
    public boolean isCancelled() {
        return isDone() && mCancelled;
    }

    /**
     * See {@link Future#isDone}.
     */
    @Override
    public boolean isDone() {
        return mDoneCondition.getCount() == 0;
    }

    private V getAfterDone() throws ExecutionException {
        if (mCancelled) {
            throw new CancellationException();
        }
        if (mException == null) {
            return mValue;
        } else {
            throw new ExecutionException(mException);
        }
    }

    /**
     * See {@link Future#get()}.
     */
    @Override
    public V get() throws InterruptedException, ExecutionException, CancellationException {
        mDoneCondition.await();
        return getAfterDone();
    }

    /**
     * See {@link Future#get(long, java.util.concurrent.TimeUnit)}.
     */
    @Override
    public V get(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException,
            TimeoutException {
        mDoneCondition.await(timeout, timeUnit);
        return getAfterDone();
    }

    /**
     * Marks the future as cancelled.
     *
     * @return True if successfully marked as cancelled, or false if the future
     *         was already done.
     */
    public boolean setCancelled() {
        boolean alreadyDone = mDone.getAndSet(true);
        if (alreadyDone) {
            return false;
        }

        mCancelled = true;
        mDoneCondition.countDown();
        return true;
    }

    /**
     * Sets the result of the future.
     *
     * @param value The result of the future.
     * @return True if the value was set, or false if the future was already
     *         done and the provided value was discarded.
     */
    public boolean setValue(V value) {
        boolean alreadyDone = mDone.getAndSet(true);
        if (alreadyDone) {
            return false;
        }

        mValue = value;
        mDoneCondition.countDown();
        return true;
    }

    /**
     * Marks the future as having an execution exception.
     *
     * @param e The exception.
     * @return True if the exception was set, or false if the future was already
     *         done and the provided exception was discarded.
     */
    public boolean setException(Exception e) {
        boolean alreadyDone = mDone.getAndSet(true);
        if (alreadyDone) {
            return false;
        }

        mException = e;
        mDoneCondition.countDown();
        return true;
    }

    /**
     * @return The value, if one has been set, or null otherwise.
     */
    public V tryGet() {
        if (isDone()) {
            return mValue;
        } else {
            return null;
        }
    }
}
