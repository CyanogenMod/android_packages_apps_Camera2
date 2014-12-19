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

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.SafeCloseable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;

/**
 * Provides thread-safe concurrent access to a camera.
 * <p>
 * Clients should choose whether to create an exclusive session, which
 * guarantees that no other clients will access the camera between requests, or
 * a nonexclusive session which allows other clients to access the camera
 * between subsequent requests.
 */
public interface FrameServer {
    /**
     * A Session enables submitting multiple Requests for frames.
     */
    public interface Session extends SafeCloseable {
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
        public void submitRequest(List<Request> burstRequests, RequestType type)
                throws CameraAccessException, InterruptedException,
                CameraCaptureSessionClosedException, ResourceAcquisitionFailedException;

        @Override
        public void close();
    }

    /**
     * Indicates that a session has been closed already, via
     * {@link FrameServer.Session#close} and no more requests may be submitted.
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
     * Creates a non-exclusive session. If there are no exclusive sessions
     * currently open, this returns immediately. Otherwise, it will block until
     * the existing exclusive session closes.
     *
     * @return A new session which may be used to interact with the underlying
     *         camera.
     */
    public Session createSession();

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
    public Session createExclusiveSession() throws InterruptedException;

    /**
     * Like {@link #createExclusiveSession}, but returns null instead of
     * blocking if the session cannot be created immediately.
     */
    public Session tryCreateExclusiveSession();
}
