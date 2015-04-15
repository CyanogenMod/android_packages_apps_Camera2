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
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.concurrent.ThreadSafe;

/**
 * ThreadSafe class to deal with the combined future and lifetime
 * for a single device request.
 */
@ThreadSafe
public class SingleDeviceRequest<TDevice> implements SafeCloseable {
    private final SettableFuture<TDevice> mFuture;
    private final Lifetime mLifetime;
    private final AtomicBoolean mIsClosed;

    public SingleDeviceRequest(Lifetime lifetime) {
        mLifetime = lifetime;
        mFuture = SettableFuture.create();
        mIsClosed = new AtomicBoolean(false);
    }

    /**
     * Return the future instance for this request.
     */
    public ListenableFuture<TDevice> getFuture() {
        return mFuture;
    }

    /**
     * Return the lifetime instance for this request.
     */
    public Lifetime getLifetime() {
        return mLifetime;
    }

    /**
     * If the future has not been set, set the value.
     */
    public boolean set(TDevice device) {
        if (!mIsClosed.get()) {
            return mFuture.set(device);
        } else {
            return false;
        }
    }

    public boolean isClosed() {
        return mIsClosed.get();
    }

    public void closeWithException(Throwable throwable) {
        if (!mIsClosed.getAndSet(true)) {
            mFuture.setException(throwable);
            mLifetime.close();
        }
    }

    @Override
    public void close() {
        if (!mIsClosed.getAndSet(true)) {
            mFuture.cancel(true /* mayInterruptIfRunning */);
            mLifetime.close();
        }
    }
}
