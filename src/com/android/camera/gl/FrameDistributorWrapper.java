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

package com.android.camera.gl;

import android.graphics.SurfaceTexture;
import android.os.Looper;

import com.android.camera.gl.FrameDistributor.FrameConsumer;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A wrapper class for {@link FrameDistributorImpl} that provides thread safe
 * access to the frame distributor.
 */
public class FrameDistributorWrapper implements AutoCloseable {
    private final AtomicReference<FrameDistributorImpl> mFrameDistributor =
            new AtomicReference<FrameDistributorImpl>();

    /**
     * Start processing frames and sending them to consumers.
     * <p/>
     * Can only be called from the main thread.
     *
     * @param consumers list of consumers that will process incoming frames.
     */
    public void start(List<FrameConsumer> consumers) {
        assertOnMainThread();
        if (mFrameDistributor.get() != null) {
            throw new IllegalStateException("FrameDistributorWrapper: start called before close.");
        } else {
            FrameDistributorImpl distributor = new FrameDistributorImpl(consumers);
            mFrameDistributor.set(distributor);
            distributor.start();
            distributor.waitForCommand();
        }
    }

    /**
     * Get the {@link SurfaceTexture} whose frames will be distributed.
     * <p>
     * You must call this after distribution has started with a call to
     * {@link #start(List)}.
     *
     * @return the input SurfaceTexture or null, if none is yet available.
     */
    public SurfaceTexture getInputSurfaceTexture() {
        FrameDistributorImpl distributor = mFrameDistributor.get();
        return (distributor != null) ? distributor.getInputSurfaceTexture() : null;
    }

    /**
     * Update the default buffer size of the input {@link SurfaceTexture}.
     *
     * @param width the new value of width of the preview buffer.
     * @param height the new value of height of the preview buffer.
     */
    public void updatePreviewBufferSize(int width, int height) {
        FrameDistributorImpl distributor = mFrameDistributor.get();
        if (distributor != null) {
            distributor.updatePreviewBufferSize(width, height);
        }
    }

    /**
     * Close the current distributor and release its resources.
     * <p>
     * Can only be called from the main thread.
     */
    @Override
    public void close() {
        assertOnMainThread();
        if (mFrameDistributor.get() != null) {
            mFrameDistributor.get().close();
            mFrameDistributor.set(null);
        } else {
            throw new IllegalStateException("FrameDistributorWrapper: close called before start.");
        }
    }

    private static void assertOnMainThread() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("Must be called on the main thread.");
        }
    }
}
