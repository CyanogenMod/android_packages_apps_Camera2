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

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.CompoundButton;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.app.CameraManager;
import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.CountDownView.OnCountDownFinishedListener;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.PieRenderer.PieListener;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

import java.util.List;

public class PhotoUI implements PieListener,
    PreviewGestures.SingleTapListener,
    FocusUI, TextureView.SurfaceTextureListener,
    CameraManager.CameraFaceDetectionCallback {

    private static final String TAG = "PhotoUI";
    private static final int DOWN_SAMPLE_FACTOR = 4;
    private final AnimationManager mAnimationManager;
    private CameraActivity mActivity;
    private PhotoController mController;

    private View mRootView;
    private SurfaceTexture mSurfaceTexture;

    private PopupWindow mPopup;
    private CountDownView mCountDownView;

    private FaceView mFaceView;
    private RenderOverlay mRenderOverlay;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewRetakeButton;
    private ImageView mReviewImage;
    private DecodeImageForReview mDecodeTaskForReview = null;

    private PhotoMenu mMenu;

    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;
    private Toast mNotSelectableToast;

    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;

    private View mFlashOverlay;

    private SurfaceTextureSizeChangedListener mSurfaceTextureSizeListener;
    private TextureView mTextureView;
    private Matrix mMatrix = null;
    private float mAspectRatio = 4f / 3f;
    private View mPreviewCover;
    private final Object mSurfaceTextureLock = new Object();
    private View mBottomBar;

    private boolean mHideFocusRing;
    private boolean mImmediateCapture;
    private final int mBottomBarMinHeight;

    /*
     * @return Whether immediate capture mode is selected from the toggle button
     */
    // TODO: don't ship with this
    public boolean isImmediateCapture() {
        return mImmediateCapture;
    }

    public interface SurfaceTextureSizeChangedListener {
        public void onSurfaceTextureSizeChanged(int uncroppedWidth, int uncroppedHeight);
    }

    private OnLayoutChangeListener mLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right,
                int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            int width = right - left;
            int height = bottom - top;
            if (mPreviewWidth != width || mPreviewHeight != height) {
                mPreviewWidth = width;
                mPreviewHeight = height;
                setTransformMatrix(width, height);
            }
        }
    };

    private class DecodeTask extends AsyncTask<Void, Void, Bitmap> {
        private final byte [] mData;
        private int mOrientation;
        private boolean mMirror;

        public DecodeTask(byte[] data, int orientation, boolean mirror) {
            mData = data;
            mOrientation = orientation;
            mMirror = mirror;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            // Decode image in background.
            Bitmap bitmap = CameraUtil.downSample(mData, DOWN_SAMPLE_FACTOR);
            if (mOrientation != 0 || mMirror) {
                Matrix m = new Matrix();
                if (mMirror) {
                    // Flip horizontally
                    m.setScale(-1f, 1f);
                }
                m.preRotate(mOrientation);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m,
                        false);
            }
            return bitmap;
        }
    }

    private class DecodeImageForReview extends DecodeTask {
        public DecodeImageForReview(byte[] data, int orientation, boolean mirror) {
            super(data, orientation, mirror);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                return;
            }
            mReviewImage.setImageBitmap(bitmap);
            mReviewImage.setVisibility(View.VISIBLE);
            mDecodeTaskForReview = null;
        }
    }

    public PhotoUI(CameraActivity activity, PhotoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;

        ViewGroup moduleRoot = (ViewGroup) mRootView.findViewById(R.id.module_layout);
        mActivity.getLayoutInflater().inflate(R.layout.photo_module,
                 (ViewGroup) moduleRoot, true);
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);

        // TODO: Each module should have their own gesture recognizer. Expect that in the
        // second part of the touch refactoring
        mRenderOverlay.setTapListener(this);

        mFlashOverlay = mRootView.findViewById(R.id.flash_overlay);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        // display the view
        mTextureView = (TextureView) mRootView.findViewById(R.id.preview_content);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.addOnLayoutChangeListener(mLayoutListener);
        initIndicators();
        mBottomBar = mRootView.findViewById(R.id.bottom_bar);

        mSurfaceTexture = mTextureView.getSurfaceTexture();
        if (mSurfaceTexture != null) {
            setTransformMatrix(mTextureView.getWidth(), mTextureView.getHeight());
            mPreviewCover.setVisibility(View.GONE);
        }

        ToggleButton focusRingToggle =
                (ToggleButton) mRootView.findViewById(R.id.toggle_focus_ring_button);
        mHideFocusRing = !focusRingToggle.isChecked();
        focusRingToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mHideFocusRing = !isChecked;
                }
            });
        ToggleButton immediateToggle =
                (ToggleButton) mRootView.findViewById(R.id.toggle_immediate_capture_button);
        mImmediateCapture= immediateToggle.isChecked();
        immediateToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mImmediateCapture = isChecked;
                }
            });

        mSurfaceTexture = mTextureView.getSurfaceTexture();
        if (mSurfaceTexture != null) {
            setTransformMatrix(mTextureView.getWidth(), mTextureView.getHeight());
            mPreviewCover.setVisibility(View.GONE);
        }
        mBottomBar.setBackgroundColor(activity.getResources().getColor(R.color.camera_mode_color));
        ViewStub faceViewStub = (ViewStub) mRootView
                .findViewById(R.id.face_view_stub);
        if (faceViewStub != null) {
            faceViewStub.inflate();
            mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
            setSurfaceTextureSizeChangedListener(mFaceView);
        }
        mAnimationManager = new AnimationManager();
        mBottomBarMinHeight = activity.getResources()
                .getDimensionPixelSize(R.dimen.bottom_bar_height_min);
    }

    public void setSurfaceTextureSizeChangedListener(SurfaceTextureSizeChangedListener listener) {
        mSurfaceTextureSizeListener = listener;
    }

    public void updatePreviewAspectRatio(float aspectRatio) {
        if (aspectRatio <= 0) {
            Log.e(TAG, "Invalid aspect ratio: " + aspectRatio);
            return;
        }
        if (aspectRatio < 1f) {
            aspectRatio = 1f / aspectRatio;
        }

        if (mAspectRatio != aspectRatio) {
            mAspectRatio = aspectRatio;
            // Update transform matrix with the new aspect ratio.
            if (mPreviewWidth != 0 && mPreviewHeight != 0) {
                setTransformMatrix(mPreviewWidth, mPreviewHeight);
            }
        }
    }

    private void setTransformMatrix(int width, int height) {
        mMatrix = mTextureView.getTransform(mMatrix);
        float scaleX = 1f, scaleY = 1f;
        float scaledTextureWidth, scaledTextureHeight;
        if (width > height) {
            scaledTextureWidth = Math.min(width,
                    (int) (height * mAspectRatio));
            scaledTextureHeight = Math.min(height,
                    (int)(width / mAspectRatio));
        } else {
            scaledTextureWidth = Math.min(width,
                    (int) (height / mAspectRatio));
            scaledTextureHeight = Math.min(height,
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

        // TODO: Need a better way to find out whether currently in landscape
        boolean landscape = width > height;
        if (landscape) {
            mMatrix.setScale(scaleX, scaleY, 0f, (float) height / 2);
        } else {
            mMatrix.setScale(scaleX, scaleY, (float) width / 2, 0.0f);
        }
        mTextureView.setTransform(mMatrix);

        // Calculate the new preview rectangle.
        RectF previewRect = new RectF(0, 0, width, height);
        mMatrix.mapRect(previewRect);
        mController.onPreviewRectChanged(CameraUtil.rectFToRect(previewRect));

        float previewAspectRatio =
                (float)mSurfaceTextureUncroppedWidth / (float)mSurfaceTextureUncroppedHeight;
        if (previewAspectRatio < 1.0) {
            previewAspectRatio = 1.0f/previewAspectRatio;
        }
        float screenAspectRatio = (float)width / (float)height;
        if (screenAspectRatio < 1.0) {
            screenAspectRatio = 1.0f/screenAspectRatio;
        }

        LayoutParams lp = (LayoutParams) mBottomBar.getLayoutParams();
        if (previewAspectRatio >= screenAspectRatio) {
            mBottomBar.setAlpha(0.5f);
            if (landscape) {
                lp.width = mBottomBarMinHeight;
                lp.height = LayoutParams.MATCH_PARENT;
            } else {
                lp.height = mBottomBarMinHeight;
                lp.width = LayoutParams.MATCH_PARENT;
            }
        }
        else {
            mBottomBar.setAlpha(1.0f);
            if (landscape) {
                lp.width = (int)((float) width - mSurfaceTextureUncroppedWidth);
                lp.height = LayoutParams.MATCH_PARENT;
            } else {
                lp.height = (int)((float) height - mSurfaceTextureUncroppedHeight);
                lp.width = LayoutParams.MATCH_PARENT;
            }
        }
        mBottomBar.setLayoutParams(lp);
    }

    protected Object getSurfaceTextureLock() {
        return mSurfaceTextureLock;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        synchronized (mSurfaceTextureLock) {
            Log.v(TAG, "SurfaceTexture ready.");
            mSurfaceTexture = surface;
            mController.onPreviewUIReady();
            // Workaround for b/11168275, see b/10981460 for more details
            if (mPreviewWidth != 0 && mPreviewHeight != 0) {
                // Re-apply transform matrix for new surface texture
                setTransformMatrix(mPreviewWidth, mPreviewHeight);
            }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored, Camera does all the work for us
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        synchronized (mSurfaceTextureLock) {
            mSurfaceTexture = null;
            mController.onPreviewUIDestroyed();
            Log.w(TAG, "SurfaceTexture destroyed");
            return true;
        }
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Make sure preview cover is hidden if preview data is available.
        if (mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    public View getRootView() {
        return mRootView;
    }

    private void initIndicators() {
        // TODO init toggle buttons on bottom bar here
    }

    public void onCameraOpened(Camera.Parameters params,
        PhotoMenu.PhotoMenuListener listener) {
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mPieRenderer.setPieListener(this);
            mRenderOverlay.addRenderer(mPieRenderer);
        }

        if (mMenu == null) {
            mMenu = new PhotoMenu(mActivity, listener);
        }
        mMenu.initialize();

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }
        mRenderOverlay.setGestures(null);

        initializeZoom(params);
    }

    public void animateCapture(final byte[] jpegData, int orientation, boolean mirror) {
        // Decode jpeg byte array and then animate the jpeg
        DecodeTask task = new DecodeTask(jpegData, orientation, mirror);
        task.execute();
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
        if (mController.isImageCaptureIntent()) {
            ViewGroup cameraControls = (ViewGroup) mRootView.findViewById(R.id.camera_controls);
            mActivity.getLayoutInflater().inflate(R.layout.review_module_control, cameraControls);

            mReviewDoneButton = mRootView.findViewById(R.id.btn_done);
            mReviewCancelButton = mRootView.findViewById(R.id.btn_cancel);
            mReviewRetakeButton = mRootView.findViewById(R.id.btn_retake);
            mReviewImage = (ImageView) mRootView.findViewById(R.id.review_image);
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

    }

    // called from onResume every other time
    public void initializeSecondTime(Camera.Parameters params) {
        initializeZoom(params);
        if (mController.isImageCaptureIntent()) {
            hidePostCaptureAlert();
        }
        // Removes pie menu.
    }

    public void showLocationDialog() {
        AlertDialog alert = mActivity.getFirstTimeLocationAlert();
        alert.show();
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

    public void animateFlash() {
        mAnimationManager.startFlashAnimation(mFlashOverlay);
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
            mController.onCaptureCancelled();
            return true;
        } else if (!mController.isCameraIdle()) {
            // ignore backs while we're taking a picture
            return true;
        } else {
            return false;
        }
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (mFaceView != null) {
            mFaceView.setBlockDraw(!previewFocused);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
        if (mPieRenderer != null) {
            mPieRenderer.setBlockFocus(!previewFocused);
        }

        if (!previewFocused && mCountDownView != null) {
            mCountDownView.cancelCountDown();
        }
    }

    public void showPopup(AbstractSettingPopup popup) {
        if (mPopup == null) {
            mPopup = new PopupWindow(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            mPopup.setOutsideTouchable(true);
            mPopup.setFocusable(true);
            mPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    mPopup = null;

                    // Switch back into fullscreen/lights-out mode after popup
                    // is dimissed.
                    mActivity.setSystemBarsVisibility(false);
                }
            });
        }
        popup.setVisibility(View.VISIBLE);
        mPopup.setContentView(popup);
        mPopup.showAtLocation(mRootView, Gravity.CENTER, 0, 0);
    }

    public void dismissPopup() {
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
    }

    public void onShowSwitcherPopup() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    public boolean collapseCameraControls() {
        // TODO: Mode switcher should behave like a popup and should hide itself when there
        // is a touch outside of it.

        // Remove all the popups/dialog boxes
        boolean ret = false;
        if (mPopup != null) {
            dismissPopup();
            ret = true;
        }
        onShowSwitcherPopup();
        return ret;
    }

    protected void showCapturedImageForReview(byte[] jpegData, int orientation, boolean mirror) {
        mDecodeTaskForReview = new DecodeImageForReview(jpegData, orientation, mirror);
        mDecodeTaskForReview.execute();
        CameraUtil.fadeIn(mReviewDoneButton);
        pauseFaceDetection();
    }

    protected void hidePostCaptureAlert() {
        if (mDecodeTaskForReview != null) {
            mDecodeTaskForReview.cancel(true);
        }
        mReviewImage.setVisibility(View.GONE);
        CameraUtil.fadeOut(mReviewDoneButton);
        CameraUtil.fadeOut(mReviewRetakeButton);
        resumeFaceDetection();
    }

    public void setDisplayOrientation(int orientation) {
        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(orientation);
        }
    }

    // shutter button handling

    public boolean isShutterPressed() {
        return false;
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {

    }

    public void pressShutterButton() {

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
        setSwipingEnabled(false);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
        }
    }

    @Override
    public void onPieClosed() {
        setSwipingEnabled(true);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(false);
        }
    }

    public void setSwipingEnabled(boolean enable) {
        mActivity.setSwipingEnabled(enable);
    }

    public SurfaceTexture getSurfaceTexture() {
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
    }

    // focus UI implementation

    private FocusIndicator getFocusIndicator() {
        if (mHideFocusRing) {
            return null;
        }
        return (mFaceView != null && mFaceView.faceExists()) ? mFaceView : mPieRenderer;
    }

    @Override
    public boolean hasFaces() {
        return (mFaceView != null && mFaceView.faceExists());
    }

    public void clearFaces() {
        if (mFaceView != null) {
            mFaceView.clear();
        }
    }

    @Override
    public void clearFocus() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) {
            indicator.clear();
        }
    }

    @Override
    public void setFocusPosition(int x, int y) {
        if (mPieRenderer != null) {
            mPieRenderer.setFocus(x, y);
        }
    }

    @Override
    public void onFocusStarted() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) {
            indicator.showStart();
        }
    }

    @Override
    public void onFocusSucceeded(boolean timeout) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) {
            indicator.showSuccess(timeout);
        }
    }

    @Override
    public void onFocusFailed(boolean timeout) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) {
            indicator.showFail(timeout);
        }
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
    public void onFaceDetection(Face[] faces, CameraManager.CameraProxy camera) {
        mFaceView.setFaces(faces);
    }

}
