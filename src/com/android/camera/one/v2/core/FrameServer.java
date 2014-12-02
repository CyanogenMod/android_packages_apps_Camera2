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

/**
 * Provides thread-safe concurrent access to a camera.
 * <p>
 * Clients should choose whether to create an exclusive session, which
 * guarantees that no other clients will access the camera between requests, or
 * a nonexclusive session which allows other clients to access the camera
 * between subsequent requests.
 */
public class FrameServer {
    /**
     * Indicates that the session has been closed already, via
     * {@link Session#close} and no more requests may be submitted.
     */
    public static class SessionClosedException extends RuntimeException {
    }

    public static enum RequestType {
        REPEATING, NON_REPEATING;

        public boolean isRepeating() {
            return equals(REPEATING);
        }
    }

    /**
     * A Session enables submitting multiple Requests for frames.
     */
    public class Session implements AutoCloseable {
        private final boolean mExclusive;
        private boolean mClosed;

        private Session(boolean exclusive) {
            mExclusive = exclusive;
            mClosed = false;
        }

        /**
         * Submits the given request, blocking until the following conditions
         * are met:
         * <ul>
         * <li>Resources are allocated for the request.</li>
         * <li>Any existing exclusive session (other than this one) is closed.</li>
         * </ul>
         *
         * @param burstRequests The request to submit to the camera device.
         * @throws java.lang.InterruptedException if interrupted before the
         *             request is be submitted.
         */
        public synchronized void submitRequest(List<Request> burstRequests, RequestType type)
                throws CameraAccessException, InterruptedException,
                CameraCaptureSessionClosedException {
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

    public FrameServer(TagDispatchCaptureSession captureSession) {
        mCaptureSession = captureSession;
        mCameraLock = new Semaphore(1);
        mClosed = false;
    }

    /**
     * Creates a non-exclusive session. If there are no exclusive sessions
     * currently open, this returns immediately. Otherwise, it will block until
     * the existing exclusive session closes.
     *
     * @return A new session which may be used to interact with the underlying
     *         camera.
     */
    public Session createSession() {
        // false indicates a nonexclusive session.
        return new Session(false);
    }

    /**
     * Creates an exclusive session.
     * <p>
     * If there are no sessions currently open, this returns immediately with a
     * new one. If there are no exclusive sessions currently open, this returns
     * immediately. Otherwise, it will block until the existing exclusive
     * session closes.
     * </p>
     * <p>
     * Note that the above implies that exclusive sessions may be created
     * alongside existing non-exclusive sessions. In this case, requests issued
     * by the non-exclusive sessions will block until the exclusive session is
     * closed.
     * </p>
     *
     * @return A new session which may be used to interact with the underlying
     *         camera.
     */
    public Session createExclusiveSession() throws InterruptedException {
        mCameraLock.acquire();
        // true indicates a nonexclusive session, which now owns {@link
        // mCameraLock}.
        return new Session(true);
    }

    /**
     * Like {@link #createExclusiveSession}, but returns null instead of
     * blocking if the session cannot be created immediately.
     */
    public Session tryCreateExclusiveSession() {
        if (mCameraLock.tryAcquire()) {
            return new Session(true);
        } else {
            return null;
        }
    }

}
