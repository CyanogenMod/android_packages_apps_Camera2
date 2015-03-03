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
import com.google.common.base.Supplier;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Helper methods for {@link Observable}.
 */
@ParametersAreNonnullByDefault
public class Observables {
    private static final SafeCloseable NOOP_CALLBACK_HANDLE = new SafeCloseable() {
        @Override
        public void close() {
            // Do Nothing.
        }
    };

    private Observables() {
    }

    /**
     * Transforms an observable with a function.
     *
     * @return The transformed observable.
     */
    public static <F, T> Observable<T> transform(final Observable<F> input,
            final Function<F, T> function) {
        return new Observable<T>() {
            @Nonnull
            @Override
            public T get() {
                return function.apply(input.get());
            }

            @CheckReturnValue
            @Nonnull
            @Override
            public SafeCloseable addCallback(Runnable callback, Executor executor) {
                return input.addCallback(callback, executor);
            }
        };
    }

    /**
     * @return An observable which recomputes its output every time an input changes.
     */
    public static <T> Observable<T> transform(final List<? extends Observable<?>> inputs,
                                              final Supplier<T> output) {
        return ObservableCombiner.transform(inputs, output);
    }

    /**
     * Transforms a set of observables with a function.
     *
     * @return The transformed observable.
     */
    public static <F, T> Observable<T> transform(final List<? extends Observable<F>> input,
            Function<List<F>, T> function) {
        return ObservableCombiner.transform(input, function);
    }

    /**
     * @return An observable which has the given constant value.
     */
    @Nonnull
    public static <T> Observable<T> of(final @Nonnull T constant) {
        return new Observable<T>() {
            @Nonnull
            @Override
            public T get() {
                return constant;
            }

            @CheckReturnValue
            @Nonnull
            @Override
            public SafeCloseable addCallback(Runnable callback, Executor executor) {
                return NOOP_CALLBACK_HANDLE;
            }
        };
    }

    @Nonnull
    @CheckReturnValue
    public static <T> SafeCloseable addThreadSafeCallback(final Observable<T> observable,
            final Updatable<T> callback) {
        return observable.addCallback(new Runnable() {
            @Override
            public void run() {
                callback.update(observable.get());
            }
        }, MoreExecutors.sameThreadExecutor());
    }
}
