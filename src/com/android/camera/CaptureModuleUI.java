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

package com.android.camera;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.android.camera.debug.Log;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewOverlay.OnZoomChangedListener;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.ProgressOverlay;
import com.android.camera2.R;

/**
 * Contains the UI for the CaptureModule.
 */
public class CaptureModuleUI implements
        PreviewStatusListener {

    private static final Log.Tag TAG = new Log.Tag("CaptureModuleUI");

    private final CameraActivity mActivity;
    private final CaptureModule mModule;
    private final View mRootView;

    private final PreviewOverlay mPreviewOverlay;
    private final ProgressOverlay mProgressOverlay;
    private final View.OnLayoutChangeListener mLayoutListener;
    private final TextureView mPreviewView;

    private final GestureDetector.OnGestureListener mPreviewGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            mModule.onSingleTapUp(null, (int) ev.getX(), (int) ev.getY());
            return true;
        }
    };
    private final FocusOverlayManager.FocusUI mFocusUI;
    private final CountDownView mCountdownView;

    private int mPreviewAreaWidth;
    private int mPreviewAreaHeight;

    /** Maximum zoom; intialize to 1.0 (disabled) */
    private float mMaxZoom = 1f;

    /** Set up listener to receive zoom changes from View and send to module. */
    private final OnZoomChangedListener mZoomChancedListener  = new OnZoomChangedListener() {
        @Override
        public void onZoomValueChanged(float ratio) {
            mModule.setZoom(ratio);
        }

        @Override
        public void onZoomStart() {
        }

        @Override
        public void onZoomEnd() {
        }
    };

    public void onPreviewAreaChanged(RectF previewArea) {
        // TODO: mFaceView.onPreviewAreaChanged(previewArea);
        mCountdownView.onPreviewAreaChanged(previewArea);
    }

    @Override
    public void onPreviewLayoutChanged(View v, int left, int top, int right,
            int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (mLayoutListener != null) {
            mLayoutListener.onLayoutChange(v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                    oldBottom);
        }
    }

    @Override
    public boolean shouldAutoAdjustTransformMatrixOnLayout() {
        return false;
    }

    @Override
    public boolean shouldAutoAdjustBottomBar() {
        return true;
    }

    @Override
    public void onPreviewFlipped() {
        // Do nothing because when preview is flipped, TextureView will lay
        // itself out again, which will then trigger a transform matrix update.
    }

    @Override
    public GestureDetector.OnGestureListener getGestureListener() {
        return mPreviewGestureListener;
    }

    @Override
    public View.OnTouchListener getTouchListener() {
        return null;
    }

    public CaptureModuleUI(CameraActivity activity, CaptureModule module, View parent,
            View.OnLayoutChangeListener layoutListener) {
        mActivity = activity;
        mModule = module;
        mRootView = parent;
        mLayoutListener = layoutListener;

        ViewGroup moduleRoot = (ViewGroup) mRootView.findViewById(R.id.module_layout);
        mActivity.getLayoutInflater().inflate(R.layout.capture_module,
                moduleRoot, true);

        mPreviewView = (TextureView) mRootView.findViewById(R.id.preview_content);

        mPreviewOverlay = (PreviewOverlay) mRootView.findViewById(R.id.preview_overlay);
        mProgressOverlay = (ProgressOverlay) mRootView.findViewById(R.id.progress_overlay);

        mFocusUI = (FocusOverlayManager.FocusUI) mRootView.findViewById(R.id.focus_overlay);
        mCountdownView = (CountDownView) mRootView.findViewById(R.id.count_down_view);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mModule.onSurfaceTextureAvailable(surface, width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return mModule.onSurfaceTextureDestroyed(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        mModule.onSurfaceTextureSizeChanged(surface, width, height);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        mModule.onSurfaceTextureUpdated(surface);
    }

    public void positionProgressOverlay(RectF area) {
        mProgressOverlay.setBounds(area);
    }

    /**
     * Getter for the width of the visible area of the preview.
     */
    public int getPreviewAreaWidth() {
        return mPreviewAreaWidth;
    }

    /**
     * Getter for the height of the visible area of the preview.
     */
    public int getPreviewAreaHeight() {
        return mPreviewAreaHeight;
    }

    public Matrix getPreviewTransform(Matrix m) {
        return mPreviewView.getTransform(m);
    }

    public void showAutoFocusInProgress() {
        mFocusUI.onFocusStarted();
    }

    public void showAutoFocusSuccess() {
        mFocusUI.onFocusSucceeded();
    }

    public void showAutoFocusFailure() {
        mFocusUI.onFocusFailed();
    }

    public void setPassiveFocusSuccess(boolean success) {
        mFocusUI.setPassiveFocusSuccess(success);
    }

    public void showDebugMessage(String message) {
        mFocusUI.showDebugMessage(message);
    }

    public void setAutoFocusTarget(int x, int y, boolean isPassiveScan, int afSize, int aeSize) {
        mFocusUI.setFocusPosition(x, y, isPassiveScan, afSize, aeSize);
    }

    public void clearAutoFocusIndicator() {
        mFocusUI.clearFocus();
    }

    public void clearAutoFocusIndicator(boolean waitUntilProgressIsHidden) {
    }

    /**
     * Starts the countdown timer.
     *
     * @param sec seconds to countdown
     */
    public void startCountdown(int sec) {
        mCountdownView.startCountDown(sec);
    }

    /**
     * Sets a listener that gets notified when the countdown is finished.
     */
    public void setCountdownFinishedListener(CountDownView.OnCountDownStatusListener listener) {
        mCountdownView.setCountDownStatusListener(listener);
    }

    /**
     * Returns whether the countdown is on-going.
     */
    public boolean isCountingDown() {
        return mCountdownView.isCountingDown();
    }

    /**
     * Cancels the on-going countdown, if any.
     */
    public void cancelCountDown() {
        mCountdownView.cancelCountDown();
    }

    /**
     * Sets the progress of the gcam picture taking.
     *
     * @param percent amount of process done in percent 0-100.
     */
    public void setPictureTakingProgress(int percent) {
        mProgressOverlay.setProgress(percent);
    }

    public Bitmap getBitMapFromPreview() {
        Matrix m = new Matrix();
        m = getPreviewTransform(m);
        Bitmap src = mPreviewView.getBitmap();
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    /**
     * Enables zoom UI, setting maximum zoom.
     * Called from Module when camera is available.
     *
     * @param maxZoom maximum zoom value.
     */
    public void initializeZoom(float maxZoom) {
        mMaxZoom = maxZoom;
        mPreviewOverlay.setupZoom(mMaxZoom, 0, mZoomChancedListener);
    }
}
