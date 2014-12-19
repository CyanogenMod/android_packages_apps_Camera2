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

import android.content.Context;

import com.android.camera.gl.FrameDistributor.FrameConsumer;

/**
 * Stub implementation for burst controller.
 */
class BurstControllerImpl implements BurstController {
    /**
     * Create a new BurstController.
     *
     * @param context the context of the application.
     * @param resultsListener listener for listening to burst events.
     */
    public BurstControllerImpl(Context context, BurstResultsListener resultsListener) {
    }

    /**
     * Returns true if burst mode is supported by camera.
     */
    public static boolean isBurstModeSupported(Context context) {
        return false;
    }

    @Override
    public BurstConfiguration startBurst() {
        return null;
    }

    @Override
    public void stopBurst(ResultsAccessor resultsAccessor) {
        // no op
    }

    @Override
    public void onPreviewSizeChanged(int width, int height) {
    }

    @Override
    public void onOrientationChanged(int orientation, boolean isMirrored) {
    }

    @Override
    public FrameConsumer getPreviewFrameConsumer() {
        throw new IllegalStateException("Not implemented.");
    }
}
