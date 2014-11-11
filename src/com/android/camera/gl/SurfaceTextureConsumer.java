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

import com.android.camera.gl.FrameDistributor;
import com.android.camera.gl.FrameDistributor.FrameConsumer;

/**
 * Consumes frames from a {@link FrameDistributor} and passes them into a
 * SurfaceTexture.
 */
//TODO: Document this class a bit more and add a test for this class.
public class SurfaceTextureConsumer implements FrameConsumer {

    private SurfaceTexture mSurfaceTexture;
    private final float[] mTransform = new float[16];
    private CopyShader mCopyShader;
    private RenderTarget mTarget;
    private int mWidth;
    private int mHeight;

    @Override
    public synchronized void onStart() {}

    @Override
    public synchronized void onNewFrameAvailable(FrameDistributor frameDistributor,
            long timestampNs) {
        if (mSurfaceTexture == null) {
            throw new IllegalStateException("Receiving frames without a SurfaceTexture!");
        }
        if (mTarget == null) {
            mTarget = frameDistributor.getRenderTarget().forSurfaceTexture(mSurfaceTexture);
        }
        renderFrameToTarget(frameDistributor);
    }

    @Override
    public synchronized void onStop() {
        releaseResources();
    }

    public synchronized SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    public synchronized void setSurfaceTexture(SurfaceTexture surfaceTexture,
            int width,
            int height) {
        mSurfaceTexture = surfaceTexture;
        mWidth = width;
        mHeight = height;
        releaseResources();
    }

    public synchronized void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public synchronized void setDefaultBufferSize(int width, int height) {
        if (mSurfaceTexture != null) {
            mSurfaceTexture.setDefaultBufferSize(width, height);
        }
    }

    public synchronized int getWidth() {
        return mWidth;
    }

    public synchronized int getHeight() {
        return mHeight;
    }

    private void releaseResources() {
        if (mTarget != null) {
            mTarget.close();
            mTarget = null;
        }
        if (mCopyShader != null) {
            mCopyShader.release();
            mCopyShader = null;
        }
    }

    private void renderFrameToTarget(FrameDistributor frameDistributor) {
        CopyShader shader = getCopyShader();
        int texture = GLToolbox.generateTexture();
        frameDistributor.acquireNextFrame(texture, mTransform);
        try {
            shader.setTransform(mTransform);
            shader.renderTextureToTarget(texture, mTarget, mWidth, mHeight);
        } finally {
            frameDistributor.releaseFrame();
            GLToolbox.deleteTexture(texture);
        }
        mTarget.swapBuffers();
    }

    private CopyShader getCopyShader() {
        if (mCopyShader == null) {
            mCopyShader = CopyShader.compileNewExternalShader();
        }
        return mCopyShader;
    }

}
