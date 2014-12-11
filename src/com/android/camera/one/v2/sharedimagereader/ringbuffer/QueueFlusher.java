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

/**
 * Flushes an image queue when low priority tickets are to be released.
 */
class QueueFlusher<T> implements TicketPoolPrioritizer.LowPriorityTicketReleaser {
    private BufferQueue<T> mImageQueue;

    public QueueFlusher(BufferQueue<T> imageQueue) {
        mImageQueue = imageQueue;
    }

    /**
     * Flushes all images, freeing up tickets.
     */
    @Override
    public void releaseLowPriorityTickets() {
        while (mImageQueue.peekNext() != null) {
            mImageQueue.discardNext();
        }
    }
}
