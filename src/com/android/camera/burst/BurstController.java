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

import com.android.camera.gl.FrameDistributor.FrameConsumer;

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
 * Camera hooks up the frame consumer for the burst module returned by
 * {@link #getPreviewFrameConsumer()} and initializes the burst module by
 * calling {@link #startBurst()}. The returned configuration for initialized
 * burst module contains the eviction strategy for the internal camera buffer.
 * This {@link BurstConfiguration#getEvictionHandler()} is then used by camera
 * to decide which frames to keep and which to reject.
 * <p/>
 * Once burst finishes, camera calls the {@link #stopBurst(ResultsAccessor)} to
 * let the burst module retrieve burst results from the internal buffer. Results
 * of burst can be extracted by calling the
 * {@link ResultsAccessor#extractImage(long)} method. Once extraction is
 * finished the burst module should call {@link ResultsAccessor#close()} method
 * to let camera free resources used by burst.
 * <p/>
 * Once post processing is complete, the burst module returns the final results
 * by calling {@link BurstResultsListener#onBurstCompleted(BurstResult)} method.
 */
public interface BurstController {

    /**
     * Starts the burst.
     *
     * @return the configuration of burst that can be used to control the
     *         ongoing burst.
     */
    public BurstConfiguration startBurst();

    /**
     * Stops the burst.
     *
     * @param resultsAccessor an instance of results accessor that can be used
     *            to query the results of the burst.
     */
    public void stopBurst(ResultsAccessor resultsAccessor);

    /**
     * Called when size of the preview changes.
     * <p>
     * Preview size can change in case of rotation or switching cameras.
     *
     * @param width the width of the preview.
     * @param height the height of the preview.
     */
    public void onPreviewSizeChanged(int width, int height);

    /**
     * Called when the orientation of the preview changes.
     *
     * @param orientation orientation of preview in degrees.
     * @param isMirrored true if preview is mirrored.
     */
    public void onOrientationChanged(int orientation, boolean isMirrored);

    /**
     * Get the consumer for preview frames.
     * <p/>
     * Burst module streams preview frames and selects "good" frames by
     * analyzing preview frames. Preview frames should have exact timestamps as
     * the high-res images held in the internal image buffer.
     */
    public FrameConsumer getPreviewFrameConsumer();

}
