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

package com.android.camera.device;

import com.android.camera.async.Lifetime;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * This class manages the lifecycle of a single device and API version.
 * A single instance deals with multiple requests for the same device
 * by canceling previous, uncompleted future requests, and tolerates
 * multiple calls to open() and close(). Once the device reaches the
 * shutdown phase (Defined as a close event with no pending open
 * requests) The object is no longer useful and a new instance should
 * be created.
 */
public class CameraDeviceLifecycle<TDevice> implements
      SingleDeviceLifecycle<TDevice, CameraDeviceKey> {

    private final Object mLock;
    private final CameraDeviceKey mDeviceKey;

    @GuardedBy("mLock")
    private final SingleDeviceStateMachine<TDevice, CameraDeviceKey> mDeviceState;

    @Nullable
    @GuardedBy("mLock")
    private SingleDeviceRequest<TDevice> mDeviceRequest;

    // TODO: Consider passing in parent lifetime to ensure this is
    // ALWAYS shut down.
    public CameraDeviceLifecycle(CameraDeviceKey cameraDeviceKey,
          SingleDeviceStateMachine<TDevice, CameraDeviceKey> deviceState) {
        mDeviceKey = cameraDeviceKey;
        mDeviceState = deviceState;
        mLock = new Object();
    }

    @Override
    public CameraDeviceKey getId() {
        return mDeviceKey;
    }

    @Override
    public ListenableFuture<TDevice> createRequest(Lifetime lifetime) {
        synchronized (mLock) {
            mDeviceRequest = new SingleDeviceRequest<>(lifetime);
            lifetime.add(mDeviceRequest);
            mDeviceState.setRequest(mDeviceRequest);

            return mDeviceRequest.getFuture();
        }
    }

    /**
     * Request that the device represented by this lifecycle should
     * attempt to reach an open state.
     */
    @Override
    public void open() {
        synchronized (mLock) {
            mDeviceState.requestOpen();
        }
    }

    /**
     * Request that the device represented by this lifecycle should
     * attempt to reach a closed state.
     */
    @Override
    public void close() {
        synchronized (mLock) {
            if (mDeviceRequest != null) {
                mDeviceRequest.close();
                mDeviceRequest = null;
            }
            mDeviceState.requestClose();
        }
    }
}
