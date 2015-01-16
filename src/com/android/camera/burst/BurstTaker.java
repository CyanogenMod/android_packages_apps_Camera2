/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.camera.burst.EvictionHandler;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Helper for taking bursts.
 */
@ParametersAreNonnullByDefault
public interface BurstTaker {
    /**
     * Start the burst.
     *
     * @param evictionHandler the strategy to use for evicting frames from the
     *            internal ring buffer.
     * @param burstController the instance for {@link BurstController}.
     */
    public void startBurst(EvictionHandler evictionHandler, BurstController burstController);

    /**
     * Stop the burst.
     */
    public void stopBurst();
}
