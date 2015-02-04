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

import com.google.common.base.Supplier;

import java.util.concurrent.Executor;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An interface for thread-safe observable objects.
 */
@ThreadSafe
@ParametersAreNonnullByDefault
public interface Observable<T> extends Supplier<T> {
    /**
     * Adds the given callback to be invoked upon changes, returning a handle to
     * be closed when the callback must be deregistered.
     * <p>
     * Note that the callback may be invoked multiple times even if the value
     * has not actually changed.
     *
     * @param callback The callback to add.
     * @param executor The executor on which the callback will be invoked.
     * @return A handle to be closed when the callback must be removed.
     */
    @Nonnull
    @CheckReturnValue
    public SafeCloseable addCallback(Runnable callback, Executor executor);

    /**
     * @return The current/latest value.
     */
    @Nonnull
    @Override
    public T get();
}
