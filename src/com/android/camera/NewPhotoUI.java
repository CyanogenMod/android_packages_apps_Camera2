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
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CameraSwitcher.CameraSwitchListener;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.CountDownView.OnCountDownFinishedListener;
import com.android.camera.ui.CameraSwitcher;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.PieRenderer.PieListener;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.ZoomRenderer;
import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;

import java.io.IOException;
import java.util.List;

public class NewPhotoUI implements PieListener,
    NewPreviewGestures.SingleTapListener,
    NewPreviewGestures.CancelEventListener,
    FocusUI, TextureView.SurfaceTextureListener,
    LocationManager.Listener,
    FaceDetectionListener,
    NewPreviewGestures.SwipeListener {

    private static final String TAG = "CAM_UI";
    private static final int UPDATE_TRANSFORM_MATRIX = 1;
    private NewCameraActivity mActivity;
    private PhotoController mController;
    private NewPreviewGestures mGestures;

    private View mRootView;
    private Object mSurfaceTexture;

    private AbstractSettingPopup mPopup;
    private ShutterButton mShutterButton;
    private CountDownView mCountDownView;

    private FaceView mFaceView;
    private RenderOverlay mRenderOverlay;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewRetakeButton;

    private View mMenuButton;
    private View mBlocker;
    private NewPhotoMenu mMenu;
    private CameraSwitcher mSwitcher;
    private View mCameraControls;

    // Small indicators which show the camera settings in the viewfinder.
    private ImageView mExposureIndicator;
    private ImageView mFlashIndicator;
    private ImageView mSceneIndicator;
    private ImageView mHdrIndicator;
    // A view group that contains all the small indicators.
    private View mOnScreenIndicators;

    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;
    private Toast mNotSelectableToast;

    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;

    private SurfaceTextureSizeChangedListener mSurfaceTextureSizeListener;
    private TextureView mTextureView;
    private Matrix mMatrix = null;
    private float mAspectRatio = 4f / 3f;
    private final Object mLock = new Object();
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

    public interface SurfaceTextureSizeChangedListener {
        public void onSurfaceTextureSizeChanged(int uncroppedWidth, int uncroppedHeight);
    }

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
                mController.onScreenSizeChanged(width, height, w, h);
            }
        }
    };

    public NewPhotoUI(NewCameraActivity activity, PhotoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;

        mActivity.getLayoutInflater().inflate(R.layout.new_photo_module,
                (ViewGroup) mRootView, true);
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        // display the view
        mTextureView = (TextureView) mRootView.findViewById(R.id.preview_content);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.addOnLayoutChangeListener(mLayoutListener);
        initIndicators();

        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mSwitcher = (CameraSwitcher) mRootView.findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(0);
        mSwitcher.setSwitchListener((CameraSwitchListener) mActivity);
        mMenuButton = mRootView.findViewById(R.id.menu);
        if (ApiHelper.HAS_FACE_DETECTION) {
            ViewStub faceViewStub = (ViewStub) mRootView
                    .findViewById(R.id.face_view_stub);
            if (faceViewStub != null) {
                faceViewStub.inflate();
                mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
                setSurfaceTextureSizeChangedListener(
                        (SurfaceTextureSizeChangedListener) mFaceView);
            }
        }
        mCameraControls = mRootView.findViewById(R.id.camera_controls);
    }

    public void onScreenSizeChanged(int width, int height, int previewWidth, int previewHeight) {
        setTransformMatrix(width, height);
    }

    public void setSurfaceTextureSizeChangedListener(SurfaceTextureSizeChangedListener listener) {
        mSurfaceTextureSizeListener = listener;
    }

    public void setPreviewSize(Size size) {
        int width = size.width;
        int height = size.height;
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
            if (mSurfaceTextureSizeListener != null) {
                mSurfaceTextureSizeListener.onSurfaceTextureSizeChanged(
                        (int) mSurfaceTextureUncroppedWidth, (int) mSurfaceTextureUncroppedHeight);
            }
        }
        scaleX = scaledTextureWidth / width;
        scaleY = scaledTextureHeight / height;
        mMatrix.setScale(scaleX, scaleY, (float) width / 2, (float) height / 2);
        mTextureView.setTransform(mMatrix);
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        synchronized (mLock) {
            mSurfaceTexture = surface;
            mLock.notifyAll();
        }
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored, Camera does all the work for us
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mSurfaceTexture = null;
        mController.stopPreview();
        Log.w(TAG, "surfaceTexture is destroyed");
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Invoked every time there's a new Camera preview frame
    }

    public View getRootView() {
        return mRootView;
    }

    private void initIndicators() {
        mOnScreenIndicators = mRootView.findViewById(R.id.on_screen_indicators);
        mExposureIndicator = (ImageView) mOnScreenIndicators.findViewById(R.id.menu_exposure_indicator);
        mFlashIndicator = (ImageView) mOnScreenIndicators.findViewById(R.id.menu_flash_indicator);
        mSceneIndicator = (ImageView) mOnScreenIndicators.findViewById(R.id.menu_scenemode_indicator);
        mHdrIndicator = (ImageView) mOnScreenIndicators.findViewById(R.id.menu_hdr_indicator);
    }

    public void onCameraOpened(PreferenceGroup prefGroup, ComboPreferences prefs,
            Camera.Parameters params, OnPreferenceChangedListener listener) {
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mPieRenderer.setPieListener(this);
        }

        if (mMenu == null) {
            mMenu = new NewPhotoMenu(mActivity, this, mPieRenderer);
            mMenu.setListener(listener);
        }
        mMenu.initialize(prefGroup);

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
        }
        mRenderOverlay.addRenderer(mPieRenderer);
        mRenderOverlay.addRenderer(mZoomRenderer);

        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new NewPreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer,
                    this);
            mGestures.setCancelEventListener(this);
        }
        mGestures.clearTouchReceivers();
        mGestures.setRenderOverlay(mRenderOverlay);
        mGestures.addTouchReceiver(mMenuButton);
        mGestures.addTouchReceiver(mBlocker);
        // make sure to add touch targets for image capture
        if (mController.isImageCaptureIntent()) {
            if (mReviewCancelButton != null) {
                mGestures.addTouchReceiver(mReviewCancelButton);
            }
            if (mReviewDoneButton != null) {
                mGestures.addTouchReceiver(mReviewDoneButton);
            }
        }
        mRenderOverlay.requestLayout();

        initializeZoom(params);
        updateOnScreenIndicators(params, prefs);
    }

    private void openMenu() {
        if (mPieRenderer != null) {
            // If autofocus is not finished, cancel autofocus so that the
            // subsequent touch can be handled by PreviewGestures
            if (mController.getCameraState() == PhotoController.FOCUSING) {
                    mController.cancelAutoFocus();
            }
            mPieRenderer.showInCenter();
        }
    }

    public void initializeControlByIntent() {
        mBlocker = mRootView.findViewById(R.id.blocker);
        mMenuButton = mRootView.findViewById(R.id.menu);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openMenu();
            }
        });
        if (mController.isImageCaptureIntent()) {
            hideSwitcher();
            ViewGroup cameraControls = (ViewGroup) mRootView.findViewById(R.id.camera_controls);
            mActivity.getLayoutInflater().inflate(R.layout.review_module_control, cameraControls);

            mReviewDoneButton = mRootView.findViewById(R.id.btn_done);
            mReviewCancelButton = mRootView.findViewById(R.id.btn_cancel);
            mReviewRetakeButton = mRootView.findViewById(R.id.btn_retake);
            mReviewCancelButton.setVisibility(View.VISIBLE);

            mReviewDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureDone();
                }
            });
            mReviewCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureCancelled();
                }
            });

            mReviewRetakeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureRetake();
                }
            });
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

    // called from onResume but only the first time
    public  void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setImageResource(R.drawable.btn_new_shutter);
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
    }

    // called from onResume every other time
    public void initializeSecondTime(Camera.Parameters params) {
        initializeZoom(params);
        if (mController.isImageCaptureIntent()) {
            hidePostCaptureAlert();
        }
        if (mMenu != null) {
            mMenu.reloadPreferences();
        }
    }

    public void initializeZoom(Camera.Parameters params) {
        if ((params == null) || !params.isZoomSupported()
                || (mZoomRenderer == null)) return;
        mZoomMax = params.getMaxZoom();
        mZoomRatios = params.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        if (mZoomRenderer != null) {
            mZoomRenderer.setZoomMax(mZoomMax);
            mZoomRenderer.setZoom(params.getZoom());
            mZoomRenderer.setZoomValue(mZoomRatios.get(params.getZoom()));
            mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
        }
    }

    public void showGpsOnScreenIndicator(boolean hasSignal) { }

    public void hideGpsOnScreenIndicator() { }

    public void overrideSettings(final String ... keyvalues) {
        mMenu.overrideSettings(keyvalues);
    }

    public void updateOnScreenIndicators(Camera.Parameters params,
            ComboPreferences prefs) {
        if (params == null) return;
        updateSceneOnScreenIndicator(params.getSceneMode());
        updateExposureOnScreenIndicator(params,
                CameraSettings.readExposure(prefs));
        updateFlashOnScreenIndicator(params.getFlashMode());
    }

    private void updateExposureOnScreenIndicator(Camera.Parameters params, int value) {
        if (mExposureIndicator == null) {
            return;
        }
        int id = 0;
        float step = params.getExposureCompensationStep();
        value = (int) Math.round(value * step);
        switch(value) {
        case -3:
            id = R.drawable.ic_indicator_ev_n3;
            break;
        case -2:
            id = R.drawable.ic_indicator_ev_n2;
            break;
        case -1:
            id = R.drawable.ic_indicator_ev_n1;
            break;
        case 0:
            id = R.drawable.ic_indicator_ev_0;
            break;
        case 1:
            id = R.drawable.ic_indicator_ev_p1;
            break;
        case 2:
            id = R.drawable.ic_indicator_ev_p2;
            break;
        case 3:
            id = R.drawable.ic_indicator_ev_p3;
            break;
        }
        mExposureIndicator.setImageResource(id);
    }

    private void updateFlashOnScreenIndicator(String value) {
        if (mFlashIndicator == null) {
            return;
        }
        if (value == null || Parameters.FLASH_MODE_OFF.equals(value)) {
            mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_off);
        } else {
            if (Parameters.FLASH_MODE_AUTO.equals(value)) {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_auto);
            } else if (Parameters.FLASH_MODE_ON.equals(value)) {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_on);
            } else {
                mFlashIndicator.setImageResource(R.drawable.ic_indicator_flash_off);
            }
        }
    }

    private void updateSceneOnScreenIndicator(String value) {
        if (mSceneIndicator == null) {
            return;
        }
        if ((value == null) || Parameters.SCENE_MODE_AUTO.equals(value)
                || Parameters.SCENE_MODE_HDR.equals(value)) {
            mSceneIndicator.setImageResource(R.drawable.ic_indicator_sce_off);
        } else {
            mSceneIndicator.setImageResource(R.drawable.ic_indicator_sce_on);
        }
    }

    public void setCameraState(int state) {
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

    @Override
    public void onTouchEventCancelled(MotionEvent cancelEvent) {
        mRootView.dispatchTouchEvent(cancelEvent);
    }

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    // forward from preview gestures to controller
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
    }

    public boolean onBackPressed() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }
        // In image capture mode, back button should:
        // 1) if there is any popup, dismiss them, 2) otherwise, get out of
        // image capture
        if (mController.isImageCaptureIntent()) {
            if (!removeTopLevelPopup()) {
                // no popup to dismiss, cancel image capture
                mController.onCaptureCancelled();
            }
            return true;
        } else if (!mController.isCameraIdle()) {
            // ignore backs while we're taking a picture
            return true;
        } else {
            return removeTopLevelPopup();
        }
    }

    public void onFullScreenChanged(boolean full) {
        if (mFaceView != null) {
            mFaceView.setBlockDraw(!full);
        }
        if (mPopup != null) {
            dismissPopup(false, full);
        }
        if (mGestures != null) {
            mGestures.setEnabled(full);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(full ? View.VISIBLE : View.GONE);
        }
        if (mPieRenderer != null) {
            mPieRenderer.setBlockFocus(!full);
        }
        setShowMenu(full);
        if (mBlocker != null) {
            mBlocker.setVisibility(full ? View.VISIBLE : View.GONE);
        }
        if (!full && mCountDownView != null) mCountDownView.cancelCountDown();
    }

    public boolean removeTopLevelPopup() {
        // Remove the top level popup or dialog box and return true if there's any
        if (mPopup != null) {
            dismissPopup(true);
            return true;
        }
        return false;
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

    public void dismissPopup(boolean topPopupOnly) {
        dismissPopup(topPopupOnly, true);
    }

    private void dismissPopup(boolean topOnly, boolean fullScreen) {
        if (fullScreen) {
            showUI();
            mBlocker.setVisibility(View.VISIBLE);
        }
        setShowMenu(fullScreen);
        if (mPopup != null) {
            ((FrameLayout) mRootView).removeView(mPopup);
            mPopup = null;
        }
        mMenu.popupDismissed(topOnly);
    }

    public void onShowSwitcherPopup() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    private void setShowMenu(boolean show) {
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (mMenuButton != null) {
            mMenuButton.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public boolean collapseCameraControls() {
        // Remove all the popups/dialog boxes
        boolean ret = false;
        if (mPopup != null) {
            dismissPopup(false);
            ret = true;
        }
        return ret;
    }

    protected void showPostCaptureAlert() {
        mOnScreenIndicators.setVisibility(View.GONE);
        mMenuButton.setVisibility(View.GONE);
        Util.fadeIn(mReviewDoneButton);
        mShutterButton.setVisibility(View.INVISIBLE);
        Util.fadeIn(mReviewRetakeButton);
    }

    protected void hidePostCaptureAlert() {
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.VISIBLE);
        Util.fadeOut(mReviewDoneButton);
        mShutterButton.setVisibility(View.VISIBLE);
        Util.fadeOut(mReviewRetakeButton);
    }

    public void setDisplayOrientation(int orientation) {
        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(orientation);
        }
    }

    // shutter button handling

    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    public void pressShutterButton() {
        if (mShutterButton.isInTouchMode()) {
            mShutterButton.requestFocusFromTouch();
        } else {
            mShutterButton.requestFocus();
        }
        mShutterButton.setPressed(true);
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
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(true);
            }
        }

        @Override
        public void onZoomEnd() {
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(false);
            }
        }
    }

    @Override
    public void onPieOpened(int centerX, int centerY) {
      //TODO:   mActivity.cancelActivityTouchHandling();
      //TODO:    mActivity.setSwipingEnabled(false);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
        }
    }

    @Override
    public void onPieClosed() {
      //TODO:     mActivity.setSwipingEnabled(true);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(false);
        }
    }

    public Object getSurfaceTexture() {
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

    // Countdown timer

    private void initializeCountDown() {
        mActivity.getLayoutInflater().inflate(R.layout.count_down_to_capture,
                (ViewGroup) mRootView, true);
        mCountDownView = (CountDownView) (mRootView.findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener((OnCountDownFinishedListener) mController);
    }

    public boolean isCountingDown() {
        return mCountDownView != null && mCountDownView.isCountingDown();
    }

    public void cancelCountDown() {
        if (mCountDownView == null) return;
        mCountDownView.cancelCountDown();
    }

    public void startCountDown(int sec, boolean playSound) {
        if (mCountDownView == null) initializeCountDown();
        mCountDownView.startCountDown(sec, playSound);
    }

    public void showPreferencesToast() {
        if (mNotSelectableToast == null) {
            String str = mActivity.getResources().getString(R.string.not_selectable_in_scene_mode);
            mNotSelectableToast = Toast.makeText(mActivity, str, Toast.LENGTH_SHORT);
        }
        mNotSelectableToast.show();
    }

    public void onPause() {
        cancelCountDown();

        // Clear UI.
        collapseCameraControls();
        if (mFaceView != null) mFaceView.clear();

        mPreviewWidth = 0;
        mPreviewHeight = 0;
    }

    // focus UI implementation

    private FocusIndicator getFocusIndicator() {
        return (mFaceView != null && mFaceView.faceExists()) ? mFaceView : mPieRenderer;
    }

    @Override
    public boolean hasFaces() {
        return (mFaceView != null && mFaceView.faceExists());
    }

    public void clearFaces() {
        if (mFaceView != null) mFaceView.clear();
    }

    @Override
    public void clearFocus() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.clear();
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mPieRenderer.setFocus(x, y);
    }

    @Override
    public void onFocusStarted() {
        getFocusIndicator().showStart();
    }

    @Override
    public void onFocusSucceeded(boolean timeout) {
        getFocusIndicator().showSuccess(timeout);
    }

    @Override
    public void onFocusFailed(boolean timeout) {
        getFocusIndicator().showFail(timeout);
    }

    @Override
    public void pauseFaceDetection() {
        if (mFaceView != null) mFaceView.pause();
    }

    @Override
    public void resumeFaceDetection() {
        if (mFaceView != null) mFaceView.resume();
    }

    public void onStartFaceDetection(int orientation, boolean mirror) {
        mFaceView.clear();
        mFaceView.setVisibility(View.VISIBLE);
        mFaceView.setDisplayOrientation(orientation);
        mFaceView.setMirror(mirror);
        mFaceView.resume();
    }

    @Override
    public void onFaceDetection(Face[] faces, android.hardware.Camera camera) {
        mFaceView.setFaces(faces);
    }

    @Override
    public void onSwipe(int direction) {
        if (direction == PreviewGestures.DIR_UP) {
            openMenu();
        }
    }
}
