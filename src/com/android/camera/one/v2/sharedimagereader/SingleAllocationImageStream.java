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

import java.util.concurrent.atomic.AtomicBoolean;

import android.view.Surface;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageDistributor;
import com.android.camera.one.v2.sharedimagereader.ticketpool.ReservableTicketPool;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketPool;

/**
 * An ImageQueueCaptureStream with a fixed-capacity which reserves space on
 * first bind().
 */
class SingleAllocationImageStream extends ImageStreamImpl {
    private final int mCapacity;
    private final ReservableTicketPool mTicketPool;
    /**
     * True if the stream has been bound at least once.
     */
    private boolean mBound;
    private boolean mClosed;

    public SingleAllocationImageStream(
            int capacity, ReservableTicketPool ticketPool,
            BufferQueue<ImageProxy> imageStream,
            BufferQueueController<ImageProxy> imageStreamController,
            ImageDistributor imageDistributor, Surface surface) {
        super(imageStream, imageStreamController, imageDistributor, surface);
        mCapacity = capacity;
        mTicketPool = ticketPool;
        mBound = false;
        mClosed = false;
    }

    @Override
    public synchronized Surface bind(BufferQueue<Long> timestamps) throws InterruptedException,
            ResourceAcquisitionFailedException {
        if (mClosed) {
            throw new ResourceAcquisitionFailedException();
        }
        if (!mBound) {
            mBound = true;
            try {
                mTicketPool.reserveCapacity(mCapacity);
            } catch (TicketPool.NoCapacityAvailableException e) {
                throw new ResourceAcquisitionFailedException(e);
            }
        }

        return super.bind(timestamps);
    }

    @Override
    public synchronized void close() {
        if (mClosed) {
            return;
        }
        mClosed = true;
        mTicketPool.close();
        super.close();
    }
}
