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

import com.android.camera.session.CaptureSessionManager;

/**
 * Functionality available to all modules and services.
 */
public interface CameraServices {

    /**
     * Returns the capture session manager instance that modules use to store
     * temporary or final capture results.
     */
    public CaptureSessionManager getCaptureSessionManager();

    /**
     * Returns the memory manager which can be used to get informed about memory
     * status updates.
     */
    public MemoryManager getMemoryManager();

    /**
     * Returns the media saver instance.
     * <p>
     * Deprecated. Use {@link #getCaptureSessionManager()} whenever possible.
     * This direct access to media saver will go away.
     */
    @Deprecated
    public MediaSaver getMediaSaver();
}
