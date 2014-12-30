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
import com.google.common.base.Supplier;

import java.util.concurrent.Executor;

/**
 * An interface for thread-safe observable objects.
 */
public interface Observable<T> extends Supplier<T> {
    /**
     * Adds the given callback, returning a handle to be closed when the
     * callback must be deregistered.
     *
     * @param callback The callback to add.
     * @param executor The executor on which the callback will be invoked.
     * @return A {@link SafeCloseable} handle to be closed when the callback
     *         must be removed.
     */
    public SafeCloseable addCallback(Callback<T> callback, Executor executor);
}
