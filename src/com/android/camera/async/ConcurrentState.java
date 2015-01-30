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

import com.android.camera.util.Callback;

import java.util.HashSet;
import java.util.Set;
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
                @Override
                public void run() {
                    mListener.onCallback(t);
                }
            });
        }
    }

    private final Object mLock;
    private final Set<ExecutorListenerPair<? super T>> mListeners;
    private T mValue;

    public ConcurrentState(T initialValue) {
        mLock = new Object();
        mListeners = new HashSet<>();
        mValue = initialValue;
    }

    /**
     * Updates the state to the latest value, notifying all listeners.
     */
    @Override
    public void update(T newValue) {
        synchronized (mLock) {
            mValue = newValue;
            // Invoke executors.execute within mLock to guarantee that
            // callbacks are serialized into their respective executor in
            // the proper order.
            for (ExecutorListenerPair<? super T> pair : mListeners) {
                pair.run(newValue);
            }
        }
    }

    @CheckReturnValue
    @Nonnull
    @Override
    public SafeCloseable addCallback(Callback<T> callback, Executor executor) {
        synchronized (mLock) {
            final ExecutorListenerPair<? super T> pair =
                    new ExecutorListenerPair<>(executor, callback);
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
     * @return The latest state.
     */
    @Nonnull
    @Override
    public T get() {
        synchronized (mLock) {
            return mValue;
        }
    }
}
