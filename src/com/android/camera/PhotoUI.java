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

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera.Face;
import android.os.AsyncTask;
import android.os.Build;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.debug.DebugPropertyHelper;
import com.android.camera.debug.Log;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.FaceView;
import com.android.camera.ui.PreviewOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GservicesHelper;
import com.android.camera.widget.AspectRatioDialogLayout;
import com.android.camera.widget.AspectRatioSelector;
import com.android.camera.widget.LocationDialogLayout;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent;
import com.android.ex.camera2.portability.CameraCapabilities;
import com.android.ex.camera2.portability.CameraSettings;

public class PhotoUI implements PreviewStatusListener,
    CameraAgent.CameraFaceDetectionCallback, PreviewStatusListener.PreviewAreaChangedListener {

    private static final Log.Tag TAG = new Log.Tag("PhotoUI");
    private static final int DOWN_SAMPLE_FACTOR = 4;
    private static final float UNSET = 0f;

    private final PreviewOverlay mPreviewOverlay;
    private final FocusUI mFocusUI;
    private final CameraActivity mActivity;
    private final PhotoController mController;

    private final View mRootView;
    private Dialog mDialog = null;

    // TODO: Remove face view logic if UX does not bring it back within a month.
    private final FaceView mFaceView;
    private DecodeImageForReview mDecodeTaskForReview = null;

    private float mZoomMax;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private float mAspectRatio = UNSET;

    private ImageView mIntentReviewImageView;

    private final GestureDetector.OnGestureListener mPreviewGestureListener
            = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            mController.onSingleTapUp(null, (int) ev.getX(), (int) ev.getY());
            return true;
        }
    };
    private final DialogInterface.OnDismissListener mOnDismissListener
            = new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            mDialog = null;
        }
    };
    private Runnable mRunnableForNextFrame = null;
    private final CountDownView mCountdownView;

    @Override
    public GestureDetector.OnGestureListener getGestureListener() {
        return mPreviewGestureListener;
    }

    @Override
    public View.OnTouchListener getTouchListener() {
        return null;
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

    /**
     * Sets the runnable to run when the next frame comes in.
     */
    public void setRunnableForNextFrame(Runnable runnable) {
        mRunnableForNextFrame = runnable;
    }

    /**
     * Starts the countdown timer.
     *
     * @param sec seconds to countdown
     */
    public void startCountdown(int sec) {
        mCountdownView.startCountDown(sec);
    }

    /**
     * Sets a listener that gets notified when the countdown is finished.
     */
    public void setCountdownFinishedListener(CountDownView.OnCountDownStatusListener listener) {
        mCountdownView.setCountDownStatusListener(listener);
    }

    /**
     * Returns whether the countdown is on-going.
     */
    public boolean isCountingDown() {
        return mCountdownView.isCountingDown();
    }

    /**
     * Cancels the on-going countdown, if any.
     */
    public void cancelCountDown() {
        mCountdownView.cancelCountDown();
    }

    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        if (mFaceView != null) {
            mFaceView.onPreviewAreaChanged(previewArea);
        }
        mCountdownView.onPreviewAreaChanged(previewArea);
    }

    private class DecodeTask extends AsyncTask<Void, Void, Bitmap> {
        private final byte [] mData;
        private final int mOrientation;
        private final boolean mMirror;

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

            mIntentReviewImageView.setImageBitmap(bitmap);
            showIntentReviewImageView();

            mDecodeTaskForReview = null;
        }
    }

    public PhotoUI(CameraActivity activity, PhotoController controller, View parent) {
        mActivity = activity;
        mController = controller;
        mRootView = parent;

        ViewGroup moduleRoot = (ViewGroup) mRootView.findViewById(R.id.module_layout);
        mActivity.getLayoutInflater().inflate(R.layout.photo_module,
                 moduleRoot, true);
        initIndicators();
        mFocusUI = (FocusUI) mRootView.findViewById(R.id.focus_overlay);
        mPreviewOverlay = (PreviewOverlay) mRootView.findViewById(R.id.preview_overlay);
        mCountdownView = (CountDownView) mRootView.findViewById(R.id.count_down_view);
        // Show faces if we are in debug mode.
        if (DebugPropertyHelper.showCaptureDebugUI()) {
            mFaceView = (FaceView) mRootView.findViewById(R.id.face_view);
        } else {
            mFaceView = null;
        }

        if (mController.isImageCaptureIntent()) {
            initIntentReviewImageView();
        }
    }

    private void initIntentReviewImageView() {
        mIntentReviewImageView = (ImageView) mRootView.findViewById(R.id.intent_review_imageview);
        mActivity.getCameraAppUI().addPreviewAreaChangedListener(
                new PreviewStatusListener.PreviewAreaChangedListener() {
                    @Override
                    public void onPreviewAreaChanged(RectF previewArea) {
                        FrameLayout.LayoutParams params =
                            (FrameLayout.LayoutParams) mIntentReviewImageView.getLayoutParams();
                        params.width = (int) previewArea.width();
                        params.height = (int) previewArea.height();
                        params.setMargins((int) previewArea.left, (int) previewArea.top, 0, 0);
                        mIntentReviewImageView.setLayoutParams(params);
                    }
                });
    }

    /**
     * Show the image review over the live preview for intent captures.
     */
    public void showIntentReviewImageView() {
        if (mIntentReviewImageView != null) {
            mIntentReviewImageView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide the image review over the live preview for intent captures.
     */
    public void hideIntentReviewImageView() {
        if (mIntentReviewImageView != null) {
            mIntentReviewImageView.setVisibility(View.INVISIBLE);
        }
    }


    public FocusUI getFocusUI() {
        return mFocusUI;
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

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mController.onPreviewUIReady();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored, Camera does all the work for us
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mController.onPreviewUIDestroyed();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (mRunnableForNextFrame != null) {
            mRootView.post(mRunnableForNextFrame);
            mRunnableForNextFrame = null;
        }
    }

    public View getRootView() {
        return mRootView;
    }

    private void initIndicators() {
        // TODO init toggle buttons on bottom bar here
    }

    public void onCameraOpened(CameraCapabilities capabilities, CameraSettings settings) {
        initializeZoom(capabilities, settings);
    }

    public void animateCapture(final byte[] jpegData, int orientation, boolean mirror) {
        // Decode jpeg byte array and then animate the jpeg
        DecodeTask task = new DecodeTask(jpegData, orientation, mirror);
        task.execute();
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {

    }

    // called from onResume every other time
    public void initializeSecondTime(CameraCapabilities capabilities, CameraSettings settings) {
        initializeZoom(capabilities, settings);
        if (mController.isImageCaptureIntent()) {
            hidePostCaptureAlert();
        }
    }

    public void showLocationAndAspectRatioDialog(
            final PhotoModule.LocationDialogCallback locationCallback,
            final PhotoModule.AspectRatioDialogCallback aspectRatioDialogCallback) {
        setDialog(new Dialog(mActivity,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen));
        final LocationDialogLayout locationDialogLayout = (LocationDialogLayout) mActivity
                .getLayoutInflater().inflate(R.layout.location_dialog_layout, null);
        locationDialogLayout.setLocationTaggingSelectionListener(
                new LocationDialogLayout.LocationTaggingSelectionListener() {
            @Override
            public void onLocationTaggingSelected(boolean selected) {
                // Update setting.
                locationCallback.onLocationTaggingSelected(selected);

                if (showAspectRatioDialogOnThisDevice()) {
                    // Go to next page.
                    showAspectRatioDialog(aspectRatioDialogCallback, mDialog);
                } else {
                    // If we don't want to show the aspect ratio dialog,
                    // dismiss the dialog right after the user chose the
                    // location setting.
                    if (mDialog != null) {
                        mDialog.dismiss();
                    }
                }
            }
        });
        mDialog.setContentView(locationDialogLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mDialog.show();
    }

    /**
     * Dismisses previous dialog if any, sets current dialog to the given dialog,
     * and set the on dismiss listener for the given dialog.
     * @param dialog dialog to show
     */
    private void setDialog(Dialog dialog) {
        if (mDialog != null) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
        }
        mDialog = dialog;
        if (mDialog != null) {
            mDialog.setOnDismissListener(mOnDismissListener);
        }
    }

    /**
     * @return Whether the dialog was shown.
     */
    public boolean showAspectRatioDialog(final PhotoModule.AspectRatioDialogCallback callback) {
        if (showAspectRatioDialogOnThisDevice()) {
            setDialog(new Dialog(mActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen));
            showAspectRatioDialog(callback, mDialog);
            return true;
        } else {
            return false;
        }
    }

    private boolean showAspectRatioDialog(final PhotoModule.AspectRatioDialogCallback callback,
            final Dialog aspectRatioDialog) {
        if (aspectRatioDialog == null) {
            Log.e(TAG, "Dialog for aspect ratio is null.");
            return false;
        }
        final AspectRatioDialogLayout aspectRatioDialogLayout =
                (AspectRatioDialogLayout) mActivity
                .getLayoutInflater().inflate(R.layout.aspect_ratio_dialog_layout, null);
        aspectRatioDialogLayout.initialize(
                new AspectRatioDialogLayout.AspectRatioChangedListener() {
                    @Override
                    public void onAspectRatioChanged(AspectRatioSelector.AspectRatio aspectRatio) {
                        // callback to set picture size.
                        callback.onAspectRatioSelected(aspectRatio, new Runnable() {
                            @Override
                            public void run() {
                                if (mDialog != null) {
                                    mDialog.dismiss();
                                }
                            }
                        });
                    }
                }, callback.getCurrentAspectRatio());
        aspectRatioDialog.setContentView(aspectRatioDialogLayout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        aspectRatioDialog.show();
        return true;
    }

    /**
     * @return Whether this is a device that we should show the aspect ratio
     *         intro dialog on.
     */
    private boolean showAspectRatioDialogOnThisDevice() {
        // We only want to show that dialog on N4/N5/N6
        // Don't show if using API2 portability, b/17462976
        return !GservicesHelper.useCamera2ApiThroughPortabilityLayer(mActivity) &&
                (ApiHelper.IS_NEXUS_4 || ApiHelper.IS_NEXUS_5 || ApiHelper.IS_NEXUS_6);
    }

    public void initializeZoom(CameraCapabilities capabilities, CameraSettings settings) {
        if ((capabilities == null) || settings == null ||
                !capabilities.supports(CameraCapabilities.Feature.ZOOM)) {
            return;
        }
        mZoomMax = capabilities.getMaxZoomRatio();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        // TODO: Need to setup a path to AppUI to do this
        mPreviewOverlay.setupZoom(mZoomMax, settings.getCurrentZoomRatio(),
                new ZoomChangeListener());
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

    public void setSwipingEnabled(boolean enable) {
        mActivity.setSwipingEnabled(enable);
    }

    public void onPause() {
        if (mFaceView != null) {
            mFaceView.clear();
        }
        if (mDialog != null) {
            mDialog.dismiss();
        }
        // recalculate aspect ratio when restarting.
        mAspectRatio = 0.0f;
    }

    public void clearFaces() {
        if (mFaceView != null) {
            mFaceView.clear();
        }
    }

    public void pauseFaceDetection() {
        if (mFaceView != null) {
            mFaceView.pause();
        }
    }

    public void resumeFaceDetection() {
        if (mFaceView != null) {
            mFaceView.resume();
        }
    }

    public void onStartFaceDetection(int orientation, boolean mirror) {
        if (mFaceView != null) {
            mFaceView.clear();
            mFaceView.setVisibility(View.VISIBLE);
            mFaceView.setDisplayOrientation(orientation);
            mFaceView.setMirror(mirror);
            mFaceView.resume();
        }
    }

    @Override
    public void onFaceDetection(Face[] faces, CameraAgent.CameraProxy camera) {
        if (mFaceView != null) {
            mFaceView.setFaces(faces);
        }
    }

}
