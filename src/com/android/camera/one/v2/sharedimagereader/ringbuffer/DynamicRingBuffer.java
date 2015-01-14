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
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.sharedimagereader.util.TicketImageProxy;
import com.android.camera.one.v2.sharedimagereader.ticketpool.Ticket;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketProvider;

import javax.annotation.Nonnull;

/**
 * A ring-buffer which uses all of the residual tickets in the provided ticket
 * pool to store as many images as possible.
 * <p>
 * The size of the ring-buffer is dictated by the amount of tickets which can be
 * retrieved from the TicketPool.
 * <p>
 * When the controller is updated with a new image, if no more capacity is
 * available, the last element in the buffer is discarded to attempt to make
 * room for the new image.
 */
class DynamicRingBuffer implements BufferQueueController<ImageProxy> {
    private final TicketProvider mTicketPool;
    private final BufferQueueController<ImageProxy> mImageSequenceController;
    private final BufferQueue<ImageProxy> mImageSequenceConsumer;

    public DynamicRingBuffer(TicketProvider ticketPool,
            BufferQueueController<ImageProxy> imageSequenceController,
            BufferQueue<ImageProxy> imageSequenceConsumer) {
        mTicketPool = ticketPool;
        mImageSequenceController = imageSequenceController;
        mImageSequenceConsumer = imageSequenceConsumer;
    }

    @Override
    public void update(@Nonnull ImageProxy image) {
        // Try to acquire a ticket to expand the ring-buffer and save the image.
        Ticket ticket = mTicketPool.tryAcquire();
        if (ticket == null) {
            // If we cannot expand the ring-buffer, remove the last element
            // (decreasing the size), and then try to increase the size again.
            mImageSequenceConsumer.discardNext();
            ticket = mTicketPool.tryAcquire();
        }
        if (ticket != null) {
            mImageSequenceController.update(new TicketImageProxy(image, ticket));
        } else {
            image.close();
        }
    }

    @Override
    public void close() {
        mImageSequenceController.close();
    }

    @Override
    public boolean isClosed() {
        return mImageSequenceController.isClosed();
    }
}
