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

package com.android.camera.ui.focus;

import android.graphics.RectF;

/**
 * Primary interface for interacting with the focus ring UI.
 */
public interface FocusRing {
    /**
     * Check the state of the passive focus ring animation.
     *
     * @return whether the passive focus animation is running.
     */
    public boolean isPassiveFocusRunning();
    /**
     * Check the state of the active focus ring animation.
     *
     * @return whether the active focus animation is running.
     */
    public boolean isActiveFocusRunning();
    /**
     * Start a passive focus animation.
     */
    public void startPassiveFocus();
    /**
     * Start an active focus animation.
     */
    public void startActiveFocus();
    /**
     * Stop any currently running focus animations.
     */
    public void stopFocusAnimations();
    /**
     * Set the location of the focus ring animation center.
     */
    public void setFocusLocation(float viewX, float viewY);

    /**
     * Set the location of the focus ring animation center.
     */
    public void centerFocusLocation();

    /**
     * Set the target radius as a ratio of min to max visible radius
     * which will internally convert and clamp the value to the
     * correct pixel radius.
     */
    public void setRadiusRatio(float ratio);

    /**
     * The physical size of preview can vary and does not map directly
     * to the size of the view. This allows for conversions between view
     * and preview space for values that are provided in preview space.
     */
    void configurePreviewDimensions(RectF previewArea);
}