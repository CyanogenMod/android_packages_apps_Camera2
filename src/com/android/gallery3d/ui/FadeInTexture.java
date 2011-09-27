/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.gallery3d.common.Utils;

import android.os.SystemClock;

// FadeInTexture is a texture which begins with a color, then gradually animates
// into a given texture.
public class FadeInTexture implements Texture {
    private static final String TAG = "FadeInTexture";

    // The duration of the animation in milliseconds
    private static final int DURATION = 180;

    private final BasicTexture mTexture;
    private final int mColor;
    private final long mStartTime;
    private final int mWidth;
    private final int mHeight;
    private final boolean mIsOpaque;
    private boolean mIsAnimating;

    public FadeInTexture(int color, BasicTexture texture) {
        mColor = color;
        mTexture = texture;
        mWidth = mTexture.getWidth();
        mHeight = mTexture.getHeight();
        mIsOpaque = mTexture.isOpaque();
        mStartTime = now();
        mIsAnimating = true;
    }

    public void draw(GLCanvas canvas, int x, int y) {
        draw(canvas, x, y, mWidth, mHeight);
    }

    public void draw(GLCanvas canvas, int x, int y, int w, int h) {
        if (isAnimating()) {
            canvas.drawMixed(mTexture, mColor, getRatio(), x, y, w, h);
        } else {
            mTexture.draw(canvas, x, y, w, h);
        }
    }

    public boolean isOpaque() {
        return mIsOpaque;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public boolean isAnimating() {
        if (mIsAnimating) {
            if (now() - mStartTime >= DURATION) {
                mIsAnimating = false;
            }
        }
        return mIsAnimating;
    }

    private float getRatio() {
        float r = (float)(now() - mStartTime) / DURATION;
        return Utils.clamp(1.0f - r, 0.0f, 1.0f);
    }

    private long now() {
        return SystemClock.uptimeMillis();
    }
}
