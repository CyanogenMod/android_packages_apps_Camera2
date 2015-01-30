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

package com.android.camera.async;

import com.android.camera.util.Callback;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Enables combining multiple {@link Observable}s together with a given
 * function.
 * <p>
 * Callbacks added to the resulting observable are notified when any of the
 * dependencies change.
 */
@ThreadSafe
@ParametersAreNonnullByDefault
final class ObservableCombiner<I, O> implements Observable<O> {
    private final ImmutableList<Observable<I>> mInputs;
    private final Function<List<I>, O> mFunction;

    private final Object mLock;

    @GuardedBy("mLock")
    private final ConcurrentState<O> mListenerNotifier;

    @GuardedBy("mLock")
    private final List<SafeCloseable> mInputCallbackHandles;

    @GuardedBy("mLock")
    private int mNumRegisteredCallbacks;

    /**
     * The thread-safe callback to be registered with each input.
     */
    private final Updatable<I> mInputCallback = new Updatable<I>() {
        public void update(I ignored) {
            mListenerNotifier.update(get());
        }
    };

    private ObservableCombiner(List<? extends Observable<I>> inputs,
            Function<List<I>, O> function, O initialValue) {
        mInputs = ImmutableList.copyOf(inputs);
        mFunction = function;
        mListenerNotifier = new ConcurrentState<>(initialValue);
        mLock = new Object();
        mInputCallbackHandles = new ArrayList<>();
        mNumRegisteredCallbacks = 0;
    }

    /**
     * Transforms a set of input observables with a function.
     *
     * @param inputs The input observables.
     * @param function The function to apply to all of the inputs.
     * @param <I> The type of all inputs values.
     * @param <O> The type of the output values.
     * @return An observable which will reflect the combination of all inputs
     *         with the given function. Changes in the output value will result
     *         in calls to any callbacks registered with the output.
     */
    static <I, O> Observable<O> transform(List<? extends Observable<I>> inputs,
            Function<List<I>, O> function) {
        // Compute the initial value.
        ArrayList<I> deps = new ArrayList<>();
        for (Observable<? extends I> input : inputs) {
            deps.add(input.get());
        }
        O initialValue = function.apply(deps);

        return new ObservableCombiner<>(inputs, function, initialValue);
    }

    @GuardedBy("mLock")
    private void addCallbacksToInputs() {
        for (Observable<I> observable : mInputs) {
            final SafeCloseable callbackHandle =
                    Observables.addThreadSafeCallback(observable, mInputCallback);

            mInputCallbackHandles.add(callbackHandle);
        }
    }

    @GuardedBy("mLock")
    private void removeCallbacksFromInputs() {
        for (SafeCloseable callbackHandle : mInputCallbackHandles) {
            callbackHandle.close();
        }
    }

    @Nonnull
    @Override
    @CheckReturnValue
    public SafeCloseable addCallback(final Callback<O> callback, Executor executor) {
        // When a callback is added to this, the "output", we must ensure that
        // callbacks are registered with all of the inputs so that they can be
        // forwarded properly.
        // Instead of adding another callback to each input for each callback
        // registered with the output, callbacks are registered when the first
        // output callback is added, and removed when the last output callback
        // is removed.

        synchronized (mLock) {
            if (mNumRegisteredCallbacks == 0) {
                addCallbacksToInputs();
            }
            mNumRegisteredCallbacks++;
        }

        // Wrap the callback in a {@link FilteredCallback} to prevent many
        // duplicate/cascading updates even if the output does not change.
        final SafeCloseable resultingCallbackHandle = mListenerNotifier.addCallback(
                new FilteredCallback<O>(callback), executor);

        return new SafeCloseable() {
            @Override
            public void close() {
                resultingCallbackHandle.close();

                synchronized (mLock) {
                    mNumRegisteredCallbacks--;
                    if (mNumRegisteredCallbacks == 0) {
                        removeCallbacksFromInputs();
                    }
                }
            }
        };
    }

    @Nonnull
    @Override
    public O get() {
        ArrayList<I> deps = new ArrayList<>();
        for (Observable<? extends I> dependency : mInputs) {
            deps.add(dependency.get());
        }
        return mFunction.apply(deps);
    }
}
