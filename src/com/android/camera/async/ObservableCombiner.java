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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
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

    private ObservableCombiner(List<? extends Observable<I>> inputs,
            Function<List<I>, O> function) {
        mInputs = ImmutableList.copyOf(inputs);
        mFunction = function;
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
        return new ObservableCombiner<>(inputs, function);
    }

    @Nonnull
    @Override
    @CheckReturnValue
    public SafeCloseable addCallback(Runnable callback, Executor executor) {
        Lifetime callbackLifetime = new Lifetime();

        for (Observable<I> input : mInputs) {
            callbackLifetime.add(input.addCallback(callback, executor));
        }

        return callbackLifetime;
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
