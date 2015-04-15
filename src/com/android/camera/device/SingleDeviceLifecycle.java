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
import com.android.camera.async.SafeCloseable;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Lifecycle for a single device from open to close.
 */
public interface SingleDeviceLifecycle<TDevice, TKey> extends SafeCloseable {
    /**
     * Get the camera device key for this lifecycle.
     */
    public TKey getId();

    /**
     * This should create a new request for each invocation and
     * should cancel the previous request (assuming that the previous
     * request has not been completed).
     */
    public ListenableFuture<TDevice> createRequest(Lifetime lifetime);

    /**
     * Tell this instance that it should attempt to get the device to
     * an open and ready state.
     */
    public void open();
}
