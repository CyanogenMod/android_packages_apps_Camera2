/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.captureintent;

import com.android.camera.app.CameraAppUI;
import com.android.camera.async.MainThread;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.ProgressOverlay;
import com.android.camera.ui.focus.FocusRing;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.Size;
import com.android.camera2.R;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Contains the UI for the ImageCaptureIntentModule.
 */
public class CaptureIntentModuleUI {
    public interface Listener {
        public void onZoomRatioChanged(float zoomRatio);
    }
    private final Listener mListener;

    private final CameraAppUI mAppUI;
    private final View mRootView;
    private final PreviewOverlay mPreviewOverlay;
    private final ProgressOverlay mProgressOverlay;
    private final FocusRing mFocusRing;
    private final CountDownView mCountdownView;

    /** The image view to display the captured picture. */
    private final ImageView mIntentReviewImageView;

    /** The layout rect of the preview. */
    private RectF mPreviewRect;

    private final PreviewStatusListener.PreviewAreaChangedListener mPreviewAreaChangedListener =
            new PreviewStatusListener.PreviewAreaChangedListener() {
                @Override
                public void onPreviewAreaChanged(RectF previewArea) {
                    mPreviewRect = previewArea;
                    mCountdownView.onPreviewAreaChanged(previewArea);
                    mProgressOverlay.setBounds(previewArea);
                    FrameLayout.LayoutParams params =
                            (FrameLayout.LayoutParams) mIntentReviewImageView.getLayoutParams();

                    /**
                     * WAR for b/19676505: On Nexus 6 / 16:9 / portrait, for
                     * some reasons the previewArea width is 1345.499999999....
                     * Use Math.ceil here to make the width 1346.
                     */
                    params.width = (int) Math.ceil((double) previewArea.width());

                    params.height = Math.round(previewArea.height());
                    params.setMargins(
                            Math.round(previewArea.left), Math.round(previewArea.top), 0, 0);
                    mIntentReviewImageView.setLayoutParams(params);
                }
            };

    public CaptureIntentModuleUI(
            CameraAppUI appUI, View parent, Listener listener) {

        mAppUI = appUI;
        mListener = listener;
        mRootView = parent;

        mIntentReviewImageView = (ImageView) mRootView.findViewById(R.id.intent_review_imageview);

        final LayoutInflater layoutInflater = AndroidServices.instance().provideLayoutInflater();

        ViewGroup moduleRoot = (ViewGroup) mRootView.findViewById(R.id.module_layout);
        layoutInflater.inflate(R.layout.capture_module, moduleRoot, true);

        mPreviewOverlay = (PreviewOverlay) mRootView.findViewById(R.id.preview_overlay);
        mProgressOverlay = (ProgressOverlay) mRootView.findViewById(R.id.progress_overlay);
        mFocusRing = (FocusRing) mRootView.findViewById(R.id.focus_ring);
        mCountdownView = (CountDownView) mRootView.findViewById(R.id.count_down_view);

        mPreviewRect = new RectF(0, 0, 0, 0);
    }

    /**
     * Obtains the current preview layout rect.
     */
    public RectF getPreviewRect() {
        return mPreviewRect;
    }

    /**
     * Obtains the current preview layout size.
     */
    public Size getPreviewSurfaceSize() {
        return new Size(mAppUI.getSurfaceWidth(), mAppUI.getSurfaceHeight());
    }

    /**
     * Configures the bottom bar UI.
     *
     * @param hardwareSpec The hardware spec.
     * @param bottomBarSpec The bottom bar spec.
     */
    public void applyModuleSpecs(
            HardwareSpec hardwareSpec,
            CameraAppUI.BottomBarUISpec bottomBarSpec) {
        MainThread.checkMainThread();
        mAppUI.applyModuleSpecs(hardwareSpec, bottomBarSpec);
    }

    /**
     * Called when the module got resumed.
     */
    public void onModuleResumed() {
        MainThread.checkMainThread();
        // Listen to preview layout change event. Adjust review image view
        // layout to match preview layout.
        //
        // Doing this here rather than in ctor is because TextureViewHelper
        // doesn't exist until the module got initialized.
        mAppUI.addPreviewAreaChangedListener(mPreviewAreaChangedListener);
    }

