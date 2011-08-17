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

import android.graphics.RectF;

import javax.microedition.khronos.opengles.GL11;

public class GLCanvasStub implements GLCanvas {
    public void setSize(int width, int height) {}
    public void clearBuffer() {}
    public void setCurrentAnimationTimeMillis(long time) {}
    public long currentAnimationTimeMillis() {
        throw new UnsupportedOperationException();
    }
    public void setAlpha(float alpha) {}
    public float getAlpha() {
        throw new UnsupportedOperationException();
    }
    public void multiplyAlpha(float alpha) {}
    public void translate(float x, float y, float z) {}
    public void scale(float sx, float sy, float sz) {}
    public void rotate(float angle, float x, float y, float z) {}
    public boolean clipRect(int left, int top, int right, int bottom) {
        throw new UnsupportedOperationException();
    }
    public int save() {
        throw new UnsupportedOperationException();
    }
    public int save(int saveFlags) {
        throw new UnsupportedOperationException();
    }
    public void setBlendEnabled(boolean enabled) {}
    public void restore() {}
    public void drawLine(float x1, float y1, float x2, float y2, GLPaint paint) {}
    public void drawRect(float x1, float y1, float x2, float y2, GLPaint paint) {}
    public void fillRect(float x, float y, float width, float height, int color) {}
    public void drawTexture(
            BasicTexture texture, int x, int y, int width, int height) {}
    public void drawMesh(BasicTexture tex, int x, int y, int xyBuffer,
            int uvBuffer, int indexBuffer, int indexCount) {}
    public void drawTexture(BasicTexture texture,
            int x, int y, int width, int height, float alpha) {}
    public void drawTexture(BasicTexture texture, RectF source, RectF target) {}
    public void drawMixed(BasicTexture from, BasicTexture to,
            float ratio, int x, int y, int w, int h) {}
    public void drawMixed(BasicTexture from, int to,
            float ratio, int x, int y, int w, int h) {}
    public void drawMixed(BasicTexture from, BasicTexture to,
            float ratio, int x, int y, int width, int height, float alpha) {}
    public BasicTexture copyTexture(int x, int y, int width, int height) {
        throw new UnsupportedOperationException();
    }
    public GL11 getGLInstance() {
        throw new UnsupportedOperationException();
    }
    public boolean unloadTexture(BasicTexture texture) {
        throw new UnsupportedOperationException();
    }
    public void deleteBuffer(int bufferId) {
        throw new UnsupportedOperationException();
    }
    public void deleteRecycledResources() {}
    public void multiplyMatrix(float[] mMatrix, int offset) {}
}
