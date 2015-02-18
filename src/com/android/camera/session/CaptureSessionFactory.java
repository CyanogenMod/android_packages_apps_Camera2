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

package com.android.camera.session;

import android.location.Location;

public interface CaptureSessionFactory {
    /**
     * Creates a new capture session.
     *
     * @param sessionManager the capture session manager.
     * @param sessionNotifier used to notify about updates to the status of a
     *            session.
     * @param title the title of the new session.
     * @param sessionStartMillis the start time of the new session (millis since
     *            epoch).
     * @param location the location of the new session.
     */
    public CaptureSession createNewSession(CaptureSessionManager sessionManager,
            SessionNotifier sessionNotifier, String title,
            long sessionStartMillis, Location location);
}
