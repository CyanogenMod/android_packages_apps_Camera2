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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import com.android.camera.util.Callback;

/**
 * Generic asynchronous state wrapper which supports two methods of interaction:
 * polling for the latest value and listening for updates.
 * <p>
 * Note that this class only supports polling and using listeners. If
 * synchronous consumption of state changes is required, see
 * {@link FutureResult} or {@link BufferQueue} and its implementations.
 * </p>
 */
public class ConcurrentState<T> implements Updatable<T>, Observable<T> {

    private static class ExecutorListenerPair<T> {
        private final Executor mExecutor;
        private final Callback<T> mListener;

        public ExecutorListenerPair(Executor executor, Callback<T> listener) {
            mExecutor = executor;
            mListener = listener;
        }

        /**
         * Runs the callback on the executor with the given value.
         */
        public void run(final T t) {
            mExecutor.execute(new Runnable() {
                public void run() {
                    mListener.onCallback(t);
                }
            });
        }
    }

    private final Object mLock;
    private final Set<ExecutorListenerPair<T>> mListeners;
    private boolean mValueSet;
    private T mValue;

    public ConcurrentState() {
        mLock = new Object();
        mListeners = new HashSet<ExecutorListenerPair<T>>();
        mValueSet = false;
    }

    /**
     * Updates the state to the latest value, notifying all listeners.
     */
    @Override
    public void update(T newValue) {
        List<ExecutorListenerPair<T>> listeners = new ArrayList<ExecutorListenerPair<T>>();
        synchronized (mLock) {
            mValueSet = true;
            mValue = newValue;
            // Copy listeners out here so we can iterate over the list outside
            // the critical section.
            listeners.addAll(mListeners);
        }
        for (ExecutorListenerPair<T> pair : listeners) {
            pair.run(newValue);
        }
    }

    @Override
    public SafeCloseable addCallback(Callback callback, Executor executor) {
        synchronized (mLock) {
            final ExecutorListenerPair<T> pair = new ExecutorListenerPair<>(executor, callback);
            mListeners.add(pair);

            return new SafeCloseable() {
                @Override
                public void close() {
                    synchronized (mLock) {
                        mListeners.remove(pair);
                    }
                }
            };
        }
    }

    /**
     * Polls for the latest value.
     *
     * @return The latest state, or defaultValue if no state has been set yet.
     */
    @Override
    public T get(T defaultValue) {
        try {
            return get();
        } catch (Pollable.NoValueSetException e) {
            return defaultValue;
        }
    }

    /**
     * Polls for the latest value.
     *
     * @return The latest state.
     * @throws com.android.camera.async.Pollable.NoValueSetException If no value has been set yet.
     */
    @Override
    public T get() throws Pollable.NoValueSetException {
        synchronized (mLock) {
            if (mValueSet) {
                return mValue;
            } else {
                throw new Pollable.NoValueSetException();
            }
        }
    }
}
