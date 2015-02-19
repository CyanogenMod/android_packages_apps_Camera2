/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.camera.burst;

import android.graphics.SurfaceTexture;

import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.imagesaver.MetadataImage;
import com.android.camera.session.CaptureSession;

import java.util.List;

/**
 * Controls the interactions with burst.
 * <p/>
 * A burst consists of a series of images. Burst module controls the internal
 * camera buffer and keeps images that best represent a burst at any given point
 * in time. The burst module makes decisions on which frames to keep by
 * analyzing low-res preview frames and keeping the corresponding high-res
 * images in the camera internal buffer. At the end of the burst, the burst
 * module retrieves results from the internal camera buffer and can do post
 * processing on the results.
 * <p/>
 * Camera initializes the burst module by calling {@link #startBurst(SurfaceTexture,
 * ImageStreamProperties, BurstResultsListener, CaptureSession)}. The returned
 * eviction strategy is used by the internal camera buffer to decide which
 * frames to keep and which to reject.
 * <p/>
 * Once burst finishes, camera calls the {@link #processBurstResults(List)} to
 * let the burst module retrieve burst results from the internal buffer. Once
 * {@link #processBurstResults(List)} completes all resources allocated for the
 * burst are freed.
 * <p/>
 * Once post processing is complete, the burst module returns the final results
 * by calling {@link BurstResultsListener#onBurstCompleted(BurstResult)} method.
 */
interface BurstController {

    /**
     * Properties of the image stream.
     */
    public static class ImageStreamProperties {
        private final int width;
        private final int height;
        private final int imageRotation;
        private final boolean isMirrored;

        public ImageStreamProperties(int width, int height,
                int imageRotation,
                boolean isMirrored) {
            this.width = width;
            this.height = height;
            this.imageRotation = imageRotation;
            this.isMirrored = isMirrored;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getImageRotation() {
            return imageRotation;
        }

        public boolean isMirrored() {
            return isMirrored;
        }
    }

    /**
     * Starts the burst.
     * <p/>
     * Takes a SurfaceTexture that is not attached to any context (call
     * {@link android.graphics.SurfaceTexture#detachFromGLContext()} before
     * passing it here. Can register as a frame available listener by calling
     * {@link SurfaceTexture#setOnFrameAvailableListener(SurfaceTexture.OnFrameAvailableListener,
     * android.os.Handler)}.
     *
     * @param surfaceTexture the SurfaceTexture for the low-res image stream.
     *            This surface should not be attached to any GL context.
     * @param imageStreamProperties the properties of the low-res image stream.
     * @param burstResultsListener the listener for burst results.
     * @param captureSession the capture session associated with the burst.
     * @return the configuration of burst that can be used to control the
     *         ongoing burst.
     */
    public EvictionHandler startBurst(SurfaceTexture surfaceTexture,
            ImageStreamProperties imageStreamProperties,
            BurstResultsListener burstResultsListener,
            CaptureSession captureSession);

    /**
     * Stops the burst.
     * <p/>
     *
     * @param capturedImages list of images captured from the burst. Implementations should
     *                        close the images as soon as possible.
     */
    public void processBurstResults(List<MetadataImage> capturedImages);
}
