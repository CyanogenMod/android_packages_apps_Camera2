/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnLayoutChangeListener;

import com.android.camera.ui.PreviewStatusListener;

/**
 * This class aims to automate TextureView transform change and notify listeners
 * (e.g. bottom bar) of the preview size change.
 */
public class TextureViewHelper implements TextureView.SurfaceTextureListener,
        OnLayoutChangeListener {

    private static final String TAG = "TextureViewHelper";
    private static final float UNSET = 0f;
    private TextureView mPreview;
    private int mWidth = 0;
    private int mHeight = 0;
    private float mAspectRatio = UNSET;
    private boolean mAutoAdjustTransform = true;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;

    private PreviewStatusListener.PreviewSizeChangedListener mPreviewSizeChangedListener;
    private OnLayoutChangeListener mOnLayoutChangeListener = null;

    public TextureViewHelper(TextureView preview) {
        mPreview = preview;
        mPreview.addOnLayoutChangeListener(this);
        mPreview.setSurfaceTextureListener(this);
    }

    /**
     * If auto adjust transform is enabled, when there is a layout change, the
     * transform matrix will be automatically adjusted based on the preview stream
     * aspect ratio in the new layout.
     *
     * @param enable whether or not auto adjustment should be enabled
     */
    public void setAutoAdjustTransform(boolean enable) {
        mAutoAdjustTransform = enable;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                               int oldTop, int oldRight, int oldBottom) {
        int width = right - left;
        int height = bottom - top;
        if (mWidth != width || mHeight != height) {
            mWidth = width;
            mHeight = height;
            if (mAutoAdjustTransform) {
                updateTransform();
            }
        }
        if (mOnLayoutChangeListener != null) {
            mOnLayoutChangeListener.onLayoutChange(v, left, top, right, bottom, oldLeft, oldTop,
                    oldRight, oldBottom);
        }
    }

    public void updateAspectRatio(float aspectRatio) {
        if (aspectRatio <= 0) {
            Log.e(TAG, "Invalid aspect ratio: " + aspectRatio);
            return;
        }
        if (aspectRatio < 1f) {
            aspectRatio = 1f / aspectRatio;
        }
        mAspectRatio = aspectRatio;
        updateTransform();
    }

    public void updateTransform(Matrix matrix) {
        RectF previewRect = new RectF(0, 0, mWidth, mHeight);
        matrix.mapRect(previewRect);

        float previewWidth = previewRect.width();
        float previewHeight = previewRect.height();
        if (previewHeight == 0 || previewWidth == 0) {
            Log.e(TAG, "Invalid preview size: " + previewWidth + " x " + previewHeight);
            return;
        }
        mPreview.setTransform(matrix);

        onPreviewSizeChanged(previewWidth, previewHeight);
    }

    public void setOnLayoutChangeListener(OnLayoutChangeListener listener) {
        mOnLayoutChangeListener = listener;
    }

    public void setSurfaceTextureListener(TextureView.SurfaceTextureListener listener) {
        mSurfaceTextureListener = listener;
    }

    /**
     * Updates the transform matrix based current width and height of TextureView
     * and preview stream aspect ratio.
     */
    private void updateTransform() {
        if (mAspectRatio == UNSET || mAspectRatio < 0 || mWidth == 0 || mHeight == 0) {
            return;
        }

        Matrix matrix = mPreview.getTransform(null);
        float scaledTextureWidth, scaledTextureHeight;
        if (mWidth > mHeight) {
            scaledTextureWidth = Math.min(mWidth,
                    (int) (mHeight * mAspectRatio));
            scaledTextureHeight = Math.min(mHeight,
                    (int) (mWidth / mAspectRatio));
        } else {
            scaledTextureWidth = Math.min(mWidth,
                    (int) (mHeight / mAspectRatio));
            scaledTextureHeight = Math.min(mHeight,
                    (int) (mWidth * mAspectRatio));
        }

        float scaleX = scaledTextureWidth / mWidth;
        float scaleY = scaledTextureHeight / mHeight;

        boolean landscape = mWidth > mHeight;
        if (landscape) {
            matrix.setScale(scaleX, scaleY, 0f, (float) mHeight / 2);
        } else {
            matrix.setScale(scaleX, scaleY, (float) mWidth / 2, 0.0f);
        }
        mPreview.setTransform(matrix);
        onPreviewSizeChanged(scaledTextureWidth, scaledTextureHeight);
    }

    private void onPreviewSizeChanged(float scaledTextureWidth, float scaledTextureHeight) {
        // Notify listeners of preview size change
        if (mPreviewSizeChangedListener == null) {
            return;
        }
        mPreviewSizeChangedListener.onPreviewSizeChanged(scaledTextureWidth, scaledTextureHeight);
    }

    /**
     * Sets a listener that will get notified when the preview size changed. This
     * can be useful for UI elements or focus overlay to adjust themselves according
     * to the preview size change.
     *
     * @param listener the listener that will get notified of preview size change
     */
    public void setPreviewSizeChangedListener(
            PreviewStatusListener.PreviewSizeChangedListener listener) {
        mPreviewSizeChangedListener = listener;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        // Workaround for b/11168275, see b/10981460 for more details
        if (mWidth != 0 && mHeight != 0) {
            // Re-apply transform matrix for new surface texture
            updateTransform();
        }
        if (mSurfaceTextureListener != null) {
            mSurfaceTextureListener.onSurfaceTextureAvailable(surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (mSurfaceTextureListener != null) {
            mSurfaceTextureListener.onSurfaceTextureSizeChanged(surface, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mSurfaceTextureListener != null) {
            mSurfaceTextureListener.onSurfaceTextureDestroyed(surface);
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (mSurfaceTextureListener != null) {
            mSurfaceTextureListener.onSurfaceTextureUpdated(surface);
        }

    }
}
