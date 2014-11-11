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

/**
 * Distributes frames from a {@link SurfaceTexture} to multiple consumers.
 * <p>
 * Frames are distributed as OpenGL textures, that can be used for further
 * processing. This is a flexible approach that allows processing both using GL
 * methods (e.g. shaders) and CPU methods by sending frames through an
 * {@link ImageReader}.
 * <p>
 * Consumers receive {@link FrameConsumer#onNewFrameAvailable(FrameDistributor)}
 * callbacks for new frames. Each consumer can grab the most current frame from
 * its GL thread by calling {@link #acquireNextFrame(int, float[])}. After
 * accessing the data, the consumer needs to call {@link #releaseFrame()}.
 */
public interface FrameDistributor {
    /**
     * Consumes frames streamed from a distributor.
     */
    public interface FrameConsumer {
        /**
         * Called when frame processing is about to start.
         * <p>
         * You can use this to do any setup required to process frames. Note
         * that this is called on the distributor's thread.
         */
        public void onStart();

        /**
         * Called when a new frame is available for processing.
         * <p>
         * Note that as this is called on the frameProducer's thread, you should
         * typically not call {@code acquireNextFrame()} from this callback.
         * Instead, call it from your GL thread, after receiving this callback.
         * When you are done processing the frame, you must call
         * {@code releaseFrame()}.
         *
         * @param frameDistributor that issued the callback
         * @param timestampNs timestamp in nanoseconds of the available frame.
         */
        public void onNewFrameAvailable(FrameDistributor frameDistributor, long timestampNs);

        /**
         * Called when frame processing is about to stop.
         * <p>
         * You can use this to release any resources that your consumer uses.
         * You must reinstate resources when {@link #onStart()} is called again.
         */
        public void onStop();
    }

    /**
     * Acquire the next available frame.
     * <p>
     * Call this after having received a
     * {@link FrameConsumer#onNewFrameAvailable(FrameDistributor)} callback. You
     * must call this from the thread in which your texture name is current and
     * valid. The texture will be filled with the frame data and must be bound
     * using GL_TEXTURE_EXTERNAL_OES. It must be a valid texture name created
     * with {@code glGenTextures()} . You must call {@link #releaseFrame()} as
     * soon as you are done processing this frame. All other consumers are
     * blocked from processing frames until you have released it, so any
     * processing should be done as efficiently as possible.
     *
     * @param textureName to fill with image data. Must be a valid texture name.
     * @param transform of the frame that needs to be applied for an upright
     *            image.
     * @return the timestamp in nanoseconds of the frame.
     */
    public long acquireNextFrame(int textureName, float[] transform);

    /**
     * Release the currently acquired frame.
     */
    public void releaseFrame();

    /**
     * Get the {@link RenderTarget} that the producer uses for GL operations.
     * <p>
     * You should rarely need to use this method. It is used exclusively by
     * consumers that reuse the FrameProducer's EGL context, and must be handled
     * with great care.
     *
     * @return the RenderTarget used by the FrameProducer.
     */
    public RenderTarget getRenderTarget();
}
