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

import com.android.camera.async.ConcurrentBufferQueue;
import com.android.camera.async.BufferQueue;

/**
 * A {@link ResponseListener} which listens for changes to a particular metadata
 * key and serializes changes in the value to a blocking queue.
 */
public class MetadataChangeResponseListener<V> implements ResponseListener {
    private final ConcurrentBufferQueue<V> mNewValues;
    private final CaptureResult.Key<V> mKey;

    private long mMostRecentTimestamp = -1;
    private V mMostRecentValue = null;

    /**
     * @param key The key associated with the value for which to listen to
     *            changes.
     */
    public MetadataChangeResponseListener(CaptureResult.Key<V> key) {
        mNewValues = new ConcurrentBufferQueue<>();
        mKey = key;
    }

    public BufferQueue<V> getValueStream() {
        return mNewValues;
    }

    /**
     * @return The most recent value, or null if no value has been set yet.
     */
    public V getMostRecentValue() {
        return mMostRecentValue;
    }

    @Override
    public void onStarted(long timestamp) {
    }

    @Override
    public void onProgressed(long timestamp, CaptureResult partialResult) {
        if (timestamp > mMostRecentTimestamp) {
            V newValue = partialResult.get(mKey);
            if (newValue != null) {
                if (mMostRecentValue != newValue) {
                    mNewValues.append(newValue);
                }
                mMostRecentValue = newValue;
                mMostRecentTimestamp = timestamp;
            }
        }
    }

    @Override
    public void onCompleted(long timestamp, TotalCaptureResult result) {
    }

    @Override
    public void onFailed(CaptureFailure failure) {
    }
}
