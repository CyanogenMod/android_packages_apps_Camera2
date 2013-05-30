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
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.PreviewSurfaceView;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.ZoomRenderer;
import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;

import java.util.List;

public class VideoUI implements SurfaceHolder.Callback, PieRenderer.PieListener,
        PreviewGestures.SingleTapListener,
        PreviewGestures.SwipeListener {
    private final static String TAG = "CAM_VideoUI";
    // module fields
    private CameraActivity mActivity;
    private View mRootView;
    private PreviewFrameLayout mPreviewFrameLayout;
    private boolean mSurfaceViewReady;
    private PreviewSurfaceView mPreviewSurfaceView;
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewPlayButton;
    private ShutterButton mShutterButton;
    private TextView mRecordingTimeView;
    private LinearLayout mLabelsLinearLayout;
    private View mTimeLapseLabel;
    private RenderOverlay mRenderOverlay;
    private PieRenderer mPieRenderer;
    private VideoMenu mVideoMenu;
    private AbstractSettingPopup mPopup;
    private ZoomRenderer mZoomRenderer;
    private PreviewGestures mGestures;
    private View mMenu;
    private View mBlocker;
    private OnScreenIndicators mOnScreenIndicators;
    private RotateLayout mRecordingTimeRect;
    private VideoController mController;
    private int mZoomMax;
    private List<Integer> mZoomRatios;
    private View mPreviewThumb;

    public VideoUI(CameraActivity activity, VideoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;
        mActivity.getLayoutInflater().inflate(R.layout.video_module, (ViewGroup) mRootView, true);
        mPreviewSurfaceView = (PreviewSurfaceView) mRootView
                .findViewById(R.id.preview_surface_view);
        initializeMiscControls();
        initializeControlByIntent();
        initializeOverlay();
    }

    private void initializeControlByIntent() {
        mBlocker = mActivity.findViewById(R.id.blocker);
        mMenu = mActivity.findViewById(R.id.menu);
        mMenu.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPieRenderer != null) {
                    mPieRenderer.showInCenter();
                }
            }
        });
        mOnScreenIndicators = new OnScreenIndicators(mActivity,
                mActivity.findViewById(R.id.on_screen_indicators));
        mOnScreenIndicators.resetToDefault();
        if (mController.isVideoCaptureIntent()) {
            mActivity.hideSwitcher();
            ViewGroup cameraControls = (ViewGroup) mActivity.findViewById(R.id.camera_controls);
            mActivity.getLayoutInflater().inflate(R.layout.review_module_control, cameraControls);
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

    public View getPreview() {
        return mPreviewFrameLayout;
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
        return mPreviewSurfaceView.getHolder();
    }

    public void hideSurfaceView() {
        mPreviewSurfaceView.setVisibility(View.GONE);
    }

    public void showSurfaceView() {
        mPreviewSurfaceView.setVisibility(View.VISIBLE);
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
        if (mGestures == null) {
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer, this);
        }
        mGestures.setRenderOverlay(mRenderOverlay);
        mGestures.reset();
        mGestures.addTouchReceiver(mMenu);
        mGestures.addUnclickableArea(mBlocker);
        if (mController.isVideoCaptureIntent()) {
            if (mReviewCancelButton != null) {
                mGestures.addTouchReceiver(mReviewCancelButton);
            }
            if (mReviewDoneButton != null) {
                mGestures.addTouchReceiver(mReviewDoneButton);
            }
            if (mReviewPlayButton != null) {
                mGestures.addTouchReceiver(mReviewPlayButton);
            }
        }

        mPreviewThumb = mActivity.findViewById(R.id.preview_thumb);
        mPreviewThumb.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.gotoGallery();
            }
        });
    }

    public void setPrefChangedListener(OnPreferenceChangedListener listener) {
        mVideoMenu.setListener(listener);
    }

    private void initializeMiscControls() {
        mPreviewFrameLayout = (PreviewFrameLayout) mRootView.findViewById(R.id.frame);
        mPreviewFrameLayout.setOnLayoutChangeListener(mActivity);
        mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);
        mShutterButton = mActivity.getShutterButton();
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

    public void updateOnScreenIndicators(Parameters param, ComboPreferences prefs) {
      mOnScreenIndicators.updateFlashOnScreenIndicator(param.getFlashMode());
      boolean location = RecordLocationPreference.get(
              prefs, mActivity.getContentResolver());
      mOnScreenIndicators.updateLocationIndicator(location);

    }

    public void setAspectRatio(double ratio) {
        mPreviewFrameLayout.setAspectRatio(ratio);
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
        mActivity.hideUI();
        mBlocker.setVisibility(View.INVISIBLE);
        setShowMenu(false);
        mPopup = popup;
        mPopup.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        ((FrameLayout) mRootView).addView(mPopup, lp);
        mGestures.addTouchReceiver(mPopup);
    }

    public void dismissPopup(boolean topLevelOnly) {
        dismissPopup(topLevelOnly, true);
    }

    public void dismissPopup(boolean topLevelPopupOnly, boolean fullScreen) {
        // In review mode, we do not want to bring up the camera UI
        if (mController.isInReviewMode()) return;

        if (fullScreen) {
            mActivity.showUI();
            mBlocker.setVisibility(View.VISIBLE);
        }
        setShowMenu(fullScreen);
        if (mPopup != null) {
            mGestures.removeTouchReceiver(mPopup);
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
        dismissPopup(false, true);
        mActivity.cancelActivityTouchHandling();
        mActivity.setSwipingEnabled(false);
    }

    @Override
    public void onPieClosed() {
        mActivity.setSwipingEnabled(true);
    }

    public void showPreviewBorder(boolean enable) {
        mPreviewFrameLayout.showBorder(enable);
    }

    // SingleTapListener
    // Preview area is touched. Take a picture.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
    }

    // SurfaceView callback
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "Surface changed. width=" + width + ". height=" + height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "Surface created");
        mSurfaceViewReady = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "Surface destroyed");
        mSurfaceViewReady = false;
        mController.stopPreview();
    }

    public boolean isSurfaceViewReady() {
        return mSurfaceViewReady;
    }

    public void showRecordingUI(boolean recording, boolean zoomSupported) {
        mMenu.setVisibility(recording ? View.GONE : View.VISIBLE);
        mOnScreenIndicators.setVisibility(recording ? View.GONE : View.VISIBLE);
        if (recording) {
            mShutterButton.setImageResource(R.drawable.btn_shutter_video_recording);
            mActivity.hideSwitcher();
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
            mActivity.showSwitcher();
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
        mMenu.setVisibility(View.GONE);
        mOnScreenIndicators.setVisibility(View.GONE);
    }

    public void hideReviewUI() {
        mReviewImage.setVisibility(View.GONE);
        mShutterButton.setEnabled(true);
        mMenu.setVisibility(View.VISIBLE);
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        Util.fadeOut(mReviewDoneButton);
        Util.fadeOut(mReviewPlayButton);
        Util.fadeIn(mShutterButton);
    }

    private void setShowMenu(boolean show) {
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (mMenu != null) {
            mMenu.setVisibility(show ? View.VISIBLE : View.GONE);
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

    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mGestures != null && mRenderOverlay != null) {
            return mGestures.dispatchTouch(m);
        }
        return false;
    }

    public void setRecordingTime(String text) {
        mRecordingTimeView.setText(text);
    }

    public void setRecordingTimeTextColor(int color) {
        mRecordingTimeView.setTextColor(color);
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

    @Override
    public void onSwipe(int direction) {
        if (direction == PreviewGestures.DIR_UP) {
            openMenu();
        }
    }

    /**
     * Enable or disable the preview thumbnail for click events.
     */
    public void enablePreviewThumb(boolean enabled) {
        if (enabled) {
            mGestures.addTouchReceiver(mPreviewThumb);
            mPreviewThumb.setVisibility(View.VISIBLE);
        } else {
            mGestures.removeTouchReceiver(mPreviewThumb);
            mPreviewThumb.setVisibility(View.GONE);
        }
    }
}
