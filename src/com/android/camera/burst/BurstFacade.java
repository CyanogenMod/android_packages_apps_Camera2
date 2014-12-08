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

package com.android.camera.burst;

import com.android.camera.gl.FrameDistributor.FrameConsumer;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;

import java.io.File;

/**
 * Facade for {@link BurstController} provides a simpler interface.
 */
public interface BurstFacade {
    /**
     * Called when camera is available.
     *
     * @param camera an instance of {@link OneCamera} that is used to start or
     *            stop the burst.
     */
    public void onCameraAttached(OneCamera camera);

    /**
     * Called when camera becomes unavailable.
     */
    public void onCameraDetached();

    /**
     * Returns the frame consumer to use for preview frames.
     */
    public FrameConsumer getPreviewFrameConsumer();

    /**
     * Starts the burst.
     *
     * @param captureSession the capture session to use for this burst.
     * @param tempSessionDirectory a directory in which temporary data can be put.
     */
    public void startBurst(CaptureSession captureSession, File tempSessionDirectory);

    /**
     * @return Whether this burst controller is ready to start another burst.
     */
    public boolean isReady();

    /**
     * Stops the burst.
     *
     * @return Whether a burst was actually stopped. Returns false if no burst
     *         was running at the time.
     */
    public boolean stopBurst();
}
