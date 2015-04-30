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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnLayoutChangeListener;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraProvider;
import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.device.CameraId;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraDeviceInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * This class aims to automate TextureView transform change and notify listeners
 * (e.g. bottom bar) of the preview size change.
 */
public class TextureViewHelper implements TextureView.SurfaceTextureListener,
        OnLayoutChangeListener {

    private static final Log.Tag TAG = new Log.Tag("TexViewHelper");
    public static final float MATCH_SCREEN = 0f;
    private static final int UNSET = -1;
    private final TextureView mPreview;
    private final CameraProvider mCameraProvider;
    private int mWidth = 0;
    private int mHeight = 0;
    private RectF mPreviewArea = new RectF();
    private float mAspectRatio = MATCH_SCREEN;
    private boolean mAutoAdjustTransform = true;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;

    private final ArrayList<PreviewStatusListener.PreviewAspectRatioChangedListener>
            mAspectRatioChangedListeners =
            new ArrayList<PreviewStatusListener.PreviewAspectRatioChangedListener>();

    private final ArrayList<PreviewStatusListener.PreviewAreaChangedListener>
            mPreviewSizeChangedListeners =
            new ArrayList<PreviewStatusListener.PreviewAreaChangedListener>();
    private OnLayoutChangeListener mOnLayoutChangeListener = null;
    private CaptureLayoutHelper mCaptureLayoutHelper = null;
    private int mOrientation = UNSET;

    // Hack to allow to know which module is running for b/20694189
    private final AppController mAppController;
    private final int mCameraModeId;
    private final int mCaptureIntentModeId;

    public TextureViewHelper(TextureView preview, CaptureLayoutHelper helper,
            CameraProvider cameraProvider, AppController appController) {
        mPreview = preview;
        mCameraProvider = cameraProvider;
        mPreview.addOnLayoutChangeListener(this);
        mPreview.setSurfaceTextureListener(this);
        mCaptureLayoutHelper = helper;
        mAppController = appController;
        mCameraModeId = appController.getAndroidContext().getResources()
                .getInteger(R.integer.camera_mode_photo);
        mCaptureIntentModeId = appController.getAndroidContext().getResources()
                .getInteger(R.integer.camera_mode_capture_intent);
    }

    /**
     * If auto adjust transform is enabled, when there is a layout change, the
     * transform matrix will be automatically adjusted based on the preview
     * stream aspect ratio in the new layout.
     *
     * @param enable whether or not auto adjustment should be enabled
     */
    public void setAutoAdjustTransform(boolean enable) {
        mAutoAdjustTransform = enable;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        Log.v(TAG, "onLayoutChange");
        int width = right - left;
        int height = bottom - top;
        int rotation = CameraUtil.getDisplayRotation();
        if (mWidth != width || mHeight != height || mOrientation != rotation) {
            mWidth = width;
            mHeight = height;
            mOrientation = rotation;
            if (!updateTransform()) {
                clearTransform();
            }
        }
        if (mOnLayoutChangeListener != null) {
            mOnLayoutChangeListener.onLayoutChange(v, left, top, right, bottom, oldLeft, oldTop,
                    oldRight, oldBottom);
        }
    }

    /**
     * Transforms the preview with the identity matrix, ensuring there is no
     * scaling on the preview. It also calls onPreviewSizeChanged, to trigger
     * any necessary preview size changing callbacks.
     */
    public void clearTransform() {
        mPreview.setTransform(new Matrix());
        mPreviewArea.set(0, 0, mWidth, mHeight);
        onPreviewAreaChanged(mPreviewArea);
        setAspectRatio(MATCH_SCREEN);
    }

    public void updateAspectRatio(float aspectRatio) {
        Log.v(TAG, "updateAspectRatio " + aspectRatio);
        if (aspectRatio <= 0) {
            Log.e(TAG, "Invalid aspect ratio: " + aspectRatio);
            return;
        }
        if (aspectRatio < 1f) {
            aspectRatio = 1f / aspectRatio;
        }
        setAspectRatio(aspectRatio);
        updateTransform();
    }

    private void setAspectRatio(float aspectRatio) {
        Log.v(TAG, "setAspectRatio: " + aspectRatio);
        if (mAspectRatio != aspectRatio) {
            Log.v(TAG, "aspect ratio changed from: " + mAspectRatio);
            mAspectRatio = aspectRatio;
            onAspectRatioChanged();
        }
    }

    private void onAspectRatioChanged() {
        mCaptureLayoutHelper.onPreviewAspectRatioChanged(mAspectRatio);
        for (PreviewStatusListener.PreviewAspectRatioChangedListener listener
                : mAspectRatioChangedListeners) {
            listener.onPreviewAspectRatioChanged(mAspectRatio);
        }
    }

    public void addAspectRatioChangedListener(
            PreviewStatusListener.PreviewAspectRatioChangedListener listener) {
        if (listener != null && !mAspectRatioChangedListeners.contains(listener)) {
            mAspectRatioChangedListeners.add(listener);
        }
    }

    /**
     * This returns the rect that is available to display the preview, and
     * capture buttons
     *
     * @return the rect.
     */
    public RectF getFullscreenRect() {
        return mCaptureLayoutHelper.getFullscreenRect();
    }

    /**
     * This takes a matrix to apply to the texture view and uses the screen
     * aspect ratio as the target aspect ratio
     *
     * @param matrix the matrix to apply
     * @param aspectRatio the aspectRatio that the preview should be
     */
    public void updateTransformFullScreen(Matrix matrix, float aspectRatio) {
        aspectRatio = aspectRatio < 1 ? 1 / aspectRatio : aspectRatio;
        if (aspectRatio != mAspectRatio) {
            setAspectRatio(aspectRatio);
        }

        mPreview.setTransform(matrix);
        mPreviewArea = mCaptureLayoutHelper.getPreviewRect();
        onPreviewAreaChanged(mPreviewArea);

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
        float aspectRatio = previewWidth / previewHeight;
        aspectRatio = aspectRatio < 1 ? 1 / aspectRatio : aspectRatio;
        if (aspectRatio != mAspectRatio) {
            setAspectRatio(aspectRatio);
        }

        RectF previewAreaBasedOnAspectRatio = mCaptureLayoutHelper.getPreviewRect();
        Matrix addtionalTransform = new Matrix();
        addtionalTransform.setRectToRect(previewRect, previewAreaBasedOnAspectRatio,
                Matrix.ScaleToFit.CENTER);
        matrix.postConcat(addtionalTransform);
        mPreview.setTransform(matrix);
        updatePreviewArea(matrix);
    }

    /**
     * Calculates and updates the preview area rect using the latest transform
     * matrix.
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
     * Returns a transformation matrix that implements rotation that is
     * consistent with CaptureLayoutHelper and TextureViewHelper. The magical
     * invariant for CaptureLayoutHelper and TextureViewHelper that must be
     * obeyed is that the bounding box of the view must be EXACTLY the bounding
     * box of the surfaceDimensions AFTER the transformation has been applied.
     *
     * @param currentDisplayOrientation The current display orientation,
     *            measured counterclockwise from to the device's natural
     *            orientation (in degrees, always a multiple of 90, and between
     *            0 and 270, inclusive).
     * @param surfaceDimensions The dimensions of the
     *            {@link android.view.Surface} on which the preview image is
     *            being rendered. It usually only makes sense for the upper-left
     *            corner to be at the origin.
     * @param desiredBounds The boundaries within the
     *            {@link android.view.Surface} where the final image should
     *            appear. These can be used to translate and scale the output,
     *            but note that the image will be stretched to fit, possibly
     *            changing its aspect ratio.
     * @return The transform matrix that should be applied to the
     *         {@link android.view.Surface} in order for the image to display
     *         properly in the device's current orientation.
     */
    public Matrix getPreviewRotationalTransform(int currentDisplayOrientation,
            RectF surfaceDimensions,
            RectF desiredBounds) {
        if (surfaceDimensions.equals(desiredBounds)) {
            return new Matrix();
        }

        Matrix transform = new Matrix();
        transform.setRectToRect(surfaceDimensions, desiredBounds, Matrix.ScaleToFit.FILL);

        RectF normalRect = surfaceDimensions;
        // Bounding box of 90 or 270 degree rotation.
        RectF rotatedRect = new RectF(normalRect.width() / 2 - normalRect.height() / 2,
                normalRect.height() / 2 - normalRect.width() / 2,
                normalRect.width() / 2 + normalRect.height() / 2,
                normalRect.height() / 2 + normalRect.width() / 2);

        OrientationManager.DeviceOrientation deviceOrientation =
                OrientationManager.DeviceOrientation.from(currentDisplayOrientation);

        // This rotation code assumes that the aspect ratio of the content
        // (not of necessarily the surface) equals the aspect ratio of view that is receiving
        // the preview.  So, a 4:3 surface that contains 16:9 data will look correct as
        // long as the view is also 16:9.
        switch (deviceOrientation) {
            case CLOCKWISE_90:
                transform.setRectToRect(rotatedRect, desiredBounds, Matrix.ScaleToFit.FILL);
                transform.preRotate(270, mWidth / 2, mHeight / 2);
                break;
            case CLOCKWISE_180:
                transform.setRectToRect(normalRect, desiredBounds, Matrix.ScaleToFit.FILL);
                transform.preRotate(180, mWidth / 2, mHeight / 2);
                break;
            case CLOCKWISE_270:
                transform.setRectToRect(rotatedRect, desiredBounds, Matrix.ScaleToFit.FILL);
                transform.preRotate(90, mWidth / 2, mHeight / 2);
                break;
            case CLOCKWISE_0:
            default:
                transform.setRectToRect(normalRect, desiredBounds, Matrix.ScaleToFit.FILL);
                break;
        }

        return transform;
    }

    /**
     * Updates the transform matrix based current width and height of
     * TextureView and preview stream aspect ratio.
     * <p>
     * If not {@code mAutoAdjustTransform}, this does nothing except return
     * {@code false}. In all other cases, it returns {@code true}, regardless of
     * whether the transform was changed.
     * </p>
     * In {@code mAutoAdjustTransform} and the CameraProvder is invalid, it is assumed
     * that the CaptureModule/PhotoModule is Camera2 API-based and must implements its
     * rotation via matrix transformation implemented in getPreviewRotationalTransform.
     *
     * @return Whether {@code mAutoAdjustTransform}.
     */
    private boolean updateTransform() {
        Log.v(TAG, "updateTransform");
        if (!mAutoAdjustTransform) {
            return false;
        }

        if (mAspectRatio == MATCH_SCREEN || mAspectRatio < 0 || mWidth == 0 || mHeight == 0) {
            return true;
        }

        Matrix matrix = new Matrix();
        CameraId cameraKey = mCameraProvider.getCurrentCameraId();
        int cameraId = -1;

        try {
            cameraId = cameraKey.getLegacyValue();
        } catch (UnsupportedOperationException ignored) {
            Log.e(TAG, "TransformViewHelper does not support Camera API2");
        }


        // Only apply this fix when Current Active Module is Photo module AND
        // Phone is Nexus4 The enhancement fix b/20694189 to original fix to
        // b/19271661 ensures that the fix should only be applied when:
        // 1) the phone is a Nexus4 which requires the specific workaround
        // 2) CaptureModule is enabled.
        // 3) the Camera Photo Mode Or Capture Intent Photo Mode is active
        if (ApiHelper.IS_NEXUS_4 && mAppController.getCameraFeatureConfig().isUsingCaptureModule()
                && (mAppController.getCurrentModuleIndex() == mCameraModeId ||
                mAppController.getCurrentModuleIndex() == mCaptureIntentModeId)) {
            Log.v(TAG, "Applying Photo Mode, Capture Module, Nexus-4 specific fix for b/19271661");
            mOrientation = CameraUtil.getDisplayRotation();
            matrix = getPreviewRotationalTransform(mOrientation,
                    new RectF(0, 0, mWidth, mHeight),
                    mCaptureLayoutHelper.getPreviewRect());
        } else if (cameraId >= 0) {
            // Otherwise, do the default, legacy action.
            CameraDeviceInfo.Characteristics info = mCameraProvider.getCharacteristics(cameraId);
            matrix = info.getPreviewTransform(mOrientation, new RectF(0, 0, mWidth, mHeight),
                    mCaptureLayoutHelper.getPreviewRect());
        } else {
            // Do Nothing
        }

        mPreview.setTransform(matrix);
        updatePreviewArea(matrix);
        return true;
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
     * Returns a new copy of the preview area, to avoid internal data being
     * modified from outside of the class.
     */
    public RectF getPreviewArea() {
        return new RectF(mPreviewArea);
    }

    /**
     * Returns a copy of the area of the whole preview, including bits clipped
     * by the view
     */
    public RectF getTextureArea() {

        if (mPreview == null) {
            return new RectF();
        }
        Matrix matrix = new Matrix();
        RectF area = new RectF(0, 0, mWidth, mHeight);
        mPreview.getTransform(matrix).mapRect(area);
        return area;
    }

    public Bitmap getPreviewBitmap(int downsample) {
        RectF textureArea = getTextureArea();
        int width = (int) textureArea.width() / downsample;
        int height = (int) textureArea.height() / downsample;
        Bitmap preview = mPreview.getBitmap(width, height);
        return Bitmap.createBitmap(preview, 0, 0, width, height, mPreview.getTransform(null), true);
    }

    /**
     * Adds a listener that will get notified when the preview area changed.
     * This can be useful for UI elements or focus overlay to adjust themselves
     * according to the preview area change.
     * <p/>
     * Note that a listener will only be added once. A newly added listener will
     * receive a notification of current preview area immediately after being
     * added.
     * <p/>
     * This function should be called on the UI thread and listeners will be
     * notified on the UI thread.
     *
     * @param listener the listener that will get notified of preview area
     *            change
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
}
