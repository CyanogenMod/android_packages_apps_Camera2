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

package com.android.gallery3d.anim;

import com.android.gallery3d.ui.GLCanvas;

import java.util.ArrayList;

public class AnimationSet extends CanvasAnimation {

    private final ArrayList<CanvasAnimation> mAnimations =
            new ArrayList<CanvasAnimation>();
    private int mSaveFlags = 0;


    public void addAnimation(CanvasAnimation anim) {
        mAnimations.add(anim);
        mSaveFlags |= anim.getCanvasSaveFlags();
    }

    @Override
    public void apply(GLCanvas canvas) {
        for (int i = 0, n = mAnimations.size(); i < n; i++) {
            mAnimations.get(i).apply(canvas);
        }
    }

    @Override
    public int getCanvasSaveFlags() {
        return mSaveFlags;
    }

    @Override
    protected void onCalculate(float progress) {
        // DO NOTHING
    }

    @Override
    public boolean calculate(long currentTimeMillis) {
        boolean more = false;
        for (CanvasAnimation anim : mAnimations) {
            more |= anim.calculate(currentTimeMillis);
        }
        return more;
    }

    @Override
    public void start() {
        for (CanvasAnimation anim : mAnimations) {
            anim.start();
        }
    }

    @Override
    public boolean isActive() {
        for (CanvasAnimation anim : mAnimations) {
            if (anim.isActive()) return true;
        }
        return false;
    }

}
