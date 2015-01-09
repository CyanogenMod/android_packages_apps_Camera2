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
import com.google.common.base.Function;

import java.util.concurrent.Executor;

/**
 * Helper methods for {@link Observable}.
 */
public class Observables {
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
            @Override
            public T get() {
                return function.apply(input.get());
            }

            @Override
            public SafeCloseable addCallback(final Callback<T> callback, Executor executor) {
                return input.addCallback(new Callback<F>() {
                    @Override
                    public void onCallback(F result) {
                        callback.onCallback(function.apply(result));
                    }
                }, executor);
            }
        };
    }

    /**
     * @return An observable which has the given constant value.
     */
    public static <T> Observable<T> of(final T constant) {
        return new Observable<T>() {
            @Override
            public T get() {
                return constant;
            }

            @Override
            public SafeCloseable addCallback(Callback<T> callback, Executor executor) {
                return new SafeCloseable() {
                    @Override
                    public void close() {
                    }
                };
            }
        };
    }
}
