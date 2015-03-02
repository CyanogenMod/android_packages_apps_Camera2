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

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

import javax.annotation.Nullable;

/**
 * TODO Replace with Guava's com.google.common.util.concurrent.Futures once we
 * can build with v. 15+
 */
public class Futures2 {
    /**
     * 2 parameter async function for joined async results.
     */
    public interface AsyncFunction2<T1, T2, TResult> {
        ListenableFuture<TResult> apply(T1 value1, T2 value2) throws Exception;
    }

    /**
     * 3 parameter async function for joined async results.
     */
    public interface AsyncFunction3<T1, T2, T3, TResult> {
        ListenableFuture<TResult> apply(T1 value1, T2 value2, T3 value3) throws Exception;
    }

    /**
     * 2 parameter function for joining multiple results into a single value.
     */
    public interface Function2<T1, T2, TResult> {
        TResult apply(T1 value1, T2 value2);
    }

    /**
     * 3 parameter function for joining multiple results into a single value.
     */
    public interface Function3<T1, T2, T3, TResult> {
        TResult apply(T1 value1, T2 value2, T3 value3);
    }

    private Futures2() {
    }

    /**
     * Creates a new ListenableFuture whose result is set from the supplied
     * future when it completes. Cancelling the supplied future will also cancel
     * the returned future, but cancelling the returned future will have no
     * effect on the supplied future.
     */
    public static <T> ListenableFuture<T> nonCancellationPropagating(
            final ListenableFuture<T> future) {
        return new ForwardingListenableFuture.SimpleForwardingListenableFuture<T>(future) {
            @Override
            public boolean cancel(boolean mayInterruptIfNecessary) {
                return false;
            }
        };
    }

    /**
     * Create a new joined future from two existing futures and a joining function
     * that combines the resulting outputs of the previous functions into a single
     * result. The resulting future will fail if any of the dependent futures also
     * fail.
     */
    public static <T1, T2, TResult> ListenableFuture<TResult> joinAll(
          final ListenableFuture<T1> f1,
          final ListenableFuture<T2> f2,
          final AsyncFunction2<T1, T2, TResult> fn) {
        ListenableFuture<?>[] futures = new ListenableFuture<?>[2];

        futures[0] = f1;
        futures[1] = f2;

        // Futures.allAsList is used instead of Futures.successfulAsList because
        // allAsList will propagate the failures instead of null values to the
        // parameters of the supplied function.
        ListenableFuture<List<Object>> result = Futures.allAsList(futures);
        return Futures.transform(result, new AsyncFunction<List<Object>, TResult>() {
            @Override
            public ListenableFuture<TResult> apply(@Nullable List<Object> list) throws Exception {
                T1 value1 = (T1) list.get(0);
                T2 value2 = (T2) list.get(1);

                return fn.apply(value1, value2);
            }
        });
    }

    /**
     * Create a new joined future from two existing futures and an async function
     * that combines the resulting outputs of the previous functions into a single
     * result. The resulting future will fail if any of the dependent futures also
     * fail.
     */
    public static <T1, T2, TResult> ListenableFuture<TResult> joinAll(
          final ListenableFuture<T1> f1,
          final ListenableFuture<T2> f2,
          final Function2<T1, T2, TResult> fn) {
        return joinAll(f1, f2, new ImmediateAsyncFunction2<>(fn));
    }

    /**
     * Create a new joined future from three existing futures and a joining function
     * that combines the resulting outputs of the previous functions into a single
     * result. The resulting future will fail if any of the dependent futures also
     * fail.
     */
    public static <T1, T2, T3, TResult> ListenableFuture<TResult> joinAll(
          final ListenableFuture<T1> f1,
          final ListenableFuture<T2> f2,
          final ListenableFuture<T3> f3,
          final AsyncFunction3<T1, T2, T3, TResult> fn) {
        ListenableFuture<?>[] futures = new ListenableFuture<?>[3];

        futures[0] = f1;
        futures[1] = f2;
        futures[2] = f3;

        // Futures.allAsList is used instead of Futures.successfulAsList because
        // allAsList will propagate the failures instead of null values to the
        // parameters of the supplied function.
        ListenableFuture<List<Object>> result = Futures.allAsList(futures);
        return Futures.transform(result, new AsyncFunction<List<Object>, TResult>() {
            @Override
            public ListenableFuture<TResult> apply(@Nullable List<Object> list) throws Exception {
                T1 value1 = (T1) list.get(0);
                T2 value2 = (T2) list.get(1);
                T3 value3 = (T3) list.get(2);

                return fn.apply(value1, value2, value3);
            }
        });
    }

    /**
     * Create a new joined future from three existing futures and an async function
     * that combines the resulting outputs of the previous functions into a single
     * result. The resulting future will fail if any of the dependent futures also
     * fail.
     */
    public static <T1, T2, T3, TResult> ListenableFuture<TResult> joinAll(
          final ListenableFuture<T1> f1,
          final ListenableFuture<T2> f2,
          final ListenableFuture<T3> f3,
          final Function3<T1, T2, T3, TResult> fn) {
        return joinAll(f1, f2, f3, new ImmediateAsyncFunction3<>(fn));
    }

    /**
     * Wrapper class for turning a Function2 into an AsyncFunction2 by returning
     * an immediate future when the function is applied.
     */
    private static final class ImmediateAsyncFunction2<T1, T2, TResult> implements
          AsyncFunction2<T1, T2, TResult> {
        private final Function2<T1, T2, TResult> mFn;

        public ImmediateAsyncFunction2(Function2<T1, T2, TResult> fn) {
            mFn = fn;
        }

        @Override
        public ListenableFuture<TResult> apply(T1 value1, T2 value2) throws Exception {
            return Futures.immediateFuture(mFn.apply(value1, value2));
        }
    }

    /**
     * Wrapper class for turning a Function3 into an AsyncFunction3 by returning
     * an immediate future when the function is applied.
     */
    private static final class ImmediateAsyncFunction3<T1, T2, T3, TResult> implements
          AsyncFunction3<T1, T2, T3, TResult> {
        private final Function3<T1, T2, T3, TResult> mFn;

        public ImmediateAsyncFunction3(Function3<T1, T2, T3, TResult> fn) {
            mFn = fn;
        }

        @Override
        public ListenableFuture<TResult> apply(T1 value1, T2 value2, T3 value3) throws Exception {
            return Futures.immediateFuture(mFn.apply(value1, value2, value3));
        }
    }
}
