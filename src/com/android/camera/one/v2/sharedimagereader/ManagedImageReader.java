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

import android.media.ImageReader;
import android.view.Surface;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.BufferQueueController;
import com.android.camera.async.ConcurrentBufferQueue;
import com.android.camera.async.Lifetime;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.core.CaptureStream;
import com.android.camera.one.v2.core.FrameServer;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.ResourceAcquisitionFailedException;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageDistributor;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageStream;
import com.android.camera.one.v2.sharedimagereader.ticketpool.ReservableTicketPool;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketPool;
import com.android.camera.one.v2.sharedimagereader.util.ImageCloser;

/**
 * Builds {@link CaptureStream}s which can share the same underlying
 * {@link ImageReader}.
 * <p>
 * Example usage:
 *
 * <pre>
 * RequestBuilder builder = ...;
 *
 * // Create a stream which holds 3 images.
 * ImageStream stream = managedImageReader.createStream(3);
 *
 * builder.addStream(stream);
 * builder.setParam(...);
 *
 * frameServer.sendRequest(builder.build());
 * frameServer.sendRequest(builder.build());
 * frameServer.sendRequest(builder.build());
 *
 * // Synchronously receive the images as they arrive...
 * ImageProxy image1 = stream.getNext();
 * ImageProxy image2 = stream.getNext();
 * ImageProxy image3 = stream.getNext();
 *
 * // Close the stream when no more images are expected.
 * stream.close();
 *
 * // Process the Images...
 *
 * // Close the images.
 * image1.close();
 * image2.close();
 * image3.close();
 * </pre>
 */
public class ManagedImageReader {
    private final Lifetime mLifetime;
    private final TicketPool mTicketPool;
    /**
     * The {@link ImageReader} surface.
     */
    private final Surface mSurface;

    private final ImageDistributor mImageDistributor;

    /**
     * @param lifetime
     * @param ticketPool
     * @param surface
     * @param imageDistributor
     */
    public ManagedImageReader(Lifetime lifetime, TicketPool ticketPool, Surface surface,
                              ImageDistributor imageDistributor) {
        mLifetime = lifetime;
        mTicketPool = ticketPool;
        mSurface = surface;
        mImageDistributor = imageDistributor;
    }

    private AllocatingImageStream createUnallocatedStream(int capacity) {
        ReservableTicketPool ticketPool = new ReservableTicketPool(mTicketPool);
        mLifetime.add(ticketPool);

        ConcurrentBufferQueue<ImageProxy> imageStream = new ConcurrentBufferQueue<>(new
                ImageCloser());
        mLifetime.add(imageStream);

        BufferQueueController<ImageProxy> imageStreamController = new
                TicketRequiredFilter(ticketPool, imageStream);
        mLifetime.add(imageStreamController);

        AllocatingImageStream stream = new AllocatingImageStream(capacity,
                ticketPool, imageStream, imageStreamController, mImageDistributor, mSurface);
        mLifetime.add(stream);

        return stream;
    }

    /**
     * Creates a logical bounded stream of images with the specified capacity.
     * Note that the required image space will be allocated/acquired the first
     * time {@link CaptureStream#bind(BufferQueue)} is called, but it will be
     * reused on subsequent invocations. So, for example, the stream provider
     * may be attached to multiple {@link RequestBuilder}s and the images for
     * those requests will share the same ticket pool with size specified by the
     * given capacity.
     *
     * @param capacity The maximum number of images which can be simultaneously
     *            held from the resulting image queue before images are dropped.
     */
    public ImageStream createStream(int capacity) {
        return createUnallocatedStream(capacity);
    }

    /**
     * Creates a logical bounded stream of images with the specified capacity,
     * blocking until the required capacity can be reserved.
     * <p>
     * Note: Unlike using {@link #createStream} with a {@link FrameServer}, this
     * method cannot guarantee non-circular-wait to eliminate the possibility of
     * deadlock. FrameServer's session lock has already been acquired. Thus,
     * there is a possibility of deadlock if callers have not already acquired
     * an exclusive session before calling this method.
     * <p>
     * TODO Use a CycleDetectingLockFactory to detect deadlock-prone misuse of
     * this method, and document the required order of resource acquisition at a
     * higher-level.
     *
     * @param capacity The maximum number of images which can be simultaneously
     *            held from the resulting image queue before images are dropped.
     */
    public ImageStream createPreallocatedStream(int capacity) throws InterruptedException,
            ResourceAcquisitionFailedException {
        AllocatingImageStream stream = createUnallocatedStream(capacity);
        try {
            stream.allocate();
            return stream;
        } catch (Exception e) {
            // If allocation failed, close the stream before returning.
            stream.close();
            throw e;
        }
    }
}
