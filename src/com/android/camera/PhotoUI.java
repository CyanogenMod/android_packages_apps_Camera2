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

import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.Toast;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.CountDownView.OnCountDownFinishedListener;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.PieRenderer.PieListener;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.ZoomRenderer;
import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;

import java.util.List;

public class PhotoUI implements PieListener,
    SurfaceHolder.Callback,
    PreviewGestures.SingleTapListener,
    FocusUI,
    LocationManager.Listener,
    FaceDetectionListener,
    PreviewGestures.SwipeListener {

    private static final String TAG = "CAM_UI";

    private CameraActivity mActivity;
    private PhotoController mController;
    private PreviewGestures mGestures;

    private View mRootView;
    private Object mSurfaceTexture;
    private volatile SurfaceHolder mSurfaceHolder;

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
    private PhotoMenu mMenu;

    private OnScreenIndicators mOnScreenIndicators;

    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;
    private Toast mNotSelectableToast;

    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private View mPreviewThumb;

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
                mController.onScreenSizeChanged(width, height, w, h);
            }
        }
    };

    public PhotoUI(CameraActivity activity, PhotoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;

        mActivity.getLayoutInflater().inflate(R.layout.photo_module,
                (ViewGroup) mRootView, true);
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);

        initIndicators();
        mCountDownView = (CountDownView) (mRootView.findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener((OnCountDownFinishedListener) mController);

        if (ApiHelper.HAS_FACE_DETECTION) {
            ViewStub faceViewStub = (ViewStub) mRootView
                    .findViewById(R.id.face_view_stub);
            if (faceViewStub != null) {
                faceViewStub.inflate();
                mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
            }
        }

    }

    public View getRootView() {
        return mRootView;
    }

    private void initIndicators() {
        mOnScreenIndicators = new OnScreenIndicators(mActivity,
                mActivity.findViewById(R.id.on_screen_indicators));
    }

    public void onCameraOpened(PreferenceGroup prefGroup, ComboPreferences prefs,
            Camera.Parameters params, OnPreferenceChangedListener listener) {
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mPieRenderer.setPieListener(this);
            mRenderOverlay.addRenderer(mPieRenderer);
        }
        if (mMenu == null) {
            mMenu = new PhotoMenu(mActivity, this, mPieRenderer);
            mMenu.setListener(listener);
        }
        mMenu.initialize(prefGroup);

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }
        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer,
                    this);
        }
        mGestures.reset();
        mGestures.setRenderOverlay(mRenderOverlay);
        mGestures.addTouchReceiver(mMenuButton);
        mGestures.addUnclickableArea(mBlocker);
        enablePreviewThumb(false);
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
        updateOnScreenIndicators(params, prefGroup, prefs);
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
        mBlocker = mActivity.findViewById(R.id.blocker);
        mPreviewThumb = mActivity.findViewById(R.id.preview_thumb);
        mPreviewThumb.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.gotoGallery();
            }
        });
        mMenuButton = mActivity.findViewById(R.id.menu);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openMenu();
            }
        });
        if (mController.isImageCaptureIntent()) {
            mActivity.hideSwitcher();
            ViewGroup cameraControls = (ViewGroup) mActivity.findViewById(R.id.camera_controls);
            mActivity.getLayoutInflater().inflate(R.layout.review_module_control, cameraControls);

            mReviewDoneButton = mActivity.findViewById(R.id.btn_done);
            mReviewCancelButton = mActivity.findViewById(R.id.btn_cancel);
            mReviewRetakeButton = mActivity.findViewById(R.id.btn_retake);
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

    // called from onResume but only the first time
    public  void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton = mActivity.getShutterButton();
        mShutterButton.setImageResource(R.drawable.btn_new_shutter);
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
        mRootView.addOnLayoutChangeListener(mLayoutListener);
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
        mRootView.addOnLayoutChangeListener(mLayoutListener);
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

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    @Override
    public void showGpsOnScreenIndicator(boolean hasSignal) { }

    @Override
    public void hideGpsOnScreenIndicator() { }

    public void overrideSettings(final String ... keyvalues) {
        mMenu.overrideSettings(keyvalues);
    }

    public void updateOnScreenIndicators(Camera.Parameters params,
            PreferenceGroup group, ComboPreferences prefs) {
        if (params == null) return;
        mOnScreenIndicators.updateSceneOnScreenIndicator(params.getSceneMode());
        mOnScreenIndicators.updateExposureOnScreenIndicator(params,
                CameraSettings.readExposure(prefs));
        mOnScreenIndicators.updateFlashOnScreenIndicator(params.getFlashMode());
        int wbIndex = 2;
        ListPreference pref = group.findPreference(CameraSettings.KEY_WHITE_BALANCE);
        if (pref != null) {
            wbIndex = pref.getCurrentIndex();
        }
        mOnScreenIndicators.updateWBIndicator(wbIndex);
        boolean location = RecordLocationPreference.get(
                prefs, mActivity.getContentResolver());
        mOnScreenIndicators.updateLocationIndicator(location);
    }

    public void setCameraState(int state) {
    }

    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mGestures != null && mRenderOverlay != null) {
            return mGestures.dispatchTouch(m);
        }
        return false;
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
            dismissPopup(full);
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

    public void enablePreviewThumb(boolean enabled) {
        if (enabled) {
            mGestures.addTouchReceiver(mPreviewThumb);
            mPreviewThumb.setVisibility(View.VISIBLE);
        } else {
            mGestures.removeTouchReceiver(mPreviewThumb);
            mPreviewThumb.setVisibility(View.GONE);
        }
    }

    public boolean removeTopLevelPopup() {
        // Remove the top level popup or dialog box and return true if there's any
        if (mPopup != null) {
            dismissPopup();
            return true;
        }
        return false;
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

    public void dismissPopup() {
        dismissPopup(true);
    }

    private void dismissPopup(boolean fullScreen) {
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
        mMenu.popupDismissed();
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
            dismissPopup();
            ret = true;
        }
        onShowSwitcherPopup();
        return ret;
    }

    protected void showPostCaptureAlert() {
        mOnScreenIndicators.setVisibility(View.GONE);
        mMenuButton.setVisibility(View.GONE);
        Util.fadeIn(mReviewDoneButton);
        mShutterButton.setVisibility(View.INVISIBLE);
        Util.fadeIn(mReviewRetakeButton);
        pauseFaceDetection();
    }

    protected void hidePostCaptureAlert() {
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.VISIBLE);
        Util.fadeOut(mReviewDoneButton);
        mShutterButton.setVisibility(View.VISIBLE);
        Util.fadeOut(mReviewRetakeButton);
        resumeFaceDetection();
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

    // focus handling


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
        dismissPopup();
        mActivity.cancelActivityTouchHandling();
        mActivity.setSwipingEnabled(false);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
        }
    }

    @Override
    public void onPieClosed() {
        mActivity.setSwipingEnabled(true);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(false);
        }
    }

    // Surface Listener

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "surfaceChanged:" + holder + " width=" + width + ". height="
                + height);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated: " + holder);
        mSurfaceHolder = holder;
        mController.onSurfaceCreated(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed: " + holder);
        mSurfaceHolder = null;
        mController.stopPreview();
    }

    public Object getSurfaceTexture() {
        return mSurfaceTexture;
    }

    public void setSurfaceTexture(Object st) {
        mSurfaceTexture = st;
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    public boolean isCountingDown() {
        return mCountDownView.isCountingDown();
    }

    public void cancelCountDown() {
        mCountDownView.cancelCountDown();
    }

    public void startCountDown(int sec, boolean playSound) {
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
        mCountDownView.cancelCountDown();
        // Close the camera now because other activities may need to use it.
        mSurfaceTexture = null;

        // Clear UI.
        collapseCameraControls();
        if (mFaceView != null) mFaceView.clear();


        mRootView.removeOnLayoutChangeListener(mLayoutListener);
        mPreviewWidth = 0;
        mPreviewHeight = 0;
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

    // forward from preview gestures to controller
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
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
