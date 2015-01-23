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

package com.android.camera.one.v2.common;

import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.async.ConcurrentBufferQueue;
import com.android.camera.async.BufferQueue;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.core.ResponseListener;
import com.android.camera.one.v2.core.ResponseListeners;

/**
 * A {@link ResponseListener} which provides a stream of timestamps.
 * @deprecated Use {@link ResponseListeners} instead.
 */
@Deprecated
public class TimestampResponseListener extends ResponseListener {
    private final Updatable<Long> mTimestamps;

    public TimestampResponseListener(Updatable<Long> timestamps) {
        mTimestamps = timestamps;
    }

    @Override
    public void onStarted(long timestamp) {
        mTimestamps.update(timestamp);
    }
}
