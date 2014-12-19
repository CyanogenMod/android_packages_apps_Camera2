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

package com.android.camera.one.v2.photo;

import android.location.Location;

import com.android.camera.app.OrientationManager;
import com.android.camera.async.Updatable;
import com.android.camera.one.v2.camera2proxy.ImageProxy;

public interface ImageSaver {
    public interface Builder {
        /**
         * Sets the title/filename (without suffix) for this capture.
         */
        public void setTitle(String title);
        /**
         * Sets the device orientation so we can compute the right JPEG rotation.
         */
        public void setOrientation(OrientationManager.DeviceOrientation orientation);
        /**
         * Sets the location of this capture.
         */
        public void setLocation(Location location);

        /**
         * Sets the callback to invoke with thumbnail data.
         */
        public void setThumbnailCallback(Updatable<byte[]> callback);

        public ImageSaver build();
    }

    /**
     * Asynchronously saves and closes the image.
     */
    public void saveAndCloseImage(ImageProxy imageProxy);
}
