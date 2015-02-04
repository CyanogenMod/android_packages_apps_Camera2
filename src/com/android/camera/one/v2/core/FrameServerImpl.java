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

import static com.google.common.base.Preconditions.checkState;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.SafeCloseable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class FrameServerImpl implements FrameServer {
    public class Session implements FrameServer.Session {
        private final Object mLock;
        private boolean mClosed;

        private Session() {
            mLock = new Object();
            mClosed = false;
        }

        @Override
        public void submitRequest(List<Request> burstRequests, RequestType type)
                throws CameraAccessException, InterruptedException,
                CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
            synchronized (mLock) {
                try {
                    if (mClosed) {
                        throw new SessionClosedException();
                    }

                    mCaptureSession.submitRequest(burstRequests, type == RequestType.REPEATING);
                } catch (Exception e) {
                    for (Request r : burstRequests) {
                        r.abort();
                    }
                    throw e;
                }
            }
        }

        @Override
        public void close() {
            synchronized (mLock) {
                if (!mClosed) {
                    mClosed = true;
                    mCameraLock.unlock();
                }
            }
        }
    }

    private final TagDispatchCaptureSession mCaptureSession;
    private final ReentrantLock mCameraLock;

    public FrameServerImpl(TagDispatchCaptureSession captureSession) {
        mCaptureSession = captureSession;
        mCameraLock = new ReentrantLock(true);
    }

    @Override
    @Nonnull
    public Session createExclusiveSession() throws InterruptedException {
        checkState(!mCameraLock.isHeldByCurrentThread(), "Cannot acquire another " +
                "FrameServer.Session on the same thread.");
        mCameraLock.lockInterruptibly();
        return new Session();
    }

    @Override
    @Nullable
    public Session tryCreateExclusiveSession() {
        if (mCameraLock.isHeldByCurrentThread()) {
            return null;
        }
        if (mCameraLock.tryLock()) {
            return new Session();
        } else {
            return null;
        }
    }
}
