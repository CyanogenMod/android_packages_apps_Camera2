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

import android.graphics.SurfaceTexture;
import android.view.Surface;

import com.android.camera.app.OrientationManager.DeviceOrientation;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.session.CaptureSession;

/**
 * Facade for the entire burst acquisition pipeline. Provides a simplified
 * interface over the {@link BurstController}.
 * <p/>
 * The expected usage of BurstFacade can be described by the regular expression
 * "<code>initialize (startBurst stopBurst)* release</code>". That is there can
 * be multiple calls to
 * {@link #startBurst(CaptureSession.CaptureSessionCreator, DeviceOrientation, Facing, int)} and
 * {@link #stopBurst()} between {@link #initialize(SurfaceTexture)} and
 * {@link #release()} calls.
 */
public interface BurstFacade {

    /**
     * Starts the burst.
     *
     * @param captureSessionCreator can create and start empty capture sessions
     * @param deviceOrientation the orientation of the device
     * @param cameraFacing the camera facing
     * @param imageOrientationDegrees the orientation of captured image in
     *            degrees
     */
    public void startBurst(CaptureSession.CaptureSessionCreator captureSessionCreator,
            DeviceOrientation deviceOrientation,
            Facing cameraFacing,
            int imageOrientationDegrees);

    /**
     * Stops the burst.
     *
     * @return Whether a burst was actually stopped. Returns false if no burst
     *         was running at the time.
     */
    public boolean stopBurst();

    /**
     * Initialize resources and use the provided {@link SurfaceTexture} for
     * streaming low-res preview frames for the burst.
     *
     * @param surfaceTexture to use for streaming
     */
    public void initialize(SurfaceTexture surfaceTexture);

    /**
     * Release any resources used by the burst.
     * <p/>
     * {@link #initialize(SurfaceTexture)} should be called in order to start
     * capturing bursts again.
     */
    public void release();

    /**
     * Returns the input surface for preview stream used by burst module.
     * <p/>
     * This is an instance of {@link Surface} that is created for the passed in
     * surface texture {@link #initialize(SurfaceTexture)}.
     */
    public Surface getInputSurface();

    /**
     * Sets an instance of {@link BurstTaker}.
     * <p/>
     * The instance of {@link BurstTaker} is available only when the capture
     * session with Camera is complete.
     */
    public void setBurstTaker(BurstTaker burstTaker);
}
