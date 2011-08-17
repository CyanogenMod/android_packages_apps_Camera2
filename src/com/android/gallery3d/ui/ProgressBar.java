/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.ui;

import android.content.Context;
import android.graphics.Rect;

public class ProgressBar extends GLView {
    private final int MAX_PROGRESS = 10000;
    private int mProgress;
    private int mSecondaryProgress;
    private BasicTexture mProgressTexture;
    private BasicTexture mSecondaryProgressTexture;
    private BasicTexture mBackgrondTexture;


    public ProgressBar(Context context, int resProgress,
            int resSecondaryProgress, int resBackground) {
        mProgressTexture = new NinePatchTexture(context, resProgress);
        mSecondaryProgressTexture = new NinePatchTexture(
                context, resSecondaryProgress);
        mBackgrondTexture = new NinePatchTexture(context, resBackground);

    }

    // The progress value is between 0 (empty) and MAX_PROGRESS (full).
    public void setProgress(int progress) {
        mProgress = progress;
    }

    public void setSecondaryProgress(int progress) {
        mSecondaryProgress = progress;
    }

    @Override
    protected void render(GLCanvas canvas) {
        Rect p = mPaddings;

        int width = getWidth() - p.left - p.right;
        int height = getHeight() - p.top - p.bottom;

        int primary = width * mProgress / MAX_PROGRESS;
        int secondary = width * mSecondaryProgress / MAX_PROGRESS;
        int x = p.left;
        int y = p.top;

        canvas.drawTexture(mBackgrondTexture, x, y, width, height);
        canvas.drawTexture(mProgressTexture, x, y, primary, height);
        canvas.drawTexture(mSecondaryProgressTexture, x, y, secondary, height);
    }
}
