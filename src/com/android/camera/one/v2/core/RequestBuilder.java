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

package com.android.camera.one.v2.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.view.Surface;

import com.android.camera.async.BufferQueue;
import com.android.camera.async.ConcurrentBufferQueue;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.CaptureRequestBuilderProxy;
import com.android.camera.one.v2.camera2proxy.CaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.common.TimestampResponseListener;

/**
 * Conveniently builds {@link Request}s.
 */
public class RequestBuilder {
    public static interface Factory {
        /**
         * Creates a new RequestBuilder.
         *
         * @param templateType See
         *            {@link android.hardware.camera2.CameraDevice#createCaptureRequest}
         */
        public RequestBuilder create(int templateType) throws CameraAccessException;
    }

    private static class UnregisteredStreamProvider implements RequestImpl
            .Allocation {
        private final CaptureStream mCaptureStream;
        private final BufferQueue<Long> mTimestampQueue;
        private final AtomicBoolean mAllocated;
        private final CaptureRequestBuilderProxy mBuilderProxy;

        private UnregisteredStreamProvider(CaptureStream captureStream,
                BufferQueue<Long> timestampQueue,
                CaptureRequestBuilderProxy builderProxy) {
            mCaptureStream = captureStream;
            mTimestampQueue = timestampQueue;
            mAllocated = new AtomicBoolean(false);
            mBuilderProxy = builderProxy;
        }

        public void allocate() throws InterruptedException, ResourceAcquisitionFailedException {
            mBuilderProxy.addTarget(mCaptureStream.bind(mTimestampQueue));
        }

        @Override
        public void abort() {
            mTimestampQueue.close();
        }
    }

    private static class RequestImpl implements Request {
        private static interface Allocation {
            public void allocate() throws InterruptedException,
                    ResourceAcquisitionFailedException;

            public void abort();
        }

        private final CaptureRequestBuilderProxy mCaptureRequestBuilder;
        private final List<Allocation> mAllocations;
        private final ResponseListener mResponseListener;

        public RequestImpl(CaptureRequestBuilderProxy builder, List<Allocation> allocations,
                ResponseListener responseListener) {
            mCaptureRequestBuilder = builder;
            mAllocations = allocations;
            mResponseListener = responseListener;
        }

        @Override
        public CaptureRequestBuilderProxy allocateCaptureRequest() throws InterruptedException,
                ResourceAcquisitionFailedException {
            for (Allocation allocation : mAllocations) {
                allocation.allocate();
            }
            return mCaptureRequestBuilder;
        }

        @Override
        public ResponseListener getResponseListener() {
            return mResponseListener;
        }

        @Override
        public void abort() {
            for (Allocation allocation : mAllocations) {
                allocation.abort();
            }
        }
    }

    private final CaptureRequestBuilderProxy mBuilder;

    private final List<RequestImpl.Allocation> mAllocations;

    /**
     * The set of ResponseListeners to dispatch to for all updates.
     */
    private final Set<ResponseListener> mResponseListeners;

    /**
     * @param builder The capture request builder to use.
     */
    public RequestBuilder(CaptureRequestBuilderProxy builder) {
        mBuilder = builder;
        mAllocations = new ArrayList<>();
        mResponseListeners = new HashSet<>();
    }

    /**
     * Adds the given response listener. Duplicate listeners are only added
     * once.
     *
     * @See {@link ResponseListeners}
     *
     * @param listener the listener to add.
     */
    public void addResponseListener(ResponseListener listener) {
        mResponseListeners.add(listener);
    }

    /**
     * Sets the given key-value pair.
     *
     * @see {@link CaptureRequest.Builder#set}.
     */
    public <T> void setParam(CaptureRequest.Key<T> key, T value) {
        mBuilder.set(key, value);
    }

    /**
     * Adds the given {@link CaptureStream} as an output target. Note that the
     * {@link Surface} associated with the given {@link CaptureStream} should be
     * one of of the surfaces added to the
     * {@link android.hardware.camera2.CameraCaptureSession} which the built
     * {@link Request} will be sent to.
     *
     * @param captureStream
     */
    public void addStream(CaptureStream captureStream) {
        ConcurrentBufferQueue<Long> timestamps = new ConcurrentBufferQueue<>();

        mAllocations.add(new UnregisteredStreamProvider(captureStream,
                timestamps, mBuilder));

        mResponseListeners.add(ResponseListeners.forTimestamps(timestamps));
    }

    /**
     * Builds a new {@link Request} based on the current state of the builder.
     * The builder should not be reused after this is called.
     *
     * @return A new {@link Request} based on the current state of the builder.
     */
    public Request build() {
        return new RequestImpl(mBuilder, mAllocations,
                new ResponseListenerBroadcaster(new ArrayList<>(mResponseListeners)));
    }

}
