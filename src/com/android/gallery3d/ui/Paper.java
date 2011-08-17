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

import com.android.gallery3d.ui.PositionRepository.Position;
import com.android.gallery3d.util.GalleryUtils;

import android.opengl.Matrix;

import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11ExtensionPack;

// This class does the overscroll effect.
class Paper {
    private static final String TAG = "Paper";
    private static final int ROTATE_FACTOR = 4;
    private OverscrollAnimation mAnimationLeft = new OverscrollAnimation();
    private OverscrollAnimation mAnimationRight = new OverscrollAnimation();
    private int mWidth, mHeight;
    private float[] mMatrix = new float[16];

    public void overScroll(float distance) {
        if (distance < 0) {
            mAnimationLeft.scroll(-distance);
        } else {
            mAnimationRight.scroll(distance);
        }
    }

    public boolean advanceAnimation(long currentTimeMillis) {
        return mAnimationLeft.advanceAnimation(currentTimeMillis)
            | mAnimationRight.advanceAnimation(currentTimeMillis);
    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public float[] getTransform(Position target, Position base,
            float scrollX, float scrollY) {
        float left = mAnimationLeft.getValue();
        float right = mAnimationRight.getValue();
        float screenX = target.x - scrollX;
        float t = ((mWidth - screenX) * left - screenX * right) / (mWidth * mWidth);
        // compress t to the range (-1, 1) by the function
        // f(t) = (1 / (1 + e^-t) - 0.5) * 2
        // then multiply by 90 to make the range (-45, 45)
        float degrees =
                (1 / (1 + (float) Math.exp(-t * ROTATE_FACTOR)) - 0.5f) * 2 * -45;
        Matrix.setIdentityM(mMatrix, 0);
        Matrix.translateM(mMatrix, 0, mMatrix, 0, base.x, base.y, base.z);
        Matrix.rotateM(mMatrix, 0, degrees, 0, 1, 0);
        Matrix.translateM(mMatrix, 0, mMatrix, 0,
                target.x - base.x, target.y - base.y, target.z - base.z);
        return mMatrix;
    }
}

class OverscrollAnimation {
    private static final String TAG = "OverscrollAnimation";
    private static final long START_ANIMATION = -1;
    private static final long NO_ANIMATION = -2;
    private static final long ANIMATION_DURATION = 500;

    private long mAnimationStartTime = NO_ANIMATION;
    private float mVelocity;
    private float mCurrentValue;

    public void scroll(float distance) {
        mAnimationStartTime = START_ANIMATION;
        mCurrentValue += distance;
    }

    public boolean advanceAnimation(long currentTimeMillis) {
        if (mAnimationStartTime == NO_ANIMATION) return false;
        if (mAnimationStartTime == START_ANIMATION) {
            mAnimationStartTime = currentTimeMillis;
            return true;
        }

        long deltaTime = currentTimeMillis - mAnimationStartTime;
        float t = deltaTime / 100f;
        mCurrentValue *= Math.pow(0.5f, t);
        mAnimationStartTime = currentTimeMillis;

        if (mCurrentValue < 1) {
            mAnimationStartTime = NO_ANIMATION;
            mCurrentValue = 0;
            return false;
        }
        return true;
    }

    public float getValue() {
        return mCurrentValue;
    }
}
