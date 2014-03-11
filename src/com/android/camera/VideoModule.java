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
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore.Video;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.Toast;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.CameraManager;
import com.android.camera.app.CameraManager.CameraPictureCallback;
import com.android.camera.app.CameraManager.CameraProxy;
import com.android.camera.app.LocationManager;
import com.android.camera.app.MediaSaver;
import com.android.camera.app.MemoryManager;
import com.android.camera.app.MemoryManager.MemoryListener;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.hardware.HardwareSpecImpl;
import com.android.camera.module.ModuleController;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsUtil;
import com.android.camera.util.AccessibilityUtils;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.UsageStatistics;
import com.android.camera2.R;
import com.google.common.logging.eventprotos;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class VideoModule extends CameraModule
    implements ModuleController,
    VideoController,
    MemoryListener,
    MediaRecorder.OnErrorListener,
    MediaRecorder.OnInfoListener, FocusOverlayManager.Listener {

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
    private Parameters mParameters;

    private boolean mIsInReviewMode;
    private boolean mSnapshotInProgress = false;

    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    // Preference must be read before starting preview. We check this before starting
    // preview.
    private boolean mPreferenceRead;

    private boolean mIsVideoCaptureIntent;
    private boolean mQuickCapture;

    private MediaRecorder mMediaRecorder;

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

    // Time Lapse parameters.
    private final boolean mCaptureTimeLapse = false;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    private final int mTimeBetweenTimeLapseFrameCaptureMs = 0;

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

    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;

    private int mZoomValue;  // The current zoom value.

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
    private Parameters mInitialParams;
    private boolean mFocusAreaSupported;
    private boolean mMeteringAreaSupported;

    private final CameraManager.CameraAFCallback mAutoFocusCallback =
            new CameraManager.CameraAFCallback() {
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
                    ? new CameraManager.CameraAFMoveCallback() {
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
                    mUI.enableShutter(true);
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
                    if ((CameraUtil.getDisplayRotation(mActivity) != mDisplayRotation)
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

    /** Whether shutter is enabled. */
    private boolean mShutterEnabled;

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
        mUI = new VideoUI(mActivity, this,  mActivity.getModuleLayoutRoot());
        mActivity.setPreviewStatusListener(mUI);

        SettingsManager settingsManager = mActivity.getSettingsManager();
        mCameraId = Integer.parseInt(settingsManager.get(SettingsManager.SETTING_CAMERA_ID));

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

        mUI.showTimeLapseUI(mCaptureTimeLapse);
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
        if (mMediaRecorderRecording || mPaused || mCameraDevice == null) {
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
        if (CameraUtil.isVideoSnapshotSupported(mParameters) && !mIsVideoCaptureIntent) {
            if (!mMediaRecorderRecording || mPaused || mSnapshotInProgress || mShutterEnabled) {
                return;
            }

            // Set rotation and gps data.
            CameraInfo info = mActivity.getCameraProvider().getCameraInfo()[mCameraId];
            int rotation = CameraUtil.getJpegRotation(info, mOrientation);
            mParameters.setRotation(rotation);
            Location loc = mLocationManager.getCurrentLocation();
            CameraUtil.setGpsParameters(mParameters, loc);
            mCameraDevice.setParameters(mParameters);

            Log.v(TAG, "Video snapshot start");
            mCameraDevice.takePicture(mHandler,
                    null, null, null, new JpegPictureCallback(loc));
            showVideoSnapshotUI(true);
            mSnapshotInProgress = true;
            UsageStatistics.captureEvent(eventprotos.NavigationChange.Mode.VIDEO_STILL,
                    null, null, null);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
     private void updateAutoFocusMoveCallback() {
        if (mPaused) {
            return;
        }

        if (mParameters.getFocusMode().equals(CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mCameraDevice.setAutoFocusMoveCallback(mHandler,
                    (CameraManager.CameraAFMoveCallback) mAutoFocusMoveCallback);
        } else {
            mCameraDevice.setAutoFocusMoveCallback(null, null);
        }
    }

    /**
     * @return Whether the currently active camera is front-facing.
     */
    private boolean isCameraFrontFacing() {
        CameraInfo info = mAppController.getCameraProvider().getCameraInfo()[mCameraId];
        return info.facing == CameraInfo.CAMERA_FACING_FRONT;
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
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return;
        }
        int newOrientation = CameraUtil.roundOrientation(orientation, mOrientation);

        if (mOrientation != newOrientation) {
            mOrientation = newOrientation;
        }

    }

    private final ButtonManager.ButtonCallback mFlashCallback =
        new ButtonManager.ButtonCallback() {
            @Override
            public void onStateChanged(int state) {
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
    public HardwareSpec getHardwareSpec() {
        return (mParameters != null ? new HardwareSpecImpl(mParameters) : null);
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
        mCameraDevice = cameraProxy;
        mInitialParams = mCameraDevice.getParameters();
        mFocusAreaSupported = CameraUtil.isFocusAreaSupported(mInitialParams);
        mMeteringAreaSupported = CameraUtil.isMeteringAreaSupported(mInitialParams);
        readVideoPreferences();
        resizeForPreviewAspectRatio();
        initializeFocusManager();

        startPreview();
        initializeVideoSnapshot();
        mUI.initializeZoom(mParameters);
        initializeControlByIntent();
    }

    private void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(mCurrentVideoUri, convertOutputFormatToMimeType(mProfile.fileFormat));
        try {
            mActivity
                    .startActivityForResult(intent, CameraActivity.REQ_CODE_DONT_SWITCH_TO_PREVIEW);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    @Override
    @OnClickAttr
    public void onReviewPlayClicked(View v) {
        startPlayVideoActivity();
    }

    @Override
    @OnClickAttr
    public void onReviewDoneClicked(View v) {
        mIsInReviewMode = false;
        doReturnToCaller(true);
    }

    @Override
    @OnClickAttr
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

                mUI.animateFlash();
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
            onStopVideoRecording();
        } else {
            startVideoRecording();
        }
        mUI.enableShutter(false);
        mFocusManager.onShutterUp();

        // Keep the shutter button disabled when in video capture intent
        // mode and recording is stopped. It'll be re-enabled when
        // re-take button is clicked.
        if (!(mIsVideoCaptureIntent && stop)) {
            mHandler.sendEmptyMessageDelayed(MSG_ENABLE_SHUTTER_BUTTON, SHUTTER_BUTTON_TIMEOUT);
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // TODO: Remove this when old camera controls are removed from the UI.
    }

    private void readVideoPreferences() {
        // The preference stores values from ListPreference and is thus string type for all values.
        // We need to convert it to int manually.
        SettingsManager settingsManager = mActivity.getSettingsManager();
        if (!settingsManager.isSet(SettingsManager.SETTING_VIDEO_QUALITY_BACK)) {
            settingsManager.setDefault(SettingsManager.SETTING_VIDEO_QUALITY_BACK);
        }
        if (!settingsManager.isSet(SettingsManager.SETTING_VIDEO_QUALITY_FRONT)) {
            settingsManager.setDefault(SettingsManager.SETTING_VIDEO_QUALITY_FRONT);
        }
        String videoQuality = settingsManager
                .get(isCameraFrontFacing() ? SettingsManager.SETTING_VIDEO_QUALITY_FRONT
                        : SettingsManager.SETTING_VIDEO_QUALITY_BACK);
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
            mMaxVideoDurationInMs = CameraSettings.getMaxVideoDuration(mActivity);
        }

        // TODO: Uncomment this block to re-enable time-lapse.
        /* // Read time lapse recording interval.
        String frameIntervalStr = settingsManager.get(
            SettingsManager.SETTING_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
        mTimeBetweenTimeLapseFrameCaptureMs = Integer.parseInt(frameIntervalStr);
        mCaptureTimeLapse = (mTimeBetweenTimeLapseFrameCaptureMs != 0);
        // TODO: This should be checked instead directly +1000.
        if (mCaptureTimeLapse) {
            quality += 1000;
        } */

        // If quality is not supported, request QUALITY_HIGH which is always supported.
        if (CamcorderProfile.hasProfile(mCameraId, quality) == false) {
            quality = CamcorderProfile.QUALITY_HIGH;
        }
        mProfile = CamcorderProfile.get(mCameraId, quality);
        mPreferenceRead = true;
        if (mCameraDevice == null) {
            return;
        }
        mParameters = mCameraDevice.getParameters();
        Point desiredPreviewSize = getDesiredPreviewSize(mAppController.getAndroidContext(),
                mParameters, mProfile, mUI.getPreviewScreenSize());
        mDesiredPreviewWidth = desiredPreviewSize.x;
        mDesiredPreviewHeight = desiredPreviewSize.y;
        mUI.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
        Log.v(TAG, "mDesiredPreviewWidth=" + mDesiredPreviewWidth +
                ". mDesiredPreviewHeight=" + mDesiredPreviewHeight);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    /**
     * Calculates the preview size and stores it in mDesiredPreviewWidth and
     * mDesiredPreviewHeight. This function checks {@link
     * android.hardware.Camera.Parameters#getPreferredPreviewSizeForVideo()}
     * but also considers the current preview area size on screen and make sure
     * the final preview size will not be smaller than 1/2 of the current
     * on screen preview area in terms of their short sides.
     *
     * @return The preferred preview size or {@code null} if the camera is not
     *         opened yet.
     */
    private static Point getDesiredPreviewSize(Context context, Parameters parameters,
            CamcorderProfile profile, Point previewScreenSize) {
        if (parameters.getSupportedVideoSizes() == null) {
            // Driver doesn't support separate outputs for preview and video.
            return new Point(profile.videoFrameWidth, profile.videoFrameHeight);
        }

        final int previewScreenShortSide = (previewScreenSize.x < previewScreenSize.y ?
                previewScreenSize.x : previewScreenSize.y);
        List<Size> sizes = parameters.getSupportedPreviewSizes();
        Size preferred = parameters.getPreferredPreviewSizeForVideo();
        final int preferredPreviewSizeShortSide = (preferred.width < preferred.height ?
                preferred.width : preferred.height);
        if (preferredPreviewSizeShortSide * 2 < previewScreenShortSide) {
            preferred.width = profile.videoFrameWidth;
            preferred.height = profile.videoFrameHeight;
        }
        int product = preferred.width * preferred.height;
        Iterator<Size> it = sizes.iterator();
        // Remove the preview sizes that are not preferred.
        while (it.hasNext()) {
            Size size = it.next();
            if (size.width * size.height > product) {
                it.remove();
            }
        }
        Size optimalSize = CameraUtil.getOptimalPreviewSize(context, sizes,
                (double) profile.videoFrameWidth / profile.videoFrameHeight);
        return new Point(optimalSize.width, optimalSize.height);
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
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mCameraDisplayOrientation = CameraUtil.getDisplayOrientation(mDisplayRotation, mCameraId);
        // Change the camera display orientation
        if (mCameraDevice != null) {
            mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
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
        if (mDisplayRotation != CameraUtil.getDisplayRotation(mActivity)) {
            setDisplayOrientation();
        }
    }

    @Override
    public void updatePreviewAspectRatio(float aspectRatio) {
        mAppController.updatePreviewAspectRatio(aspectRatio);
    }

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

    private void startPreview() {
        Log.v(TAG, "startPreview");

        SurfaceTexture surfaceTexture = mActivity.getCameraAppUI().getSurfaceTexture();
        if (!mPreferenceRead || surfaceTexture == null || mPaused == true ||
                mCameraDevice == null) {
            return;
        }

        mCameraDevice.setErrorCallback(mHandler, mErrorCallback);
        if (mPreviewing == true) {
            stopPreview();
        }

        setDisplayOrientation();
        mCameraDevice.setDisplayOrientation(mCameraDisplayOrientation);
        setCameraParameters();

        if (mFocusManager != null) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus
            // to resume it because it may have been paused by autoFocus call.
            String focusMode = mFocusManager.getFocusMode();
            if (CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE.equals(focusMode)) {
                mCameraDevice.cancelAutoFocus();
            }
        }

        // This is to notify app controller that preview will start next, so app
        // controller can set preview callbacks if needed. This has to happen before
        // preview is started as a workaround of the framework issue related to preview
        // callbacks that causes preview stretch and crash. (More details see b/12210027
        // and b/12591410
        mAppController.onPreviewReadyToStart();
        try {
            mCameraDevice.setPreviewTexture(surfaceTexture);
            mCameraDevice.startPreview();
            mPreviewing = true;
            onPreviewStarted();
        } catch (Throwable ex) {
            closeCamera();
            throw new RuntimeException("startPreview failed", ex);
        }
    }

    private void onPreviewStarted() {
        mUI.enableShutter(true);
        mAppController.onPreviewStarted();
        if (mFocusManager != null) {
            mFocusManager.onPreviewStarted();
        }
    }

    @Override
    public void stopPreview() {
        if (!mPreviewing) {
            return;
        }
        mCameraDevice.stopPreview();
        if (mFocusManager != null) {
            mFocusManager.onPreviewStopped();
        }
        mPreviewing = false;
    }

    private void closeCamera() {
        Log.v(TAG, "closeCamera");
        if (mCameraDevice == null) {
            Log.d(TAG, "already stopped.");
            return;
        }
        mCameraDevice.setZoomChangeListener(null);
        mCameraDevice.setErrorCallback(null, null);
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
                    mUI.clickShutter();
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getRepeatCount() == 0) {
                    mUI.clickShutter();
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
                mUI.pressShutter(false);
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
        Log.v(TAG, "initializeRecorder");
        // If the mCameraDevice is null, then this activity is going to finish
        if (mCameraDevice == null) {
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
        mMediaRecorder.setCamera(mCameraDevice.getCamera());
        if (!mCaptureTimeLapse) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setProfile(mProfile);
        mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);
        if (mCaptureTimeLapse) {
            double fps = 1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs;
            setCaptureRate(mMediaRecorder, fps);
        }

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

        // See android.hardware.Camera.Parameters.setRotation for
        // documentation.
        // Note that mOrientation here is the device orientation, which is the opposite of
        // what activity.getWindowManager().getDefaultDisplay().getRotation() would return,
        // which is the orientation the graphics need to rotate in order to render correctly.
        int rotation = 0;
        if (mOrientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
            CameraInfo info = mActivity.getCameraProvider().getCameraInfo()[mCameraId];
            if (isCameraFrontFacing()) {
                rotation = (info.orientation - mOrientation + 360) % 360;
            } else {  // back-facing camera
                rotation = (info.orientation + mOrientation) % 360;
            }
        }
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
        Log.v(TAG, "Releasing media recorder.");
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

    private void saveVideo() {
        if (mVideoFileDescriptor == null) {
            long duration = SystemClock.uptimeMillis() - mRecordingStartTime;
            if (duration > 0) {
                if (mCaptureTimeLapse) {
                    duration = getTimeLapseVideoLength(duration);
                }
            } else {
                Log.w(TAG, "Video duration <= 0 : " + duration);
            }
            getServices().getMediaSaver().addVideo(mCurrentVideoFilename,
                    duration, mCurrentVideoValues,
                    mOnVideoSavedListener, mContentResolver);
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
            mActivity.updateStorageSpaceAndHint();
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
    private void pauseAudioPlayback() {
        AudioManager am = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    // For testing.
    public boolean isRecording() {
        return mMediaRecorderRecording;
    }

    private void startVideoRecording() {
        Log.v(TAG, "startVideoRecording");
        mUI.cancelAnimations();
        mUI.setSwipingEnabled(false);
        mUI.showFocusUI(false);
        mUI.showVideoRecordingHints(false);

        mActivity.updateStorageSpaceAndHint();
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.v(TAG, "Storage issue, ignore the start request");
            return;
        }

        //??
        //if (!mCameraDevice.waitDone()) return;
        mCurrentVideoUri = null;

        initializeRecorder();
        if (mMediaRecorder == null) {
            Log.e(TAG, "Fail to initialize media recorder");
            return;
        }

        pauseAudioPlayback();

        try {
            mMediaRecorder.start(); // Recording is now started
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not start media recorder. ", e);
            releaseMediaRecorder();
            // If start fails, frameworks will not lock the camera for us.
            mCameraDevice.lock();
            return;
        }
        mAppController.getCameraAppUI().setSwipeEnabled(false);

        // Make sure the video recording has started before announcing
        // this in accessibility.
        AccessibilityUtils.makeAnnouncement(mUI.getShutterButton(),
                mActivity.getString(R.string.video_recording_started));

        // The parameters might have been altered by MediaRecorder already.
        // We need to force mCameraDevice to refresh before getting it.
        mCameraDevice.refreshParameters();
        // The parameters may have been changed by MediaRecorder upon starting
        // recording. We need to alter the parameters if we support camcorder
        // zoom. To reduce latency when setting the parameters during zoom, we
        // update mParameters here once.
        mParameters = mCameraDevice.getParameters();

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
        mUI.showTimeLapseUI(false);
    }

    private boolean stopVideoRecording() {
        Log.v(TAG, "stopVideoRecording");
        mUI.setSwipingEnabled(true);
        mUI.showFocusUI(true);
        mUI.showVideoRecordingHints(true);

        boolean fail = false;
        if (mMediaRecorderRecording) {
            boolean shouldAddToMediaStoreNow = false;

            try {
                mMediaRecorder.setOnErrorListener(null);
                mMediaRecorder.setOnInfoListener(null);
                mMediaRecorder.stop();
                shouldAddToMediaStoreNow = true;
                mCurrentVideoFilename = mVideoFilename;
                Log.v(TAG, "stopVideoRecording: Setting current video filename: "
                        + mCurrentVideoFilename);
                float duration = (SystemClock.uptimeMillis() - mRecordingStartTime) / 1000;
                String statisticFilename = (mCurrentVideoFilename == null
                        ? "INTENT"
                        : mCurrentVideoFilename);
                UsageStatistics.captureEvent(eventprotos.NavigationChange.Mode.VIDEO_CAPTURE,
                        statisticFilename, mParameters, duration);
                AccessibilityUtils.makeAnnouncement(mUI.getShutterButton(),
                        mActivity.getAndroidContext().getString(R.string
                                .video_recording_stopped));
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
        if (!mPaused) {
            setFocusParameters();
            mCameraDevice.lock();
            if (!ApiHelper.HAS_SURFACE_TEXTURE_RECORDING) {
                stopPreview();
                // Switch back to use SurfaceTexture for preview.
                startPreview();
            }
            // Update the parameters here because the parameters might have been altered
            // by MediaRecorder.
            mParameters = mCameraDevice.getParameters();
        }
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

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mTimeBetweenTimeLapseFrameCaptureMs;
        return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
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
        if (!mCaptureTimeLapse) {
            text = millisecondToTimeString(deltaAdjusted, false);
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            text = millisecondToTimeString(getTimeLapseVideoLength(delta), true);
            targetNextUpdateDelay = mTimeBetweenTimeLapseFrameCaptureMs;
        }

        mUI.setRecordingTime(text);

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = mActivity.getResources().getColor(countdownRemainingTime
                    ? R.color.recording_time_remaining_text
                    : R.color.recording_time_elapsed_text);

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

        mParameters.setPreviewSize(mDesiredPreviewWidth, mDesiredPreviewHeight);
        int[] fpsRange = CameraUtil.getMaxPreviewFpsRange(mParameters);
        if (fpsRange.length > 0) {
            mParameters.setPreviewFpsRange(
                    fpsRange[Parameters.PREVIEW_FPS_MIN_INDEX],
                    fpsRange[Parameters.PREVIEW_FPS_MAX_INDEX]);
        } else {
            mParameters.setPreviewFrameRate(mProfile.videoFrameRate);
        }

        enableTorchMode(settingsManager.isCameraBackFacing());

        // Set white balance parameter.
        String whiteBalance = settingsManager.get(SettingsManager.SETTING_WHITE_BALANCE);
        if (isSupported(whiteBalance,
                mParameters.getSupportedWhiteBalance())) {
            mParameters.setWhiteBalance(whiteBalance);
        } else {
            whiteBalance = mParameters.getWhiteBalance();
            if (whiteBalance == null) {
                whiteBalance = Parameters.WHITE_BALANCE_AUTO;
            }
        }

        // Set zoom.
        if (mParameters.isZoomSupported()) {
            mParameters.setZoom(mZoomValue);
        }
        updateFocusParameters();

        mParameters.set(CameraUtil.RECORDING_HINT, CameraUtil.TRUE);

        // Enable video stabilization. Convenience methods not available in API
        // level <= 14
        String vstabSupported = mParameters.get("video-stabilization-supported");
        if ("true".equals(vstabSupported)) {
            mParameters.set("video-stabilization", "true");
        }

        // Set picture size.
        // The logic here is different from the logic in still-mode camera.
        // There we determine the preview size based on the picture size, but
        // here we determine the picture size based on the preview size.
        List<Size> supported = mParameters.getSupportedPictureSizes();
        Size optimalSize = CameraUtil.getOptimalVideoSnapshotPictureSize(supported,
                (double) mDesiredPreviewWidth / mDesiredPreviewHeight);
        Size original = mParameters.getPictureSize();
        if (!original.equals(optimalSize)) {
            mParameters.setPictureSize(optimalSize.width, optimalSize.height);
        }
        Log.v(TAG, "Video snapshot size is " + optimalSize.width + "x" +
                optimalSize.height);

        // Set JPEG quality.
        int jpegQuality = CameraProfile.getJpegEncodingQualityParameter(mCameraId,
                CameraProfile.QUALITY_HIGH);
        mParameters.setJpegQuality(jpegQuality);

        mCameraDevice.setParameters(mParameters);
        // Nexus 5 through KitKat 4.4.2 requires a second call to
        // .setParameters() for frame rate settings to take effect.
        mCameraDevice.setParameters(mParameters);

        // Update UI based on the new parameters.
        mUI.updateOnScreenIndicators(mParameters);
    }

    private void updateFocusParameters() {
        // Set continuous autofocus. During recording, we use "continuous-video"
        // auto focus mode to ensure smooth focusing. Whereas during preview (i.e.
        // before recording starts) we use "continuous-picture" auto focus mode
        // for faster but slightly jittery focusing.
        List<String> supportedFocus = mParameters.getSupportedFocusModes();
        if (mMediaRecorderRecording) {
            if (isSupported(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, supportedFocus)) {
                mParameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                mFocusManager.overrideFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else {
                mFocusManager.overrideFocusMode(null);
            }
        } else {
            mFocusManager.overrideFocusMode(null);
            if (isSupported(CameraUtil.FOCUS_MODE_CONTINUOUS_PICTURE, supportedFocus)) {
                mParameters.setFocusMode(mFocusManager.getFocusMode());
                if (mFocusAreaSupported) {
                    mParameters.setFocusAreas(mFocusManager.getFocusAreas());
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
        mUI.enableShutter(false);
        mZoomValue = 0;

        showVideoSnapshotUI(false);

        if (!mPreviewing) {
            requestCamera(mCameraId);
        } else {
            // preview already started
            mUI.enableShutter(true);
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

        UsageStatistics.changeScreen(eventprotos.NavigationChange.Mode.VIDEO_CAPTURE,
                eventprotos.CameraEvent.InteractionCause.BUTTON);
        getServices().getMemoryManager().addListener(this);
    }

    @Override
    public void pause() {
        mPaused = true;

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
        settingsManager.set(SettingsManager.SETTING_CAMERA_ID, "" + mCameraId);

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
        mZoomValue = 0;
        mUI.setOrientationIndicator(0, false);

        // Start switch camera animation. Post a message because
        // onFrameAvailable from the old camera may already exist.
        mHandler.sendEmptyMessage(MSG_SWITCH_CAMERA_START_ANIMATION);
        mUI.updateOnScreenIndicators(mParameters);
    }

    private void initializeVideoSnapshot() {
        if (mParameters == null) {
            return;
        }
    }

    void showVideoSnapshotUI(boolean enabled) {
        if (mParameters == null) {
            return;
        }
        if (CameraUtil.isVideoSnapshotSupported(mParameters) && !mIsVideoCaptureIntent) {
            if (enabled) {
                mUI.animateFlash();
            } else {
                mUI.showPreviewBorder(enabled);
            }
            mUI.enableShutter(!enabled);
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
        if (mParameters.getFlashMode() == null) {
            return;
        }

        SettingsManager settingsManager = mActivity.getSettingsManager();

        String flashMode;
        if (enable) {
            flashMode = settingsManager.get(SettingsManager.SETTING_VIDEOCAMERA_FLASH_MODE);
        } else {
            flashMode = Parameters.FLASH_MODE_OFF;
        }
        List<String> supportedFlash = mParameters.getSupportedFlashModes();
        if (isSupported(flashMode, supportedFlash)) {
            mParameters.setFlashMode(flashMode);
        } else {
            flashMode = mParameters.getFlashMode();
            if (flashMode == null) {
                flashMode = mActivity.getString(
                        R.string.pref_camera_flashmode_no_flash);
                mParameters.setFlashMode(flashMode);
            }
        }
        mCameraDevice.setParameters(mParameters);
        mUI.updateOnScreenIndicators(mParameters);
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
            Log.v(TAG, "onPictureTaken");
            mSnapshotInProgress = false;
            showVideoSnapshotUI(false);
            storeImage(jpegData, mLocation);
        }
    }

    private void storeImage(final byte[] data, Location loc) {
        long dateTaken = System.currentTimeMillis();
        String title = CameraUtil.createJpegName(dateTaken);
        ExifInterface exif = Exif.getExif(data);
        int orientation = Exif.getOrientation(exif);

        getServices().getMediaSaver().addImage(
                data, title, dateTaken, loc, orientation,
                exif, mOnPhotoSavedListener, mContentResolver);
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

    @Override
    public void startPreCaptureAnimation() {
        mAppController.startPreCaptureAnimation();
    }

    private void requestCamera(int id) {
        mActivity.getCameraProvider().requestCamera(id);
    }

    @Override
    public void onMemoryStateChanged(int state) {
        setShutterEnabled(state == MemoryManager.STATE_OK);
    }

    @Override
    public void onLowMemory() {
        // Not much we can do in the video module.
    }

    private void setShutterEnabled(boolean enabled) {
        mShutterEnabled = enabled;
        mUI.enableShutter(enabled);
    }

    /***********************FocusOverlayManager Listener****************************/
    @Override
    public void autoFocus() {
        mCameraDevice.autoFocus(mHandler, mAutoFocusCallback);
    }

    @Override
    public void cancelAutoFocus() {
        mCameraDevice.cancelAutoFocus();
        setFocusParameters();
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
        updateFocusParameters();
        mCameraDevice.setParameters(mParameters);
    }

}
