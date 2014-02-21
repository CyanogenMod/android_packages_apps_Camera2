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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.CameraProfile;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;

import com.android.camera.PhotoModule.NamedImages.NamedEntity;
import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.CameraManager.CameraAFCallback;
import com.android.camera.app.CameraManager.CameraAFMoveCallback;
import com.android.camera.app.CameraManager.CameraPictureCallback;
import com.android.camera.app.CameraManager.CameraProxy;
import com.android.camera.app.CameraManager.CameraShutterCallback;
import com.android.camera.app.LocationManager;
import com.android.camera.app.MediaSaver;
import com.android.camera.app.MemoryManager;
import com.android.camera.app.MemoryManager.MemoryListener;
import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.ExifTag;
import com.android.camera.exif.Rational;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.hardware.HardwareSpecImpl;
import com.android.camera.module.ModuleController;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsUtil;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.SmartCameraHelper;
import com.android.camera.util.UsageStatistics;
import com.android.camera2.R;
import com.google.common.logging.eventprotos;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Vector;

public class PhotoModule
        extends CameraModule
        implements PhotoController,
        ModuleController,
        MemoryListener,
        FocusOverlayManager.Listener,
        ShutterButton.OnShutterButtonListener,
        SensorEventListener,
        SettingsManager.OnSettingChangedListener {

    private static final String TAG = "PhotoModule";

    // We number the request code from 1000 to avoid collision with Gallery.
    private static final int REQUEST_CROP = 1000;

    // Messages defined for the UI thread handler.
    private static final int MSG_FIRST_TIME_INIT = 1;
    private static final int MSG_SET_CAMERA_PARAMETERS_WHEN_IDLE = 2;
    private static final int MSG_SHOW_TAP_TO_FOCUS_TOAST = 3;
    private static final int MSG_SWITCH_TO_GCAM_MODULE = 4;

    // The subset of parameters we need to update in setCameraParameters().
    private static final int UPDATE_PARAM_INITIALIZE = 1;
    private static final int UPDATE_PARAM_ZOOM = 2;
    private static final int UPDATE_PARAM_PREFERENCE = 4;
    private static final int UPDATE_PARAM_ALL = -1;

    // This is the delay before we execute onResume tasks when coming
    // from the lock screen, to allow time for onPause to execute.
    private static final int ON_RESUME_TASKS_DELAY_MSEC = 20;

    private static final String DEBUG_IMAGE_PREFIX = "DEBUG_";

    private CameraActivity mActivity;
    private CameraProxy mCameraDevice;
    private int mCameraId;
    private Parameters mParameters;
    private boolean mPaused;

    private PhotoUI mUI;

    // The activity is going to switch to the specified camera id. This is
    // needed because texture copy is done in GL thread. -1 means camera is not
    // switching.
    protected int mPendingSwitchCameraId = -1;

    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    private int mUpdateSet;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private int mZoomValue; // The current zoom value.

    private Parameters mInitialParams;
    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;
    private boolean mAeLockSupported;
    private boolean mAwbLockSupported;
    private boolean mContinuousFocusSupported;

    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private static final String sTempCropFilename = "crop-temp";

    private boolean mFaceDetectionStarted = false;

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    private String mCropValue;
    private Uri mSaveUri;

    private Uri mDebugUri;

    // We use a queue to generated names of the images to be used later
    // when the image is ready to be saved.
    private NamedImages mNamedImages;

    private final Runnable mDoSnapRunnable = new Runnable() {
        @Override
        public void run() {
            onShutterButtonClick();
        }
    };

    /**
     * An unpublished intent flag requesting to return as soon as capturing is
     * completed. TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    private int mDisplayRotation;
    // The value for android.hardware.Camera.setDisplayOrientation.
    private int mCameraDisplayOrientation;
    // The value for UI components like indicators.
    private int mDisplayOrientation;
    // The value for android.hardware.Camera.Parameters.setRotation.
    private int mJpegRotation;
    // Indicates whether we are using front camera
    private boolean mMirror;
    private boolean mFirstTimeInitialized;
    private boolean mIsImageCaptureIntent;

    private int mCameraState = PREVIEW_STOPPED;
    private boolean mSnapshotOnIdle = false;

    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;
    private AppController mAppController;

    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final Object mAutoFocusMoveCallback =
            ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK
                    ? new AutoFocusMoveCallback()
                    : null;

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    private long mFocusStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private long mOnResumeTime;
    private byte[] mJpegImageData;

    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;
    public long mCaptureStartTime;

    // This handles everything about focus.
    private FocusOverlayManager mFocusManager;

    private final int mGcamModeIndex;

    private String mSceneMode;

    private final Handler mHandler = new MainHandler(this);

    private boolean mQuickCapture;
    private SensorManager mSensorManager;
    private final float[] mGData = new float[3];
    private final float[] mMData = new float[3];
    private final float[] mR = new float[16];
    private int mHeading = -1;

    /** Whether shutter is enabled. */
    private boolean mShutterEnabled = true;

    /** True if all the parameters needed to start preview is ready. */
    private boolean mCameraPreviewParamsReady = false;

    private final MediaSaver.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaver.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                    }
                }
            };

    private void checkDisplayRotation() {
        // Set the display orientation if display rotation has changed.
        // Sometimes this happens when the device is held upside
        // down and camera app is opened. Rotation animation will
        // take some time and the rotation value we have got may be
        // wrong. Framework does not have a callback for this now.
        if (CameraUtil.getDisplayRotation(mActivity) != mDisplayRotation) {
            setDisplayOrientation();
        }
        if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkDisplayRotation();
                }
            }, 100);
        }
    }

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private static class MainHandler extends Handler {
        private final WeakReference<PhotoModule> mModule;

        public MainHandler(PhotoModule module) {
            super(Looper.getMainLooper());
            mModule = new WeakReference<PhotoModule>(module);
        }

        @Override
        public void handleMessage(Message msg) {
            PhotoModule module = mModule.get();
            if (module == null) {
                return;
            }
            switch (msg.what) {
                case MSG_FIRST_TIME_INIT: {
                    module.initializeFirstTime();
                    break;
                }

                case MSG_SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    module.setCameraParametersWhenIdle(0);
                    break;
                }

                case MSG_SHOW_TAP_TO_FOCUS_TOAST: {
                    module.showTapToFocusToast();
                    break;
                }

                case MSG_SWITCH_TO_GCAM_MODULE: {
                    module.mActivity.onModeSelected(module.mGcamModeIndex);
                }
            }
        }
    }

    /**
     * Constructs a new photo module.
     */
    public PhotoModule(AppController app) {
        super(app);
        mGcamModeIndex = app.getAndroidContext().getResources()
                .getInteger(R.integer.camera_mode_gcam);
    }

    @Override
    public void init(CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent) {
        mActivity = activity;
        // TODO: Need to look at the controller interface to see if we can get
        // rid of passing in the activity directly.
        mAppController = mActivity;

        mUI = new PhotoUI(mActivity, this, mActivity.getModuleLayoutRoot());
        mActivity.setPreviewStatusListener(mUI);
        mActivity.getCameraAppUI().setBottomBarShutterListener(this);

        SettingsManager settingsManager = mActivity.getSettingsManager();
        mCameraId = Integer.parseInt(settingsManager.get(SettingsManager.SETTING_CAMERA_ID));

        mContentResolver = mActivity.getContentResolver();

        // Surface texture is from camera screen nail and startPreview needs it.
        // This must be done before startPreview.
        mIsImageCaptureIntent = isImageCaptureIntent();

        mActivity.getCameraProvider().requestCamera(mCameraId);

        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mLocationManager = mActivity.getLocationManager();
        mSensorManager = (SensorManager) (mActivity.getSystemService(Context.SENSOR_SERVICE));
    }

    @Override
    public boolean isUsingBottomBar() {
        return true;
    }

    private void initializeControlByIntent() {
        if (mIsImageCaptureIntent) {
            mActivity.getCameraAppUI().transitionToIntentCaptureLayout();
            setupCaptureParams();
        }
    }

    private void onPreviewStarted() {
        mAppController.onPreviewStarted();
        setCameraState(IDLE);
        startFaceDetection();
        locationFirstRun();
    }

    @Override
    public void onPreviewInitialDataReceived() {
        startSmartCamera();
    }

    // Prompt the user to pick to record location for the very first run of
    // camera only
    private void locationFirstRun() {
        SettingsManager settingsManager = mActivity.getSettingsManager();

        // Check whether we can parse the value. If not, let the user choose
        // again.
        try {
            if (settingsManager.isSet(SettingsManager.SETTING_RECORD_LOCATION)) {
                // This line will throw an exception if the value cannot be
                // parsed. Otherwise, we will return.
                settingsManager.getBoolean(SettingsManager.SETTING_RECORD_LOCATION);
                return;
            }
        } catch (Exception ex) {
            // Fall through and open the dialog.
        }
        if (mActivity.isSecureCamera()) {
            return;
        }
        // Check if the back camera exists
        int backCameraId = mAppController.getCameraProvider().getFirstBackCameraId();
        if (backCameraId == -1) {
            // If there is no back camera, do not show the prompt.
            return;
        }
        mUI.showLocationDialog();
    }

    @Override
    public void onPreviewUIReady() {
        startPreview();
    }

    @Override
    public void onPreviewUIDestroyed() {
        if (mCameraDevice == null) {
            return;
        }
        mCameraDevice.setPreviewTexture(null);
        stopPreview();
    }

    @Override
    public void startPreCaptureAnimation() {
        mAppController.startPreCaptureAnimation();
    }

    private void onCameraOpened() {
        openCameraCommon();
        initializeControlByIntent();
    }

    private void switchCamera() {
        if (mPaused) {
            return;
        }
        SettingsManager settingsManager = mActivity.getSettingsManager();

        Log.v(TAG, "Start to switch camera. id=" + mPendingSwitchCameraId);
        closeCamera();
        mCameraId = mPendingSwitchCameraId;
        mPendingSwitchCameraId = -1;
        settingsManager.set(SettingsManager.SETTING_CAMERA_ID, "" + mCameraId);
        mActivity.getCameraProvider().requestCamera(mCameraId);
        mUI.clearFaces();
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        }

        // TODO: this needs to be brought into onCameraAvailable();
        CameraInfo info = mActivity.getCameraProvider().getCameraInfo()[mCameraId];
        mMirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
        mFocusManager.setMirror(mMirror);
        // Start switch camera animation. Post a message because
        // onFrameAvailable from the old camera may already exist.
    }

    private final ButtonManager.ButtonCallback mCameraCallback =
            new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int state) {
                    if (mPaused || mPendingSwitchCameraId != -1) {
                        return;
                    }
                    mPendingSwitchCameraId = state;

                    Log.v(TAG, "Start to switch camera. cameraId=" + state);
                    // We need to keep a preview frame for the animation before
                    // releasing the camera. This will trigger
                    // onPreviewTextureCopied.
                    // TODO: Need to animate the camera switch
                    switchCamera();
                }
            };

    private final ButtonManager.ButtonCallback mHdrPlusCallback =
            new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int state) {
                    if (GcamHelper.hasGcamCapture()) {
                        // Set the camera setting to default backfacing.
                        SettingsManager settingsManager = mActivity.getSettingsManager();
                        settingsManager.setDefault(SettingsManager.SETTING_CAMERA_ID);
                        mHandler.sendEmptyMessage(MSG_SWITCH_TO_GCAM_MODULE);
                    } else {
                        mSceneMode = CameraUtil.SCENE_MODE_HDR;
                        updateParametersSceneMode();
                    }
                }
            };

    private final View.OnClickListener mCancelCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onCaptureCancelled();
        }
    };

    private final View.OnClickListener mDoneCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onCaptureDone();
        }
    };

    private final View.OnClickListener mRetakeCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mActivity.getCameraAppUI().transitionToIntentCaptureLayout();
            onCaptureRetake();
        }
    };

    @Override
    public HardwareSpec getHardwareSpec() {
        return (mParameters != null ? new HardwareSpecImpl(mParameters) : null);
    }

    @Override
    public CameraAppUI.BottomBarUISpec getBottomBarSpec() {
        CameraAppUI.BottomBarUISpec bottomBarSpec = new CameraAppUI.BottomBarUISpec();

        bottomBarSpec.enableCamera = true;
        bottomBarSpec.cameraCallback = mCameraCallback;
        bottomBarSpec.enableFlash = true;
        bottomBarSpec.enableHdr = true;
        bottomBarSpec.hdrCallback = mHdrPlusCallback;
        bottomBarSpec.enableGridLines = true;

        if (isImageCaptureIntent()) {
            bottomBarSpec.showCancel = true;
            bottomBarSpec.cancelCallback = mCancelCallback;
            bottomBarSpec.showDone = true;
            bottomBarSpec.doneCallback = mDoneCallback;
            bottomBarSpec.showRetake = true;
            bottomBarSpec.retakeCallback = mRetakeCallback;
        }

        return bottomBarSpec;
    }

    // either open a new camera or switch cameras
    private void openCameraCommon() {
        mUI.onCameraOpened(mParameters);
        if (mIsImageCaptureIntent) {
            // Set hdr plus to default: off.
            SettingsManager settingsManager = mActivity.getSettingsManager();
            settingsManager.setDefault(SettingsManager.SETTING_CAMERA_HDR_PLUS);
        }
        updateSceneMode();
    }

    @Override
    public void updatePreviewAspectRatio(float aspectRatio) {
        mAppController.updatePreviewAspectRatio(aspectRatio);
    }

    private void resetExposureCompensation() {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        if (settingsManager == null) {
            Log.e(TAG, "Settings manager is null!");
            return;
        }
        settingsManager.setDefault(SettingsManager.SETTING_EXPOSURE);
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized || mPaused) {
            return;
        }

        // Initialize location service.
        mActivity.syncLocationManagerSetting();

        mUI.initializeFirstTime();

        // We set the listener only when both service and shutterbutton
        // are initialized.
        getServices().getMemoryManager().addListener(this);

        mNamedImages = new NamedImages();

        mFirstTimeInitialized = true;
        addIdleHandler();

        mActivity.updateStorageSpaceAndHint();
    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
        // Start location update if needed.
        mActivity.syncLocationManagerSetting();

        getServices().getMemoryManager().addListener(this);
        mNamedImages = new NamedImages();
        mUI.initializeSecondTime(mParameters);
    }

    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                Storage.ensureOSXCompatible();
                return false;
            }
        });
    }

    private void startSmartCamera() {
        SmartCameraHelper.register(mCameraDevice, mParameters.getPreviewSize(), mActivity,
                (ViewGroup) mActivity.findViewById(R.id.camera_app_root));
    }

    private void stopSmartCamera() {
        SmartCameraHelper.tearDown();
    }

    @Override
    public void startFaceDetection() {
        if (mFaceDetectionStarted) {
            return;
        }
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = true;
            CameraInfo info = mAppController.getCameraProvider().getCameraInfo()[mCameraId];
            mUI.onStartFaceDetection(mDisplayOrientation,
                    (info.facing == CameraInfo.CAMERA_FACING_FRONT));
            mCameraDevice.setFaceDetectionCallback(mHandler, mUI);
            mCameraDevice.startFaceDetection();
        }
    }

    @Override
    public void stopFaceDetection() {
        if (!mFaceDetectionStarted) {
            return;
        }
        if (mParameters.getMaxNumDetectedFaces() > 0) {
            mFaceDetectionStarted = false;
            mCameraDevice.setFaceDetectionCallback(null, null);
            mCameraDevice.stopFaceDetection();
            mUI.clearFaces();
        }
    }

    private final class ShutterCallback
            implements CameraShutterCallback {

        private final boolean mNeedsAnimation;

        public ShutterCallback(boolean needsAnimation) {
            mNeedsAnimation = needsAnimation;
        }

        @Override
        public void onShutter(CameraProxy camera) {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.v(TAG, "mShutterLag = " + mShutterLag + "ms");
            if (mNeedsAnimation) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        animateAfterShutter();
                    }
                });
            }
        }
    }

    private final class PostViewPictureCallback
            implements CameraPictureCallback {
        @Override
        public void onPictureTaken(byte[] data, CameraProxy camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback
            implements CameraPictureCallback {
        @Override
        public void onPictureTaken(byte[] rawData, CameraProxy camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.v(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private final class JpegPictureCallback
            implements CameraPictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(final byte[] jpegData, CameraProxy camera) {
            setShutterEnabled(true);
            if (mPaused) {
                return;
            }
            if (mIsImageCaptureIntent) {
                stopPreview();
            }
            if (mSceneMode == CameraUtil.SCENE_MODE_HDR) {
                mUI.setSwipingEnabled(true);
            }

            mJpegPictureCallbackTime = System.currentTimeMillis();
            // If postview callback has arrived, the captured image is displayed
            // in postview callback. If not, the captured image is displayed in
            // raw picture callback.
            if (mPostViewPictureCallbackTime != 0) {
                mShutterToPictureDisplayedTime =
                        mPostViewPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
            } else {
                mShutterToPictureDisplayedTime =
                        mRawPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mRawPictureCallbackTime;
            }
            Log.v(TAG, "mPictureDisplayedToJpegCallbackTime = "
                    + mPictureDisplayedToJpegCallbackTime + "ms");

            mFocusManager.updateFocusUI(); // Ensure focus indicator is hidden.
            if (!mIsImageCaptureIntent) {
                setupPreview();
            }

            ExifInterface exif = Exif.getExif(jpegData);
            int orientation = Exif.getOrientation(exif);

            if (!mIsImageCaptureIntent) {
                // Calculate the width and the height of the jpeg.
                Size s = mParameters.getPictureSize();
                int width, height;
                if ((mJpegRotation + orientation) % 180 == 0) {
                    width = s.width;
                    height = s.height;
                } else {
                    width = s.height;
                    height = s.width;
                }
                NamedEntity name = mNamedImages.getNextNameEntity();
                String title = (name == null) ? null : name.title;
                long date = (name == null) ? -1 : name.date;

                // Handle debug mode outputs
                if (mDebugUri != null) {
                    // If using a debug uri, save jpeg there.
                    saveToDebugUri(jpegData);

                    // Adjust the title of the debug image shown in mediastore.
                    if (title != null) {
                        title = DEBUG_IMAGE_PREFIX + title;
                    }
                }

                if (title == null) {
                    Log.e(TAG, "Unbalanced name/data pair");
                } else {
                    if (date == -1) {
                        date = mCaptureStartTime;
                    }
                    if (mHeading >= 0) {
                        // heading direction has been updated by the sensor.
                        ExifTag directionRefTag = exif.buildTag(
                                ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                                ExifInterface.GpsTrackRef.MAGNETIC_DIRECTION);
                        ExifTag directionTag = exif.buildTag(
                                ExifInterface.TAG_GPS_IMG_DIRECTION,
                                new Rational(mHeading, 1));
                        exif.setTag(directionRefTag);
                        exif.setTag(directionTag);
                    }
                    getServices().getMediaSaver().addImage(
                            jpegData, title, date, mLocation, width, height,
                            orientation, exif, mOnMediaSavedListener, mContentResolver);
                }
                // Animate capture with real jpeg data instead of a preview
                // frame.
                mUI.animateCapture(jpegData, orientation, mMirror);
            } else {
                mJpegImageData = jpegData;
                if (!mQuickCapture) {
                    mUI.showCapturedImageForReview(jpegData, orientation, mMirror);
                } else {
                    onCaptureDone();
                }
            }

            // Check this in advance of each shot so we don't add to shutter
            // latency. It's true that someone else could write to the SD card
            // in the mean time and fill it, but that could have happened
            // between the shutter press and saving the JPEG too.
            mActivity.updateStorageSpaceAndHint();

            long now = System.currentTimeMillis();
            mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
            Log.v(TAG, "mJpegCallbackFinishTime = "
                    + mJpegCallbackFinishTime + "ms");
            mJpegPictureCallbackTime = 0;
        }
    }

    private final class AutoFocusCallback implements CameraAFCallback {
        @Override
        public void onAutoFocus(
                boolean focused, CameraProxy camera) {
            if (mPaused) {
                return;
            }

            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.v(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms");
            setCameraState(IDLE);
            mFocusManager.onAutoFocus(focused, mUI.isShutterPressed());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private final class AutoFocusMoveCallback
            implements CameraAFMoveCallback {
        @Override
        public void onAutoFocusMoving(
                boolean moving, CameraProxy camera) {
            mFocusManager.onAutoFocusMoving(moving);
        }
    }

    /**
     * This class is just a thread-safe queue for name,date holder objects.
     */
    public static class NamedImages {
        private final Vector<NamedEntity> mQueue;

        public NamedImages() {
            mQueue = new Vector<NamedEntity>();
        }

        public void nameNewImage(long date) {
            NamedEntity r = new NamedEntity();
            r.title = CameraUtil.createJpegName(date);
            r.date = date;
            mQueue.add(r);
        }

        public NamedEntity getNextNameEntity() {
            synchronized (mQueue) {
                if (!mQueue.isEmpty()) {
                    return mQueue.remove(0);
                }
            }
            return null;
        }

        public static class NamedEntity {
            public String title;
            public long date;
        }
    }

    private void setCameraState(int state) {
        mCameraState = state;
        switch (state) {
            case PhotoController.PREVIEW_STOPPED:
            case PhotoController.SNAPSHOT_IN_PROGRESS:
            case PhotoController.SWITCHING_CAMERA:
                // TODO: Tell app UI to disable swipe
                break;
            case PhotoController.IDLE:
                // TODO: Tell app UI to enable swipe
                break;
        }
    }

    private void animateAfterShutter() {
        // Only animate when in full screen capture mode
        // i.e. If monkey/a user swipes to the gallery during picture taking,
        // don't show animation
        if (!mIsImageCaptureIntent) {
            mUI.animateFlash();
        }
    }

    @Override
    public boolean capture() {
        // If we are already in the middle of taking a snapshot or the image
        // save request is full then ignore.
        if (mCameraDevice == null || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA || !mShutterEnabled) {
            return false;
        }
        mCaptureStartTime = System.currentTimeMillis();
        mPostViewPictureCallbackTime = 0;
        mJpegImageData = null;

        final boolean animateBefore = (mSceneMode == CameraUtil.SCENE_MODE_HDR);

        if (animateBefore) {
            animateAfterShutter();
        }

        // Set rotation and gps data.
        int orientation;

        // We need to be consistent with the framework orientation (i.e. the
        // orientation of the UI.) when the auto-rotate screen setting is on.
        if (mActivity.isAutoRotateScreen()) {
            orientation = (360 - mDisplayRotation) % 360;
        } else {
            orientation = mOrientation;
        }
        CameraInfo info = mActivity.getCameraProvider().getCameraInfo()[mCameraId];
        mJpegRotation = CameraUtil.getJpegRotation(info, orientation);
        mParameters.setRotation(mJpegRotation);
        Location loc = mActivity.getLocationManager().getCurrentLocation();
        CameraUtil.setGpsParameters(mParameters, loc);
        mCameraDevice.setParameters(mParameters);

        // We don't want user to press the button again while taking a
        // multi-second HDR photo.
        setShutterEnabled(false);
        mCameraDevice.takePicture(mHandler,
                new ShutterCallback(!animateBefore),
                mRawPictureCallback, mPostViewPictureCallback,
                new JpegPictureCallback(loc));

        mNamedImages.nameNewImage(mCaptureStartTime);

        mFaceDetectionStarted = false;
        setCameraState(SNAPSHOT_IN_PROGRESS);
        UsageStatistics.captureEvent(eventprotos.NavigationChange.Mode.PHOTO_CAPTURE,
                UsageStatistics.hashFileName(mNamedImages.mQueue.lastElement().title + ".jpg"),
                mParameters);
        return true;
    }

    @Override
    public void setFocusParameters() {
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    private void updateSceneMode() {
        // If scene mode is set, we cannot set flash mode, white balance, and
        // focus mode, instead, we read it from driver
        if (!Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            overrideCameraSettings(mParameters.getFlashMode(),
                    mParameters.getWhiteBalance(), mParameters.getFocusMode());
        }
    }

    private void overrideCameraSettings(final String flashMode,
            final String whiteBalance, final String focusMode) {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        settingsManager.set(SettingsManager.SETTING_FLASH_MODE, flashMode);
        settingsManager.set(SettingsManager.SETTING_WHITE_BALANCE, whiteBalance);
        settingsManager.set(SettingsManager.SETTING_FOCUS_MODE, focusMode);
    }

    @Override
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return;
        }
        mOrientation = CameraUtil.roundOrientation(orientation, mOrientation);

        // Show the toast after getting the first orientation changed.
        if (mHandler.hasMessages(MSG_SHOW_TAP_TO_FOCUS_TOAST)) {
            mHandler.removeMessages(MSG_SHOW_TAP_TO_FOCUS_TOAST);
            showTapToFocusToast();
        }
    }

    @Override
    public void onCameraAvailable(CameraProxy cameraProxy) {
        if (mPaused) {
            return;
        }
        mCameraDevice = cameraProxy;

        resetExposureCompensation();
        initializeCapabilities();

        // Reset zoom value index.
        mZoomValue = 0;
        if (mFocusManager == null) {
            initializeFocusManager();
        }
        mFocusManager.setParameters(mInitialParams);

        // Do camera parameter dependent initialization.
        mParameters = mCameraDevice.getParameters();
        setCameraParameters(UPDATE_PARAM_ALL);
        // Set a listener which updates camera parameters based
        // on changed settings.
        SettingsManager settingsManager = mActivity.getSettingsManager();
        settingsManager.addListener(this);
        mCameraPreviewParamsReady = true;

        startPreview();

        onCameraOpened();
    }

    @Override
    public void onCaptureCancelled() {
        mActivity.setResultEx(Activity.RESULT_CANCELED, new Intent());
        mActivity.finish();
    }

    @Override
    public void onCaptureRetake() {
        if (mPaused) {
            return;
        }
        mUI.hidePostCaptureAlert();
        setupPreview();
    }

    @Override
    public void onCaptureDone() {
        if (mPaused) {
            return;
        }

        byte[] data = mJpegImageData;

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value. If the
            // caller specifies a "save uri" then write the data to its
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    mActivity.setResultEx(Activity.RESULT_OK);
                    mActivity.finish();
                } catch (IOException ex) {
                    // ignore exception
                } finally {
                    CameraUtil.closeSilently(outputStream);
                }
            } else {
                ExifInterface exif = Exif.getExif(data);
                int orientation = Exif.getOrientation(exif);
                Bitmap bitmap = CameraUtil.makeBitmap(data, 50 * 1024);
                bitmap = CameraUtil.rotate(bitmap, orientation);
                mActivity.setResultEx(Activity.RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
                mActivity.finish();
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = mActivity.getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = mActivity.openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } catch (IOException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } finally {
                CameraUtil.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean(CameraUtil.KEY_RETURN_DATA, true);
            }
            if (mActivity.isSecureCamera()) {
                newExtras.putBoolean(CameraUtil.KEY_SHOW_WHEN_LOCKED, true);
            }

            // TODO: Share this constant.
            final String CROP_ACTION = "com.android.camera.action.CROP";
            Intent cropIntent = new Intent(CROP_ACTION);

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            mActivity.startActivityForResult(cropIntent, REQUEST_CROP);
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        if (mPaused || (mCameraState == SNAPSHOT_IN_PROGRESS)
                || (mCameraState == PREVIEW_STOPPED)) {
            return;
        }

        // Do not do focus if there is not enough storage.
        if (pressed && !canTakePicture()) {
            return;
        }

        if (pressed) {
            mFocusManager.onShutterDown();
        } else {
            mFocusManager.onShutterUp();
        }
    }

    @Override
    public void onShutterButtonClick() {
        if (mPaused || (mCameraState == SWITCHING_CAMERA)
                || (mCameraState == PREVIEW_STOPPED)) {
            return;
        }

        // Do not take the picture if there is not enough storage.
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpaceBytes());
            return;
        }
        Log.v(TAG, "onShutterButtonClick: mCameraState=" + mCameraState);

        if (mSceneMode == CameraUtil.SCENE_MODE_HDR) {
            mUI.setSwipingEnabled(false);
        }
        // If the user wants to do a snapshot while the previous one is still
        // in progress, remember the fact and do it after we finish the previous
        // one and re-start the preview. Snapshot in progress also includes the
        // state that autofocus is focusing and a picture will be taken when
        // focus callback arrives.
        if ((mFocusManager.isFocusingSnapOnFinish() || mCameraState == SNAPSHOT_IN_PROGRESS)
                && !mIsImageCaptureIntent) {
            mSnapshotOnIdle = true;
            return;
        }

        mSnapshotOnIdle = false;
        mFocusManager.doSnap();
    }

    private void onResumeTasks() {
        Log.v(TAG, "Executing onResumeTasks.");
        mActivity.getCameraProvider().requestCamera(mCameraId);

        mJpegPictureCallbackTime = 0;
        mZoomValue = 0;

        mOnResumeTime = SystemClock.uptimeMillis();
        checkDisplayRotation();

        // If first time initialization is not finished, put it in the
        // message queue.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(MSG_FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }

        UsageStatistics.changeScreen(eventprotos.NavigationChange.Mode.PHOTO_CAPTURE,
                eventprotos.CameraEvent.InteractionCause.BUTTON);

        Sensor gsensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (gsensor != null) {
            mSensorManager.registerListener(this, gsensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        Sensor msensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (msensor != null) {
            mSensorManager.registerListener(this, msensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /**
     * The focus manager is the first UI related element to get initialized, and
     * it requires the RenderOverlay, so initialize it here
     */
    private void initializeFocusManager() {
        // Create FocusManager object. startPreview needs it.
        // if mFocusManager not null, reuse it
        // otherwise create a new instance
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        } else {
            CameraInfo info = mAppController.getCameraProvider().getCameraInfo()[mCameraId];
            mMirror = (info.facing == CameraInfo.CAMERA_FACING_FRONT);
            String[] defaultFocusModes = mActivity.getResources().getStringArray(
                    R.array.pref_camera_focusmode_default_array);
            mFocusManager = new FocusOverlayManager(mActivity.getSettingsManager(),
                    defaultFocusModes,
                    mInitialParams, this, mMirror,
                    mActivity.getMainLooper(), mUI.getFocusUI());
        }
        mAppController.addPreviewAreaSizeChangedListener(mFocusManager);
    }

    @Override
    public void resume() {
        mPaused = false;
        if (mFocusManager != null) {
            // If camera is not open when resume is called, focus manager will
            // not
            // be initialized yet, in which case it will start listening to
            // preview area size change later in the initialization.
            mAppController.addPreviewAreaSizeChangedListener(mFocusManager);
        }

        if (mUI.getPreviewAreaSizeChangedListener() != null) {
            mAppController.addPreviewAreaSizeChangedListener(
                    mUI.getPreviewAreaSizeChangedListener());
        }

        // Add delay on resume from lock screen only, in order to to speed up
        // the onResume --> onPause --> onResume cycle from lock screen.
        // Don't do always because letting go of thread can cause delay.
        String action = mActivity.getIntent().getAction();
        if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action)
                || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)) {
            Log.v(TAG, "On resume, from lock screen.");
            // Note: onPauseAfterSuper() will delete this runnable, so we will
            // at most have 1 copy queued up.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    onResumeTasks();
                }
            }, ON_RESUME_TASKS_DELAY_MSEC);
        } else {
            Log.v(TAG, "On resume.");
            onResumeTasks();
        }
    }

    @Override
    public void pause() {
        mPaused = true;
        Sensor gsensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (gsensor != null) {
            mSensorManager.unregisterListener(this, gsensor);
        }

        Sensor msensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (msensor != null) {
            mSensorManager.unregisterListener(this, msensor);
        }

        // Reset the focus first. Camera CTS does not guarantee that
        // cancelAutoFocus is allowed after preview stops.
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            mCameraDevice.cancelAutoFocus();
        }

        // If the camera has not been opened asynchronously yet,
        // and startPreview hasn't been called, then this is a no-op.
        // (e.g. onResume -> onPause -> onResume).
        stopPreview();

        mNamedImages = null;

        if (mLocationManager != null) {
            mLocationManager.recordLocation(false);
        }

        // If we are in an image capture intent and has taken
        // a picture, we just clear it in onPause.
        mJpegImageData = null;

        // Remove the messages and runnables in the queue.
        mHandler.removeCallbacksAndMessages(null);

        closeCamera();
        mActivity.enableKeepScreenOn(false);
        mUI.onPause();

        mPendingSwitchCameraId = -1;
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        }
        getServices().getMemoryManager().removeListener(this);
        mAppController.removePreviewAreaSizeChangedListener(mFocusManager);
        if (mUI.getPreviewAreaSizeChangedListener() != null) {
            mAppController.removePreviewAreaSizeChangedListener(
                    mUI.getPreviewAreaSizeChangedListener());
        }

        SettingsManager settingsManager = mActivity.getSettingsManager();
        settingsManager.removeListener(this);
    }

    @Override
    public void destroy() {
        // TODO: implement this.
    }

    @Override
    public void onLayoutOrientationChanged(boolean isLandscape) {
        setDisplayOrientation();
    }

    @Override
    public void updateCameraOrientation() {
        if (mDisplayRotation != CameraUtil.getDisplayRotation(mActivity)) {
            setDisplayOrientation();
        }
    }

    private boolean canTakePicture() {
        return isCameraIdle()
                && (mActivity.getStorageSpaceBytes() > Storage.LOW_STORAGE_THRESHOLD_BYTES);
    }

    @Override
    public void autoFocus() {
        mFocusStartTime = System.currentTimeMillis();
        mCameraDevice.autoFocus(mHandler, mAutoFocusCallback);
        setCameraState(FOCUSING);
    }

    @Override
    public void cancelAutoFocus() {
        mCameraDevice.cancelAutoFocus();
        setCameraState(IDLE);
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mPaused || mCameraDevice == null || !mFirstTimeInitialized
                || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA
                || mCameraState == PREVIEW_STOPPED) {
            return;
        }

        // Check if metering area or focus area is supported.
        if (!mFocusAreaSupported && !mMeteringAreaSupported) {
            return;
        }
        mFocusManager.onSingleTapUp(x, y);
    }

    @Override
    public boolean onBackPressed() {
        return mUI.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_FOCUS:
                if (/* TODO: mActivity.isInCameraApp() && */mFirstTimeInitialized) {
                    if (event.getRepeatCount() == 0) {
                        onShutterButtonFocus(true);
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_CAMERA:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // If we get a dpad center event without any focused view, move
                // the focus to the shutter button and press it.
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    // Start auto-focus immediately to reduce shutter lag. After
                    // the shutter button gets the focus, onShutterButtonFocus()
                    // will be called again but it is fine.
                    onShutterButtonFocus(true);
                    mUI.pressShutterButton();
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (/* mActivity.isInCameraApp() && */mFirstTimeInitialized) {
                    onShutterButtonClick();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    onShutterButtonFocus(false);
                }
                return true;
        }
        return false;
    }

    private void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.setZoomChangeListener(null);
            mCameraDevice.setFaceDetectionCallback(null, null);
            mCameraDevice.setErrorCallback(null);

            mFaceDetectionStarted = false;
            mActivity.getCameraProvider().releaseCamera(mCameraDevice.getCameraId());
            mCameraDevice = null;
            setCameraState(PREVIEW_STOPPED);
            mFocusManager.onCameraReleased();
        }
    }

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mDisplayOrientation = CameraUtil.getDisplayOrientation(mDisplayRotation, mCameraId);
        mCameraDisplayOrientation = mDisplayOrientation;
        mUI.setDisplayOrientation(mDisplayOrientation);
        if (mFocusManager != null) {
            mFocusManager.setDisplayOrientation(mDisplayOrientation);
        }
        // Change the camera display orientation
        if (mCameraDevice != null) {
            mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
        }
    }

    /** Only called by UI thread. */
    private void setupPreview() {
        mFocusManager.resetTouchFocus();
        startPreview();
    }

    /**
     * Returns whether we can/should start the preview or not.
     */
    private boolean checkPreviewPreconditions() {
        if (mPaused) {
            return false;
        }

        if (mCameraDevice == null) {
            Log.w(TAG, "startPreview: camera device not ready yet.");
            return false;
        }

        SurfaceTexture st = mActivity.getCameraAppUI().getSurfaceTexture();
        if (st == null) {
            Log.w(TAG, "startPreview: surfaceTexture is not ready.");
            return false;
        }

        if (!mCameraPreviewParamsReady) {
            Log.w(TAG, "startPreview: parameters for preview is not ready.");
            return false;
        }
        return true;
    }

    /**
     * The start/stop preview should only run on the UI thread.
     */
    private void startPreview() {
        if (!checkPreviewPreconditions()) {
            return;
        }

        mCameraDevice.setErrorCallback(mErrorCallback);
        // ICS camera frameworks has a bug. Face detection state is not cleared
        // after taking a picture. Stop the preview to work around it. The bug
        // was fixed in JB.
        if (mCameraState != PREVIEW_STOPPED) {
            stopPreview();
        }

        setDisplayOrientation();

        if (!mSnapshotOnIdle) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus
            // to resume it because it may have been paused by autoFocus call.
            String focusMode = mFocusManager.getFocusMode();
            if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(focusMode)) {
                mCameraDevice.cancelAutoFocus();
            }
            mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.
        }
        setCameraParameters(UPDATE_PARAM_ALL);
        // Let UI set its expected aspect ratio
        mCameraDevice.setPreviewTexture(mActivity.getCameraAppUI().getSurfaceTexture());

        // This is to notify app controller that preview will start next, so app
        // controller can set preview callbacks if needed. This has to happen
        // before preview is started as a workaround of the framework bug
        // related to
        // preview callbacks at b/12591410.
        mAppController.onPreviewReadyToStart();
        Log.v(TAG, "startPreview");
        mCameraDevice.startPreview();

        mFocusManager.onPreviewStarted();
        onPreviewStarted();

        if (mSnapshotOnIdle) {
            mHandler.post(mDoSnapRunnable);
        }
    }

    @Override
    public void stopPreview() {
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            Log.v(TAG, "stopPreview");
            mCameraDevice.stopPreview();
            mFaceDetectionStarted = false;
        }
        setCameraState(PREVIEW_STOPPED);
        if (mFocusManager != null) {
            mFocusManager.onPreviewStopped();
        }
        stopSmartCamera();
    }

    @Override
    public void onSettingChanged(SettingsManager settingsManager, int id) {
        switch (id) {
            case SettingsManager.SETTING_FLASH_MODE: {
                updateParametersFlashMode();
                break;
            }
            default: {
                // Do nothing.
            }
        }

        if (mCameraDevice != null) {
            mCameraDevice.setParameters(mParameters);
        }
    }

    private void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        int[] fpsRange = CameraUtil.getPhotoPreviewFpsRange(mParameters);
        if (fpsRange != null && fpsRange.length > 0) {
            mParameters.setPreviewFpsRange(
                    fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
                    fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
        }

        mParameters.set(CameraUtil.RECORDING_HINT, CameraUtil.FALSE);

        // Disable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "false");
        }
    }

    private void updateCameraParametersZoom() {
        // Set zoom.
        if (mParameters.isZoomSupported()) {
            mParameters.setZoom(mZoomValue);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAutoExposureLockIfSupported() {
        if (mAeLockSupported) {
            mParameters.setAutoExposureLock(mFocusManager.getAeAwbLock());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAutoWhiteBalanceLockIfSupported() {
        if (mAwbLockSupported) {
            mParameters.setAutoWhiteBalanceLock(mFocusManager.getAeAwbLock());
        }
    }

    private void setFocusAreasIfSupported() {
        if (mFocusAreaSupported) {
            mParameters.setFocusAreas(mFocusManager.getFocusAreas());
        }
    }

    private void setMeteringAreasIfSupported() {
        if (mMeteringAreaSupported) {
            mParameters.setMeteringAreas(mFocusManager.getMeteringAreas());
        }
    }

    private void updateCameraParametersPreference() {
        setAutoExposureLockIfSupported();
        setAutoWhiteBalanceLockIfSupported();
        setFocusAreasIfSupported();
        setMeteringAreasIfSupported();

        // Initialize focus mode.
        mFocusManager.overrideFocusMode(null);
        mParameters.setFocusMode(mFocusManager.getFocusMode());

        // Set picture size.
        updateParametersPictureSize();

        // Set JPEG quality.
        updateParametersPictureQuality();

        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.

        // Set exposure compensation
        updateParametersExposureCompensation();

        // Set the scene mode: also sets flash and white balance.
        updateParametersSceneMode();

        if (mContinuousFocusSupported && ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK) {
            updateAutoFocusMoveCallback();
        }
    }

    private void updateParametersPictureSize() {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        String pictureSize = settingsManager.get(SettingsManager.SETTING_PICTURE_SIZE);

        List<Size> supported = mParameters.getSupportedPictureSizes();
        SettingsUtil.setCameraPictureSize(pictureSize, supported, mParameters);
        Size size = mParameters.getPictureSize();

        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Size> sizes = mParameters.getSupportedPreviewSizes();
        Size optimalSize = CameraUtil.getOptimalPreviewSize(mActivity, sizes,
                (double) size.width / size.height);
        Size original = mParameters.getPreviewSize();
        if (!original.equals(optimalSize)) {
            mParameters.setPreviewSize(optimalSize.width, optimalSize.height);

            // Zoom related settings will be changed for different preview
            // sizes, so set and read the parameters to get latest values
            if (mHandler.getLooper() == Looper.myLooper()) {
                // On UI thread only, not when camera starts up
                setupPreview();
            } else {
                mCameraDevice.setParameters(mParameters);
            }
            mParameters = mCameraDevice.getParameters();
        }

        if (optimalSize.width != 0 && optimalSize.height != 0) {
            mUI.updatePreviewAspectRatio((float) optimalSize.width
                    / (float) optimalSize.height);
        }
        Log.v(TAG, "Preview size is " + optimalSize.width + "x" + optimalSize.height);
    }

    private void updateParametersPictureQuality() {
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);
    }

    private void updateParametersExposureCompensation() {
        SettingsManager settingsManager = mActivity.getSettingsManager();

        int value = Integer.parseInt(settingsManager.get(SettingsManager.SETTING_EXPOSURE));
        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (value >= min && value <= max) {
            mParameters.setExposureCompensation(value);
        } else {
            Log.w(TAG, "invalid exposure range: " + value);
        }
    }

    private void updateParametersSceneMode() {
        SettingsManager settingsManager = mActivity.getSettingsManager();

        mSceneMode = settingsManager.get(SettingsManager.SETTING_SCENE_MODE);
        if (CameraUtil.isSupported(mSceneMode, mParameters.getSupportedSceneModes())) {
            if (!mParameters.getSceneMode().equals(mSceneMode)) {
                mParameters.setSceneMode(mSceneMode);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mCameraDevice.setParameters(mParameters);
                mParameters = mCameraDevice.getParameters();
            }
        } else {
            mSceneMode = mParameters.getSceneMode();
            if (mSceneMode == null) {
                mSceneMode = Parameters.SCENE_MODE_AUTO;
            }
        }

        if (Parameters.SCENE_MODE_AUTO.equals(mSceneMode)) {
            // Set flash mode.
            updateParametersFlashMode();

            // Set white balance mode.
            updateParametersWhiteBalanceMode();

            // Set focus mode.
            mFocusManager.overrideFocusMode(null);
            mParameters.setFocusMode(mFocusManager.getFocusMode());
        } else {
            mFocusManager.overrideFocusMode(mParameters.getFocusMode());
        }
    }

    private void updateParametersFlashMode() {
        SettingsManager settingsManager = mActivity.getSettingsManager();

        String flashMode = settingsManager.get(SettingsManager.SETTING_FLASH_MODE);
        List<String> supportedFlash = mParameters.getSupportedFlashModes();
        if (CameraUtil.isSupported(flashMode, supportedFlash)) {
            mParameters.setFlashMode(flashMode);
        }
    }

    private void updateParametersWhiteBalanceMode() {
        SettingsManager settingsManager = mActivity.getSettingsManager();

        // Set white balance parameter.
        String whiteBalance = settingsManager.get(SettingsManager.SETTING_WHITE_BALANCE);
        if (CameraUtil.isSupported(whiteBalance,
                mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void updateAutoFocusMoveCallback() {
        if (mParameters.getFocusMode().equals(CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mCameraDevice.setAutoFocusMoveCallback(mHandler,
                    (CameraAFMoveCallback) mAutoFocusMoveCallback);
        } else {
            mCameraDevice.setAutoFocusMoveCallback(null, null);
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    private void setCameraParameters(int updateSet) {
        if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
            updateCameraParametersInitialize();
        }

        if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
            updateCameraParametersZoom();
        }

        if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
            updateCameraParametersPreference();
        }

        mCameraDevice.setParameters(mParameters);
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
            updateSceneMode();
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(MSG_SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(MSG_SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    @Override
    public boolean isCameraIdle() {
        return (mCameraState == IDLE) ||
                (mCameraState == PREVIEW_STOPPED) ||
                ((mFocusManager != null) && mFocusManager.isFocusCompleted()
                && (mCameraState != SWITCHING_CAMERA));
    }

    @Override
    public boolean isImageCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
        || CameraActivity.ACTION_IMAGE_CAPTURE_SECURE.equals(action));
    }

    private void setupCaptureParams() {
        Bundle myExtras = mActivity.getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    private void showTapToFocusToast() {
        // TODO: Use a toast?
        new RotateTextToast(mActivity, R.string.tap_to_focus, 0).show();
        // Clear the preference.
        SettingsManager settingsManager = mActivity.getSettingsManager();
        settingsManager.setBoolean(
                SettingsManager.SETTING_CAMERA_FIRST_USE_HINT_SHOWN, false);
    }

    private void initializeCapabilities() {
        mInitialParams = mCameraDevice.getParameters();
        mFocusAreaSupported = CameraUtil.isFocusAreaSupported(mInitialParams);
        mMeteringAreaSupported = CameraUtil.isMeteringAreaSupported(mInitialParams);
        mAeLockSupported = CameraUtil.isAutoExposureLockSupported(mInitialParams);
        mAwbLockSupported = CameraUtil.isAutoWhiteBalanceLockSupported(mInitialParams);
        mContinuousFocusSupported = mInitialParams.getSupportedFocusModes().contains(
                CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

    private void setShutterEnabled(boolean enabled) {
        mShutterEnabled = enabled;
        mUI.enableShutter(enabled);
    }

    // TODO: Remove this
    @Override
    public int onZoomChanged(int index) {
        // Not useful to change zoom value when the activity is paused.
        if (mPaused) {
            return index;
        }
        mZoomValue = index;
        if (mParameters == null || mCameraDevice == null) {
            return index;
        }
        // Set zoom parameters asynchronously
        mParameters.setZoom(mZoomValue);
        mCameraDevice.setParameters(mParameters);
        Parameters p = mCameraDevice.getParameters();
        if (p != null) {
            return p.getZoom();
        }
        return index;
    }

    @Override
    public int getCameraState() {
        return mCameraState;
    }

    @Override
    public void onMemoryStateChanged(int state) {
        setShutterEnabled(state == MemoryManager.STATE_OK);
    }

    @Override
    public void onLowMemory() {
        // Not much we can do in the photo module.
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        float[] data;
        if (type == Sensor.TYPE_ACCELEROMETER) {
            data = mGData;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            data = mMData;
        } else {
            // we should not be here.
            return;
        }
        for (int i = 0; i < 3; i++) {
            data[i] = event.values[i];
        }
        float[] orientation = new float[3];
        SensorManager.getRotationMatrix(mR, null, mGData, mMData);
        SensorManager.getOrientation(mR, orientation);
        mHeading = (int) (orientation[0] * 180f / Math.PI) % 360;
        if (mHeading < 0) {
            mHeading += 360;
        }
    }

    // For debugging only.
    public void setDebugUri(Uri uri) {
        mDebugUri = uri;
    }

    // For debugging only.
    private void saveToDebugUri(byte[] data) {
        if (mDebugUri != null) {
            OutputStream outputStream = null;
            try {
                outputStream = mContentResolver.openOutputStream(mDebugUri);
                outputStream.write(data);
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception while writing debug jpeg file", e);
            } finally {
                CameraUtil.closeSilently(outputStream);
            }
        }
    }
}
