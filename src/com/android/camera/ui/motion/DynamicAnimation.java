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

package com.android.camera.ui.motion;

import android.graphics.Canvas;

/**
 * Rendering object that can be driven by an animator instance.
 */
public interface DynamicAnimation {

    /**
     * Check to determine if this animation is currently in a stable state.
     *
     * @return true if the animation is stable, false if it should continue to be redrawn.
     */
    boolean isActive();

    /**
     * Update and draw the animation onto the given canvas.
     *
     * @param t current animation frame time.
     * @param dt delta since the last update.
     * @param canvas the canvas to draw the animation onto.
     */
    void draw(long t, long dt, Canvas canvas);
}
