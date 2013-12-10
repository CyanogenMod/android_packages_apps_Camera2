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

package com.android.camera.filmstrip;

import com.android.camera.CameraActivity;

/**
 * The bottom controls on the filmstrip.
 */
public interface BottomControls extends CameraActivity.OnActionBarVisibilityListener {
    /** Values for the view state of the button. */
    public final int VIEW_NONE = 0;
    public final int VIEW_PHOTO_SPHERE = 1;
    public final int VIEW_RGBZ = 2;

    /**
     * Sets a new or replaces an existing listener for bottom control events.
     */
    void setListener(Listener listener);

    /**
     * Sets the visibility of the edit button.
     */
    void setEditButtonVisibility(boolean visible);

    /**
     * Sets the visibility of the view-photosphere button.
     *
     * @param state one of {@link #VIEW_NONE}, {@link #VIEW_PHOTO_SPHERE},
     *            {@link #VIEW_RGBZ}.
     */
    void setViewButtonVisibility(int state);

    /**
     * Sets the visibility of the tiny-planet button.
     */
    void setTinyPlanetButtonVisibility(boolean visible);

    // TODO: Remove this.
    @Override
    void onActionBarVisibilityChanged(boolean isVisible);

    /**
     * Classes implementing this interface can listen for events on the bottom
     * controls.
     */
    public static interface Listener {
        /**
         * Called when the user pressed the "view" button to e.g. view a photo
         * sphere or RGBZ image.
         */
        public void onView();

        /**
         * Called when the user pressed the "edit" button.
         */
        public void onEdit();

        /**
         * Called when the user pressed the "tiny planet" button.
         */
        public void onTinyPlanet();
    }
}
