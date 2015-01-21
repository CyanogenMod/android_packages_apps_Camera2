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

package com.android.camera.stats.profiler;

/**
 * A profile is the primary mechanism used to start, stop,
 * and mark the duration of various things within a method.
 */
public interface Profile {
    /**
     * Start, or restart the timers associated with
     * instance
     */
    public Profile start();

    /**
     * Mark an empty event at the current time.
     */
    public void mark();

    /**
     * Mark something at the current time.
     */
    public void mark(String reason);

    /**
     * Stop the profile.
     */
    public void stop();

    /**
     * Stop the profile for a given reason.
     */
    public void stop(String reason);
}
