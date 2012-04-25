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

package com.android.gallery3d.ui;

import android.opengl.GLU;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.opengles.GL11;
import javax.microedition.khronos.opengles.GL11Ext;
import javax.microedition.khronos.opengles.GL11ExtensionPack;

/**
 * A wrapper class of GL 2D texture.
 */
public class Raw2DTexture extends BasicTexture {
    private static final int[] sTextureId = new int[1];
    // OpenGL related fields used when copy.
    private static final int[] sBufferName = new int[1];
    private int mFBO;  // Frame buffer object.

    // We need copy from another texture when in camera capture animation.
    // The copy is done through a framebuffer object with an attached
    // destination texture. The source texture is rendered to the framebuffer
    // and the data will be stored in the destination texture.
    public static void copy(GLCanvas canvas, BasicTexture src, Raw2DTexture dst) {
        int[] viewPort = new int[4];
        GL11 gl11 = canvas.getGLInstance();
        GL11ExtensionPack gl11ep = (GL11ExtensionPack) gl11;

        if (!dst.isLoaded(canvas)) {
            dst.prepare(canvas.getGLInstance());
        }

        gl11ep.glBindFramebufferOES(GL11ExtensionPack.GL_FRAMEBUFFER_OES, dst.mFBO);
        gl11ep.glFramebufferTexture2DOES(
                GL11ExtensionPack.GL_FRAMEBUFFER_OES,
                GL11ExtensionPack.GL_COLOR_ATTACHMENT0_OES,
                GL11.GL_TEXTURE_2D,
                dst.getId(), 0);
        checkFramebufferStatus(gl11ep);

        // Draw the source onto our destination.
        // The texture coords and vertex pointer are already set properly. We don't
        // need to set again.
        gl11.glBindTexture(src.getTarget(), src.getId());
        boolean targetEnabled = gl11.glIsEnabled(src.getTarget());
        gl11.glEnable(src.getTarget());

        // Set the texture matrix.
        gl11.glMatrixMode(GL11.GL_TEXTURE);
        gl11.glPushMatrix();
        gl11.glLoadIdentity();

        // Set the view port.
        gl11.glGetIntegerv(GL11.GL_VIEWPORT, viewPort, 0);
        gl11.glViewport(0, 0, dst.getTextureWidth(), dst.getTextureHeight());

        // Set the projection matrix.
        gl11.glMatrixMode(GL11.GL_PROJECTION);
        gl11.glPushMatrix();
        gl11.glLoadIdentity();
        GLU.gluOrtho2D(gl11, 0, 1, 0, 1);

        // Set the modelview matrix.
        gl11.glMatrixMode(GL11.GL_MODELVIEW);
        gl11.glPushMatrix();
        gl11.glLoadIdentity();

        // Draw the texture.
        gl11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);

        // Clear.
        if (!targetEnabled) gl11.glDisable(src.getTarget());
        gl11ep.glBindFramebufferOES(GL11ExtensionPack.GL_FRAMEBUFFER_OES, 0);
        gl11.glBindTexture(src.getTarget(), 0);
        gl11.glViewport(viewPort[0], viewPort[1], viewPort[2], viewPort[3]);
        gl11.glMatrixMode(GL11.GL_TEXTURE);
        gl11.glPopMatrix();
        gl11.glMatrixMode(GL11.GL_PROJECTION);
        gl11.glPopMatrix();
        gl11.glMatrixMode(GL11.GL_MODELVIEW);
        gl11.glPopMatrix();
        GLId.glDeleteFramebuffers(gl11ep, 1, dst.sBufferName, 0);
    }

    public Raw2DTexture(int w, int h) {
        setSize(w, h);
        GLId.glGenTextures(1, sTextureId, 0);
        mId = sTextureId[0];
        GLId.glGenBuffers(1, sBufferName, 0);
        mFBO = sBufferName[0];
    }

    private void prepare(GL11 gl11) {
        gl11.glBindTexture(GL11.GL_TEXTURE_2D, mId);
        gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP_TO_EDGE);
        gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP_TO_EDGE);
        gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        gl11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        gl11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                getTextureWidth(), getTextureHeight(),
                0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);
        gl11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        mState = UploadedTexture.STATE_LOADED;
    }

    @Override
    protected boolean onBind(GLCanvas canvas) {
        if (!isLoaded(canvas)) {
            return false;
        }

        return true;
    }

    @Override
    public int getTarget() {
        return GL11.GL_TEXTURE_2D;
    }

    public boolean isOpaque() {
        return true;
    }

    @Override
    public void yield() {
        // we cannot free the texture because we have no backup.
    }

    private static void checkFramebufferStatus(GL11ExtensionPack gl11ep) {
        int status = gl11ep.glCheckFramebufferStatusOES(GL11ExtensionPack.GL_FRAMEBUFFER_OES);
        if (status != GL11ExtensionPack.GL_FRAMEBUFFER_COMPLETE_OES) {
            String msg = "";
            switch (status) {
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_FORMATS_OES:
                    msg = "FRAMEBUFFER_FORMATS"; break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT_OES:
                    msg = "FRAMEBUFFER_ATTACHMENT"; break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT_OES:
                    msg = "FRAMEBUFFER_MISSING_ATTACHMENT"; break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER_OES:
                    msg = "FRAMEBUFFER_DRAW_BUFFER"; break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER_OES:
                    msg = "FRAMEBUFFER_READ_BUFFER"; break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_UNSUPPORTED_OES:
                    msg = "FRAMEBUFFER_UNSUPPORTED"; break;
                case GL11ExtensionPack.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS_OES:
                    msg = "FRAMEBUFFER_INCOMPLETE_DIMENSIONS"; break;
            }
            throw new RuntimeException(msg + ":" + Integer.toHexString(status));
        }
    }
}
