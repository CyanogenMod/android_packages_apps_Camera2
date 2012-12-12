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

package com.android.gallery3d.glrenderer;

import com.android.gallery3d.glrenderer.BasicTexture;
import com.android.gallery3d.ui.GLCanvasStub;

import javax.microedition.khronos.opengles.GL11;

public class GLCanvasMock extends GLCanvasStub {
    // fillRect
    int mFillRectCalled;
    float mFillRectWidth;
    float mFillRectHeight;
    int mFillRectColor;
    // drawMixed
    int mDrawMixedCalled;
    float mDrawMixedRatio;
    // drawTexture;
    int mDrawTextureCalled;

    private GL11 mGL;

    public GLCanvasMock(GL11 gl) {
        mGL = gl;
    }

    public GLCanvasMock() {
        mGL = new GLStub();
    }

    @Override
    public GL11 getGLInstance() {
        return mGL;
    }

    @Override
    public void fillRect(float x, float y, float width, float height, int color) {
        mFillRectCalled++;
        mFillRectWidth = width;
        mFillRectHeight = height;
        mFillRectColor = color;
    }

    @Override
    public void drawTexture(
                BasicTexture texture, int x, int y, int width, int height) {
        mDrawTextureCalled++;
    }

    @Override
    public void drawMixed(BasicTexture from, BasicTexture to,
            float ratio, int x, int y, int w, int h) {
        mDrawMixedCalled++;
        mDrawMixedRatio = ratio;
    }
}
