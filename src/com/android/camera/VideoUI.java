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
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera.Parameters;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CameraRootView;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

import java.util.List;

public class VideoUI implements PieRenderer.PieListener,
        PreviewGestures.SingleTapListener,
        CameraRootView.MyDisplayListener,
        SurfaceTextureListener, SurfaceHolder.Callback {
    private static final String TAG = "CAM_VideoUI";
    private static final int UPDATE_TRANSFORM_MATRIX = 1;
    // module fields
    private CameraActivity mActivity;
    private View mRootView;
    private TextureView mTextureView;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewPlayButton;
    private TextView mRecordingTimeView;
    private LinearLayout mLabelsLinearLayout;
    private View mTimeLapseLabel;
    private RenderOverlay mRenderOverlay;
    private PieRenderer mPieRenderer;
    private VideoMenu mVideoMenu;
    private SettingsPopup mPopup;
    private ZoomRenderer mZoomRenderer;
    private RotateLayout mRecordingTimeRect;
    private boolean mRecordingStarted = false;
    private SurfaceTexture mSurfaceTexture;
    private VideoController mController;
    private int mZoomMax;
    private List<Integer> mZoomRatios;
    private View mFlashOverlay;

    private View mBottomBar;
    private View mPreviewCover;
    private SurfaceView mSurfaceView = null;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;
    private float mAspectRatio = 4f / 3f;
    private Matrix mMatrix = null;
    private final AnimationManager mAnimationManager;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_TRANSFORM_MATRIX:
                    setTransformMatrix(mPreviewWidth, mPreviewHeight);
                    break;
                default:
                    break;
            }
        }
    };
    private OnLayoutChangeListener mLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right,
                int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            int width = right - left;
            int height = bottom - top;
            // Full-screen screennail
            int w = width;
            int h = height;
            if (CameraUtil.getDisplayRotation(mActivity) % 180 != 0) {
                w = height;
                h = width;
            }
            if (mPreviewWidth != width || mPreviewHeight != height) {
                mPreviewWidth = width;
                mPreviewHeight = height;
                onScreenSizeChanged(width, height, w, h);
            }
        }
    };

    public void showPreviewCover() {
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    private class SettingsPopup extends PopupWindow {
        public SettingsPopup(View popup) {
            super(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            setOutsideTouchable(true);
            setFocusable(true);
            popup.setVisibility(View.VISIBLE);
            setContentView(popup);
            showAtLocation(mRootView, Gravity.CENTER, 0, 0);
        }

        public void dismiss(boolean topLevelOnly) {
            super.dismiss();
            popupDismissed();
            showUI();

            // Switch back into fullscreen/lights-out mode after popup
            // is dimissed.
            mActivity.setSystemBarsVisibility(false);
        }

        @Override
        public void dismiss() {
            // Called by Framework when touch outside the popup or hit back key
            dismiss(true);
        }
    }

    public VideoUI(CameraActivity activity, VideoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        ViewGroup moduleRoot = (ViewGroup) mRootView.findViewById(R.id.module_layout);
        mActivity.getLayoutInflater().inflate(R.layout.video_module,
                (ViewGroup) moduleRoot, true);

        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        mRenderOverlay.setTapListener(this);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        mTextureView = (TextureView) mRootView.findViewById(R.id.preview_content);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.addOnLayoutChangeListener(mLayoutListener);

        mSurfaceTexture = mTextureView.getSurfaceTexture();
        if (mSurfaceTexture != null) {
            setTransformMatrix(mTextureView.getWidth(), mTextureView.getHeight());
            mPreviewCover.setVisibility(View.GONE);
        }

        mSurfaceTexture = mTextureView.getSurfaceTexture();
        if (mSurfaceTexture != null) {
            mPreviewWidth = mTextureView.getWidth();
            mPreviewHeight = mTextureView.getHeight();
            setTransformMatrix(mPreviewWidth, mPreviewHeight);
            mPreviewCover.setVisibility(View.GONE);
        }
        mFlashOverlay = mRootView.findViewById(R.id.flash_overlay);
        initializeMiscControls();
        initializeControlByIntent();
        initializeOverlay();
        mAnimationManager = new AnimationManager();

        mBottomBar = mRootView.findViewById(R.id.bottom_bar);
        mBottomBar.setBackgroundColor(activity.getResources().getColor(R.color.video_mode_color));
    }


    public void initializeSurfaceView() {
        mSurfaceView = new SurfaceView(mActivity);
        ((ViewGroup) mRootView).addView(mSurfaceView, 0);
        mSurfaceView.getHolder().addCallback(this);
    }

    private void initializeControlByIntent() {
        if (mController.isVideoCaptureIntent()) {
            // Cannot use RotateImageView for "done" and "cancel" button because
            // the tablet layout uses RotateLayout, which cannot be cast to
            // RotateImageView.
            mReviewDoneButton = mRootView.findViewById(R.id.btn_done);
            mReviewCancelButton = mRootView.findViewById(R.id.btn_cancel);
            mReviewPlayButton = mRootView.findViewById(R.id.btn_play);
            mReviewCancelButton.setVisibility(View.VISIBLE);
            mReviewDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewDoneClicked(v);
                }
            });
            mReviewCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewCancelClicked(v);
                }
            });
            mReviewPlayButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewPlayClicked(v);
                }
            });
        }
    }

    public void setPreviewSize(int width, int height) {
        if (width == 0 || height == 0) {
            Log.w(TAG, "Preview size should not be 0.");
            return;
        }
        if (width > height) {
            mAspectRatio = (float) width / height;
        } else {
            mAspectRatio = (float) height / width;
        }
        mHandler.sendEmptyMessage(UPDATE_TRANSFORM_MATRIX);
    }

    public int getPreviewWidth() {
        return mPreviewWidth;
    }

    public int getPreviewHeight() {
        return mPreviewHeight;
    }

    public void onScreenSizeChanged(int width, int height, int previewWidth, int previewHeight) {
        setTransformMatrix(width, height);
    }

    private void setTransformMatrix(int width, int height) {
        mMatrix = mTextureView.getTransform(mMatrix);
        int orientation = CameraUtil.getDisplayRotation(mActivity);
        float scaleX = 1f, scaleY = 1f;
        float scaledTextureWidth, scaledTextureHeight;
        if (width > height) {
            scaledTextureWidth = Math.max(width,
                    (int) (height * mAspectRatio));
            scaledTextureHeight = Math.max(height,
                    (int)(width / mAspectRatio));
        } else {
            scaledTextureWidth = Math.max(width,
                    (int) (height / mAspectRatio));
            scaledTextureHeight = Math.max(height,
                    (int) (width * mAspectRatio));
        }

        if (mSurfaceTextureUncroppedWidth != scaledTextureWidth ||
                mSurfaceTextureUncroppedHeight != scaledTextureHeight) {
            mSurfaceTextureUncroppedWidth = scaledTextureWidth;
            mSurfaceTextureUncroppedHeight = scaledTextureHeight;
        }
        scaleX = scaledTextureWidth / width;
        scaleY = scaledTextureHeight / height;
        mMatrix.setScale(scaleX, scaleY, (float) width / 2, (float) height / 2);
        mTextureView.setTransform(mMatrix);

        if (mSurfaceView != null && mSurfaceView.getVisibility() == View.VISIBLE) {
            LayoutParams lp = (LayoutParams) mSurfaceView.getLayoutParams();
            lp.width = (int) mSurfaceTextureUncroppedWidth;
            lp.height = (int) mSurfaceTextureUncroppedHeight;
            lp.gravity = Gravity.CENTER;
            mSurfaceView.requestLayout();
        }
    }

    /**
     * Starts a flash animation
     */
    public void animateFlash() {
        mAnimationManager.startFlashAnimation(mFlashOverlay);
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

    public void hideUI() {

    }

    public void showUI() {

    }

    public boolean arePreviewControlsVisible() {
        return false;
    }

    public boolean collapseCameraControls() {
        boolean ret = false;
        if (mPopup != null) {
            dismissPopup(false);
            ret = true;
        }
        return ret;
    }

    public boolean removeTopLevelPopup() {
        if (mPopup != null) {
            dismissPopup(true);
            return true;
        }
        return false;
    }

    public void enableCameraControls(boolean enable) {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    public void initDisplayChangeListener() {
        ((CameraRootView) mRootView).setDisplayChangeListener(this);
    }

    public void removeDisplayChangeListener() {
        ((CameraRootView) mRootView).removeDisplayChangeListener();
    }

    public void overrideSettings(final String... keyvalues) {
        mVideoMenu.overrideSettings(keyvalues);
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
        setTransformMatrix(mPreviewWidth, mPreviewHeight);
    }

    public void showSurfaceView() {
        mSurfaceView.setVisibility(View.VISIBLE);
        mTextureView.setVisibility(View.GONE);
        setTransformMatrix(mPreviewWidth, mPreviewHeight);
    }

    private void initializeOverlay() {
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mVideoMenu = new VideoMenu(mActivity, this, mPieRenderer);
            mPieRenderer.setPieListener(this);
        }
        mRenderOverlay.addRenderer(mPieRenderer);
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
        }
        mRenderOverlay.addRenderer(mZoomRenderer);
        mRenderOverlay.setGestures(null);
    }

    public void setPrefChangedListener(OnPreferenceChangedListener listener) {
        mVideoMenu.setListener(listener);
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

    public void setAspectRatio(double ratio) {
      //  mPreviewFrameLayout.setAspectRatio(ratio);
    }

    public void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    private void openMenu() {
        if (mPieRenderer != null) {
            mPieRenderer.showInCenter();
        }
    }

    public void dismissPopup(boolean topLevelOnly) {
        // In review mode, we do not want to bring up the camera UI
        if (mController.isInReviewMode()) return;
        if (mPopup != null) {
            mPopup.dismiss(topLevelOnly);
        }
    }

    private void popupDismissed() {
        mPopup = null;
    }

    public void showPopup(AbstractSettingPopup popup) {
        hideUI();

        if (mPopup != null) {
            mPopup.dismiss(false);
        }
        mPopup = new SettingsPopup(popup);
    }

    public void onShowSwitcherPopup() {
        hidePieRenderer();
    }

    public boolean hidePieRenderer() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }
        return false;
    }

    public void enableShutter(boolean enable) {

    }

    // PieListener
    @Override
    public void onPieOpened(int centerX, int centerY) {
        setSwipingEnabled(false);
    }

    @Override
    public void onPieClosed() {
        setSwipingEnabled(true);
    }

    public void setSwipingEnabled(boolean enable) {
        mActivity.setSwipingEnabled(enable);
    }

    public void showPreviewBorder(boolean enable) {
       // TODO: mPreviewFrameLayout.showBorder(enable);
    }

    // SingleTapListener
    // Preview area is touched. Take a picture.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
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
        CameraUtil.fadeIn(mReviewDoneButton);
        CameraUtil.fadeIn(mReviewPlayButton);
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void hideReviewUI() {
        mReviewImage.setVisibility(View.GONE);
        CameraUtil.fadeOut(mReviewDoneButton);
        CameraUtil.fadeOut(mReviewPlayButton);
    }

    private void setShowMenu(boolean show) {

    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }

        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
        setShowMenu(previewFocused);
    }

    public void initializePopup(PreferenceGroup pref) {
        mVideoMenu.initialize(pref);
    }

    public void initializeZoom(Parameters param) {
        mZoomMax = param.getMaxZoom();
        mZoomRatios = param.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomRenderer.setZoomMax(mZoomMax);
        mZoomRenderer.setZoom(param.getZoom());
        mZoomRenderer.setZoomValue(mZoomRatios.get(param.getZoom()));
        mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
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
    public void onDisplayChanged() {
        mController.updateCameraOrientation();
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            int newZoom = mController.onZoomChanged(index);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoomValue(mZoomRatios.get(newZoom));
            }
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
        // Make sure preview cover is hidden if preview data is available.
        if (mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
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
