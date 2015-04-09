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

package com.android.camera.processing.memory;

import com.android.camera.async.SafeCloseable;

import javax.annotation.Nullable;

/**
 * Pool for and/or creating or reusing expensive resources.
 */
public interface LruResourcePool<TKey, TValue> {
    /**
     * Returns a wrapped reference to a resource.
     *
     * @param key size of memory.
     * @return T object representing a reusable n-bytes of memory.
     */
    public Resource<TValue> acquire(TKey key);

    /**
     * Closeable resource object that will release the underlying object back to the
     * source resource pool. A resource may be released or closed multiple times,
     * and calls to get will return null if the resource has already been released.
     */
    public interface Resource<T> extends SafeCloseable {
        /**
         * Get the underlying resource. This will return null if the resource has been
         * closed and returned to the pool.
         *
         * @return the resource represented by this instance.
         */
        @Nullable
        public T get();
    }
}
