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

package com.android.camera.burst;

import android.support.v4.util.LongSparseArray;

import com.android.camera.async.SafeCloseable;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

import java.util.ArrayList;
import java.util.List;

/**
 * A RingBuffer that is used during burst capture. It takes a
 * {@link EvictionHandler} instance and uses it to evict frames when the ring
 * buffer runs out of capacity.
 */
class RingBuffer<T extends ImageProxy> implements SafeCloseable {
    private final int mMaxCapacity;
    private final EvictionHandler mEvictionHandler;
    private final LongSparseArray<T> mImages = new LongSparseArray<>();

    /**
     * Create a new ring buffer instance.
     *
     * @param maxCapacity the maximum number of images in the ring buffer.
     * @param evictionHandler
     */
    public RingBuffer(int maxCapacity, EvictionHandler evictionHandler) {
        mMaxCapacity = maxCapacity;
        mEvictionHandler = evictionHandler;
    }

    /**
     * Insert an image in the ring buffer, evicting any frames if necessary.
     *
     * @param image the image to be inserted.
     */
    public synchronized void insertImage(T image) {
        long timestamp = image.getTimestamp();
        if (mImages.get(timestamp) != null) {
            image.close();
            return;
        }
        // Add image to ring buffer so it can be closed in case eviction
        // handler throws.
        addImage(image);
        mEvictionHandler.onFrameInserted(timestamp);
        if (mImages.size() > mMaxCapacity) {
            long selectFrameToDrop = mEvictionHandler.selectFrameToDrop();
            removeAndCloseImage(selectFrameToDrop);
            mEvictionHandler.onFrameDropped(selectFrameToDrop);
        }
    }

    /**
     * Returns all images present in the ring buffer.
     */
    public synchronized List<T> getAndRemoveAllImages() {
        List<T> allImages = new ArrayList<>(mImages.size());
        for (int i = 0; i < mImages.size(); i++) {
            allImages.add(mImages.valueAt(i));
        }
        mImages.clear();
        return allImages;
    }

    /**
     * Closes the ring buffer and any images in the ring buffer.
     */
    @Override
    public synchronized void close() {
        for (int i = 0; i < mImages.size(); i++) {
            mImages.valueAt(i).close();
        }
        mImages.clear();
    }

    private synchronized void removeAndCloseImage(long timestampNs) {
        mImages.get(timestampNs).close();
        mImages.remove(timestampNs);
    }

    private synchronized void addImage(T image) {
        mImages.put(image.getTimestamp(), image);
    }
}
