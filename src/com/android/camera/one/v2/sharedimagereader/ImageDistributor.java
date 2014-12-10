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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.RefCountedImageProxy;
import com.android.camera.one.v2.core.SingleCloseImageProxy;

/**
 * Distributes incoming images to output {@link BufferQueueController}s
 * according to their timestamp.
 */
public class ImageDistributor {
    /**
     * An input timestamp stream and an output image stream to receive images
     * with timestamps which match those found in the input stream.
     */
    private static class DispatchRecord implements AutoCloseable {
        public final BufferQueue<Long> timestampBufferQueue;
        public final BufferQueueController<ImageProxy> imageStream;

        private DispatchRecord(BufferQueue<Long> timestampBufferQueue,
                               BufferQueueController<ImageProxy> imageStream) {
            this.timestampBufferQueue = timestampBufferQueue;
            this.imageStream = imageStream;
        }

        @Override
        public void close() {
            timestampBufferQueue.close();
            imageStream.close();
        }
    }

    /**
     * Contains pairs mapping {@link BufferQueue}s of timestamps of images to
     * the {@link BufferQueueController} to receive images with those
     * timestamps.
     */
    private final Set<DispatchRecord> mDispatchTable;

    /**
     * A stream to consume timestamps for all images captured by the underlying
     * device. This is used as a kind of clock-signal to indicate when timestamp
     * streams for entries in the {@link #mDispatchTable} are up-to-date.
     */
    private final BufferQueue<Long> mGlobalTimestampBufferQueue;

    /*
     * @param globalTimestampStream A stream of timestamps for every capture
     * processed by the underlying {@link CaptureSession}. This is used to
     * synchronize all of the timestamp streams associated with each added
     * output stream.
     */
    public ImageDistributor(BufferQueue<Long> globalTimestampBufferQueue) {
        mGlobalTimestampBufferQueue = globalTimestampBufferQueue;
        mDispatchTable = Collections.synchronizedSet(new HashSet<DispatchRecord>());
    }

    /**
     * Distributes the image to all added routes according to timestamp. Note
     * that this waits until the global timestamp stream indicates that the next
     * image has been captured to ensure that the timestamp streams for all
     * routes are up-to-date.
     * <p>
     * If interrupted, this will close the image and return.
     * </p>
     * It is assumed that incoming images will have unique, increasing
     * timestamps.
     *
     * @param image The image to distribute.
     */
    public void distributeImage(ImageProxy image) {
        // TODO Profile GC impact, and pool all allocations if necessary &
        // possible.
        final long timestamp = image.getTimestamp();

        // Wait until the global timestamp stream indicates that either the
        // *next* image has been captured, or the stream has been closed. Both
        // of these conditions are sufficient to guarantee that all other
        // timestamp streams should have an entry for the *current* image's
        // timestamp (if the associated image stream needs the image). Note that
        // this assumes that {@link #mGlobalImageTimestamp} and each timestamp
        // stream associated with a {@link DispatchRecord} are updated on the
        // same thread in order.
        try {
            while (mGlobalTimestampBufferQueue.getNext() <= timestamp) {
            }
        } catch (InterruptedException e) {
            image.close();
            return;
        } catch (BufferQueue.BufferQueueClosedException e) {
            // If the stream is closed, then all other timestamp streams must be
            // up-to-date.
        }

        List<BufferQueueController<ImageProxy>> streamsToReceiveImage = new ArrayList<>();
        Set<DispatchRecord> deadRecords = new HashSet<>();

        for (DispatchRecord dispatchRecord : mDispatchTable) {
            if (dispatchRecord.timestampBufferQueue.isClosed() ||
                    dispatchRecord.imageStream.isClosed()) {
                dispatchRecord.close();
                deadRecords.add(dispatchRecord);
            }
            Long requestedImageTimestamp = dispatchRecord.timestampBufferQueue.peekNext();
            if (requestedImageTimestamp == null) {
                continue;
            }
            if (requestedImageTimestamp.longValue() == timestamp) {
                // Discard the value we just looked at.
                dispatchRecord.timestampBufferQueue.discardNext();
                streamsToReceiveImage.add(dispatchRecord.imageStream);
            }
        }

        mDispatchTable.removeAll(deadRecords);

        if (streamsToReceiveImage.isEmpty()) {
            image.close();
            return;
        }

        RefCountedImageProxy sharedImage = new RefCountedImageProxy(image,
                streamsToReceiveImage.size());
        for (BufferQueueController<ImageProxy> outputStream : streamsToReceiveImage) {
            // Wrap shared image to ensure that *each* stream must close the image
            // before the underlying reference count is decremented, regardless
            // of how many times it is closed from each stream.
            ImageProxy singleCloseImage = new SingleCloseImageProxy(sharedImage);
            outputStream.update(singleCloseImage);
        }
    }

    /**
     * Registers the given output image stream as a destination for images with
     * timestamps present in inputTimestampStream. Note that
     * inputTimestampStream is assumed to be synchronized with the global
     * timestamp stream such that it must always contain a timestamp for a
     * requested image before the global timestamp stream provides the timestamp
     * for the *next* image.
     *
     * @param inputTimestampBufferQueue A stream of timestamps of images to be
     *            routed to the given output stream. This should be closed by
     *            the producer when no more images are expected.
     * @param outputStream The image stream on which to add images.
     */
    public void addRoute(BufferQueue<Long> inputTimestampBufferQueue, BufferQueueController<ImageProxy> outputStream) {
        mDispatchTable.add(new DispatchRecord(inputTimestampBufferQueue, outputStream));
    }
}
