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

import java.util.Map;

/**
 * A listener to the various events generated during a burst.
 */
public interface BurstResultsListener {
    /**
     * Called when burst starts.
     */
    public void onBurstStarted();

    /**
     * Called when burst completes.
     *
     * @param burstResult the result of the captured burst.
     */
    public void onBurstCompleted(BurstResult burstResult);

    /**
     * Called when there is an unrecoverable error during capturing a burst.
     * <p/>
     * The burst failed with an unrecoverable error and did not produce any
     * results.
     */
    public void onBurstError(Exception error);

    /**
     * Called when artifact count is available.
     * <p/>
     * This happens before the post processing phase.
     *
     * @param artifactTypeCount A map from the type of artifact to count of
     *            artifact.
     */
    // TODO: Reconsider this method, perhaps return a Future for each artifact
    // in the BurstResult.
    public void onArtifactCountAvailable(Map<String, Integer> artifactTypeCount);
}
