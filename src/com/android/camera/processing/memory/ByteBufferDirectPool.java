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

import java.nio.ByteBuffer;

/**
 * Resource pool for large, directly allocated byte buffers. The integer key
 * represents the size of the bytebuffer.
 */
public final class ByteBufferDirectPool extends SimpleLruResourcePool<Integer, ByteBuffer> {
    public ByteBufferDirectPool(int lruSize) {
        super(lruSize);
    }

    @Override
    protected ByteBuffer create(Integer bytes) {
        return ByteBuffer.allocateDirect(bytes);
    }

    @Override
    protected ByteBuffer recycle(Integer integer, ByteBuffer byteBuffer) {
        // Reset byte buffer location and limits
        byteBuffer.rewind();
        byteBuffer.limit(byteBuffer.capacity());
        return byteBuffer;
    }
}
