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

import java.util.ArrayList;
import java.util.List;

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
    private RectF mPreviewArea = new RectF();
    private float mAspectRatio = UNSET;
    private boolean mAutoAdjustTransform = true;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;
    /**
     * To avoid multiple array allocations in clearMatrixTrans().
     */
    private final float mMatrixVal[] = new float[9];
    /**
     * Used by align*InRect() methods and centerPreviewInRect().
     */
    private final Matrix mUntranslatedMatrix = new Matrix();
    /**
     * Used by align*InRect() methods and centerPreviewInRect().
     */
    private final RectF mUntranslatedPreviewArea = new RectF();

    private final ArrayList<PreviewStatusListener.PreviewAreaChangedListener>
            mPreviewSizeChangedListeners =
            new ArrayList<PreviewStatusListener.PreviewAreaChangedListener>();
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
            } else {
                clearTransform();
            }
        }
        if (mOnLayoutChangeListener != null) {
            mOnLayoutChangeListener.onLayoutChange(v, left, top, right, bottom, oldLeft, oldTop,
                    oldRight, oldBottom);
        }
    }

    /**
     * Transforms the preview with the identity matrix, ensuring there
     * is no scaling on the preview.  It also calls onPreviewSizeChanged, to
     * trigger any necessary preview size changing callbacks.
     */
    public void clearTransform() {
        mPreview.setTransform(new Matrix());
        mPreviewArea.set(0, 0, mWidth, mHeight);
        onPreviewAreaChanged(mPreviewArea);
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
        updatePreviewArea(matrix);
    }

    /**
     * Calculates and updates the preview area rect using the latest transform matrix.
     */
    private void updatePreviewArea(Matrix matrix) {
        mPreviewArea.set(0, 0, mWidth, mHeight);
        matrix.mapRect(mPreviewArea);
        onPreviewAreaChanged(mPreviewArea);
    }

    public void setOnLayoutChangeListener(OnLayoutChangeListener listener) {
        mOnLayoutChangeListener = listener;
    }

    public void setSurfaceTextureListener(TextureView.SurfaceTextureListener listener) {
        mSurfaceTextureListener = listener;
    }

    /**
     * Aligns the preview to the right of the given reference area. Also
     * centers the preview vertically.
     *
     * @param rect The reference area
     */
    public void alignRightInRect(final RectF rect) {
        updateUntranslatedMatrixAndArea();

        float transX = (rect.width() - mUntranslatedPreviewArea.width());
        float transY = (rect.height() - mUntranslatedPreviewArea.height()) / 2.0f;

        mUntranslatedMatrix.postTranslate(rect.left + transX, rect.top + transY);
        updateTransform(mUntranslatedMatrix);
    }

    /**
     * Aligns the preview to the bottom of the given reference area. Also
     * centers the preview horizontally.
     *
     * @param rect The reference area
     */
    public void alignBottomInRect(final RectF rect) {
        updateUntranslatedMatrixAndArea();

        float transX = (rect.width() - mUntranslatedPreviewArea.width()) / 2.0f;
        float transY = (rect.height() - mUntranslatedPreviewArea.height());

        mUntranslatedMatrix.postTranslate(rect.left + transX, rect.top + transY);
        updateTransform(mUntranslatedMatrix);
    }

    /**
     * Centers the preview in the given area by setting the transform matrix.
     *
     * @param rect The reference area.
     */
    public void centerPreviewInRect(final RectF rect) {
        updateUntranslatedMatrixAndArea();

        float transX = (rect.width() - mUntranslatedPreviewArea.width()) / 2.0f;
        float transY = (rect.height() - mUntranslatedPreviewArea.height()) / 2.0f;

        mUntranslatedMatrix.postTranslate(rect.left + transX, rect.top + transY);
        updateTransform(mUntranslatedMatrix);
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

        boolean landscape = mWidth > mHeight;
        if (landscape) {
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

        if (landscape) {
            matrix.setScale(scaleX, scaleY, 0f, (float) mHeight / 2);
        } else {
            matrix.setScale(scaleX, scaleY, (float) mWidth / 2, 0.0f);
        }
        mPreview.setTransform(matrix);
        updatePreviewArea(matrix);
    }

    private void onPreviewAreaChanged(final RectF previewArea) {
        // Notify listeners of preview area change
        final List<PreviewStatusListener.PreviewAreaChangedListener> listeners =
                new ArrayList<PreviewStatusListener.PreviewAreaChangedListener>(
                        mPreviewSizeChangedListeners);
        // This method can be called during layout pass. We post a Runnable so
        // that the callbacks won't happen during the layout pass.
        mPreview.post(new Runnable() {
            @Override
            public void run() {
                for (PreviewStatusListener.PreviewAreaChangedListener listener : listeners) {
                    listener.onPreviewAreaChanged(previewArea);
                }
            }
        });
    }

    /**
     * Returns a new copy of the preview area, to avoid internal data being modified
     * from outside of the class.
     */
    public RectF getPreviewArea() {
        return new RectF(mPreviewArea);
    }

    /**
     * Adds a listener that will get notified when the preview area changed. This
     * can be useful for UI elements or focus overlay to adjust themselves according
     * to the preview area change.
     *
     * Note that a listener will only be added once. A newly added listener will receive
     * a notification of current preview area immediately after being added.
     *
     * This function should be called on the UI thread and listeners will be notified
     * on the UI thread.
     *
     * @param listener the listener that will get notified of preview area change
     */
    public void addPreviewAreaSizeChangedListener(
            PreviewStatusListener.PreviewAreaChangedListener listener) {
        if (listener != null && !mPreviewSizeChangedListeners.contains(listener)) {
            mPreviewSizeChangedListeners.add(listener);
            if (mPreviewArea.width() == 0 || mPreviewArea.height() == 0) {
                listener.onPreviewAreaChanged(new RectF(0, 0, mWidth, mHeight));
            } else {
                listener.onPreviewAreaChanged(new RectF(mPreviewArea));
            }
        }
    }

    /**
     * Removes a listener that gets notified when the preview area changed.
     *
     * @param listener the listener that gets notified of preview area change
     */
    public void removePreviewAreaSizeChangedListener(
            PreviewStatusListener.PreviewAreaChangedListener listener) {
        if (listener != null && mPreviewSizeChangedListeners.contains(listener)) {
            mPreviewSizeChangedListeners.remove(listener);
        }
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

    /**
     * Clears the translation of the matrix.
     *
     * @param m The matrix in which the translation will be cleared.
     */
    private void clearMatrixTrans(Matrix m) {
        m.getValues(mMatrixVal);
        mMatrixVal[Matrix.MTRANS_X] = 0;
        mMatrixVal[Matrix.MTRANS_Y] = 0;
        m.setValues(mMatrixVal);
    }

    /**
     * Updates {@code mUntranslatedMatrix} and {mUntranslatedPreviewArea}.
     */
    private void updateUntranslatedMatrixAndArea() {
        mPreview.getTransform(mUntranslatedMatrix);
        clearMatrixTrans(mUntranslatedMatrix);
        mUntranslatedPreviewArea.set(0, 0, mPreview.getWidth(), mPreview.getHeight());
        mUntranslatedMatrix.mapRect(mUntranslatedPreviewArea);
    }
}
