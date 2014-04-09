/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.app;

import java.util.HashMap;

/**
 * Keeps track of memory used by the app and informs modules and services if
 * memory gets low.
 */
public interface MemoryManager {
    /**
     * Classes implementing this interface will be able to get updates about
     * memory status changes.
     */
    public static interface MemoryListener {
        /**
         * Called when the app is experiencing a change in memory state. Modules
         * should listen to these to not exceed the available memory.
         *
         * @param state the new state, one of {@link MemoryManager#STATE_OK},
         *            {@link MemoryManager#STATE_LOW_MEMORY},
         */
        public void onMemoryStateChanged(int state);

        /**
         * Called when the system is about to kill our app due to high memory
         * load.
         */
        public void onLowMemory();
    }

    /** The memory status is OK. The app can function as normal. */
    public static final int STATE_OK = 0;

    /** The memory is running low. E.g. no new media should be captured. */
    public static final int STATE_LOW_MEMORY = 1;

    /**
     * Add a new listener that is informed about upcoming memory events.
     */
    public void addListener(MemoryListener listener);

    /**
     * Removes an already registered listener.
     */
    public void removeListener(MemoryListener listener);

    /**
     * Returns the maximum amount of memory allowed to be allocated in native
     * code by our app (in megabytes).
     */
    public int getMaxAllowedNativeMemoryAllocation();

    /**
     * Queries the memory consumed, total memory, and memory thresholds for this app.
     *
     * @return HashMap containing memory metrics keyed by string labels
     *     defined in {@link MemoryQuery}.
     */
    public HashMap queryMemory();
}
