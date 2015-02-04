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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Generic asynchronous state wrapper which supports two methods of interaction:
 * polling for the latest value and listening for updates.
 */
@ParametersAreNonnullByDefault
public class ConcurrentState<T> implements Updatable<T>, Observable<T> {
    private static class ExecutorListenerPair implements Runnable {
        private final Executor mExecutor;
        private final Runnable mListener;

        public ExecutorListenerPair(Executor executor, Runnable listener) {
            mExecutor = executor;
            mListener = listener;
        }

        /**
         * Runs the callback on the executor.
         */
        @Override
        public void run() {
            mExecutor.execute(mListener);
        }
    }

    private final Set<ExecutorListenerPair> mListeners;
    private volatile T mValue;

    public ConcurrentState(T initialValue) {
        // Callbacks are typically only added and removed at startup/shutdown,
        // but {@link #update} is often called at high-frequency. So, using a
        // read-optimized data structure is appropriate here.
        mListeners = new CopyOnWriteArraySet<>();
        mValue = initialValue;
    }

    /**
     * Updates the state to the latest value, notifying all listeners.
     */
    @Override
    public void update(T newValue) {
        mValue = newValue;
        for (ExecutorListenerPair pair : mListeners) {
            pair.run();
        }
    }

    @CheckReturnValue
    @Nonnull
    @Override
    public SafeCloseable addCallback(Runnable callback, Executor executor) {
        final ExecutorListenerPair pair = new ExecutorListenerPair(executor, callback);
        mListeners.add(pair);
        return new SafeCloseable() {
            @Override
            public void close() {
                mListeners.remove(pair);
            }
        };
    }

    /**
     * Polls for the latest value.
     *
     * @return The latest state.
     */
    @Nonnull
    @Override
    public T get() {
        return mValue;
    }
}
