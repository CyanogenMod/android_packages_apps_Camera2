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

import static com.android.camera.one.v2.core.ResponseListeners.forFinalMetadata;
import static com.android.camera.one.v2.core.ResponseListeners.forTimestamps;

import com.android.camera.async.HandlerFactory;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Observable;
import com.android.camera.async.Observables;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;
import com.android.camera.one.v2.core.ResponseListener;
import com.android.camera.one.v2.core.ResponseListeners;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageDistributor;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageDistributorFactory;
import com.android.camera.one.v2.sharedimagereader.imagedistributor.ImageStream;
import com.android.camera.one.v2.sharedimagereader.metadatasynchronizer.MetadataPool;
import com.android.camera.one.v2.sharedimagereader.metadatasynchronizer.MetadataPoolFactory;
import com.android.camera.one.v2.sharedimagereader.ringbuffer.DynamicRingBufferFactory;
import com.android.camera.one.v2.sharedimagereader.ticketpool.FiniteTicketPool;
import com.android.camera.one.v2.sharedimagereader.ticketpool.TicketPool;

/**
 * Like {@link SharedImageReaderFactory}, but provides a single
 * {@link ImageStream} with a dynamic capacity which changes depending on demand
 * from the {@link ManagedImageReader}.
 */
public class ZslSharedImageReaderFactory {
    private final ManagedImageReader mSharedImageReader;
    private final ImageStream mZslCaptureStream;
    private final MetadataPool mMetadataPool;
    private final Observable<Integer> mAvailableImageCount;
    private final ResponseListener mResponseListener;

    /**
     * @param lifetime The lifetime of the SharedImageReader, and other
     *            components, to produce. Note that this may be shorter than the
     *            lifetime of the provided ImageReader.
     * @param imageReader The ImageReader to wrap. Note that this can outlive
     *            the resulting SharedImageReader instance.
     * @param handlerFactory Used for create handler threads on which to receive
     *            callbacks from the platform.
     * @param maxRingBufferSize Limits the size of the ring-buffer. This reduces
     *            steady-state memory consumption since ImageReader images are
     *            allocated on-demand, so no more than maxRingBufferSize + 2
     *            images are guaranteed to have to be allocated, as opposed to
     *            imageReader.getMaxImages().
     */
    public ZslSharedImageReaderFactory(Lifetime lifetime, ImageReaderProxy imageReader,
            HandlerFactory handlerFactory, int maxRingBufferSize) {
        ImageDistributorFactory imageDistributorFactory = new ImageDistributorFactory(lifetime,
                imageReader, handlerFactory);
        ImageDistributor imageDistributor = imageDistributorFactory.provideImageDistributor();
        Updatable<Long> globalTimestampQueue = imageDistributorFactory
                .provideGlobalTimestampCallback();

        // TODO Try using 1 instead.
        // Leave 2 ImageReader Images available to allow ImageDistributor and
        // the camera system to have some slack to work with.
        TicketPool rootTicketPool = new FiniteTicketPool(imageReader.getMaxImages() - 2);

        DynamicRingBufferFactory ringBufferFactory = new DynamicRingBufferFactory(
                new Lifetime(lifetime), rootTicketPool, Observables.of(maxRingBufferSize));

        MetadataPoolFactory metadataPoolFactory = new MetadataPoolFactory(
                ringBufferFactory.provideRingBufferInput());

        mZslCaptureStream = new ImageStreamImpl(
                ringBufferFactory.provideRingBufferOutput(),
                metadataPoolFactory.provideImageQueue(),
                imageDistributor, imageReader.getSurface());

        mMetadataPool = metadataPoolFactory.provideMetadataPool();

        mSharedImageReader = new ManagedImageReader(
                new Lifetime(lifetime), ringBufferFactory.provideTicketPool(),
                imageReader.getSurface(), imageDistributor);

        mAvailableImageCount = ringBufferFactory.provideTicketPool().getAvailableTicketCount();

        // Create a ResponseListener which updates the global timestamp queue
        // and the metadata callback.
        mResponseListener = ResponseListeners.forListeners(
                forTimestamps(globalTimestampQueue),
                forFinalMetadata(metadataPoolFactory.provideMetadataCallback()));
    }

    public ManagedImageReader provideSharedImageReader() {
        return mSharedImageReader;
    }

    public ResponseListener provideGlobalResponseListener() {
        return mResponseListener;
    }

    public ImageStream provideZSLStream() {
        return mZslCaptureStream;
    }

    public MetadataPool provideMetadataPool() {
        return mMetadataPool;
    }

    public Observable<Integer> provideAvailableImageCount() {
        return mAvailableImageCount;
    }
}
