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

package com.android.camera.one.v2.sharedimagereader.ringbuffer;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Observable;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketPool;
import com.google.common.util.concurrent.MoreExecutors;

/*
 * Creates a dynamic ring buffer of images.
 * <p>
 * The ring buffer is implicitly defined by an input and an output.
 * <p>
 * The size of the ring-buffer is dictated by how many tickets it can retrieve from the root
 * ticket pool.  As tickets become available, the ring-buffer expands.
 * <p>
 * The ring buffer also provides its own ticket pool.  When tickets are requested from this pool,
 * the ring buffer shrinks.
 * <p>
 * Images which cannot be held in the ring-buffer are released immediately.
 */
public class DynamicRingBufferFactory {
    private final TicketPool mOutputTicketPool;
    private final BufferQueueController<ImageProxy> mRingBufferInput;
    private final BufferQueue<ImageProxy> mRingBufferOutput;

    public DynamicRingBufferFactory(Lifetime lifetime, TicketPool rootTicketPool,
            final Observable<Integer> maxRingBufferSize) {
        final DynamicRingBuffer ringBuffer = new DynamicRingBuffer(rootTicketPool);
        lifetime.add(ringBuffer);
        lifetime.add(maxRingBufferSize.addCallback(new Runnable() {
            @Override
            public void run() {
                ringBuffer.setMaxSize(Math.max(0, maxRingBufferSize.get()));
            }
        }, MoreExecutors.sameThreadExecutor()));
        ringBuffer.setMaxSize(Math.max(0, maxRingBufferSize.get()));

        mOutputTicketPool = ringBuffer;
        mRingBufferInput = ringBuffer;
        mRingBufferOutput = ringBuffer;
    }

    public TicketPool provideTicketPool() {
        return mOutputTicketPool;
    }

    public BufferQueueController<ImageProxy> provideRingBufferInput() {
        return mRingBufferInput;
    }

    public BufferQueue<ImageProxy> provideRingBufferOutput() {
        return mRingBufferOutput;
    }
}
