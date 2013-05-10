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

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.gallery3d.glrenderer.BasicTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.GLId;
import com.android.gallery3d.glrenderer.GLPaint;
import com.android.gallery3d.glrenderer.RawTexture;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL11;

public class GLCanvasStub implements GLCanvas {
    @Override
    public void setSize(int width, int height) {}
    @Override
    public void clearBuffer() {}
    @Override
    public void clearBuffer(float[] argb) {}
    public void setCurrentAnimationTimeMillis(long time) {}
    public long currentAnimationTimeMillis() {
        throw new UnsupportedOperationException();
    }
    @Override
    public void setAlpha(float alpha) {}
    @Override
    public float getAlpha() {
        throw new UnsupportedOperationException();
    }
    @Override
    public void multiplyAlpha(float alpha) {}
    @Override
    public void translate(float x, float y, float z) {}
    @Override
    public void translate(float x, float y) {}
    @Override
    public void scale(float sx, float sy, float sz) {}
    @Override
    public void rotate(float angle, float x, float y, float z) {}
    public boolean clipRect(int left, int top, int right, int bottom) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void save() {
        throw new UnsupportedOperationException();
    }
    @Override
    public void save(int saveFlags) {
        throw new UnsupportedOperationException();
    }
    public void setBlendEnabled(boolean enabled) {}
    @Override
    public void restore() {}
    @Override
    public void drawLine(float x1, float y1, float x2, float y2, GLPaint paint) {}
    @Override
    public void drawRect(float x1, float y1, float x2, float y2, GLPaint paint) {}
    @Override
    public void fillRect(float x, float y, float width, float height, int color) {}
    @Override
    public void drawTexture(
            BasicTexture texture, int x, int y, int width, int height) {}
    @Override
    public void drawMesh(BasicTexture tex, int x, int y, int xyBuffer,
            int uvBuffer, int indexBuffer, int indexCount) {}
    public void drawTexture(BasicTexture texture,
            int x, int y, int width, int height, float alpha) {}
    @Override
    public void drawTexture(BasicTexture texture, RectF source, RectF target) {}
    @Override
    public void drawTexture(BasicTexture texture, float[] mTextureTransform,
            int x, int y, int w, int h) {}
    public void drawMixed(BasicTexture from, BasicTexture to,
            float ratio, int x, int y, int w, int h) {}
    @Override
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
    @Override
    public boolean unloadTexture(BasicTexture texture) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void deleteBuffer(int bufferId) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void deleteRecycledResources() {}
    @Override
    public void multiplyMatrix(float[] mMatrix, int offset) {}
    @Override
    public void dumpStatisticsAndClear() {}
    @Override
    public void beginRenderTarget(RawTexture texture) {}
    @Override
    public void endRenderTarget() {}
    @Override
    public void drawMixed(BasicTexture from, int toColor,
            float ratio, RectF src, RectF target) {}

    @Override
    public void setTextureParameters(BasicTexture texture) {
    }
    @Override
    public void initializeTextureSize(BasicTexture texture, int format, int type) {
    }
    @Override
    public void initializeTexture(BasicTexture texture, Bitmap bitmap) {
    }
    @Override
    public void texSubImage2D(BasicTexture texture, int xOffset, int yOffset, Bitmap bitmap,
            int format, int type) {
    }
    @Override
    public int uploadBuffer(ByteBuffer buffer) {
        return 0;
    }
    @Override
    public int uploadBuffer(FloatBuffer buffer) {
        return 0;
    }
    @Override
    public void recoverFromLightCycle() {
    }
    @Override
    public void getBounds(Rect bounds, int x, int y, int width, int height) {
    }
    @Override
    public GLId getGLId() {
        return null;
    }
}
