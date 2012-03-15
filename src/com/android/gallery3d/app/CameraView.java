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

package com.android.gallery3d.app;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import java.io.IOException;

// This is a sample View which demos the usage of ScreenNailBridge. It
// is not intended for production use.
public class CameraView extends TextureView implements
        TextureView.SurfaceTextureListener, ScreenNailBridge.Listener {
    private static final String TAG = "CameraView";
    private static final int PREVIEW_WIDTH = 960;
    private static final int PREVIEW_HEIGHT = 720;
    private Camera mCamera;
    private ScreenNailBridge mScreenNailBridge;

    public CameraView(Context context) {
        super(context);
        init();
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setVisibility(View.INVISIBLE);
        setSurfaceTextureListener(this);
    }

    public void setScreenNailBridge(ScreenNailBridge s) {
        mScreenNailBridge = s;
    }

    @Override
    public void onMeasure(int widthSpec, int heightSpec) {
        int width = getDefaultSize(PREVIEW_WIDTH, widthSpec);
        int height = getDefaultSize(PREVIEW_HEIGHT, heightSpec);
        // Keep aspect ratio
        if (width * PREVIEW_HEIGHT > PREVIEW_WIDTH * height) {
            width = PREVIEW_WIDTH * height / PREVIEW_HEIGHT;
        } else {
            height = PREVIEW_HEIGHT * width / PREVIEW_WIDTH;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        mScreenNailBridge.setSize(w, h);
    }

    @Override
    public void updateView(boolean visible, int x, int y, int w, int h) {
        if (!visible) {
            setVisibility(View.INVISIBLE);
        } else {
            setVisibility(View.VISIBLE);
            setTranslationX(x);
            setTranslationY(y);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        try {
            mCamera = Camera.open();

            Camera.Parameters param = mCamera.getParameters();
            param.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
            mCamera.setParameters(param);

            mCamera.setPreviewTexture(surface);
            mCamera.startPreview();
        } catch (Throwable ex) {
            Log.e(TAG, "failed to open camera", ex);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCamera.stopPreview();
        mCamera.release();
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }
}
