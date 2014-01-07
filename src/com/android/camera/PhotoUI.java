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
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Toast;

import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.app.CameraManager;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.ModeListView;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

import java.util.List;

public class PhotoUI implements PreviewStatusListener,
    CameraManager.CameraFaceDetectionCallback {

    private static final String TAG = "PhotoUI";
    private static final int DOWN_SAMPLE_FACTOR = 4;
    private static final float UNSET = 0f;

    private final PreviewOverlay mPreviewOverlay;
    private final FocusUI mFocusUI;
    private CameraActivity mActivity;
    private PhotoController mController;

    private View mRootView;
    private SurfaceTexture mSurfaceTexture;

    private FaceView mFaceView;
    private DecodeImageForReview mDecodeTaskForReview = null;
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
    private float mAspectRatio = UNSET;
    private final Object mSurfaceTextureLock = new Object();

    private ButtonManager.ButtonCallback mCameraCallback;
    private ButtonManager.ButtonCallback mHdrCallback;
    private ButtonManager.ButtonCallback mRefocusCallback;

    private final OnClickListener mCancelCallback = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mController.onCaptureCancelled();
        }
    };
    private final OnClickListener mDoneCallback = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mController.onCaptureDone();
        }
    };
    private final OnClickListener mRetakeCallback = new OnClickListener() {
        @Override
        public void onClick(View v) {
            setupIntentToggleButtons();
            mActivity.getCameraAppUI().transitionToIntentLayout();
            mController.onCaptureRetake();
        }
    };

    private final GestureDetector.OnGestureListener mPreviewGestureListener
            = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            mController.onSingleTapUp(null, (int) ev.getX(), (int) ev.getY());
            return true;
        }
    };

    @Override
    public GestureDetector.OnGestureListener getGestureListener() {
        return mPreviewGestureListener;
    }

    public interface SurfaceTextureSizeChangedListener {
        public void onSurfaceTextureSizeChanged(int uncroppedWidth, int uncroppedHeight);
    }

    @Override
    public void onPreviewLayoutChanged(View v, int left, int top, int right,
            int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int width = right - left;
        int height = bottom - top;
        if (mPreviewWidth != width || mPreviewHeight != height) {
            mPreviewWidth = width;
            mPreviewHeight = height;
        }
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
        // display the view
        mTextureView = (TextureView) mRootView.findViewById(R.id.preview_content);
        initIndicators();

        mSurfaceTexture = mTextureView.getSurfaceTexture();

        // Customize the bottom bar.
        if (mActivity.getCurrentModuleIndex() == ModeListView.MODE_PHOTO) {
            // Simple photo mode.
            activity.getCameraAppUI().setBottomBarColor(
                activity.getResources().getColor(R.color.camera_mode_color));
        } else {
            // Advanced photo mode.
            activity.getCameraAppUI().setBottomBarColor(
                activity.getResources().getColor(R.color.craft_mode_color));
        }

        ViewStub faceViewStub = (ViewStub) mRootView
                .findViewById(R.id.face_view_stub);
        if (faceViewStub != null) {
            faceViewStub.inflate();
            mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
            setSurfaceTextureSizeChangedListener(mFaceView);
        }
        mFocusUI = (FocusUI) mRootView.findViewById(R.id.focus_overlay);
        mPreviewOverlay = (PreviewOverlay) mRootView.findViewById(R.id.preview_overlay);
    }

    public FocusUI getFocusUI() {
        return mFocusUI;
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
            mController.updatePreviewAspectRatio(mAspectRatio);
        }
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
            ButtonManager.ButtonCallback hdrCallback,
            ButtonManager.ButtonCallback refocusCallback) {
        mCameraCallback = cameraCallback;
        mHdrCallback = hdrCallback;
        mRefocusCallback = refocusCallback;

        if (mController.isImageCaptureIntent()) {
            setupIntentToggleButtons();
            mActivity.getCameraAppUI().transitionToIntentLayout();
        } else {
            setupToggleButtons();
        }

        initializeZoom(params);
    }

    public void animateCapture(final byte[] jpegData, int orientation, boolean mirror) {
        // Decode jpeg byte array and then animate the jpeg
        DecodeTask task = new DecodeTask(jpegData, orientation, mirror);
        task.execute();
    }

    private void setupToggleButtons() {
        ButtonManager buttonManager = mActivity.getButtonManager();
        buttonManager.enableButton(ButtonManager.BUTTON_CAMERA, R.id.camera_toggle_button,
            mCameraCallback, R.array.camera_id_icons);
        buttonManager.enableButton(ButtonManager.BUTTON_FLASH, R.id.flash_toggle_button,
            null, R.array.camera_flashmode_icons);

        if (mActivity.getCurrentModuleIndex() == ModeListView.MODE_PHOTO) {
            // Simple photo mode.
            buttonManager.hideButton(ButtonManager.BUTTON_HDRPLUS, R.id.hdr_plus_toggle_button);
            buttonManager.hideButton(ButtonManager.BUTTON_REFOCUS, R.id.refocus_toggle_button);
        } else {
            // Advanced photo mode.
            buttonManager.enableButton(ButtonManager.BUTTON_HDRPLUS, R.id.hdr_plus_toggle_button,
                mHdrCallback, R.array.pref_camera_hdr_plus_icons);
            buttonManager.enableButton(ButtonManager.BUTTON_REFOCUS, R.id.refocus_toggle_button,
                mRefocusCallback, R.array.refocus_icons);
        }
    }

    private void setupIntentToggleButtons() {
        setupToggleButtons();
        ButtonManager buttonManager = mActivity.getButtonManager();
        buttonManager.enablePushButton(ButtonManager.BUTTON_CANCEL, R.id.cancel_button,
                mCancelCallback);
        buttonManager.enablePushButton(ButtonManager.BUTTON_DONE, R.id.done_button,
                mDoneCallback);
        buttonManager.enablePushButton(ButtonManager.BUTTON_RETAKE, R.id.retake_button,
                mRetakeCallback);
    }

    public void initializeControlByIntent() {
        if (mController.isImageCaptureIntent()) {
            setupIntentToggleButtons();
            mActivity.getCameraAppUI().transitionToIntentLayout();
        }
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {

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
        mController.startPreCaptureAnimation();
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

        setupIntentToggleButtons();
        mActivity.getCameraAppUI().transitionToIntentReviewLayout();

        pauseFaceDetection();
    }

    protected void hidePostCaptureAlert() {
        if (mDecodeTaskForReview != null) {
            mDecodeTaskForReview.cancel(true);
        }
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

    public void onPause() {
        if (mFaceView != null) mFaceView.clear();
    }

    public void clearFaces() {
        if (mFaceView != null) {
            mFaceView.clear();
        }
    }

    public void pauseFaceDetection() {
        if (mFaceView != null) mFaceView.pause();
    }

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
