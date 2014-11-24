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

package com.android.camera.one.v2.components;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import com.android.camera.one.v2.core.ResponseListener;

/**
 * A {@link com.android.camera.one.v2.core.ResponseListener} which monitors the auto-focus state.
 * TODO Replace listener+executor pattern with generic event listener
 */
public class AutoFocusMonitor implements ResponseListener {
    private final Executor mListenerExecutor;
    private final List<Listener> mListeners;
    private volatile int mLatestAFState;
    private long mLatestUpdateTimestamp;

    /**
     * @param listeners The list of listeners to notify of changes in auto focus state.
     * @param listenerExecutor The executor on which to invoke the listeners.
     */
    public AutoFocusMonitor(List<Listener> listeners, Executor listenerExecutor) {
        mListenerExecutor = listenerExecutor;
        mListeners = new ArrayList<>(listeners);
        mLatestAFState = CaptureResult.CONTROL_AF_STATE_INACTIVE;
        mLatestUpdateTimestamp = -1;
    }

    /**
     * @return The most recently-observed auto-focus state. One of
     *         {@link CaptureResult}.CONTROL_AF_STATE_*
     */
    public int getLatestAFState() {
        return mLatestAFState;
    }

    @Override
    public void onStarted(long timestamp) {

    }

    @Override
    public void onProgressed(long timestamp, CaptureResult partialResult) {
        if (timestamp > mLatestUpdateTimestamp) {
            final Integer newValue = partialResult.get(CaptureResult.CONTROL_AF_STATE);
            if (newValue != null && newValue != mLatestAFState) {
                mLatestAFState = newValue;
                mLatestUpdateTimestamp = timestamp;
                for (final Listener listener : mListeners) {
                    mListenerExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            listener.onAutoFocusStateChange(newValue.intValue());
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onCompleted(long timestamp, TotalCaptureResult result) {

    }

    @Override
    public void onFailed(CaptureFailure failure) {

    }

    public static interface Listener {
        /**
         * @param newAFState One of {@link CaptureResult}.CONTROL_AF_STATE_*
         */
        public void onAutoFocusStateChange(int newAFState);
    }
}
