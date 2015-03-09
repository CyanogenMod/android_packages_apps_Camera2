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

package com.android.camera;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.focus.FocusRing;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraCapabilities;
import com.android.ex.camera2.portability.CameraSettings;

public class VideoUI implements PreviewStatusListener {
    private static final Log.Tag TAG = new Log.Tag("VideoUI");

    private final static float UNSET = 0f;
    private final PreviewOverlay mPreviewOverlay;
    // module fields
    private final CameraActivity mActivity;
    private final View mRootView;
    private final FocusRing mFocusRing;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private TextView mRecordingTimeView;
    private LinearLayout mLabelsLinearLayout;
    private RotateLayout mRecordingTimeRect;
    private boolean mRecordingStarted = false;
    private final VideoController mController;
    private float mZoomMax;

    private float mAspectRatio = UNSET;
    private final AnimationManager mAnimationManager;

    @Override
    public void onPreviewLayoutChanged(View v, int left, int top, int right,
            int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
    }

    @Override
    public boolean shouldAutoAdjustTransformMatrixOnLayout() {
        return true;
    }

    @Override
    public void onPreviewFlipped() {
        mController.updateCameraOrientation();
    }

    private final GestureDetector.OnGestureListener mPreviewGestureListener
            = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            mController.onSingleTapUp(null, (int) ev.getX(), (int) ev.getY());
            return true;
        }
    };

    public VideoUI(CameraActivity activity, VideoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        ViewGroup moduleRoot = (ViewGroup) mRootView.findViewById(R.id.module_layout);
        mActivity.getLayoutInflater().inflate(R.layout.video_module,
                moduleRoot, true);

        mPreviewOverlay = (PreviewOverlay) mRootView.findViewById(R.id.preview_overlay);

        initializeMiscControls();
        mAnimationManager = new AnimationManager();
        mFocusRing = (FocusRing) mRootView.findViewById(R.id.focus_ring);
    }

    public void setPreviewSize(int width, int height) {
        if (width == 0 || height == 0) {
            Log.w(TAG, "Preview size should not be 0.");
            return;
        }
        float aspectRatio;
        if (width > height) {
            aspectRatio = (float) width / height;
        } else {
            aspectRatio = (float) height / width;
        }
        setAspectRatio(aspectRatio);
    }

    public FocusRing getFocusRing() {
        return mFocusRing;
    }

    /**
     * Cancels on-going animations
     */
    public void cancelAnimations() {
        mAnimationManager.cancelAnimations();
    }

    public void setOrientationIndicator(int orientation, boolean animation) {
        // We change the orientation of the linearlayout only for phone UI
        // because when in portrait the width is not enough.
        if (mLabelsLinearLayout != null) {
            if (((orientation / 90) & 1) == 0) {
                mLabelsLinearLayout.setOrientation(LinearLayout.VERTICAL);
            } else {
                mLabelsLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
            }
        }
        mRecordingTimeRect.setOrientation(0, animation);
    }

    private void initializeMiscControls() {
        mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);
        mRecordingTimeView = (TextView) mRootView.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) mRootView.findViewById(R.id.recording_time_rect);
        // The R.id.labels can only be found in phone layout.
        // That is, mLabelsLinearLayout should be null in tablet layout.
        mLabelsLinearLayout = (LinearLayout) mRootView.findViewById(R.id.labels);
    }

    public void updateOnScreenIndicators(CameraSettings settings) {
    }

    public void setAspectRatio(float ratio) {
        if (ratio <= 0) {
            return;
        }
        float aspectRatio = ratio > 1 ? ratio : 1 / ratio;
        if (aspectRatio != mAspectRatio) {
            mAspectRatio = aspectRatio;
            mController.updatePreviewAspectRatio(mAspectRatio);
        }
    }

    public void setSwipingEnabled(boolean enable) {
        mActivity.setSwipingEnabled(enable);
    }

    public void showPreviewBorder(boolean enable) {
       // TODO: mPreviewFrameLayout.showBorder(enable);
    }

    public void showRecordingUI(boolean recording) {
        mRecordingStarted = recording;
        if (recording) {
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            mRecordingTimeView.announceForAccessibility(
                    mActivity.getResources().getString(R.string.video_recording_started));
        } else {
            mRecordingTimeView.announceForAccessibility(
                    mActivity.getResources().getString(R.string.video_recording_stopped));
            mRecordingTimeView.setVisibility(View.GONE);
        }
    }

    public void showReviewImage(Bitmap bitmap) {
        mReviewImage.setImageBitmap(bitmap);
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void showReviewControls() {
        mActivity.getCameraAppUI().transitionToIntentReviewLayout();
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void initializeZoom(CameraSettings settings, CameraCapabilities capabilities) {
        mZoomMax = capabilities.getMaxZoomRatio();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        // TODO: setup zoom through App UI.
        mPreviewOverlay.setupZoom(mZoomMax, settings.getCurrentZoomRatio(),
                new ZoomChangeListener());
    }

    public void setRecordingTime(String text) {
        mRecordingTimeView.setText(text);
    }

    public void setRecordingTimeTextColor(int color) {
        mRecordingTimeView.setTextColor(color);
    }

    public boolean isVisible() {
        return false;
    }

    @Override
    public GestureDetector.OnGestureListener getGestureListener() {
        return mPreviewGestureListener;
    }

    @Override
    public View.OnTouchListener getTouchListener() {
        return null;
    }

    /**
     * Hide the focus indicator.
     */
    public void hidePassiveFocusIndicator() {
        if (mFocusRing != null) {
            Log.v(TAG, "mFocusRing.stopFocusAnimations()");
            mFocusRing.stopFocusAnimations();
        }
    }

    /**
     * Show the passive focus indicator.
     */
    public void showPassiveFocusIndicator() {
        if (mFocusRing != null) {
            mFocusRing.startPassiveFocus();
        }
    }


    /**
     * @return The size of the available preview area.
     */
    public Point getPreviewScreenSize() {
        return new Point(mRootView.getMeasuredWidth(), mRootView.getMeasuredHeight());
    }

    /**
     * Adjust UI to an orientation change if necessary.
     */
    public void onOrientationChanged(OrientationManager orientationManager,
                                     OrientationManager.DeviceOrientation deviceOrientation) {
        // do nothing.
    }

    private class ZoomChangeListener implements PreviewOverlay.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(float ratio) {
            mController.onZoomChanged(ratio);
        }

        @Override
        public void onZoomStart() {
        }

        @Override
        public void onZoomEnd() {
        }
    }

    // SurfaceTexture callbacks
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mController.onPreviewUIReady();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mController.onPreviewUIDestroyed();
        Log.d(TAG, "surfaceTexture is destroyed");
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void onPause() {
        // recalculate aspect ratio when restarting.
        mAspectRatio = 0.0f;
    }
}
