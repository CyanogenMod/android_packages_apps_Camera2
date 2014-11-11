/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.gl;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.view.Surface;
import android.view.SurfaceHolder;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Encapsulates a target into which a GL operation can draw into.
 * <p>
 * A RenderTarget can take on many forms, such as an offscreen buffer, an FBO
 * attached to a texture, or a SurfaceTexture target. Regardless of output type,
 * once a RenderTarget is focused, any issued OpenGL draw commands will be
 * rasterized into that target.
 * <p>
 * Note, that this class is a simplified version of the MFF's
 * {@code RenderTarget} class.
 */
// TODO: Add a test for the class.
public class RenderTarget implements AutoCloseable {

    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int EGL_OPENGL_ES2_BIT = 4;

    /** The cached EGLConfig instance. */
    private static EGLConfig mEglConfig = null;

    /** The display for which the EGLConfig was chosen. We expect only one. */
    private static EGLDisplay mConfiguredDisplay;

    private final EGL10 mEgl;
    private final EGLDisplay mDisplay;
    private final EGLContext mContext;
    private final EGLSurface mSurface;
    private final int mFbo;

    private final boolean mOwnsContext;
    private final boolean mOwnsSurface;

    private static int sRedSize = 8;
    private static int sGreenSize = 8;
    private static int sBlueSize = 8;
    private static int sAlphaSize = 8;
    private static int sDepthSize = 0;
    private static int sStencilSize = 0;

    public static RenderTarget newTarget(int width, int height) {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay eglDisplay = createDefaultDisplay(egl);
        EGLConfig eglConfig = chooseEglConfig(egl, eglDisplay);
        EGLContext eglContext = createContext(egl, eglDisplay, eglConfig);
        EGLSurface eglSurface = createSurface(egl, eglDisplay, width, height);
        RenderTarget result = new RenderTarget(eglDisplay, eglContext, eglSurface, 0, true, true);
        return result;
    }

