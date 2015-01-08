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

import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * TODO Replace with Guava's com.google.common.util.concurrent.Futures once we
 * can build with v. 15+
 */
public class Futures2 {
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
}
