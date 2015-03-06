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

package com.android.camera.one.v2.photo;

import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.one.v2.camera2proxy.AndroidTotalCaptureResultProxy;
import com.android.camera.one.v2.camera2proxy.TotalCaptureResultProxy;
import com.android.camera.one.v2.core.ResponseListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * A ResponseListener which puts the result in a Future. Each instance is only
 * good for a single (non-repeating) request.
 */
public final class MetadataFuture extends ResponseListener {
    private final SettableFuture<TotalCaptureResultProxy> mMetadata;

    public MetadataFuture() {
        mMetadata = SettableFuture.create();
    }

    @Override
    public void onCompleted(TotalCaptureResult result) {
        super.onCompleted(result);
        mMetadata.set(new AndroidTotalCaptureResultProxy(result));
    }

    @Override
    public void onFailed(CaptureFailure failure) {
        super.onFailed(failure);
        if (failure.getReason() == CaptureFailure.REASON_FLUSHED) {
            mMetadata.cancel(true);
        } else if (failure.getReason() == CaptureFailure.REASON_ERROR) {
            mMetadata.setException(new IllegalStateException("CaptureFailure.REASON_ERROR!"));
        }
    }

    public ListenableFuture<TotalCaptureResultProxy> getMetadata() {
        return mMetadata;
    }
}