    public RenderTarget forTexture(int texName, int texTarget, int width, int height) {
        // NOTE: We do not need to lookup any previous bindings of this texture
        // to an FBO, as
        // multiple FBOs to a single texture is valid.
        int fbo = GLToolbox.generateFbo();
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLToolbox.checkGlError("glBindFramebuffer");
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                texName,
                texTarget,
                0);
        GLToolbox.checkGlError("glFramebufferTexture2D");
        return new RenderTarget(mDisplay, mContext, surface(), fbo, false, false);
    }

    public RenderTarget forSurfaceHolder(SurfaceHolder surfaceHolder) {
        EGLConfig eglConfig = chooseEglConfig(mEgl, mDisplay);
        EGLSurface eglSurf = mEgl.eglCreateWindowSurface(mDisplay, eglConfig, surfaceHolder, null);
        checkEglError(mEgl, "eglCreateWindowSurface");
        checkSurface(mEgl, eglSurf);
        RenderTarget result = new RenderTarget(mDisplay, mContext, eglSurf, 0, false, true);
        return result;
    }

    public RenderTarget forSurfaceTexture(SurfaceTexture surfaceTexture) {
        EGLConfig eglConfig = chooseEglConfig(mEgl, mDisplay);
        EGLSurface eglSurf = mEgl.eglCreateWindowSurface(mDisplay, eglConfig, surfaceTexture, null);
        checkEglError(mEgl, "eglCreateWindowSurface");
        checkSurface(mEgl, eglSurf);
        RenderTarget result = new RenderTarget(mDisplay, mContext, eglSurf, 0, false, true);
        return result;
    }

    public RenderTarget forSurface(Surface surface) {
        EGLConfig eglConfig = chooseEglConfig(mEgl, mDisplay);
        EGLSurface eglSurf = mEgl.eglCreateWindowSurface(mDisplay, eglConfig, surface, null);
        checkEglError(mEgl, "eglCreateWindowSurface");
        checkSurface(mEgl, eglSurf);
        RenderTarget result = new RenderTarget(mDisplay, mContext, eglSurf, 0, false, true);
        return result;
    }

    public static void setEGLConfigChooser(int redSize, int greenSize, int blueSize, int alphaSize,
            int depthSize, int stencilSize) {
        sRedSize = redSize;
        sGreenSize = greenSize;
        sBlueSize = blueSize;
        sAlphaSize = alphaSize;
        sDepthSize = depthSize;
        sStencilSize = stencilSize;
    }

    public void focus() {
        mEgl.eglMakeCurrent(mDisplay, surface(), surface(), mContext);
        if (getCurrentFbo() != mFbo) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbo);
            GLToolbox.checkGlError("glBindFramebuffer");
        }
    }

    public static void focusNone() {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        egl.eglMakeCurrent(egl.eglGetCurrentDisplay(),
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT);
        checkEglError(egl, "eglMakeCurrent");
    }

    public void swapBuffers() {
        mEgl.eglSwapBuffers(mDisplay, surface());
    }

    public EGLContext getContext() {
        return mContext;
    }

    @Override
    public void close() {
        if (mOwnsContext) {
            mEgl.eglDestroyContext(mDisplay, mContext);
        }
        if (mOwnsSurface) {
            mEgl.eglDestroySurface(mDisplay, mSurface);
        }
        if (mFbo != 0) {
            GLToolbox.deleteFbo(mFbo);
        }
    }

    @Override
    public String toString() {
        return "RenderTarget(" + mDisplay + ", " + mContext + ", " + mSurface + ", " + mFbo + ")";
    }

    private static EGLConfig chooseEglConfig(EGL10 egl, EGLDisplay display) {
        if (mEglConfig == null || !display.equals(mConfiguredDisplay)) {
            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = getDesiredConfig();
            if (!egl.eglChooseConfig(display, configSpec, configs, 1, configsCount)) {
                throw new IllegalArgumentException("EGL Error: eglChooseConfig failed " +
                        getEGLErrorString(egl));
            } else if (configsCount[0] > 0) {
                mEglConfig = configs[0];
                mConfiguredDisplay = display;
            }
        }
        return mEglConfig;
    }

    private static int[] getDesiredConfig() {
        return new int[] {
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, sRedSize,
                EGL10.EGL_GREEN_SIZE, sGreenSize,
                EGL10.EGL_BLUE_SIZE, sBlueSize,
                EGL10.EGL_ALPHA_SIZE, sAlphaSize,
                EGL10.EGL_DEPTH_SIZE, sDepthSize,
                EGL10.EGL_STENCIL_SIZE, sStencilSize,
                EGL10.EGL_NONE
        };
    }

    private RenderTarget(EGLDisplay display, EGLContext context, EGLSurface surface, int fbo,
            boolean ownsContext, boolean ownsSurface) {
        mEgl = (EGL10) EGLContext.getEGL();
        mDisplay = display;
        mContext = context;
        mSurface = surface;
        mFbo = fbo;
        mOwnsContext = ownsContext;
        mOwnsSurface = ownsSurface;
    }

    private EGLSurface surface() {
        return mSurface;
    }

    private static void initEgl(EGL10 egl, EGLDisplay display) {
        int[] version = new int[2];
        if (!egl.eglInitialize(display, version)) {
            throw new RuntimeException("EGL Error: eglInitialize failed " + getEGLErrorString(egl));
        }
    }

    private static EGLDisplay createDefaultDisplay(EGL10 egl) {
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        checkDisplay(egl, display);
        initEgl(egl, display);
        return display;
    }

    private static EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
        int[] attrib_list = {
                EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
        EGLContext ctxt = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attrib_list);
        checkContext(egl, ctxt);
        return ctxt;
    }

    private static EGLSurface createSurface(EGL10 egl, EGLDisplay display, int width, int height) {
        EGLConfig eglConfig = chooseEglConfig(egl, display);
        int[] attribs = {
                EGL10.EGL_WIDTH, width, EGL10.EGL_HEIGHT, height, EGL10.EGL_NONE };
        return egl.eglCreatePbufferSurface(display, eglConfig, attribs);
    }

    private static int getCurrentFbo() {
        int[] result = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, result, 0);
        return result[0];
    }

    private static void checkDisplay(EGL10 egl, EGLDisplay display) {
        if (display == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL Error: Bad display: " + getEGLErrorString(egl));
        }
    }

    private static void checkContext(EGL10 egl, EGLContext context) {
        if (context == EGL10.EGL_NO_CONTEXT) {
            throw new RuntimeException("EGL Error: Bad context: " + getEGLErrorString(egl));
        }
    }

    private static void checkSurface(EGL10 egl, EGLSurface surface) {
        if (surface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("EGL Error: Bad surface: " + getEGLErrorString(egl));
        }
    }

    private static void checkEglError(EGL10 egl, String command) {
        int error = egl.eglGetError();
        if (error != EGL10.EGL_SUCCESS) {
            throw new RuntimeException("Error executing " + command + "! EGL error = 0x"
                    + Integer.toHexString(error));
        }
    }

    private static String getEGLErrorString(EGL10 egl) {
        int eglError = egl.eglGetError();
        return "EGL Error 0x" + Integer.toHexString(eglError);
    }

}