    /**
     * Called when the module got paused.
     */
    public void onModulePaused() {
        MainThread.checkMainThread();
        mAppUI.removePreviewAreaChangedListener(mPreviewAreaChangedListener);
    }

    /**
     * Update preview transform matrix.
     *
     * @param matrix The preview transform matrix.
     */
    public void updatePreviewTransform(Matrix matrix) {
        MainThread.checkMainThread();
        mAppUI.updatePreviewTransform(matrix);
    }

    /**
     * Update preview aspect ratio.
     *
     * This is useful only when TextureViewHelper auto transform is enabled.
     *
     * @param aspectRatio The preview aspect ratio.
     */
    public void updatePreviewAspectRatio(float aspectRatio) {
        MainThread.checkMainThread();
        mAppUI.updatePreviewAspectRatio(aspectRatio);
    }

    /**
     * Called when the preview is started.
     */
    public void onPreviewStarted() {
        MainThread.checkMainThread();
        mAppUI.onPreviewStarted();
    }

    /**
     * Enables zoom UI, setting maximum zoom.
     * Called from Module when camera is available.
     *
     * @param maxZoom maximum zoom value.
     */
    public void initializeZoom(float maxZoom) {
        MainThread.checkMainThread();
        mPreviewOverlay.setupZoom(maxZoom, 0, mZoomChancedListener);
    }

    public FocusRing getFocusRing() {
        return mFocusRing;
    }

    public void setShutterButtonEnabled(boolean enabled) {
        MainThread.checkMainThread();
        mAppUI.setShutterButtonEnabled(enabled);
    }

    public void startFlashAnimation(boolean shortFlash) {
        MainThread.checkMainThread();
        mAppUI.startFlashAnimation(shortFlash);
    }

    /**
     * Starts the countdown timer.
     *
     * @param sec seconds to countdown
     */
    public void startCountdown(int sec) {
        MainThread.checkMainThread();
        mAppUI.transitionToCancel();
        mCountdownView.startCountDown(sec);
    }

    /**
     * Cancels the on-going countdown, if any.
     */
    public void cancelCountDown() {
        mCountdownView.cancelCountDown();
    }

    /**
     * Sets a listener that gets notified when the countdown is finished.
     */
    public void setCountdownFinishedListener(CountDownView.OnCountDownStatusListener listener) {
        mCountdownView.setCountDownStatusListener(listener);
    }

    /**
     * Transition to the UI where users can review the taken photo.
     *
     * @param reviewPictureBitmap The picture bitmap to be shown.
     */
    public void showPictureReviewUI(Bitmap reviewPictureBitmap) {
        MainThread.checkMainThread();

        mIntentReviewImageView.setImageBitmap(reviewPictureBitmap);
        mIntentReviewImageView.setVisibility(View.VISIBLE);

        mAppUI.transitionToIntentReviewLayout();
        mAppUI.hideModeOptions();
        mAppUI.disableModeOptions();
        mAppUI.setShutterButtonEnabled(false);
    }

    /**
     * Transition to the UI where users can take a photo.
     */
    public void showPictureCaptureUI() {
        MainThread.checkMainThread();

        mIntentReviewImageView.setVisibility(View.INVISIBLE);
        mIntentReviewImageView.setImageBitmap(null);

        mAppUI.transitionToIntentCaptureLayout();
        mAppUI.syncModeOptionIndicators();
        mAppUI.enableModeOptions();
        mAppUI.showModeOptions();
        mAppUI.setShutterButtonEnabled(true);
        mAppUI.setShouldSuppressCaptureIndicator(true);
    }

    public void freezeScreenUntilPreviewReady() {
        MainThread.checkMainThread();
        mAppUI.freezeScreenUntilPreviewReady();
    }

    /** Set up listener to receive zoom changes from View and send to module. */
    private final PreviewOverlay.OnZoomChangedListener mZoomChancedListener =
            new PreviewOverlay.OnZoomChangedListener() {
        @Override
        public void onZoomValueChanged(float ratio) {
            mListener.onZoomRatioChanged(ratio);
        }

        @Override
        public void onZoomStart() {
        }

        @Override
        public void onZoomEnd() {
        }
    };
}
