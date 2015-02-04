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

import com.google.common.collect.ImmutableList;

import java.util.Collection;

/**
 * Combines multiple {@link ResponseListener}s into a single one which
 * dispatches to all listeners for each callback.
 */
class ResponseListenerBroadcaster extends ResponseListener {
    private final ImmutableList<ResponseListener> mListeners;

    public ResponseListenerBroadcaster(ResponseListener[] listeners) {
        mListeners = ImmutableList.copyOf(listeners);
    }

    public ResponseListenerBroadcaster(Collection<ResponseListener> listeners) {
        mListeners = ImmutableList.copyOf(listeners);
    }

    @Override
    public void onStarted(long timestamp) {
        for (ResponseListener listener : mListeners) {
            listener.onStarted(timestamp);
        }
    }

    @Override
    public void onProgressed(CaptureResult partialResult) {
        for (ResponseListener listener : mListeners) {
            listener.onProgressed(partialResult);
        }
    }

    @Override
    public void onCompleted(TotalCaptureResult result) {
        for (ResponseListener listener : mListeners) {
            listener.onCompleted(result);
        }
    }

    @Override
    public void onFailed(CaptureFailure failure) {
        for (ResponseListener listener : mListeners) {
            listener.onFailed(failure);
        }
    }
}
