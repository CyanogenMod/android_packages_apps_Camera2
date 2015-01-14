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

import com.android.camera.async.BufferQueue;
import com.android.camera.async.Lifetime;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;
import com.android.camera.one.v2.common.TimestampResponseListener;
import com.android.camera.one.v2.common.TotalCaptureResultResponseListener;
import com.android.camera.one.v2.core.RequestBuilder;
import com.android.camera.one.v2.core.RequestTemplate;
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
 * from the {@link ImageStreamFactory}.
 */
public class ZslSharedImageReaderFactory {
    private final ImageStreamFactory mSharedImageReader;
    private final ImageStream mZslCaptureStream;
    private final MetadataPool mMetadataPool;
    private final RequestTemplate mRequestTemplate;

    /**
     * @param lifetime The lifetime of the SharedImageReader, and other
     *            components, to produce. Note that this may be shorter than the
     *            lifetime of the provided ImageReader.
     * @param imageReader The ImageReader to wrap. Note that this can outlive
     *            the resulting SharedImageReader instance.
     */
    public ZslSharedImageReaderFactory(Lifetime lifetime, ImageReaderProxy imageReader, RequestBuilder
            .Factory rootRequestTemplate) {
        ImageDistributorFactory imageDistributorFactory = new ImageDistributorFactory(lifetime,
                imageReader);
        ImageDistributor imageDistributor = imageDistributorFactory.provideImageDistributor();
        Updatable<Long> globalTimestampQueue = imageDistributorFactory
                .provideGlobalTimestampCallback();

        // TODO Try using 1 instead.
        TicketPool rootTicketPool = new FiniteTicketPool(imageReader.getMaxImages() - 2);

        DynamicRingBufferFactory ringBufferFactory = new DynamicRingBufferFactory(
                new Lifetime(lifetime), rootTicketPool);

        MetadataPoolFactory metadataPoolFactory = new MetadataPoolFactory(
                ringBufferFactory.provideRingBufferInput());

        mZslCaptureStream = new ImageStreamImpl(
                ringBufferFactory.provideRingBufferOutput(),
                metadataPoolFactory.provideImageQueue(),
                imageDistributor, imageReader.getSurface());

        mMetadataPool = metadataPoolFactory.provideMetadataPool();

        mSharedImageReader = new ImageStreamFactory(
                new Lifetime(lifetime), ringBufferFactory.provideTicketPool(),
                imageReader.getSurface(), imageDistributor);

        mRequestTemplate = new RequestTemplate(rootRequestTemplate);
        mRequestTemplate.addStream(mZslCaptureStream);
        mRequestTemplate.addResponseListener(new TimestampResponseListener(globalTimestampQueue));
        mRequestTemplate.addResponseListener(new TotalCaptureResultResponseListener(
                metadataPoolFactory.provideMetadataCallback()));
    }

    public ImageStreamFactory provideSharedImageReader() {
        return mSharedImageReader;
    }

    public BufferQueue<ImageProxy> provideZSLCaptureStream() {
        return mZslCaptureStream;
    }

    public MetadataPool provideMetadataPool() {
        return mMetadataPool;
    }

    public RequestBuilder.Factory provideRequestTemplate() {
        return mRequestTemplate;
    }
}
