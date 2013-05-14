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
import android.graphics.SurfaceTexture;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CameraSwitcher;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.ui.CameraSwitcher.CameraSwitchListener;
import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;

import java.util.List;

public class NewVideoUI implements PieRenderer.PieListener,
        NewPreviewGestures.SingleTapListener,
        SurfaceTextureListener, SurfaceHolder.Callback {
    private final static String TAG = "CAM_VideoUI";
    private static final int UPDATE_TRANSFORM_MATRIX = 1;
    // module fields
    private NewCameraActivity mActivity;
    private View mRootView;
    private TextureView mTextureView;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewPlayButton;
    private ShutterButton mShutterButton;
    private CameraSwitcher mSwitcher;
    private TextView mRecordingTimeView;
    private LinearLayout mLabelsLinearLayout;
    private View mTimeLapseLabel;
    private RenderOverlay mRenderOverlay;
    private PieRenderer mPieRenderer;
    private NewVideoMenu mVideoMenu;
    private View mCameraControls;
    private AbstractSettingPopup mPopup;
    private ZoomRenderer mZoomRenderer;
    private NewPreviewGestures mGestures;
    private View mMenuButton;
    private View mBlocker;
    private View mOnScreenIndicators;
    private ImageView mFlashIndicator;
    private RotateLayout mRecordingTimeRect;
    private final Object mLock = new Object();
    private SurfaceTexture mSurfaceTexture;
    private VideoController mController;
    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private SurfaceView mSurfaceView = null;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;
    private float mAspectRatio = 4f / 3f;
    private Matrix mMatrix = null;
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
            if (Util.getDisplayRotation(mActivity) % 180 != 0) {
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

    public NewVideoUI(NewCameraActivity activity, VideoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mActivity.getLayoutInflater().inflate(R.layout.new_video_module, (ViewGroup) mRootView, true);
        mTextureView = (TextureView) mRootView.findViewById(R.id.preview_content);
        mTextureView.setSurfaceTextureListener(this);
        mRootView.addOnLayoutChangeListener(mLayoutListener);
        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mSwitcher = (CameraSwitcher) mRootView.findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(1);
        mSwitcher.setSwitchListener((CameraSwitchListener) mActivity);
        initializeMiscControls();
        initializeControlByIntent();
        initializeOverlay();
    }


    public void initializeSurfaceView() {
        mSurfaceView = new SurfaceView(mActivity);
        ((ViewGroup) mRootView).addView(mSurfaceView, 0);
        mSurfaceView.getHolder().addCallback(this);
    }

    private void initializeControlByIntent() {
        mBlocker = mActivity.findViewById(R.id.blocker);
        mMenuButton = mActivity.findViewById(R.id.menu);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPieRenderer != null) {
                    mPieRenderer.showInCenter();
                }
            }
        });

        mCameraControls = mActivity.findViewById(R.id.camera_controls);
        mOnScreenIndicators = mActivity.findViewById(R.id.on_screen_indicators);
        mFlashIndicator = (ImageView) mActivity.findViewById(R.id.menu_flash_indicator);
        if (mController.isVideoCaptureIntent()) {
            hideSwitcher();
            mActivity.getLayoutInflater().inflate(R.layout.review_module_control, (ViewGroup) mCameraControls);
            // Cannot use RotateImageView for "done" and "cancel" button because
            // the tablet layout uses RotateLayout, which cannot be cast to
            // RotateImageView.
            mReviewDoneButton = mActivity.findViewById(R.id.btn_done);
            mReviewCancelButton = mActivity.findViewById(R.id.btn_cancel);
            mReviewPlayButton = mActivity.findViewById(R.id.btn_play);
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
        int orientation = Util.getDisplayRotation(mActivity);
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

    public void hideUI() {
        mCameraControls.setVisibility(View.INVISIBLE);
        hideSwitcher();
        mShutterButton.setVisibility(View.GONE);
    }

    public void showUI() {
        mCameraControls.setVisibility(View.VISIBLE);
        showSwitcher();
        mShutterButton.setVisibility(View.VISIBLE);
    }

    public void hideSwitcher() {
        mSwitcher.closePopup();
        mSwitcher.setVisibility(View.INVISIBLE);
    }

    public void showSwitcher() {
        mSwitcher.setVisibility(View.VISIBLE);
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
        if (mGestures != null) {
            mGestures.setZoomOnly(!enable);
        }
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    public void overrideSettings(final String... keyvalues) {
        mVideoMenu.overrideSettings(keyvalues);
    }

    public void setOrientationIndicator(int orientation, boolean animation) {
        if (mGestures != null) {
            mGestures.setOrientation(orientation);
        }
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
            mVideoMenu = new NewVideoMenu(mActivity, this, mPieRenderer);
            mPieRenderer.setPieListener(this);
        }
        mRenderOverlay.addRenderer(mPieRenderer);
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
        }
        mRenderOverlay.addRenderer(mZoomRenderer);
        if (mGestures == null) {
            mGestures = new NewPreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer);
            mRenderOverlay.setGestures(mGestures);
        }
        mGestures.setRenderOverlay(mRenderOverlay);
    }

    public void setPrefChangedListener(OnPreferenceChangedListener listener) {
        mVideoMenu.setListener(listener);
    }

    private void initializeMiscControls() {
        mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
        mShutterButton.requestFocus();
        mShutterButton.enableTouch(true);
        mRecordingTimeView = (TextView) mRootView.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) mRootView.findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = mRootView.findViewById(R.id.time_lapse_label);
        // The R.id.labels can only be found in phone layout.
        // That is, mLabelsLinearLayout should be null in tablet layout.
        mLabelsLinearLayout = (LinearLayout) mRootView.findViewById(R.id.labels);
    }

    public void updateOnScreenIndicators(Parameters param) {
        if (param == null) return;
        String value = param.getFlashMode();
        if (mFlashIndicator == null) return;
        if (value == null || Parameters.FLASH_MODE_OFF.equals(value)) {
            mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_off);
        } else {
            if (Parameters.FLASH_MODE_AUTO.equals(value)) {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_auto);
            } else if (Parameters.FLASH_MODE_ON.equals(value)
                    || Parameters.FLASH_MODE_TORCH.equals(value)) {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_on);
            } else {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_off);
            }
        }
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

    public void showPopup(AbstractSettingPopup popup) {
        hideUI();
        mBlocker.setVisibility(View.INVISIBLE);
        setShowMenu(false);
        mPopup = popup;
        mPopup.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        ((FrameLayout) mRootView).addView(mPopup, lp);
    }

    public void dismissPopup(boolean topLevelOnly) {
        dismissPopup(topLevelOnly, true);
    }

    public void dismissPopup(boolean topLevelPopupOnly, boolean fullScreen) {
        if (fullScreen) {
            showUI();
            mBlocker.setVisibility(View.VISIBLE);
        }
        setShowMenu(fullScreen);
        if (mPopup != null) {
            ((FrameLayout) mRootView).removeView(mPopup);
            mPopup = null;
        }
        mVideoMenu.popupDismissed(topLevelPopupOnly);
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

    // disable preview gestures after shutter is pressed
    public void setShutterPressed(boolean pressed) {
        if (mGestures == null) return;
        mGestures.setEnabled(!pressed);
    }

    public void enableShutter(boolean enable) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enable);
        }
    }

    // PieListener
    @Override
    public void onPieOpened(int centerX, int centerY) {
        // TODO: mActivity.cancelActivityTouchHandling();
        // mActivity.setSwipingEnabled(false);
    }

    @Override
    public void onPieClosed() {
        // TODO: mActivity.setSwipingEnabled(true);
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

    public void showRecordingUI(boolean recording, boolean zoomSupported) {
        mMenuButton.setVisibility(recording ? View.GONE : View.VISIBLE);
        mOnScreenIndicators.setVisibility(recording ? View.GONE : View.VISIBLE);
        if (recording) {
            mShutterButton.setImageResource(R.drawable.btn_shutter_video_recording);
            hideSwitcher();
            mRecordingTimeView.setText("");
            mRecordingTimeView.setVisibility(View.VISIBLE);
            // The camera is not allowed to be accessed in older api levels during
            // recording. It is therefore necessary to hide the zoom UI on older
            // platforms.
            // See the documentation of android.media.MediaRecorder.start() for
            // further explanation.
            if (!ApiHelper.HAS_ZOOM_WHEN_RECORDING && zoomSupported) {
                // TODO: disable zoom UI here.
            }
        } else {
            mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
            showSwitcher();
            mRecordingTimeView.setVisibility(View.GONE);
            if (!ApiHelper.HAS_ZOOM_WHEN_RECORDING && zoomSupported) {
                // TODO: enable zoom UI here.
            }
        }
    }

    public void showReviewImage(Bitmap bitmap) {
        mReviewImage.setImageBitmap(bitmap);
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void showReviewControls() {
        Util.fadeOut(mShutterButton);
        Util.fadeIn(mReviewDoneButton);
        Util.fadeIn(mReviewPlayButton);
        mReviewImage.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.GONE);
        mOnScreenIndicators.setVisibility(View.GONE);
    }

    public void hideReviewUI() {
        mReviewImage.setVisibility(View.GONE);
        mShutterButton.setEnabled(true);
        mMenuButton.setVisibility(View.VISIBLE);
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        Util.fadeOut(mReviewDoneButton);
        Util.fadeOut(mReviewPlayButton);
        Util.fadeIn(mShutterButton);
    }

    private void setShowMenu(boolean show) {
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (mMenuButton != null) {
            mMenuButton.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public void onFullScreenChanged(boolean full) {
        if (mGestures != null) {
            mGestures.setEnabled(full);
        }
        if (mPopup != null) {
            dismissPopup(false, full);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(full ? View.VISIBLE : View.GONE);
        }
        setShowMenu(full);
        if (mBlocker != null) {
            // this can not happen in capture mode
            mBlocker.setVisibility(full ? View.VISIBLE : View.GONE);
        }
    }

    public void initializePopup(PreferenceGroup pref) {
        mVideoMenu.initialize(pref);
    }

    public void initializeZoom(Parameters param) {
        if (param == null || !param.isZoomSupported()) return;
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
        mShutterButton.performClick();
    }

    public void pressShutter(boolean pressed) {
        mShutterButton.setPressed(pressed);
    }

    public View getShutterButton() {
        return mShutterButton;
    }

    // Gestures and touch events

    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mPopup != null || mSwitcher.showsPopup()) {
            boolean handled = mRootView.dispatchTouchEvent(m);
            if (!handled && mPopup != null) {
                dismissPopup(false);
            }
            return handled;
        } else if (mGestures != null && mRenderOverlay != null) {
            if (mGestures.dispatchTouch(m)) {
                return true;
            } else {
                return mRootView.dispatchTouchEvent(m);
            }
        }
        return true;
    }
    public void setRecordingTime(String text) {
        mRecordingTimeView.setText(text);
    }

    public void setRecordingTimeTextColor(int color) {
        mRecordingTimeView.setTextColor(color);
    }

    public boolean isVisible() {
        return mTextureView.getVisibility() == View.VISIBLE;
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
        synchronized (mLock) {
            if (mSurfaceTexture == null) {
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    Log.w(TAG, "Unexpected interruption when waiting to get surface texture");
                }
            }
        }
        return mSurfaceTexture;
    }

    // SurfaceTexture callbacks
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        synchronized (mLock) {
            mSurfaceTexture = surface;
            mLock.notifyAll();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mSurfaceTexture = null;
        mController.stopPreview();
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
