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

package com.android.camera.one.v2.core;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.ConcurrentState;
import com.android.camera.async.Observable;
import com.android.camera.async.SafeCloseable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;
import com.android.camera.util.Callback;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Decorates a {@link FrameServer} to enable listening for changes to
 * availability (whether or not an exclusive session can likely be acquired
 * immediately).
 */
@ParametersAreNonnullByDefault
final class ObservableFrameServer implements FrameServer, Observable<Boolean> {
    private final ConcurrentState<Boolean> mAvailability;
    private final FrameServer mDelegate;

    private class SessionImpl implements Session {
        private final AtomicBoolean mClosed;
        private final Session mDelegate;

        private SessionImpl(Session delegate) {
            mClosed = new AtomicBoolean(false);
            mDelegate = delegate;
        }

        @Override
        public void submitRequest(List<Request> burstRequests, RequestType type)
                throws CameraAccessException, InterruptedException,
                CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
            mDelegate.submitRequest(burstRequests, type);
        }

        @Override
        public void close() {
            if (!mClosed.getAndSet(true)) {
                mAvailability.update(true);
                mDelegate.close();
            }
        }
    }

    public ObservableFrameServer(FrameServer delegate) {
        mDelegate = delegate;
        mAvailability = new ConcurrentState<>(true);
    }

    @Nonnull
    @Override
    public Session createExclusiveSession() throws InterruptedException {
        Session session = mDelegate.createExclusiveSession();
        mAvailability.update(false);
        return new SessionImpl(session);
    }

    @Nullable
    @Override
    public Session tryCreateExclusiveSession() {
        Session session = mDelegate.tryCreateExclusiveSession();
        if (session == null) {
            return null;
        } else {
            mAvailability.update(false);
            return new SessionImpl(session);
        }
    }

    @Nonnull
    @Override
    public SafeCloseable addCallback(Runnable callback, Executor executor) {
        return mAvailability.addCallback(callback, executor);
    }

    @Nonnull
    @Override
    public Boolean get() {
        return mAvailability.get();
    }
}
