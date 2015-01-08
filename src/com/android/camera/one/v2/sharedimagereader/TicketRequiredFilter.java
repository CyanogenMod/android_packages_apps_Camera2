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

import com.android.camera.async.BufferQueueController;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.sharedimagereader.ticketpool.Ticket;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketProvider;
import com.android.camera.one.v2.sharedimagereader.util.TicketImageProxy;

import javax.annotation.Nonnull;

/**
 * Decorates a BufferQueueController by attaching incoming images with a ticket
 * from a TicketPool. If no ticket can be acquired, the incoming image is
 * immediately closed.
 */
class TicketRequiredFilter implements BufferQueueController<ImageProxy> {
    private final TicketProvider mTicketProvider;
    private final BufferQueueController<ImageProxy> mImageSequence;

    public TicketRequiredFilter(TicketProvider ticketProvider,
                                BufferQueueController<ImageProxy> imageSequence) {
        mTicketProvider = ticketProvider;
        mImageSequence = imageSequence;
    }

    @Override
    public void update(@Nonnull ImageProxy image) {
        Ticket ticket = mTicketProvider.tryAcquire();
        if (ticket == null) {
            image.close();
        } else {
            mImageSequence.update(new TicketImageProxy(image, ticket));
        }
    }

    @Override
    public void close() {
        mImageSequence.close();
    }

    @Override
    public boolean isClosed() {
        return mImageSequence.isClosed();
    }
}
