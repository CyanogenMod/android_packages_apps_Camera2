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

import java.util.List;
import java.util.concurrent.Semaphore;

import android.hardware.camera2.CameraAccessException;

import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;

class FrameServerImpl implements FrameServer {
    public class Session implements FrameServer.Session {
        private final boolean mExclusive;
        private boolean mClosed;

        private Session(boolean exclusive) {
            mExclusive = exclusive;
            mClosed = false;
        }

        @Override
        public synchronized void submitRequest(List<Request> burstRequests, RequestType type)
                throws CameraAccessException, InterruptedException,
                CameraCaptureSessionClosedException, ResourceAcquisitionFailedException {
            try {
                if (mClosed) {
                    throw new SessionClosedException();
                }

                // Exclusive sessions already own this lock. Non-exclusive must
                // acquire it here, for each request.
                if (!mExclusive) {
                    mCameraLock.acquire();
                }
                try {
                    mCaptureSession.submitRequest(burstRequests, type.isRepeating());
                } finally {
                    if (!mExclusive) {
                        mCameraLock.release();
                    }
                }
            } catch (Exception e) {
                for (Request r : burstRequests) {
                    r.abort();
                }
                throw e;
            }
        }

        @Override
        public void close() {
            if (mClosed) {
                return;
            } else {
                mClosed = true;

                if (mExclusive) {
                    mCameraLock.release();
                }
            }
        }
    }

    private final TagDispatchCaptureSession mCaptureSession;
    private final Semaphore mCameraLock;
    private boolean mClosed;

    public FrameServerImpl(TagDispatchCaptureSession captureSession) {
        mCaptureSession = captureSession;
        mCameraLock = new Semaphore(1);
        mClosed = false;
    }

    @Override
    public Session createSession() {
        // false indicates a nonexclusive session.
        return new Session(false);
    }

    @Override
    public Session createExclusiveSession() throws InterruptedException {
        mCameraLock.acquire();
        // true indicates an exclusive session, which now owns {@link
        // mCameraLock}.
        return new Session(true);
    }

    @Override
    public Session tryCreateExclusiveSession() {
        if (mCameraLock.tryAcquire()) {
            return new Session(true);
        } else {
            return null;
        }
    }

}
