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

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import android.media.ImageReader;
import android.view.Surface;

import com.android.camera.one.v2.async.RefCountedBufferQueueController;
import com.android.camera.one.v2.async.BufferQueue;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.CaptureStream;

/**
 * Enables the creation of multiple logical image streams, each with their own
 * guaranteed capacity, over a single ImageReader.
 */
public class SharedImageReader implements AutoCloseable {
    public class ImageCaptureBufferQueue implements CaptureStream, BufferQueue<ImageProxy> {
        private final int mCapacity;
        private final BoundedImageBufferQueue mImageStream;
        private final RefCountedBufferQueueController<ImageProxy> mStreamSource;

        /**
         * True if the stream has been bound at least once.
         */
        private final AtomicBoolean mBound;

        private ImageCaptureBufferQueue(int capacity) {
            mCapacity = capacity;
            mBound = new AtomicBoolean(false);
            mImageStream = new BoundedImageBufferQueue(new BoundedImageBufferQueue
                    .ImageTicketSink() {
                        @Override
                        public void onImageTicketReleased() {
                            mGlobalTicketPool.release();
                        }
                    });
            mStreamSource = new RefCountedBufferQueueController<>(mImageStream);
        }

        @Override
        public synchronized Surface bind(BufferQueue<Long> timestamps) throws InterruptedException {
            // Acquire some tickets to create a new {@link
            // BoundedImageStream}
            boolean previouslyBound = mBound.getAndSet(true);
            if (!previouslyBound) {
                // TODO Maybe throw something instead of deadlock if mCapacity > mMaxHeldImages
                mGlobalTicketPool.acquire(mCapacity);
                mImageStream.addCapacity(mCapacity);
            }
            mStreamSource.addRef();
            // Reference counting is necessary since mImageDistributor will
            // close() mImageStream when timestamps is closed and no more images
            // are expected to be routed to that output image stream.
            mImageDistributor.addRoute(timestamps, mStreamSource);
            return mSurface;
        }

        @Override
        public void close() {
            mImageStream.close();
        }

        @Override
        public ImageProxy getNext() throws InterruptedException, BufferQueueClosedException {
            return mImageStream.getNext();
        }

        @Override
        public ImageProxy getNext(long timeout, TimeUnit unit) throws InterruptedException,
                TimeoutException, BufferQueueClosedException {
            return mImageStream.getNext(timeout, unit);
        }

        @Override
        public ImageProxy peekNext() {
            return mImageStream.peekNext();
        }

        @Override
        public void discardNext() {
            mImageStream.discardNext();
        }

        @Override
        public boolean isClosed() {
            return mImageStream.isClosed();
        }
    }

    /**
     * The maximum number of images which may be held open for a prolonged
     * period of time.
     */
    private final int mMaxHeldImages;
    /**
     * A semaphore with at-most mMaxHeldImages permits.<br>
     * This stores the global pool of tickets which can be used to logically
     * reserve frames from the underlying ImageReader. Having a ticket implies
     * the ability to hang on to an Image for an indefinite period of time.
     */
    private final Semaphore mGlobalTicketPool;
    /**
     * The {@link ImageReader} surface.
     */
    private final Surface mSurface;

    private final ImageDistributor mImageDistributor;

    private boolean mClosed;

    /**
     * @param surface The {@link Surface} of the underlying {@link ImageReader}.
     * @param maxOpenImages The maximum number of images which may be acquired
     *            from the {@link ImageReader} at a time.
     */
    public SharedImageReader(Surface surface, int maxOpenImages, ImageDistributor
            imageDistributor) {
        mSurface = surface;
        mMaxHeldImages = maxOpenImages;
        mGlobalTicketPool = new Semaphore(mMaxHeldImages);
        mImageDistributor = imageDistributor;
        mClosed = true;
    }

    /**
     * @return The maximum number of images which may be acquired at any time.
     *         This limits the aggregate capacity of all
     *         {@link com.android.camera.one.v2.sharedimagereader.SharedImageReader.ImageCaptureBufferQueue}s in use at any given time.
     */
    public int getMaxAllocatableImageCount() {
        return mMaxHeldImages;
    }

    /**
     * @return The current number of images which may be reserved for
     *         {@link com.android.camera.one.v2.sharedimagereader.SharedImageReader.ImageCaptureBufferQueue}s.
     */
    public int getCurrentAllocatableImageCount() {
        return mGlobalTicketPool.availablePermits();
    }

    /**
     * Waits until all images are closed.
     *
     * @throws InterruptedException
     */
    @Override
    public void close() throws InterruptedException {
        mGlobalTicketPool.acquire(mMaxHeldImages);
    }

    /**
     * Creates a logical bounded stream of images with the specified capacity.
     * Note that the ticket pool will be allocated/acquired the first time
     * {@link CaptureStream#bind(BufferQueue)} is called, but it will be reused
     * on subsequent invocations. So, for example, the stream provider may be
     * attached to multiple
     * {@link com.android.camera.one.v2.core.RequestBuilder}s and the images for
     * those requests will share the same ticket pool with size specified by the
     * given capacity.
     *
     * @param capacity The maximum number of images which can be simultaneously
     *            held from any of the BoundedImageStreams produced by the
     *            resulting provider.
     */
    public ImageCaptureBufferQueue createStream(final int capacity) {
        return new ImageCaptureBufferQueue(capacity);
    }
}
