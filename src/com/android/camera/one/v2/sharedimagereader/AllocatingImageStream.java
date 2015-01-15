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

package com.android.camera.one.v2.sharedimagereader;

import android.view.Surface;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageDistributor;
import com.android.camera.one.v2.sharedimagereader.ticketpool.ReservableTicketPool;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketPool;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An ImageQueueCaptureStream with a fixed-capacity which can reserve space
 * ahead of time via {@link #allocate}.
 */
class AllocatingImageStream extends ImageStreamImpl {
    private final int mCapacity;
    private final ReservableTicketPool mTicketPool;
    /**
     * True if capacity for the stream has has been allocated.
     */
    private AtomicBoolean mAllocated;
    private AtomicBoolean mClosed;

    /**
     * @param capacity The capacity to reserve on first bind.
     * @param ticketPool The ticket pool to reserve capacity in. Note that this
     *            class takes ownership of this ticket pool.
     * @param imageStream The output to the images queue.
     * @param imageStreamController The input to the image queue.
     * @param imageDistributor The image distributor to register with on bind()
     *            such that the image queue begins receiving images.
     * @param surface
     */
    public AllocatingImageStream(
            int capacity, ReservableTicketPool ticketPool,
            BufferQueue<ImageProxy> imageStream,
            BufferQueueController<ImageProxy> imageStreamController,
            ImageDistributor imageDistributor, Surface surface) {
        super(imageStream, imageStreamController, imageDistributor, surface);
        mCapacity = capacity;
        mTicketPool = ticketPool;
        mAllocated = new AtomicBoolean(false);
        mClosed = new AtomicBoolean(false);
    }

    public void allocate() throws InterruptedException, ResourceAcquisitionFailedException {
        if (!mAllocated.getAndSet(true)) {
            try {
                mTicketPool.reserveCapacity(mCapacity);
            } catch (TicketPool.NoCapacityAvailableException e) {
                throw new ResourceAcquisitionFailedException(e);
            }
        }
    }

    @Override
    public Surface bind(BufferQueue<Long> timestamps) throws InterruptedException,
            ResourceAcquisitionFailedException {
        allocate();
        return super.bind(timestamps);
    }

    @Override
    public void close() {
        if (mClosed.getAndSet(true)) {
            return;
        }
        mTicketPool.close();
        super.close();
    }
}
