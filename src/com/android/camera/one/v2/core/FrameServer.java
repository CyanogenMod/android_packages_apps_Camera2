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

import android.hardware.camera2.CameraAccessException;

import com.android.camera.async.SafeCloseable;
import com.android.camera.one.v2.camera2proxy.CameraCaptureSessionClosedException;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides thread-safe access to a camera.
 */
@ThreadSafe
public interface FrameServer {
    /**
     * A Session enables submitting multiple Requests for frames.
     */
    @ThreadSafe
    public interface Session extends SafeCloseable {
        /**
         * Submits the given request, blocking until resources are allocated for
         * the request.
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
        REPEATING, NON_REPEATING
    }

    /**
     * Creates an exclusive session. Blocks, if necessary, until any existing
     * exclusive session is closed.
     *
     * @return A new session which may be used to interact with the underlying
     *         camera.
     */
    @Nonnull
    public Session createExclusiveSession() throws InterruptedException;

    /**
     * Like {@link #createExclusiveSession}, but returns null instead of
     * blocking if the session cannot be created immediately.
     */
    @Nullable
    public Session tryCreateExclusiveSession();
}
