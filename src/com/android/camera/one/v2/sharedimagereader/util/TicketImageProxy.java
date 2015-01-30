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

package com.android.camera.one.v2.sharedimagereader.util;

import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.sharedimagereader.ticketpool.Ticket;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Combines an {@link ImageProxy} with a {@link Ticket}.
 */
@ThreadSafe
public class TicketImageProxy extends ForwardingImageProxy {
    private final Ticket mTicket;
    private final AtomicBoolean mClosed;

    public TicketImageProxy(ImageProxy image, Ticket ticket) {
        super(image);
        mTicket = ticket;
        mClosed = new AtomicBoolean(false);
    }

    @Override
    public void close() {
        if (mClosed.getAndSet(true)) {
            return;
        }
        // The ticket must be closed *after* the image is closed to avoid a race
        // condition here in which another image is reserved before this one is
        // actually released.
        super.close();
        mTicket.close();
    }
}
