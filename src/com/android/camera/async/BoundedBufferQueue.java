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

/**
 * A {@link BufferQueue} of instances of a limited, managed resource. At any given
 * time, the stream and its consumer will own at-most a fixed, finite number of
 * elements, specified by {@link #getCapacity}.
 */
public interface BoundedBufferQueue<T extends AutoCloseable> extends BufferQueue<T> {
    /**
     * @return The total size of the stream. It is the consumer's responsibility
     *         to keep track of how many elements have been acquired and not
     *         released.
     */
    public int getCapacity();
}
