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

package com.android.camera.one.v2.sharedimagereader.metadatasynchronizer;

import android.hardware.camera2.CaptureResult;

import com.android.camera.async.Futures2;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.GuardedBy;

@ParametersAreNonnullByDefault
public class MetadataPoolImpl implements Updatable<TotalCaptureResultProxy>, MetadataPool {
    @GuardedBy("mLock")
    private final Map<Long, SettableFuture<TotalCaptureResultProxy>> mMetadataFutures;
    private final Object mLock;

    public MetadataPoolImpl() {
        mMetadataFutures = new HashMap<>();
        mLock = new Object();
    }

    @VisibleForTesting
    public int getMapSize() {
        synchronized (mLock) {
            return mMetadataFutures.size();
        }
    }

    private SettableFuture<TotalCaptureResultProxy> getOrCreateFuture(long timestamp) {
        SettableFuture<TotalCaptureResultProxy> metadataFuture;
        synchronized (mLock) {
            if (!mMetadataFutures.containsKey(timestamp)) {
                mMetadataFutures.put(timestamp, SettableFuture.<TotalCaptureResultProxy> create());
            }

            metadataFuture = mMetadataFutures.get(timestamp);
        }
        return metadataFuture;
    }

    @Nonnull
    @Override
    public ListenableFuture<TotalCaptureResultProxy> removeMetadataFuture(final long timestamp) {
        ListenableFuture<TotalCaptureResultProxy> future = getOrCreateFuture(timestamp);
        // Remove the future from the map when it is done to free the memory.
        Futures.addCallback(future, new FutureCallback<TotalCaptureResultProxy>() {
            @Override
            public void onSuccess(TotalCaptureResultProxy totalCaptureResultProxy) {
                synchronized (mLock) {
                    mMetadataFutures.remove(timestamp);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new UnsupportedOperationException();
            }
        });
        return Futures2.nonCancellationPropagating(future);
    }

    @Override
    public void update(@Nonnull TotalCaptureResultProxy metadata) {
        long timestamp = metadata.get(CaptureResult.SENSOR_TIMESTAMP);
        SettableFuture<TotalCaptureResultProxy> future = getOrCreateFuture(timestamp);
        future.set(metadata);
    }
}
