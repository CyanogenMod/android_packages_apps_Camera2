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

package com.android.camera.widget;

import android.animation.Animator;
import android.graphics.Canvas;
import android.view.MotionEvent;

/**
 * This class aims to encapsulate animation internal states, so that different
 * animations running on the same view are more independent.
 */
public abstract class AnimationEffects {

    public boolean onTouchEvent(MotionEvent ev) {
        return false;
    }

    public abstract void setSize(int width, int height);

    /**
     * Draws what is needed for the animation in the background, before view calls
     * super.draw().
     *
     * @param canvas canvas that the animation effects will draw on
     */
    public void drawBackground(Canvas canvas) {}

    /**
     * Draws what is needed for the animation in the foreground, after view calls
     * super.draw().
     *
     * @param canvas canvas that the animation effects will draw on
     */
    public abstract void drawForeground(Canvas canvas);

    /**
     * Starts the animation and sets the given animator listener as the listener
     * for the animation.
     */
    public abstract void startAnimation(Animator.AnimatorListener animatorListener);

    /**
     * Cancels the current animation effects.
     *
     * @return true if successful, false otherwise.
     */
    public boolean cancelAnimation() {
        return false;
    }

    /**
     * Ends the current animation effects. This should be called when animation is
     * canceled as well, to make sure all the states are properly set.
     */
    public abstract void endAnimation();

    /**
     * Returns whether super should be drawn when the animation is going on.
     */
    public boolean shouldDrawSuper() {
        return true;
    }
}
