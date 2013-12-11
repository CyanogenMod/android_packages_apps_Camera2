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
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.AsyncTask;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.CompoundButton;

import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.app.CameraManager;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.BottomBar;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

import java.util.List;

public class PhotoUI implements
    FocusUI, PreviewStatusListener,
    CameraManager.CameraFaceDetectionCallback {

    private static final String TAG = "PhotoUI";
    private static final int DOWN_SAMPLE_FACTOR = 4;

    private final PreviewOverlay mPreviewOverlay;
    private CameraActivity mActivity;
    private PhotoController mController;

    private View mRootView;
    private SurfaceTexture mSurfaceTexture;

    private FaceView mFaceView;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewRetakeButton;
    private ImageView mReviewImage;
    private DecodeImageForReview mDecodeTaskForReview = null;
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
    private final Object mSurfaceTextureLock = new Object();
    private BottomBar mBottomBar;
    private final int mBottomBarMinHeight;
    private final int mBottomBarOptimalHeight;

    private boolean mHideFocusRing;
    private boolean mImmediateCapture;

    private final GestureDetector.OnGestureListener mPreviewGestureListener
            = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            mController.onSingleTapUp(null, (int) ev.getX(), (int) ev.getY());
            return true;
        }
    };

    /*
     * @return Whether immediate capture mode is selected from the toggle button
     */
    // TODO: don't ship with this
    public boolean isImmediateCapture() {
        return mImmediateCapture;
    }

    @Override
    public GestureDetector.OnGestureListener getGestureListener() {
        return mPreviewGestureListener;
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

        mFlashOverlay = mRootView.findViewById(R.id.flash_overlay);
        // display the view
        mTextureView = (TextureView) mRootView.findViewById(R.id.preview_content);
        mTextureView.addOnLayoutChangeListener(mLayoutListener);
        initIndicators();

        mBottomBar = (BottomBar) mRootView.findViewById(R.id.bottom_bar);
        mBottomBar.setButtonLayout(R.layout.photo_bottombar_buttons);
        mBottomBarMinHeight = activity.getResources()
                .getDimensionPixelSize(R.dimen.bottom_bar_height_min);
        mBottomBarOptimalHeight = activity.getResources()
                .getDimensionPixelSize(R.dimen.bottom_bar_height_optimal);

        mSurfaceTexture = mTextureView.getSurfaceTexture();
        if (mSurfaceTexture != null) {
            setTransformMatrix(mTextureView.getWidth(), mTextureView.getHeight());
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

        mBottomBar.setBackgroundColor(activity.getResources().getColor(R.color.camera_mode_color));
        ViewStub faceViewStub = (ViewStub) mRootView
                .findViewById(R.id.face_view_stub);
        if (faceViewStub != null) {
            faceViewStub.inflate();
            mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
            setSurfaceTextureSizeChangedListener(mFaceView);
        }

        mPreviewOverlay = (PreviewOverlay) mRootView.findViewById(R.id.preview_overlay);
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
        mActivity.getCameraAppUI().adjustPreviewAndBottomBarSize(width, height, mBottomBar,
                mAspectRatio, mBottomBarMinHeight, mBottomBarOptimalHeight);
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
    }

    public View getRootView() {
        return mRootView;
    }

    private void initIndicators() {
        // TODO init toggle buttons on bottom bar here
    }

    public void onCameraOpened(Camera.Parameters params,
            ButtonManager.ButtonCallback cameraCallback,
            ButtonManager.ButtonCallback hdrCallback) {
        ButtonManager buttonManager = mActivity.getButtonManager();
        SettingsManager settingsManager = mActivity.getSettingsManager();
        if (settingsManager.isCameraBackFacing()) {
            buttonManager.enableButton(ButtonManager.BUTTON_FLASH, R.id.flash_toggle_button,
                null, R.array.camera_flashmode_icons);
        } else {
            buttonManager.disableButton(ButtonManager.BUTTON_FLASH,
                R.id.flash_toggle_button);
        }
        buttonManager.enableButton(ButtonManager.BUTTON_CAMERA, R.id.camera_toggle_button,
            cameraCallback, R.array.camera_id_icons);
        buttonManager.enableButton(ButtonManager.BUTTON_HDRPLUS, R.id.hdr_plus_toggle_button,
                hdrCallback, R.array.pref_camera_hdr_plus_icons);

        initializeZoom(params);
    }

    public void animateCapture(final byte[] jpegData, int orientation, boolean mirror) {
        // Decode jpeg byte array and then animate the jpeg
        DecodeTask task = new DecodeTask(jpegData, orientation, mirror);
        task.execute();
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
        if ((params == null) || !params.isZoomSupported()) return;
        mZoomMax = params.getMaxZoom();
        mZoomRatios = params.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        // TODO: Need to setup a path to AppUI to do this
        mPreviewOverlay.setupZoom(mZoomMax, params.getZoom(), mZoomRatios, new ZoomChangeListener());
    }

    public void animateFlash() {
        mActivity.startPreCaptureAnimation();
    }

    public boolean onBackPressed() {
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

    public void setSwipingEnabled(boolean enable) {
        mActivity.setSwipingEnabled(enable);
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    public void showPreferencesToast() {
        if (mNotSelectableToast == null) {
            String str = mActivity.getResources().getString(R.string.not_selectable_in_scene_mode);
            mNotSelectableToast = Toast.makeText(mActivity, str, Toast.LENGTH_SHORT);
        }
        mNotSelectableToast.show();
    }

    public void onPause() {
        if (mFaceView != null) mFaceView.clear();
    }

    // focus UI implementation
    private FocusIndicator getFocusIndicator() {
        if (mHideFocusRing) {
            return null;
        }
        return (mFaceView != null && mFaceView.faceExists()) ? mFaceView : null;
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
