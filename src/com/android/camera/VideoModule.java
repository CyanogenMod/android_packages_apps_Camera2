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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.LocationManager;
import com.android.camera.app.MediaSaver;
import com.android.camera.app.MemoryManager;
import com.android.camera.app.MemoryManager.MemoryListener;
import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.hardware.HardwareSpecImpl;
import com.android.camera.module.ModuleController;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsUtil;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.stats.UsageStatistics;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent;
import com.android.ex.camera2.portability.CameraAgent.CameraPictureCallback;
import com.android.ex.camera2.portability.CameraAgent.CameraProxy;
import com.android.ex.camera2.portability.CameraCapabilities;
import com.android.ex.camera2.portability.CameraDeviceInfo.Characteristics;
import com.android.ex.camera2.portability.CameraSettings;
import com.google.common.logging.eventprotos;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class VideoModule extends CameraModule
        implements FocusOverlayManager.Listener, MediaRecorder.OnErrorListener,
        MediaRecorder.OnInfoListener, MemoryListener,
        OrientationManager.OnOrientationChangeListener, VideoController {

    private static final Log.Tag TAG = new Log.Tag("VideoModule");

    // Messages defined for the UI thread handler.
    private static final int MSG_CHECK_DISPLAY_ROTATION = 4;
    private static final int MSG_UPDATE_RECORD_TIME = 5;
    private static final int MSG_ENABLE_SHUTTER_BUTTON = 6;
    private static final int MSG_SWITCH_CAMERA = 8;
    private static final int MSG_SWITCH_CAMERA_START_ANIMATION = 9;

    private static final long SHUTTER_BUTTON_TIMEOUT = 500L; // 500ms

    /**
     * An unpublished intent flag requesting to start recording straight away
     * and return as soon as recording is stopped.
     * TODO: consider publishing by moving into MediaStore.
     */
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // module fields
    private CameraActivity mActivity;
    private boolean mPaused;

    // if, during and intent capture, the activity is paused (e.g. when app switching or reviewing a
    // shot video), we don't want the bottom bar intent ui to reset to the capture button
    private boolean mDontResetIntentUiOnResume;

    private int mCameraId;
    private CameraSettings mCameraSettings;
    private CameraCapabilities mCameraCapabilities;
    private HardwareSpec mHardwareSpec;

    private boolean mIsInReviewMode;
    private boolean mSnapshotInProgress = false;

    // Preference must be read before starting preview. We check this before starting
    // preview.
    private boolean mPreferenceRead;

    private boolean mIsVideoCaptureIntent;
    private boolean mQuickCapture;

    private MediaRecorder mMediaRecorder;
    /** Manager used to mute sounds and vibrations during video recording. */
    private AudioManager mAudioManager;
    /*
     * The ringer mode that was set when video recording started. We use this to
     * reset the mode once video recording has stopped.
     */
    private int mOriginalRingerMode;

    private boolean mSwitchingCamera;
    private boolean mMediaRecorderRecording = false;
    private long mRecordingStartTime;
    private boolean mRecordingTimeCountsDown = false;
    private long mOnResumeTime;
    // The video file that the hardware camera is about to record into
    // (or is recording into.
    private String mVideoFilename;
    private ParcelFileDescriptor mVideoFileDescriptor;

    // The video file that has already been recorded, and that is being
    // examined by the user.
    private String mCurrentVideoFilename;
    private Uri mCurrentVideoUri;
    private boolean mCurrentVideoUriFromMediaSaved;
    private ContentValues mCurrentVideoValues;

    private CamcorderProfile mProfile;

    // The video duration limit. 0 means no limit.
    private int mMaxVideoDurationInMs;

    boolean mPreviewing = false; // True if preview is started.
    // The display rotation in degrees. This is only valid when mPreviewing is
    // true.
    private int mDisplayRotation;
    private int mCameraDisplayOrientation;
    private AppController mAppController;

    private int mDesiredPreviewWidth;
    private int mDesiredPreviewHeight;
    private ContentResolver mContentResolver;

    private LocationManager mLocationManager;

    private int mPendingSwitchCameraId;
    private final Handler mHandler = new MainHandler();
    private VideoUI mUI;
    private CameraProxy mCameraDevice;

    private float mZoomValue;  // The current zoom ratio.

    private final MediaSaver.OnMediaSavedListener mOnVideoSavedListener =
            new MediaSaver.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mCurrentVideoUri = uri;
                        mCurrentVideoUriFromMediaSaved = true;
                        onVideoSaved();
                        mActivity.notifyNewMedia(uri);
                    }
                }
            };

    private final MediaSaver.OnMediaSavedListener mOnPhotoSavedListener =
            new MediaSaver.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                    }
                }
            };
    private FocusOverlayManager mFocusManager;
    private boolean mMirror;
    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;

    private final CameraAgent.CameraAFCallback mAutoFocusCallback =
            new CameraAgent.CameraAFCallback() {
        @Override
        public void onAutoFocus(boolean focused, CameraProxy camera) {
            if (mPaused) {
                return;
            }
            mFocusManager.onAutoFocus(focused, false);
        }
    };

    private final Object mAutoFocusMoveCallback =
            ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK
                    ? new CameraAgent.CameraAFMoveCallback() {
                @Override
                public void onAutoFocusMoving(boolean moving, CameraProxy camera) {
                     mFocusManager.onAutoFocusMoving(moving);
                }
            } : null;

    /**
     * This Handler is used to post message back onto the main thread of the
     * application.
     */
    private class MainHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case MSG_ENABLE_SHUTTER_BUTTON:
                    mAppController.setShutterEnabled(true);
                    break;

                case MSG_UPDATE_RECORD_TIME: {
                    updateRecordingTime();
                    break;
                }

                case MSG_CHECK_DISPLAY_ROTATION: {
                    // Restart the preview if display rotation has changed.
                    // Sometimes this happens when the device is held upside
                    // down and camera app is opened. Rotation animation will
                    // take some time and the rotation value we have got may be
                    // wrong. Framework does not have a callback for this now.
                    if ((CameraUtil.getDisplayRotation() != mDisplayRotation)
                            && !mMediaRecorderRecording && !mSwitchingCamera) {
                        startPreview();
                    }
                    if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
                        mHandler.sendEmptyMessageDelayed(MSG_CHECK_DISPLAY_ROTATION, 100);
                    }
                    break;
                }

                case MSG_SWITCH_CAMERA: {
                    switchCamera();
                    break;
                }

                case MSG_SWITCH_CAMERA_START_ANIMATION: {
                    //TODO:
                    //((CameraScreenNail) mActivity.mCameraScreenNail).animateSwitchCamera();

                    // Enable all camera controls.
                    mSwitchingCamera = false;
                    break;
                }

                default:
                    Log.v(TAG, "Unhandled message: " + msg.what);
                    break;
            }
        }
    }

    private BroadcastReceiver mReceiver = null;

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                stopVideoRecording();
            } else if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
                Toast.makeText(mActivity,
                        mActivity.getResources().getString(R.string.wait), Toast.LENGTH_LONG).show();
            }
        }
    }

    private int mShutterIconId;


    /**
     * Construct a new video module.
     */
    public VideoModule(AppController app) {
        super(app);
    }

    @Override
    public String getPeekAccessibilityString() {
        return mAppController.getAndroidContext()
            .getResources().getString(R.string.video_accessibility_peek);
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                mActivity.getString(R.string.video_file_name_format));

        return dateFormat.format(date);
    }

    @Override
    public void init(CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent) {
        mActivity = activity;
        // TODO: Need to look at the controller interface to see if we can get
        // rid of passing in the activity directly.
        mAppController = mActivity;
        mAudioManager = AndroidServices.instance().provideAudioManager();

        mActivity.updateStorageSpaceAndHint(null);

        mUI = new VideoUI(mActivity, this,  mActivity.getModuleLayoutRoot());
        mActivity.setPreviewStatusListener(mUI);

        SettingsManager settingsManager = mActivity.getSettingsManager();
        mCameraId = settingsManager.getInteger(mAppController.getModuleScope(),
                                               Keys.KEY_CAMERA_ID);

        /*
         * To reduce startup time, we start the preview in another thread.
         * We make sure the preview is started at the end of onCreate.
         */
        requestCamera(mCameraId);

        mContentResolver = mActivity.getContentResolver();

        // Surface texture is from camera screen nail and startPreview needs it.
        // This must be done before startPreview.
        mIsVideoCaptureIntent = isVideoCaptureIntent();

        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mLocationManager = mActivity.getLocationManager();

        mUI.setOrientationIndicator(0, false);
        setDisplayOrientation();

        mPendingSwitchCameraId = -1;

        mShutterIconId = CameraUtil.getCameraShutterIconId(
                mAppController.getCurrentModuleIndex(), mAppController.getAndroidContext());
    }

    @Override
    public boolean isUsingBottomBar() {
        return true;
    }

    private void initializeControlByIntent() {
        if (isVideoCaptureIntent()) {
            if (!mDontResetIntentUiOnResume) {
                mActivity.getCameraAppUI().transitionToIntentCaptureLayout();
            }
            // reset the flag
            mDontResetIntentUiOnResume = false;
        }
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mPaused || mCameraDevice == null) {
            return;
        }
        if (mMediaRecorderRecording) {
            if (!mSnapshotInProgress) {
                takeASnapshot();
            }
            return;
        }
        // Check if metering area or focus area is supported.
        if (!mFocusAreaSupported && !mMeteringAreaSupported) {
            return;
        }
        // Tap to focus.
        mFocusManager.onSingleTapUp(x, y);
    }

    private void takeASnapshot() {
        // Only take snapshots if video snapshot is supported by device
        if(!mCameraCapabilities.supports(CameraCapabilities.Feature.VIDEO_SNAPSHOT)) {
            Log.w(TAG, "Cannot take a video snapshot - not supported by hardware");
            return;
        }
        if (!mIsVideoCaptureIntent) {
            if (!mMediaRecorderRecording || mPaused || mSnapshotInProgress
                    || !mAppController.isShutterEnabled() || mCameraDevice == null) {
                return;
            }

            Location loc = mLocationManager.getCurrentLocation();
            CameraUtil.setGpsParameters(mCameraSettings, loc);
            mCameraDevice.applySettings(mCameraSettings);

            Log.i(TAG, "Video snapshot start");
            mCameraDevice.takePicture(mHandler,
                    null, null, null, new JpegPictureCallback(loc));
            showVideoSnapshotUI(true);
            mSnapshotInProgress = true;
        }
    }

     private void updateAutoFocusMoveCallback() {
        if (mPaused || mCameraDevice == null) {
            return;
        }

        if (mCameraSettings.getCurrentFocusMode() == CameraCapabilities.FocusMode.CONTINUOUS_PICTURE) {
            mCameraDevice.setAutoFocusMoveCallback(mHandler,
                    (CameraAgent.CameraAFMoveCallback) mAutoFocusMoveCallback);
        } else {
            mCameraDevice.setAutoFocusMoveCallback(null, null);
        }
    }

    /**
     * @return Whether the currently active camera is front-facing.
     */
    private boolean isCameraFrontFacing() {
        return mAppController.getCameraProvider().getCharacteristics(mCameraId)
                .isFacingFront();
    }

    /**
     * @return Whether the currently active camera is back-facing.
     */
    private boolean isCameraBackFacing() {
        return mAppController.getCameraProvider().getCharacteristics(mCameraId)
                .isFacingBack();
    }

    /**
     * The focus manager gets initialized after camera is available.
     */
    private void initializeFocusManager() {
        // Create FocusManager object. startPreview needs it.
        // if mFocusManager not null, reuse it
        // otherwise create a new instance
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        } else {
            mMirror = isCameraFrontFacing();
            String[] defaultFocusModesStrings = mActivity.getResources().getStringArray(
                    R.array.pref_camera_focusmode_default_array);
            CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
            ArrayList<CameraCapabilities.FocusMode> defaultFocusModes =
                    new ArrayList<CameraCapabilities.FocusMode>();
            for (String modeString : defaultFocusModesStrings) {
                CameraCapabilities.FocusMode mode = stringifier.focusModeFromString(modeString);
                if (mode != null) {
                    defaultFocusModes.add(mode);
                }
            }
            mFocusManager = new FocusOverlayManager(mAppController,
                    defaultFocusModes, mCameraCapabilities, this, mMirror,
                    mActivity.getMainLooper(), mUI.getFocusRing());
        }
        mAppController.addPreviewAreaSizeChangedListener(mFocusManager);
    }

    @Override
    public void onOrientationChanged(OrientationManager orientationManager,
                                     OrientationManager.DeviceOrientation deviceOrientation) {
        mUI.onOrientationChanged(orientationManager, deviceOrientation);
    }

    private final ButtonManager.ButtonCallback mFlashCallback =
        new ButtonManager.ButtonCallback() {
            @Override
            public void onStateChanged(int state) {
                if (mPaused) {
                    return;
                }
                // Update flash parameters.
                enableTorchMode(true);
            }
        };

    private final ButtonManager.ButtonCallback mCameraCallback =
        new ButtonManager.ButtonCallback() {
            @Override
            public void onStateChanged(int state) {
                if (mPaused || mAppController.getCameraProvider().waitingForCamera()) {
                    return;
                }
                ButtonManager buttonManager = mActivity.getButtonManager();
                buttonManager.disableCameraButtonAndBlock();
                mPendingSwitchCameraId = state;
                Log.d(TAG, "Start to copy texture.");

                // Disable all camera controls.
                mSwitchingCamera = true;
                switchCamera();
            }
        };

    private final View.OnClickListener mCancelCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onReviewCancelClicked(v);
        }
    };

    private final View.OnClickListener mDoneCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onReviewDoneClicked(v);
        }
    };
    private final View.OnClickListener mReviewCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onReviewPlayClicked(v);
        }
    };

    @Override
    public void hardResetSettings(SettingsManager settingsManager) {
        // VideoModule does not need to hard reset any settings.
    }

    @Override
    public HardwareSpec getHardwareSpec() {
        if (mHardwareSpec == null) {
            mHardwareSpec = (mCameraSettings != null ?
                    new HardwareSpecImpl(getCameraProvider(), mCameraCapabilities,
                            mAppController.getCameraFeatureConfig(), isCameraFrontFacing()) : null);
        }
        return mHardwareSpec;
    }

    @Override
    public CameraAppUI.BottomBarUISpec getBottomBarSpec() {
        CameraAppUI.BottomBarUISpec bottomBarSpec = new CameraAppUI.BottomBarUISpec();

        bottomBarSpec.enableCamera = true;
        bottomBarSpec.cameraCallback = mCameraCallback;
        bottomBarSpec.enableTorchFlash = true;
        bottomBarSpec.flashCallback = mFlashCallback;
        bottomBarSpec.hideHdr = true;
        bottomBarSpec.enableGridLines = true;
        bottomBarSpec.enableExposureCompensation = false;
        bottomBarSpec.isExposureCompensationSupported = false;

        if (isVideoCaptureIntent()) {
            bottomBarSpec.showCancel = true;
            bottomBarSpec.cancelCallback = mCancelCallback;
            bottomBarSpec.showDone = true;
            bottomBarSpec.doneCallback = mDoneCallback;
            bottomBarSpec.showReview = true;
            bottomBarSpec.reviewCallback = mReviewCallback;
        }

        return bottomBarSpec;
    }

    @Override
    public void onCameraAvailable(CameraProxy cameraProxy) {
        if (cameraProxy == null) {
            Log.w(TAG, "onCameraAvailable returns a null CameraProxy object");
            return;
        }
        mCameraDevice = cameraProxy;
        mCameraCapabilities = mCameraDevice.getCapabilities();
        mAppController.getCameraAppUI().showAccessibilityZoomUI(
                mCameraCapabilities.getMaxZoomRatio());
        mCameraSettings = mCameraDevice.getSettings();
        mFocusAreaSupported = mCameraCapabilities.supports(CameraCapabilities.Feature.FOCUS_AREA);
        mMeteringAreaSupported =
                mCameraCapabilities.supports(CameraCapabilities.Feature.METERING_AREA);
        readVideoPreferences();
        updateDesiredPreviewSize();
        resizeForPreviewAspectRatio();
        initializeFocusManager();
        // TODO: Having focus overlay manager caching the parameters is prone to error,
        // we should consider passing the parameters to focus overlay to ensure the
        // parameters are up to date.
        mFocusManager.updateCapabilities(mCameraCapabilities);

        startPreview();
        initializeVideoSnapshot();
        mUI.initializeZoom(mCameraSettings, mCameraCapabilities);
        initializeControlByIntent();

        mHardwareSpec = new HardwareSpecImpl(getCameraProvider(), mCameraCapabilities,
                mAppController.getCameraFeatureConfig(), isCameraFrontFacing());

        ButtonManager buttonManager = mActivity.getButtonManager();
        buttonManager.enableCameraButton();
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(mCurrentVideoUri, convertOutputFormatToMimeType(mProfile.fileFormat));
        try {
            mActivity.launchActivityByIntent(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    @Override
    public void onReviewPlayClicked(View v) {
        startPlayVideoActivity();
    }

    @Override
    public void onReviewDoneClicked(View v) {
        mIsInReviewMode = false;
        doReturnToCaller(true);
    }

    @Override
    public void onReviewCancelClicked(View v) {
        // TODO: It should be better to not even insert the URI at all before we
        // confirm done in review, which means we need to handle temporary video
        // files in a quite different way than we currently had.
        // Make sure we don't delete the Uri sent from the video capture intent.
        if (mCurrentVideoUriFromMediaSaved) {
            mContentResolver.delete(mCurrentVideoUri, null, null);
        }
        mIsInReviewMode = false;
        doReturnToCaller(false);
    }

    @Override
    public boolean isInReviewMode() {
        return mIsInReviewMode;
    }

    private void onStopVideoRecording() {
        mAppController.getCameraAppUI().setSwipeEnabled(true);
        boolean recordFail = stopVideoRecording();
        if (mIsVideoCaptureIntent) {
            if (mQuickCapture) {
                doReturnToCaller(!recordFail);
            } else if (!recordFail) {
                showCaptureResult();
            }
        } else if (!recordFail){
            // Start capture animation.
            if (!mPaused && ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
                // The capture animation is disabled on ICS because we use SurfaceView
                // for preview during recording. When the recording is done, we switch
                // back to use SurfaceTexture for preview and we need to stop then start
                // the preview. This will cause the preview flicker since the preview
                // will not be continuous for a short period of time.
                mAppController.startFlashAnimation(false);
            }
        }
    }

    public void onVideoSaved() {
        if (mIsVideoCaptureIntent) {
            showCaptureResult();
        }
    }

    public void onProtectiveCurtainClick(View v) {
        // Consume clicks
    }

    @Override
    public void onShutterButtonClick() {
        if (mSwitchingCamera) {
            return;
        }
        boolean stop = mMediaRecorderRecording;

        if (stop) {
            // CameraAppUI mishandles mode option enable/disable
            // for video, override that
            mAppController.getCameraAppUI().enableModeOptions();
            onStopVideoRecording();
        } else {
            // CameraAppUI mishandles mode option enable/disable
            // for video, override that
            mAppController.getCameraAppUI().disableModeOptions();
            startVideoRecording();
        }
        mAppController.setShutterEnabled(false);
        if (mCameraSettings != null) {
            mFocusManager.onShutterUp(mCameraSettings.getCurrentFocusMode());
        }

        // Keep the shutter button disabled when in video capture intent
        // mode and recording is stopped. It'll be re-enabled when
        // re-take button is clicked.
        if (!(mIsVideoCaptureIntent && stop)) {
            mHandler.sendEmptyMessageDelayed(MSG_ENABLE_SHUTTER_BUTTON, SHUTTER_BUTTON_TIMEOUT);
        }
    }

    @Override
    public void onShutterCoordinate(TouchCoordinate coord) {
        // Do nothing.
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // TODO: Remove this when old camera controls are removed from the UI.
    }

    private void readVideoPreferences() {
        // The preference stores values from ListPreference and is thus string type for all values.
        // We need to convert it to int manually.
        SettingsManager settingsManager = mActivity.getSettingsManager();
        String videoQualityKey = isCameraFrontFacing() ? Keys.KEY_VIDEO_QUALITY_FRONT
            : Keys.KEY_VIDEO_QUALITY_BACK;
        String videoQuality = settingsManager
                .getString(SettingsManager.SCOPE_GLOBAL, videoQualityKey);
        int quality = SettingsUtil.getVideoQuality(videoQuality, mCameraId);
        Log.d(TAG, "Selected video quality for '" + videoQuality + "' is " + quality);

        // Set video quality.
        Intent intent = mActivity.getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality =
                    intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            if (extraVideoQuality > 0) {
                quality = CamcorderProfile.QUALITY_HIGH;
            } else {  // 0 is mms.
                quality = CamcorderProfile.QUALITY_LOW;
            }
        }

        // Set video duration limit. The limit is read from the preference,
        // unless it is specified in the intent.
        if (intent.hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
            int seconds =
                    intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0);
            mMaxVideoDurationInMs = 1000 * seconds;
        } else {
            mMaxVideoDurationInMs = SettingsUtil.getMaxVideoDuration(mActivity
                    .getAndroidContext());
        }

        // If quality is not supported, request QUALITY_HIGH which is always supported.
        if (CamcorderProfile.hasProfile(mCameraId, quality) == false) {
            quality = CamcorderProfile.QUALITY_HIGH;
        }
        mProfile = CamcorderProfile.get(mCameraId, quality);
        mPreferenceRead = true;
    }

    /**
     * Calculates and sets local class variables for Desired Preview sizes.
     * This function should be called after every change in preview camera
     * resolution and/or before the preview starts. Note that these values still
     * need to be pushed to the CameraSettings to actually change the preview
     * resolution.  Does nothing when camera pointer is null.
     */
    private void updateDesiredPreviewSize() {
        if (mCameraDevice == null) {
            return;
        }

        mCameraSettings = mCameraDevice.getSettings();
        Point desiredPreviewSize = getDesiredPreviewSize(
              mCameraCapabilities, mProfile, mUI.getPreviewScreenSize());
        mDesiredPreviewWidth = desiredPreviewSize.x;
        mDesiredPreviewHeight = desiredPreviewSize.y;
        mUI.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
        Log.v(TAG, "Updated DesiredPreview=" + mDesiredPreviewWidth + "x"
                + mDesiredPreviewHeight);
    }

    /**
     * Calculates the preview size and stores it in mDesiredPreviewWidth and
     * mDesiredPreviewHeight.
     *
     * <p>This function checks {@link
     * com.android.camera.cameradevice.CameraCapabilities#getPreferredPreviewSizeForVideo()}
     * but also considers the current preview area size on screen and make sure
     * the final preview size will not be smaller than 1/2 of the current
     * on screen preview area in terms of their short sides.  This function has
     * highest priority of WYSIWYG, 1:1 matching as its best match, even if
     * there's a larger preview that meets the condition above. </p>
     *
     * @return The preferred preview size or {@code null} if the camera is not
     *         opened yet.
     */
    private static Point getDesiredPreviewSize(CameraCapabilities capabilities,
          CamcorderProfile profile, Point previewScreenSize) {
        if (capabilities.getSupportedVideoSizes() == null) {
            // Driver doesn't support separate outputs for preview and video.
            return new Point(profile.videoFrameWidth, profile.videoFrameHeight);
        }

        final int previewScreenShortSide = (previewScreenSize.x < previewScreenSize.y ?
                previewScreenSize.x : previewScreenSize.y);
        List<Size> sizes = Size.convert(capabilities.getSupportedPreviewSizes());
        Size preferred = new Size(capabilities.getPreferredPreviewSizeForVideo());
        final int preferredPreviewSizeShortSide = (preferred.width() < preferred.height() ?
                preferred.width() : preferred.height());
        if (preferredPreviewSizeShortSide * 2 < previewScreenShortSide) {
            preferred = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
        }
        int product = preferred.width() * preferred.height();
        Iterator<Size> it = sizes.iterator();
        // Remove the preview sizes that are not preferred.
        while (it.hasNext()) {
            Size size = it.next();
            if (size.width() * size.height() > product) {
                it.remove();
            }
        }

        // Take highest priority for WYSIWYG when the preview exactly matches
        // video frame size.  The variable sizes is assumed to be filtered
        // for sizes beyond the UI size.
        for (Size size : sizes) {
            if (size.width() == profile.videoFrameWidth
                    && size.height() == profile.videoFrameHeight) {
                Log.v(TAG, "Selected =" + size.width() + "x" + size.height()
                           + " on WYSIWYG Priority");
                return new Point(profile.videoFrameWidth, profile.videoFrameHeight);
            }
        }

        Size optimalSize = CameraUtil.getOptimalPreviewSize(sizes,
                (double) profile.videoFrameWidth / profile.videoFrameHeight);
        return new Point(optimalSize.width(), optimalSize.height());
    }

    private void resizeForPreviewAspectRatio() {
        mUI.setAspectRatio((float) mProfile.videoFrameWidth / mProfile.videoFrameHeight);
    }

    private void installIntentFilter() {
        // install an intent filter to receive SD card related events.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addDataScheme("file");
        mReceiver = new MyBroadcastReceiver();
        mActivity.registerReceiver(mReceiver, intentFilter);
    }

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation();
        Characteristics info =
                mActivity.getCameraProvider().getCharacteristics(mCameraId);
        mCameraDisplayOrientation = info.getPreviewOrientation(mDisplayRotation);
        // Change the camera display orientation
        if (mCameraDevice != null) {
            mCameraDevice.setDisplayOrientation(mDisplayRotation);
        }
        if (mFocusManager != null) {
            mFocusManager.setDisplayOrientation(mCameraDisplayOrientation);
        }
    }

    @Override
    public void updateCameraOrientation() {
        if (mMediaRecorderRecording) {
            return;
        }
        if (mDisplayRotation != CameraUtil.getDisplayRotation()) {
            setDisplayOrientation();
        }
    }

    @Override
    public void updatePreviewAspectRatio(float aspectRatio) {
        mAppController.updatePreviewAspectRatio(aspectRatio);
    }

    /**
     * Returns current Zoom value, with 1.0 as the value for no zoom.
     */
    private float currentZoomValue() {
        return mCameraSettings.getCurrentZoomRatio();
    }

    @Override
    public void onZoomChanged(float ratio) {
        // Not useful to change zoom value when the activity is paused.
        if (mPaused) {
            return;
        }
        mZoomValue = ratio;
        if (mCameraSettings == null || mCameraDevice == null) {
            return;
        }
        // Set zoom parameters asynchronously
        mCameraSettings.setZoomRatio(mZoomValue);
        mCameraDevice.applySettings(mCameraSettings);
    }

    private void startPreview() {
        Log.i(TAG, "startPreview");

        SurfaceTexture surfaceTexture = mActivity.getCameraAppUI().getSurfaceTexture();
        if (!mPreferenceRead || surfaceTexture == null || mPaused == true ||
                mCameraDevice == null) {
            return;
        }

        if (mPreviewing == true) {
            stopPreview();
        }

        setDisplayOrientation();
        mCameraDevice.setDisplayOrientation(mDisplayRotation);
        setCameraParameters();

        if (mFocusManager != null) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus
            // to resume it because it may have been paused by autoFocus call.
            CameraCapabilities.FocusMode focusMode =
                    mFocusManager.getFocusMode(mCameraSettings.getCurrentFocusMode());
            if (focusMode == CameraCapabilities.FocusMode.CONTINUOUS_PICTURE) {
                mCameraDevice.cancelAutoFocus();
            }
        }

        // This is to notify app controller that preview will start next, so app
        // controller can set preview callbacks if needed. This has to happen before
        // preview is started as a workaround of the framework issue related to preview
        // callbacks that causes preview stretch and crash. (More details see b/12210027
        // and b/12591410. Don't apply this to L, see b/16649297.
        if (!ApiHelper.isLOrHigher()) {
            Log.v(TAG, "calling onPreviewReadyToStart to set one shot callback");
            mAppController.onPreviewReadyToStart();
        } else {
            Log.v(TAG, "on L, no one shot callback necessary");
        }
        try {
            mCameraDevice.setPreviewTexture(surfaceTexture);
            mCameraDevice.startPreviewWithCallback(new Handler(Looper.getMainLooper()),
                    new CameraAgent.CameraStartPreviewCallback() {
                @Override
                public void onPreviewStarted() {
                    VideoModule.this.onPreviewStarted();
                }
            });
            mPreviewing = true;
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
    }

    private void onPreviewStarted() {
        mAppController.setShutterEnabled(true);
        mAppController.onPreviewStarted();
        if (mFocusManager != null) {
            mFocusManager.onPreviewStarted();
        }
    }

    @Override
    public void onPreviewInitialDataReceived() {
    }

    @Override
    public void stopPreview() {
        if (!mPreviewing) {
            Log.v(TAG, "Skip stopPreview since it's not mPreviewing");
            return;
        }
        if (mCameraDevice == null) {
            Log.v(TAG, "Skip stopPreview since mCameraDevice is null");
            return;
        }

        Log.v(TAG, "stopPreview");
        mCameraDevice.stopPreview();
        if (mFocusManager != null) {
            mFocusManager.onPreviewStopped();
        }
        mPreviewing = false;
    }

    private void closeCamera() {
        Log.i(TAG, "closeCamera");
        if (mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }
        mCameraDevice.setZoomChangeListener(null);
        mActivity.getCameraProvider().releaseCamera(mCameraDevice.getCameraId());
        mCameraDevice = null;
        mPreviewing = false;
        mSnapshotInProgress = false;
        if (mFocusManager != null) {
            mFocusManager.onCameraReleased();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mPaused) {
            return true;
        }
        if (mMediaRecorderRecording) {
            onStopVideoRecording();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Do not handle any key if the activity is paused.
        if (mPaused) {
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                if (event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                    return true;
                }
            case KeyEvent.KEYCODE_MENU:
                // Consume menu button presses during capture.
                return mMediaRecorderRecording;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
                onShutterButtonClick();
                return true;
            case KeyEvent.KEYCODE_MENU:
                // Consume menu button presses during capture.
                return mMediaRecorderRecording;
        }
        return false;
    }

    @Override
    public boolean isVideoCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    private void doReturnToCaller(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = Activity.RESULT_OK;
            resultIntent.setData(mCurrentVideoUri);
            resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            resultCode = Activity.RESULT_CANCELED;
        }
        mActivity.setResultEx(resultCode, resultIntent);
        mActivity.finish();
    }

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    // Prepares media recorder.
    private void initializeRecorder() {
        Log.i(TAG, "initializeRecorder: " + Thread.currentThread());
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) {
            Log.w(TAG, "null camera proxy, not recording");
            return;
        }
        Intent intent = mActivity.getIntent();
        Bundle myExtras = intent.getExtras();

        long requestedSizeLimit = 0;
        closeVideoFileDescriptor();
        mCurrentVideoUriFromMediaSaved = false;
        if (mIsVideoCaptureIntent && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mVideoFileDescriptor =
                            mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
            }
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }
        mMediaRecorder = new MediaRecorder();
        // Unlock the camera object before passing it to media recorder.
        mCameraDevice.unlock();
        // We rely here on the fact that the unlock call above is synchronous
        // and blocks until it occurs in the handler thread. Thereby ensuring
        // that we are up to date with handler requests, and if this proxy had
        // ever been released by a prior command, it would be null.
        Camera camera = mCameraDevice.getCamera();
        // If the camera device is null, the camera proxy is stale and recording
        // should be ignored.
        if (camera == null) {
            Log.w(TAG, "null camera within proxy, not recording");
            return;
        }

        mMediaRecorder.setCamera(camera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(mProfile);
        mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);

        setRecordLocation();

        // Set output file.
        // Try Uri in the intent first. If it doesn't exist, use our own
        // instead.
        if (mVideoFileDescriptor != null) {
            mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
        } else {
            generateVideoFilename(mProfile.fileFormat);
            mMediaRecorder.setOutputFile(mVideoFilename);
        }

        // Set maximum file size.
        long maxFileSize = mActivity.getStorageSpaceBytes() - Storage.LOW_STORAGE_THRESHOLD_BYTES;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }

        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) {
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            // on the size restriction.
        }

        int sensorOrientation =
                mActivity.getCameraProvider().getCharacteristics(mCameraId).getSensorOrientation();
        int deviceOrientation =
                mAppController.getOrientationManager().getDeviceOrientation().getDegrees();
        int rotation = CameraUtil.getImageRotation(
                sensorOrientation, deviceOrientation, isCameraFrontFacing());
        mMediaRecorder.setOrientationHint(rotation);

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed for " + mVideoFilename, e);
            releaseMediaRecorder();
            throw new RuntimeException(e);
        }

        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);
    }

    private static void setCaptureRate(MediaRecorder recorder, double fps) {
        recorder.setCaptureRate(fps);
    }

    private void setRecordLocation() {
        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mMediaRecorder.setLocation((float) loc.getLatitude(),
                    (float) loc.getLongitude());
        }
    }

    private void releaseMediaRecorder() {
        Log.i(TAG, "Releasing media recorder.");
        if (mMediaRecorder != null) {
            cleanupEmptyFile();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        mVideoFilename = null;
    }

    private void generateVideoFilename(int outputFileFormat) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        // Used when emailing.
        String filename = title + convertOutputFormatToFileExt(outputFileFormat);
        String mime = convertOutputFormatToMimeType(outputFileFormat);
        String path = Storage.DIRECTORY + '/' + filename;
        String tmpPath = path + ".tmp";
        mCurrentVideoValues = new ContentValues(9);
        mCurrentVideoValues.put(Video.Media.TITLE, title);
        mCurrentVideoValues.put(Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(MediaColumns.DATE_MODIFIED, dateTaken / 1000);
        mCurrentVideoValues.put(Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues.put(Video.Media.DATA, path);
        mCurrentVideoValues.put(Video.Media.WIDTH, mProfile.videoFrameWidth);
        mCurrentVideoValues.put(Video.Media.HEIGHT, mProfile.videoFrameHeight);
        mCurrentVideoValues.put(Video.Media.RESOLUTION,
                Integer.toString(mProfile.videoFrameWidth) + "x" +
                Integer.toString(mProfile.videoFrameHeight));
        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mCurrentVideoValues.put(Video.Media.LATITUDE, loc.getLatitude());
            mCurrentVideoValues.put(Video.Media.LONGITUDE, loc.getLongitude());
        }
        mVideoFilename = tmpPath;
        Log.v(TAG, "New video filename: " + mVideoFilename);
    }

    private void logVideoCapture(long duration) {
        String flashSetting = mActivity.getSettingsManager()
                .getString(mAppController.getCameraScope(),
                           Keys.KEY_VIDEOCAMERA_FLASH_MODE);
        boolean gridLinesOn = Keys.areGridLinesOn(mActivity.getSettingsManager());
        int width = (Integer) mCurrentVideoValues.get(Video.Media.WIDTH);
        int height = (Integer) mCurrentVideoValues.get(Video.Media.HEIGHT);
        long size = new File(mCurrentVideoFilename).length();
        String name = new File(mCurrentVideoValues.getAsString(Video.Media.DATA)).getName();
        UsageStatistics.instance().videoCaptureDoneEvent(name, duration, isCameraFrontFacing(),
                currentZoomValue(), width, height, size, flashSetting, gridLinesOn);
    }

    private void saveVideo() {
        if (mVideoFileDescriptor == null) {
            long duration = SystemClock.uptimeMillis() - mRecordingStartTime;
            if (duration > 0) {
                //
            } else {
                Log.w(TAG, "Video duration <= 0 : " + duration);
            }
            mCurrentVideoValues.put(Video.Media.SIZE, new File(mCurrentVideoFilename).length());
            mCurrentVideoValues.put(Video.Media.DURATION, duration);
            getServices().getMediaSaver().addVideo(mCurrentVideoFilename,
                    mCurrentVideoValues, mOnVideoSavedListener);
            logVideoCapture(duration);
        }
        mCurrentVideoValues = null;
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    // from MediaRecorder.OnErrorListener
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            stopVideoRecording();
            mActivity.updateStorageSpaceAndHint(null);
        }
    }

    // from MediaRecorder.OnInfoListener
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mMediaRecorderRecording) {
                onStopVideoRecording();
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (mMediaRecorderRecording) {
                onStopVideoRecording();
            }

            // Show the toast.
            Toast.makeText(mActivity, R.string.video_reach_size_limit,
                    Toast.LENGTH_LONG).show();
        }
    }

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void silenceSoundsAndVibrations() {
        // Get the audio focus which causes other music players to stop.
        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        // Store current ringer mode so we can set it once video recording is
        // finished.
        mOriginalRingerMode = mAudioManager.getRingerMode();
        // Make sure no system sounds and vibrations happen during video
        // recording.
        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
    }

    private void restoreRingerMode() {
        // First check if ringer mode was changed during the recording. If not,
        // re-set the mode that was set before video recording started.
        if (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
            mAudioManager.setRingerMode(mOriginalRingerMode);
        }
    }

    // For testing.
    public boolean isRecording() {
        return mMediaRecorderRecording;
    }

    private void startVideoRecording() {
        Log.i(TAG, "startVideoRecording: " + Thread.currentThread());
        mUI.cancelAnimations();
        mUI.setSwipingEnabled(false);
        mUI.hidePassiveFocusIndicator();
        mAppController.getCameraAppUI().hideCaptureIndicator();
        mAppController.getCameraAppUI().setShouldSuppressCaptureIndicator(true);

        mActivity.updateStorageSpaceAndHint(new CameraActivity.OnStorageUpdateDoneListener() {
            @Override
            public void onStorageUpdateDone(long bytes) {
                if (bytes <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
                    Log.w(TAG, "Storage issue, ignore the start request");
                } else {
                    if (mCameraDevice == null) {
                        Log.v(TAG, "in storage callback after camera closed");
                        return;
                    }
                    if (mPaused == true) {
                        Log.v(TAG, "in storage callback after module paused");
                        return;
                    }

                    // Monkey is so fast so it could trigger startVideoRecording twice. To prevent
                    // app crash (b/17313985), do nothing here for the second storage-checking
                    // callback because recording is already started.
                    if (mMediaRecorderRecording) {
                        Log.v(TAG, "in storage callback after recording started");
                        return;
                    }

                    mCurrentVideoUri = null;

                    initializeRecorder();
                    if (mMediaRecorder == null) {
                        Log.e(TAG, "Fail to initialize media recorder");
                        return;
                    }

                    try {
                        mMediaRecorder.start(); // Recording is now started
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Could not start media recorder. ", e);
                        mAppController.getFatalErrorHandler().onGenericCameraAccessFailure();
                        releaseMediaRecorder();
                        // If start fails, frameworks will not lock the camera for us.
                        mCameraDevice.lock();
                        return;
                    }
                    // Make sure we stop playing sounds and disable the
                    // vibrations during video recording. Post delayed to avoid
                    // silencing the recording start sound.
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            silenceSoundsAndVibrations();
                        }
                    }, 250);

                    mAppController.getCameraAppUI().setSwipeEnabled(false);

                    // The parameters might have been altered by MediaRecorder already.
                    // We need to force mCameraDevice to refresh before getting it.
                    mCameraDevice.refreshSettings();
                    // The parameters may have been changed by MediaRecorder upon starting
                    // recording. We need to alter the parameters if we support camcorder
                    // zoom. To reduce latency when setting the parameters during zoom, we
                    // update the settings here once.
                    mCameraSettings = mCameraDevice.getSettings();

                    mMediaRecorderRecording = true;
                    mActivity.lockOrientation();
                    mRecordingStartTime = SystemClock.uptimeMillis();

                    // A special case of mode options closing: during capture it should
                    // not be possible to change mode state.
                    mAppController.getCameraAppUI().hideModeOptions();
                    mAppController.getCameraAppUI().animateBottomBarToVideoStop(R.drawable.ic_stop);
                    mUI.showRecordingUI(true);

                    setFocusParameters();

                    updateRecordingTime();
                    mActivity.enableKeepScreenOn(true);
                }
            }
        });
    }

    private Bitmap getVideoThumbnail() {
        Bitmap bitmap = null;
        if (mVideoFileDescriptor != null) {
            bitmap = Thumbnail.createVideoThumbnailBitmap(mVideoFileDescriptor.getFileDescriptor(),
                    mDesiredPreviewWidth);
        } else if (mCurrentVideoUri != null) {
            try {
                mVideoFileDescriptor = mContentResolver.openFileDescriptor(mCurrentVideoUri, "r");
                bitmap = Thumbnail.createVideoThumbnailBitmap(
                        mVideoFileDescriptor.getFileDescriptor(), mDesiredPreviewWidth);
            } catch (java.io.FileNotFoundException ex) {
                // invalid uri
                Log.e(TAG, ex.toString());
            }
        }

        if (bitmap != null) {
            // MetadataRetriever already rotates the thumbnail. We should rotate
            // it to match the UI orientation (and mirror if it is front-facing camera).
            bitmap = CameraUtil.rotateAndMirror(bitmap, 0, isCameraFrontFacing());
        }
        return bitmap;
    }

    private void showCaptureResult() {
        mIsInReviewMode = true;
        Bitmap bitmap = getVideoThumbnail();
        if (bitmap != null) {
            mUI.showReviewImage(bitmap);
        }
        mUI.showReviewControls();
    }

    private boolean stopVideoRecording() {
        // Do nothing if camera device is still capturing photo. Monkey test can trigger app crashes
        // (b/17313985) without this check. Crash could also be reproduced by continuously tapping
        // on shutter button and preview with two fingers.
        if (mSnapshotInProgress) {
            Log.v(TAG, "Skip stopVideoRecording since snapshot in progress");
            return true;
        }
        Log.v(TAG, "stopVideoRecording");

        // Re-enable sound as early as possible to avoid interfering with stop
        // recording sound.
        restoreRingerMode();

        mUI.setSwipingEnabled(true);
        mUI.showPassiveFocusIndicator();
        mAppController.getCameraAppUI().setShouldSuppressCaptureIndicator(false);

        boolean fail = false;
        if (mMediaRecorderRecording) {
            boolean shouldAddToMediaStoreNow = false;

            try {
                mMediaRecorder.setOnErrorListener(null);
                mMediaRecorder.setOnInfoListener(null);
                mMediaRecorder.stop();
                shouldAddToMediaStoreNow = true;
                mCurrentVideoFilename = mVideoFilename;
                Log.v(TAG, "stopVideoRecording: current video filename: " + mCurrentVideoFilename);
            } catch (RuntimeException e) {
                Log.e(TAG, "stop fail",  e);
                if (mVideoFilename != null) {
                    deleteVideoFile(mVideoFilename);
                }
                fail = true;
            }
            mMediaRecorderRecording = false;
            mActivity.unlockOrientation();

            // If the activity is paused, this means activity is interrupted
            // during recording. Release the camera as soon as possible because
            // face unlock or other applications may need to use the camera.
            if (mPaused) {
                // b/16300704: Monkey is fast so it could pause the module while recording.
                // stopPreview should definitely be called before switching off.
                stopPreview();
                closeCamera();
            }

            mUI.showRecordingUI(false);
            // The orientation was fixed during video recording. Now make it
            // reflect the device orientation as video recording is stopped.
            mUI.setOrientationIndicator(0, true);
            mActivity.enableKeepScreenOn(false);
            if (shouldAddToMediaStoreNow && !fail) {
                if (mVideoFileDescriptor == null) {
                    saveVideo();
                } else if (mIsVideoCaptureIntent) {
                    // if no file save is needed, we can show the post capture UI now
                    showCaptureResult();
                }
            }
        }
        // release media recorder
        releaseMediaRecorder();

        mAppController.getCameraAppUI().showModeOptions();
        mAppController.getCameraAppUI().animateBottomBarToFullSize(mShutterIconId);
        if (!mPaused && mCameraDevice != null) {
            setFocusParameters();
            mCameraDevice.lock();
            if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
                stopPreview();
                // Switch back to use SurfaceTexture for preview.
                startPreview();
            }
            // Update the parameters here because the parameters might have been altered
            // by MediaRecorder.
            mCameraSettings = mCameraDevice.getSettings();
        }

        // Check this in advance of each shot so we don't add to shutter
        // latency. It's true that someone else could write to the SD card
        // in the mean time and fill it, but that could have happened
        // between the shutter press and saving the file too.
        mActivity.updateStorageSpaceAndHint(null);

        return fail;
    }

    private static String millisecondToTimeString(long milliSeconds, boolean displayCentiSeconds) {
        long seconds = milliSeconds / 1000; // round down to compute seconds
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainderMinutes = minutes - (hours * 60);
        long remainderSeconds = seconds - (minutes * 60);

        StringBuilder timeStringBuilder = new StringBuilder();

        // Hours
        if (hours > 0) {
            if (hours < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(hours);

            timeStringBuilder.append(':');
        }

        // Minutes
        if (remainderMinutes < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderMinutes);
        timeStringBuilder.append(':');

        // Seconds
        if (remainderSeconds < 10) {
            timeStringBuilder.append('0');
        }
        timeStringBuilder.append(remainderSeconds);

        // Centi seconds
        if (displayCentiSeconds) {
            timeStringBuilder.append('.');
            long remainderCentiSeconds = (milliSeconds - seconds * 1000) / 10;
            if (remainderCentiSeconds < 10) {
                timeStringBuilder.append('0');
            }
            timeStringBuilder.append(remainderCentiSeconds);
        }

        return timeStringBuilder.toString();
    }

    private void updateRecordingTime() {
        if (!mMediaRecorderRecording) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0
                && delta >= mMaxVideoDurationInMs - 60000);

        long deltaAdjusted = delta;
        if (countdownRemainingTime) {
            deltaAdjusted = Math.max(0, mMaxVideoDurationInMs - deltaAdjusted) + 999;
        }
        String text;

        long targetNextUpdateDelay;

        text = millisecondToTimeString(deltaAdjusted, false);
        targetNextUpdateDelay = 1000;

        mUI.setRecordingTime(text);

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = mActivity.getResources().getColor(R.color.recording_time_remaining_text);

            mUI.setRecordingTimeTextColor(color);
        }

        long actualNextUpdateDelay = targetNextUpdateDelay - (delta % targetNextUpdateDelay);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_RECORD_TIME, actualNextUpdateDelay);
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    @SuppressWarnings("deprecation")
    private void setCameraParameters() {
        SettingsManager settingsManager = mActivity.getSettingsManager();

        // Update Desired Preview size in case video camera resolution has changed.
        updateDesiredPreviewSize();

        Size previewSize = new Size(mDesiredPreviewWidth, mDesiredPreviewHeight);
        mCameraSettings.setPreviewSize(previewSize.toPortabilitySize());
        // This is required for Samsung SGH-I337 and probably other Samsung S4 versions
        if (Build.BRAND.toLowerCase().contains("samsung")) {
            mCameraSettings.setSetting("video-size",
                    mProfile.videoFrameWidth + "x" + mProfile.videoFrameHeight);
        }
        int[] fpsRange =
                CameraUtil.getMaxPreviewFpsRange(mCameraCapabilities.getSupportedPreviewFpsRange());
        if (fpsRange.length > 0) {
            mCameraSettings.setPreviewFpsRange(fpsRange[0], fpsRange[1]);
        } else {
            mCameraSettings.setPreviewFrameRate(mProfile.videoFrameRate);
        }

        enableTorchMode(Keys.isCameraBackFacing(settingsManager, mAppController.getModuleScope()));

        // Set zoom.
        if (mCameraCapabilities.supports(CameraCapabilities.Feature.ZOOM)) {
            mCameraSettings.setZoomRatio(mZoomValue);
        }
        updateFocusParameters();

        mCameraSettings.setRecordingHintEnabled(true);

        if (mCameraCapabilities.supports(CameraCapabilities.Feature.VIDEO_STABILIZATION)) {
            mCameraSettings.setVideoStabilization(true);
        }

        // Set picture size.
        // The logic here is different from the logic in still-mode camera.
        // There we determine the preview size based on the picture size, but
        // here we determine the picture size based on the preview size.
        List<Size> supported = Size.convert(mCameraCapabilities.getSupportedPhotoSizes());
        Size optimalSize = CameraUtil.getOptimalVideoSnapshotPictureSize(supported,
                mDesiredPreviewWidth, mDesiredPreviewHeight);
        Size original = new Size(mCameraSettings.getCurrentPhotoSize());
        if (!original.equals(optimalSize)) {
            mCameraSettings.setPhotoSize(optimalSize.toPortabilitySize());
        }
        Log.d(TAG, "Video snapshot size is " + optimalSize);

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mCameraSettings.setPhotoJpegCompressionQuality(jpegQuality);

        if (mCameraDevice != null) {
            mCameraDevice.applySettings(mCameraSettings);
            // Nexus 5 through KitKat 4.4.2 requires a second call to
            // .setParameters() for frame rate settings to take effect.
            mCameraDevice.applySettings(mCameraSettings);
        }

        // Update UI based on the new parameters.
        mUI.updateOnScreenIndicators(mCameraSettings);
    }

    private void updateFocusParameters() {
        // Set continuous autofocus. During recording, we use "continuous-video"
        // auto focus mode to ensure smooth focusing. Whereas during preview (i.e.
        // before recording starts) we use "continuous-picture" auto focus mode
        // for faster but slightly jittery focusing.
        Set<CameraCapabilities.FocusMode> supportedFocus = mCameraCapabilities
                .getSupportedFocusModes();
        if (mMediaRecorderRecording) {
            if (mCameraCapabilities.supports(CameraCapabilities.FocusMode.CONTINUOUS_VIDEO)) {
                mCameraSettings.setFocusMode(CameraCapabilities.FocusMode.CONTINUOUS_VIDEO);
                mFocusManager.overrideFocusMode(CameraCapabilities.FocusMode.CONTINUOUS_VIDEO);
            } else {
                mFocusManager.overrideFocusMode(null);
            }
        } else {
            // FIXME(b/16984793): This is broken. For some reasons, CONTINUOUS_PICTURE is not on
            // when preview starts.
            mFocusManager.overrideFocusMode(null);
            if (mCameraCapabilities.supports(CameraCapabilities.FocusMode.CONTINUOUS_PICTURE)) {
                mCameraSettings.setFocusMode(
                        mFocusManager.getFocusMode(mCameraSettings.getCurrentFocusMode()));
                if (mFocusAreaSupported) {
                    mCameraSettings.setFocusAreas(mFocusManager.getFocusAreas());
                }
            }
        }
        updateAutoFocusMoveCallback();
    }

    @Override
    public void resume() {
        if (isVideoCaptureIntent()) {
            mDontResetIntentUiOnResume = mPaused;
        }

        mPaused = false;
        installIntentFilter();
        mAppController.setShutterEnabled(false);
        mZoomValue = 1.0f;

        OrientationManager orientationManager = mAppController.getOrientationManager();
        orientationManager.addOnOrientationChangeListener(this);
        mUI.onOrientationChanged(orientationManager, orientationManager.getDeviceOrientation());

        showVideoSnapshotUI(false);

        if (!mPreviewing) {
            requestCamera(mCameraId);
        } else {
            // preview already started
            mAppController.setShutterEnabled(true);
        }

        if (mFocusManager != null) {
            // If camera is not open when resume is called, focus manager will not
            // be initialized yet, in which case it will start listening to
            // preview area size change later in the initialization.
            mAppController.addPreviewAreaSizeChangedListener(mFocusManager);
        }

        if (mPreviewing) {
            mOnResumeTime = SystemClock.uptimeMillis();
            mHandler.sendEmptyMessageDelayed(MSG_CHECK_DISPLAY_ROTATION, 100);
        }
        getServices().getMemoryManager().addListener(this);
    }

    @Override
    public void pause() {
        mPaused = true;

        mAppController.getOrientationManager().removeOnOrientationChangeListener(this);

        if (mFocusManager != null) {
            // If camera is not open when resume is called, focus manager will not
            // be initialized yet, in which case it will start listening to
            // preview area size change later in the initialization.
            mAppController.removePreviewAreaSizeChangedListener(mFocusManager);
            mFocusManager.removeMessages();
        }
        if (mMediaRecorderRecording) {
            // Camera will be released in onStopVideoRecording.
            onStopVideoRecording();
        } else {
            stopPreview();
            closeCamera();
            releaseMediaRecorder();
        }

        closeVideoFileDescriptor();

        if (mReceiver != null) {
            mActivity.unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        mHandler.removeMessages(MSG_CHECK_DISPLAY_ROTATION);
        mHandler.removeMessages(MSG_SWITCH_CAMERA);
        mHandler.removeMessages(MSG_SWITCH_CAMERA_START_ANIMATION);
        mPendingSwitchCameraId = -1;
        mSwitchingCamera = false;
        mPreferenceRead = false;
        getServices().getMemoryManager().removeListener(this);
        mUI.onPause();
    }

    @Override
    public void destroy() {

    }

    @Override
    public void onLayoutOrientationChanged(boolean isLandscape) {
        setDisplayOrientation();
    }

    // TODO: integrate this into the SettingsManager listeners.
    public void onSharedPreferenceChanged() {

    }

    private void switchCamera() {
        if (mPaused)  {
            return;
        }
        SettingsManager settingsManager = mActivity.getSettingsManager();

        Log.d(TAG, "Start to switch camera.");
        mCameraId = mPendingSwitchCameraId;
        mPendingSwitchCameraId = -1;
        settingsManager.set(mAppController.getModuleScope(),
                            Keys.KEY_CAMERA_ID, mCameraId);

        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        }
        closeCamera();
        requestCamera(mCameraId);

        mMirror = isCameraFrontFacing();
        if (mFocusManager != null) {
            mFocusManager.setMirror(mMirror);
        }

        // From onResume
        mZoomValue = 1.0f;
        mUI.setOrientationIndicator(0, false);

        // Start switch camera animation. Post a message because
        // onFrameAvailable from the old camera may already exist.
        mHandler.sendEmptyMessage(MSG_SWITCH_CAMERA_START_ANIMATION);
        mUI.updateOnScreenIndicators(mCameraSettings);
    }

    private void initializeVideoSnapshot() {
        if (mCameraSettings == null) {
            return;
        }
    }

    void showVideoSnapshotUI(boolean enabled) {
        if (mCameraSettings == null) {
            return;
        }
        if (mCameraCapabilities.supports(CameraCapabilities.Feature.VIDEO_SNAPSHOT) &&
                !mIsVideoCaptureIntent) {
            if (enabled) {
                mAppController.startFlashAnimation(false);
            } else {
                mUI.showPreviewBorder(enabled);
            }
            mAppController.setShutterEnabled(!enabled);
        }
    }

    /**
     * Used to update the flash mode. Video mode can turn on the flash as torch
     * mode, which we would like to turn on and off when we switching in and
     * out to the preview.
     *
     * @param enable Whether torch mode can be enabled.
     */
    private void enableTorchMode(boolean enable) {
        if (mCameraSettings.getCurrentFlashMode() == null) {
            return;
        }

        SettingsManager settingsManager = mActivity.getSettingsManager();

        CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
        CameraCapabilities.FlashMode flashMode;
        if (enable) {
            flashMode = stringifier
                .flashModeFromString(settingsManager.getString(mAppController.getCameraScope(),
                                                               Keys.KEY_VIDEOCAMERA_FLASH_MODE));
        } else {
            flashMode = CameraCapabilities.FlashMode.OFF;
        }
        if (mCameraCapabilities.supports(flashMode)) {
            mCameraSettings.setFlashMode(flashMode);
        }
        /* TODO: Find out how to deal with the following code piece:
        else {
            flashMode = mCameraSettings.getCurrentFlashMode();
            if (flashMode == null) {
                flashMode = mActivity.getString(
                        R.string.pref_camera_flashmode_no_flash);
                mParameters.setFlashMode(flashMode);
            }
        }*/
        if (mCameraDevice != null) {
            mCameraDevice.applySettings(mCameraSettings);
        }
        mUI.updateOnScreenIndicators(mCameraSettings);
    }

    @Override
    public void onPreviewVisibilityChanged(int visibility) {
        if (mPreviewing) {
            enableTorchMode(visibility == ModuleController.VISIBILITY_VISIBLE);
        }
    }

    private final class JpegPictureCallback implements CameraPictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
        }

        @Override
        public void onPictureTaken(byte [] jpegData, CameraProxy camera) {
            Log.i(TAG, "Video snapshot taken.");
            mSnapshotInProgress = false;
            showVideoSnapshotUI(false);
            storeImage(jpegData, mLocation);
        }
    }

    private void storeImage(final byte[] data, Location loc) {
        long dateTaken = System.currentTimeMillis();
        String title = CameraUtil.instance().createJpegName(dateTaken);
        ExifInterface exif = Exif.getExif(data);
        int orientation = Exif.getOrientation(exif);

        String flashSetting = mActivity.getSettingsManager()
            .getString(mAppController.getCameraScope(), Keys.KEY_VIDEOCAMERA_FLASH_MODE);
        Boolean gridLinesOn = Keys.areGridLinesOn(mActivity.getSettingsManager());
        UsageStatistics.instance().photoCaptureDoneEvent(
                eventprotos.NavigationChange.Mode.VIDEO_STILL, title + ".jpeg", exif,
                isCameraFrontFacing(), false, currentZoomValue(), flashSetting, gridLinesOn,
                null, null, null, null, null, null, null);

        getServices().getMediaSaver().addImage(data, title, dateTaken, loc, orientation, exif,
                mOnPhotoSavedListener);
    }

    private String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/3gpp";
    }

    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return ".mp4";
        }
        return ".3gp";
    }

    private void closeVideoFileDescriptor() {
        if (mVideoFileDescriptor != null) {
            try {
                mVideoFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close fd", e);
            }
            mVideoFileDescriptor = null;
        }
    }

    @Override
    public void onPreviewUIReady() {
        startPreview();
    }

    @Override
    public void onPreviewUIDestroyed() {
        stopPreview();
    }

    private void requestCamera(int id) {
        mActivity.getCameraProvider().requestCamera(id);
    }

    @Override
    public void onMemoryStateChanged(int state) {
        mAppController.setShutterEnabled(state == MemoryManager.STATE_OK);
    }

    @Override
    public void onLowMemory() {
        // Not much we can do in the video module.
    }

    /***********************FocusOverlayManager Listener****************************/
    @Override
    public void autoFocus() {
        if (mCameraDevice != null) {
            mCameraDevice.autoFocus(mHandler, mAutoFocusCallback);
        }
    }

    @Override
    public void cancelAutoFocus() {
        if (mCameraDevice != null) {
            mCameraDevice.cancelAutoFocus();
            setFocusParameters();
        }
    }

    @Override
    public boolean capture() {
        return false;
    }

    @Override
    public void startFaceDetection() {

    }

    @Override
    public void stopFaceDetection() {

    }

    @Override
    public void setFocusParameters() {
        if (mCameraDevice != null) {
            updateFocusParameters();
            mCameraDevice.applySettings(mCameraSettings);
        }
    }
}
