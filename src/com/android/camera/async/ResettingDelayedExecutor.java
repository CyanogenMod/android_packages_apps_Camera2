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

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An executor which executes with a delay, discarding pending executions such
 * that at most one task is queued at any time.
 */
public class ResettingDelayedExecutor implements Executor, SafeCloseable {
    private final ScheduledExecutorService mExecutor;
    private final long mDelay;
    private final TimeUnit mDelayUnit;
    /**
     * Lock for all mutable state: {@link #mLatestRunRequest} and
     * {@link #mClosed}.
     */
    private final Object mLock;
    private ScheduledFuture<?> mLatestRunRequest;
    private boolean mClosed;

    public ResettingDelayedExecutor(ScheduledExecutorService executor, long delay, TimeUnit
            delayUnit) {
        mExecutor = executor;
        mDelay = delay;
        mDelayUnit = delayUnit;
        mLock = new Object();
        mClosed = false;
    }

    /**
     * Resets any pending executions.
     */
    public void reset() {
        synchronized (mLock) {
            // Cancel any existing, queued task before scheduling another.
            if (mLatestRunRequest != null) {
                mLatestRunRequest.cancel(false /* mayInterruptIfRunning */);
            }
        }
    }

    @Override
    public void execute(Runnable runnable) {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            reset();
            mLatestRunRequest = mExecutor.schedule(runnable, mDelay, mDelayUnit);
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }
            mClosed = true;
            mExecutor.shutdownNow();
        }
    }
}
