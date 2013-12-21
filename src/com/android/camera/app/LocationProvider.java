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

import android.location.Location;

/**
 * A generic interface for a location provider {Fused, GPS, Network}.
 */
public interface LocationProvider {

    /**
     * Report when connection fails so another location provider may be used.
     */
    public interface OnConnectionFailedListener {
        /**
         * Report connection failure.
         */
        public void onConnectionFailed();
    }

    /**
     * Get the current location.
     */
    public Location getCurrentLocation();

    /**
     * Turn on/off recording of location.
     *
     * @param recordLocation Whether or not to record location.
     */
    public void recordLocation(boolean recordLocation);

    /**
     * Disconnect the location provider after use. The location provider can no longer acquire
     * locations after this has been called.
     */
    public void disconnect();
}