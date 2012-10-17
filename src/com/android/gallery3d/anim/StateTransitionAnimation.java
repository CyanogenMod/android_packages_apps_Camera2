/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.anim;

import android.view.animation.DecelerateInterpolator;

import com.android.gallery3d.ui.GLCanvas;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.RawTexture;

public class StateTransitionAnimation extends Animation {
    private static final float BACKGROUND_ALPHA_FROM = 1f;
    private static final float BACKGROUND_ALPHA_TO = 0f;
    private static final float BACKGROUND_SCALE_FROM = 1f;
    private static final float BACKGROUND_SCALE_TO = 0f;
    private static final float FOREGROUND_ALPHA_FROM = 0.9f;
    private static final float FOREGROUND_ALPHA_TO = 1f;
    private static final float FOREGROUND_SCALE_FROM = 3f;
    private static final float FOREGROUND_SCALE_TO = 1f;

    private float mCurrentForegroundScale;
    private float mCurrentBackgroundScale;
    private float mCurrentBackgroundAlpha;
    private float mCurrentForegroundAlpha;

    public StateTransitionAnimation(int duration) {
        setDuration(duration);
        setInterpolator(new DecelerateInterpolator());
    }

    @Override
    protected void onCalculate(float progress) {
        mCurrentForegroundScale = FOREGROUND_SCALE_FROM
                + (FOREGROUND_SCALE_TO - FOREGROUND_SCALE_FROM) * progress;
        mCurrentForegroundAlpha = FOREGROUND_ALPHA_FROM
                + (FOREGROUND_ALPHA_TO - FOREGROUND_ALPHA_FROM) * progress;
        mCurrentBackgroundAlpha = BACKGROUND_ALPHA_FROM
                + (BACKGROUND_ALPHA_TO - BACKGROUND_ALPHA_FROM) * progress;
        mCurrentBackgroundScale = BACKGROUND_SCALE_FROM
                + (BACKGROUND_SCALE_TO - BACKGROUND_SCALE_FROM) * progress;
    }

    public void applyBackground(GLView view, GLCanvas canvas, RawTexture fadeTexture) {
        canvas.clearBuffer(view.getBackgroundColor());
        canvas.save();
        canvas.setAlpha(mCurrentBackgroundAlpha);
        int xOffset = view.getWidth() / 2;
        int yOffset = view.getHeight() / 2;
        canvas.translate(xOffset, yOffset);
        canvas.scale(mCurrentBackgroundScale, mCurrentBackgroundScale, 1);
        fadeTexture.draw(canvas, -xOffset, -yOffset);
        canvas.restore();
    }

    public void applyForegroundTransformation(GLView view, GLCanvas canvas) {
        int xOffset = view.getWidth() / 2;
        int yOffset = view.getHeight() / 2;
        canvas.translate(xOffset, yOffset);
        canvas.scale(mCurrentForegroundScale, mCurrentForegroundScale, 1);
        canvas.translate(-xOffset, -yOffset);
        canvas.setAlpha(mCurrentForegroundAlpha);
    }
}
