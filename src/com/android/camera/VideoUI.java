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
import android.graphics.SurfaceTexture;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.app.CameraAppUI;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.RotateLayout;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

import java.util.List;

public class VideoUI implements PreviewStatusListener, SurfaceHolder.Callback {
    private static final String TAG = "VideoUI";

    private final static float UNSET = 0f;
    private final PreviewOverlay mPreviewOverlay;
    // module fields
    private final CameraActivity mActivity;
    private final View mRootView;
    private final TextureView mTextureView;
    private final FocusOverlayManager.FocusUI mFocusUI;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewPlayButton;
    private TextView mRecordingTimeView;
    private LinearLayout mLabelsLinearLayout;
    private View mTimeLapseLabel;
    private RotateLayout mRecordingTimeRect;
    private boolean mRecordingStarted = false;
    private SurfaceTexture mSurfaceTexture;
    private final VideoController mController;
    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private SurfaceView mSurfaceView = null;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;

    private ButtonManager.ButtonCallback mFlashCallback;
    private ButtonManager.ButtonCallback mCameraCallback;

    private final OnClickListener mCancelCallback = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mController.onReviewCancelClicked(v);
        }
    };
    private final OnClickListener mDoneCallback = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mController.onReviewDoneClicked(v);
        }
    };
    private final OnClickListener mReviewCallback = new OnClickListener() {
        @Override
        public void onClick(View v) {
            customizeButtons(mActivity.getButtonManager(), mFlashCallback, mCameraCallback);
            mActivity.getCameraAppUI().transitionToIntentLayout();
            mController.onReviewPlayClicked(v);
        }
    };

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
    public boolean shouldAutoAdjustBottomBar() {
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
        mTextureView = (TextureView) mRootView.findViewById(R.id.preview_content);

        mSurfaceTexture = mTextureView.getSurfaceTexture();

        initializeMiscControls();
        initializeControlByIntent();
        mAnimationManager = new AnimationManager();
        mFocusUI = (FocusOverlayManager.FocusUI) mRootView.findViewById(R.id.focus_overlay);
    }

    public void initializeSurfaceView() {
        mSurfaceView = new SurfaceView(mActivity);
        ((ViewGroup) mRootView).addView(mSurfaceView, 0);
        mSurfaceView.getHolder().addCallback(this);
    }

    /**
     * Customize the mode options such that flash and camera
     * switching are enabled.
     */
    public void customizeButtons(ButtonManager buttonManager,
                                   ButtonManager.ButtonCallback flashCallback,
                                   ButtonManager.ButtonCallback cameraCallback) {

        buttonManager.enableButton(ButtonManager.BUTTON_CAMERA,
            cameraCallback, R.array.camera_id_icons);
        buttonManager.enableButton(ButtonManager.BUTTON_TORCH,
            flashCallback, R.array.video_flashmode_icons);
        buttonManager.hideButton(ButtonManager.BUTTON_HDRPLUS);
        buttonManager.hideButton(ButtonManager.BUTTON_REFOCUS);

        mActivity.getCameraAppUI().setBottomBarShutterIcon(CameraAppUI.VIDEO_SHUTTER_ICON);

        if (mController.isVideoCaptureIntent()) {
            buttonManager.enablePushButton(ButtonManager.BUTTON_CANCEL,
                mCancelCallback);
            buttonManager.enablePushButton(ButtonManager.BUTTON_DONE,
                mDoneCallback);
            buttonManager.enablePushButton(ButtonManager.BUTTON_REVIEW,
                mReviewCallback, R.drawable.ic_play);
        }
    }

    private void initializeControlByIntent() {
        if (mController.isVideoCaptureIntent()) {
            customizeButtons(mActivity.getButtonManager(), mFlashCallback, mCameraCallback);
            mActivity.getCameraAppUI().transitionToIntentLayout();
        }
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

    public FocusOverlayManager.FocusUI getFocusUI() {
        return mFocusUI;
    }

    /**
     * Starts a flash animation
     */
    public void animateFlash() {
        mController.startPreCaptureAnimation();
    }

    /**
     * Starts a capture animation
     */
    public void animateCapture() {
        Bitmap bitmap = null;
        if (mTextureView != null) {
            bitmap = mTextureView.getBitmap((int) mSurfaceTextureUncroppedWidth / 2,
                    (int) mSurfaceTextureUncroppedHeight / 2);
        }
        animateCapture(bitmap);
    }

    /**
     * Starts a capture animation
     * @param bitmap the captured image that we shrink and slide in the animation
     */
    public void animateCapture(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "No valid bitmap for capture animation.");
            return;
        }
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

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceView.getHolder();
    }

    public void hideSurfaceView() {
        mSurfaceView.setVisibility(View.GONE);
        mTextureView.setVisibility(View.VISIBLE);
    }

    public void showSurfaceView() {
        mSurfaceView.setVisibility(View.VISIBLE);
        mTextureView.setVisibility(View.GONE);
    }

    public void onCameraOpened(ButtonManager.ButtonCallback flashCallback,
            ButtonManager.ButtonCallback cameraCallback) {
        mFlashCallback = flashCallback;
        mCameraCallback = cameraCallback;
    }

    private void initializeMiscControls() {
        mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);
        mRecordingTimeView = (TextView) mRootView.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) mRootView.findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = mRootView.findViewById(R.id.time_lapse_label);
        // The R.id.labels can only be found in phone layout.
        // That is, mLabelsLinearLayout should be null in tablet layout.
        mLabelsLinearLayout = (LinearLayout) mRootView.findViewById(R.id.labels);
    }

    public void updateOnScreenIndicators(Parameters param) {
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

    public void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    public void enableShutter(boolean enable) {

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
        } else {
            mRecordingTimeView.setVisibility(View.GONE);
        }
    }

    public void showReviewImage(Bitmap bitmap) {
        mReviewImage.setImageBitmap(bitmap);
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void showReviewControls() {
        customizeButtons(mActivity.getButtonManager(), mFlashCallback, mCameraCallback);
        mActivity.getCameraAppUI().transitionToIntentReviewLayout();
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void hideReviewUI() {
        mReviewImage.setVisibility(View.GONE);
        CameraUtil.fadeOut(mReviewDoneButton);
        CameraUtil.fadeOut(mReviewPlayButton);
    }

    public void initializeZoom(Parameters param) {
        mZoomMax = param.getMaxZoom();
        mZoomRatios = param.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        // TODO: setup zoom through App UI.
        mPreviewOverlay.setupZoom(mZoomMax, param.getZoom(), mZoomRatios, new ZoomChangeListener());
    }

    public void clickShutter() {

    }

    public void pressShutter(boolean pressed) {

    }

    public View getShutterButton() {
        return null;
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

    private class ZoomChangeListener implements PreviewOverlay.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            mController.onZoomChanged(index);
        }

        @Override
        public void onZoomStart() {
        }

        @Override
        public void onZoomEnd() {
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    // SurfaceTexture callbacks
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mSurfaceTexture = surface;
        mController.onPreviewUIReady();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mSurfaceTexture = null;
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

    // SurfaceHolder callbacks
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "Surface changed. width=" + width + ". height=" + height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "Surface created");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "Surface destroyed");
        mController.stopPreview();
    }
}
