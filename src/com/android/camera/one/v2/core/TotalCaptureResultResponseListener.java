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

import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.one.v2.async.ConcurrentBufferQueue;
import com.android.camera.one.v2.async.BufferQueue;

/**
 * A {@link ResponseListener} which provides a stream of
 * {@link TotalCaptureResult}s.
 */
public class TotalCaptureResultResponseListener implements ResponseListener {
    private final ConcurrentBufferQueue<TotalCaptureResult> mResults;

    public TotalCaptureResultResponseListener() {
        mResults = new ConcurrentBufferQueue<>();
    }

    public BufferQueue<TotalCaptureResult> getResult() {
        return mResults;
    }

    @Override
    public void onStarted(long timestamp) {
    }

    @Override
    public void onProgressed(long timestamp, CaptureResult partialResult) {
    }

    @Override
    public void onCompleted(long timestamp, TotalCaptureResult result) {
        mResults.append(result);
    }

    @Override
    public void onFailed(CaptureFailure failure) {
    }
}
