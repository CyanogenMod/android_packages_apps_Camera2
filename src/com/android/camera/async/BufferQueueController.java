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

import javax.annotation.Nonnull;

/**
 * An output stream of objects which can be closed from either the producer or
 * the consumer.
 */
public interface BufferQueueController<T> extends Updatable<T>, SafeCloseable {
    /**
     * Adds the given element to the stream. Streams must support calling this
     * even after closed.
     *
     * @param element The element to add.
     */
    @Override
    public void update(@Nonnull T element);

    /**
     * Closes the stream. Implementations must tolerate multiple calls to close.
     */
    @Override
    public void close();

    /**
     * @return Whether the stream is closed.
     */
    public boolean isClosed();
}
