/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnLayoutChangeListener;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.CameraAppUI.BottomBarUISpec;
import com.android.camera.app.MediaSaver;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.module.ModuleController;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.AutoFocusMode;
import com.android.camera.one.OneCamera.AutoFocusState;
import com.android.camera.one.OneCamera.CaptureReadyCallback;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.OneCamera.PhotoCaptureParameters;
import com.android.camera.one.OneCamera.PhotoCaptureParameters.Flash;
import com.android.camera.one.OneCameraManager;
import com.android.camera.remote.RemoteCameraModule;
import com.android.camera.session.CaptureSession;
import com.android.camera.settings.Keys;
import com.android.camera.settings.ResolutionUtil;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.Size;
import com.android.camera.util.SystemProperties;
import com.android.camera.util.UsageStatistics;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent.CameraProxy;

import java.io.File;

/**
 * New Capture module that is made to support photo and video capture on top of
 * the OneCamera API, to transparently support GCam.
 * <p>
 * This has been a re-write with pieces taken and improved from GCamModule and
 * PhotoModule, which are to be retired eventually.
 * <p>
 * TODO:
 * <ul>
 * <li>Server-side logging
 * <li>Focusing
 * <li>Show location dialog
 * <li>Show resolution dialog on certain devices
 * <li>Store location
 * <li>Timer
 * <li>Capture intent
 * </ul>
 */
public class CaptureModule extends CameraModule
        implements MediaSaver.QueueListener,
        ModuleController,
        OneCamera.PictureCallback,
        OneCamera.FocusStateListener,
        OneCamera.ReadyStateChangedListener,
        PreviewStatusListener.PreviewAreaChangedListener,
        RemoteCameraModule,
        SensorEventListener,
        SettingsManager.OnSettingChangedListener,
        TextureView.SurfaceTextureListener {

    /**
     * Called on layout changes.
     */
    private final OnLayoutChangeListener mLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            int width = right - left;
            int height = bottom - top;
            updatePreviewTransform(width, height, false);
        }
    };

    /**
     * Called when the captured media has been saved.
     */
    private final MediaSaver.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaver.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mAppController.notifyNewMedia(uri);
                    }
                }
            };

    /**
     * Called when the user pressed the back/front camera switch button.
     */
    private final ButtonManager.ButtonCallback mCameraSwitchCallback =
            new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int cameraId) {
                    // At the time this callback is fired, the camera id
                    // has be set to the desired camera.
                    if (mPaused) {
                        return;
                    }

                    mSettingsManager.set(mAppController.getModuleScope(), Keys.KEY_CAMERA_ID,
                            cameraId);

                    Log.d(TAG, "Start to switch camera. cameraId=" + cameraId);
                    switchCamera(getFacingFromCameraId(cameraId));
                }
            };

    /**
     * Show AF target in center of preview and start animation.
     */
    Runnable mShowAutoFocusTargetInCenterRunnable = new Runnable() {
        @Override
        public void run() {
            mUI.setAutoFocusTarget(((int) (mPreviewArea.left + mPreviewArea.right)) / 2,
                    ((int) (mPreviewArea.top + mPreviewArea.bottom)) / 2);
            mUI.showAutoFocusInProgress();
        }
    };

    /**
     * Hide AF target UI element.
     */
    Runnable mHideAutoFocusTargetRunnable = new Runnable() {
        @Override
        public void run() {
            // showAutoFocusSuccess() just hides the AF UI.
            mUI.showAutoFocusSuccess();
        }
    };

    private static final Tag TAG = new Tag("CaptureModule");
    private static final String PHOTO_MODULE_STRING_ID = "PhotoModule";
    /** Enable additional debug output. */
    private static final boolean DEBUG = true;
    /**
     * This is the delay before we execute onResume tasks when coming from the
     * lock screen, to allow time for onPause to execute.
     * <p>
     * TODO: Make sure this value is in sync with what we see on L.
     */
    private static final int ON_RESUME_TASKS_DELAY_MSEC = 20;

    /** System Properties switch to enable debugging focus UI. */
    private static final String PROP_FOCUS_DEBUG_UI_KEY = "persist.camera.focus_debug_ui";
    private static final String PROP_FOCUS_DEBUG_UI_OFF = "0";
    private static final boolean FOCUS_DEBUG_UI = !PROP_FOCUS_DEBUG_UI_OFF
            .equals(SystemProperties.get(PROP_FOCUS_DEBUG_UI_KEY, PROP_FOCUS_DEBUG_UI_OFF));

    private final Object mDimensionLock = new Object();
    /**
     * Lock for race conditions in the SurfaceTextureListener callbacks.
     */
    private final Object mSurfaceLock = new Object();
    /** Controller giving us access to other services. */
    private final AppController mAppController;
    /** The applications settings manager. */
    private final SettingsManager mSettingsManager;
    /** Application context. */
    private final Context mContext;
    private CaptureModuleUI mUI;
    /** The camera manager used to open cameras. */
    private OneCameraManager mCameraManager;
    /** The currently opened camera device. */
    private OneCamera mCamera;
    /** The direction the currently opened camera is facing to. */
    private Facing mCameraFacing = Facing.BACK;
    /** The texture used to render the preview in. */
    private SurfaceTexture mPreviewTexture;

    /** State by the module state machine. */
    private static enum ModuleState {
        IDLE,
        WATCH_FOR_NEXT_FRAME_AFTER_PREVIEW_STARTED,
        UPDATE_TRANSFORM_ON_NEXT_SURFACE_TEXTURE_UPDATE,
    }

    /** The current state of the module. */
    private ModuleState mState = ModuleState.IDLE;
    /** Current orientation of the device. */
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    /** Current zoom value. */
    private final float mZoomValue = 1f;

    /** True if in AF tap-to-focus sequence. */
    private boolean mTapToFocusInProgress = false;

    /** Persistence of Tap to Focus target UI after scan complete. */
    private static final int FOCUS_HOLD_UI_MILLIS = 500;
    /** Persistence of Tap to Focus target UI timeout. */
    private static final int FOCUS_HOLD_UI_TIMEOUT_MILLIS = 1500;

    /** Accelerometer data. */
    private final float[] mGData = new float[3];
    /** Magnetic sensor data. */
    private final float[] mMData = new float[3];
    /** Temporary rotation matrix. */
    private final float[] mR = new float[16];
    /** Current compass heading. */
    private int mHeading = -1;

    /** Whether the module is paused right now. */
    private boolean mPaused;

    /** Whether this module was resumed from lockscreen capture intent. */
    private boolean mIsResumeFromLockScreen = false;

    private final Runnable mResumeTaskRunnable = new Runnable() {
        @Override
        public void run() {
            onResumeTasks();
        }
    };

    /** Main thread handler. */
    private Handler mMainHandler;

    /** Current display rotation in degrees. */
    private int mDisplayRotation;
    /** Current screen width in pixels. */
    private int mScreenWidth;
    /** Current screen height in pixels. */
    private int mScreenHeight;
    /** Current width of preview frames from camera. */
    private int mPreviewBufferWidth;
    /** Current height of preview frames from camera.. */
    private int mPreviewBufferHeight;
    /** Area used by preview. */
    RectF mPreviewArea;

    /** The current preview transformation matrix. */
    private Matrix mPreviewTranformationMatrix = new Matrix();
    /** TODO: This is N5 specific. */
    public static final float FULLSCREEN_ASPECT_RATIO = 16 / 9f;

    /** A directory to store debug information in during development. */
    private final File mDebugDataDir;

    /** CLEAN UP START */
    // private SoundPool mSoundPool;
    // private int mCaptureStartSoundId;
    // private static final int NO_SOUND_STREAM = -999;
    // private final int mCaptureStartSoundStreamId = NO_SOUND_STREAM;
    // private int mCaptureDoneSoundId;
    // private SoundClips.Player mSoundPlayer;
    // private boolean mFirstLayout;
    // private int[] mTargetFPSRanges;
    // private float mZoomValue;
    // private int mSensorOrientation;
    // private int mLensFacing;
    // private volatile float mMaxZoomRatio = 1.0f;
    // private String mFlashMode;
    /** CLEAN UP END */

    /** Constructs a new capture module. */
    public CaptureModule(AppController appController) {
        super(appController);
        mAppController = appController;
        mContext = mAppController.getAndroidContext();
        mSettingsManager = mAppController.getSettingsManager();
        mSettingsManager.addListener(this);
        mDebugDataDir = mContext.getExternalCacheDir();
    }

    @Override
    public void init(CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent) {
        Log.d(TAG, "init");
        mIsResumeFromLockScreen = isResumeFromLockscreen(activity);
        mMainHandler = new Handler(activity.getMainLooper());
        mCameraManager = mAppController.getCameraManager();
        mDisplayRotation = CameraUtil.getDisplayRotation(mContext);
        mCameraFacing = getFacingFromCameraId(mSettingsManager.getInteger(
                mAppController.getModuleScope(),
                Keys.KEY_CAMERA_ID));
        mUI = new CaptureModuleUI(activity, this, mAppController.getModuleLayoutRoot(),
                mLayoutListener);
        mAppController.setPreviewStatusListener(mUI);
        mPreviewTexture = mAppController.getCameraAppUI().getSurfaceTexture();
        if (mPreviewTexture != null) {
            initSurface(mPreviewTexture);
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onShutterCoordinate(TouchCoordinate coord) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onShutterButtonClick() {
        // TODO: Add focusing.
        if (mCamera == null) {
            return;
        }

        // Set up the capture session.
        long sessionTime = System.currentTimeMillis();
        String title = CameraUtil.createJpegName(sessionTime);
        CaptureSession session = getServices().getCaptureSessionManager()
                .createNewSession(title, sessionTime, null);

        // TODO: Add location.

        // Set up the parameters for this capture.
        PhotoCaptureParameters params = new PhotoCaptureParameters();
        params.title = title;
        params.callback = this;
        params.orientation = getOrientation();
        params.flashMode = getFlashModeFromSettings();
        params.heading = mHeading;
        params.debugDataFolder = mDebugDataDir;

        // Take the picture.
        mCamera.takePicture(params, session);
    }

    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        mPreviewArea = previewArea;
        // mUI.updatePreviewAreaRect(previewArea);
        // mUI.positionProgressOverlay(previewArea);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // This is literally the same as the GCamModule implementation.
        int type = event.sensor.getType();
        float[] data;
        if (type == Sensor.TYPE_ACCELEROMETER) {
            data = mGData;
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            data = mMData;
        } else {
            Log.w(TAG, String.format("Unexpected sensor type %s", event.sensor.getName()));
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onQueueStatus(boolean full) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onRemoteShutterPress() {
        // TODO: Check whether shutter is enabled.
        onShutterButtonClick();
    }

    @Override
    public void onSurfaceTextureAvailable(final SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable");
        // Force to re-apply transform matrix here as a workaround for
        // b/11168275
        updatePreviewTransform(width, height, true);
        initSurface(surface);
    }

    public void initSurface(final SurfaceTexture surface) {
        mPreviewTexture = surface;
        closeCamera();

        mCameraManager.open(mCameraFacing, getPictureSizeFromSettings(), new OpenCallback() {
            @Override
            public void onFailure() {
                Log.e(TAG, "Could not open camera.");
                mCamera = null;
                mAppController.showErrorAndFinish(R.string.cannot_connect_camera);
            }

            @Override
            public void onCameraOpened(final OneCamera camera) {
                Log.d(TAG, "onCameraOpened: " + camera);
                mCamera = camera;
                updateBufferDimension();

                // If the surface texture is not destroyed, it may have the last
                // frame lingering.
                // We need to hold off setting transform until preview is
                // started.
                resetDefaultBufferSize();
                mState = ModuleState.WATCH_FOR_NEXT_FRAME_AFTER_PREVIEW_STARTED;

                Log.d(TAG, "starting preview ...");

                // TODO: Consider rolling these two calls into one.
                camera.startPreview(new Surface(surface), new CaptureReadyCallback() {

                    @Override
                    public void onSetupFailed() {
                        Log.e(TAG, "Could not set up preview.");
                        mCamera.close(null);
                        mCamera = null;
                        // TODO: Show an error message and exit.
                    }

                    @Override
                    public void onReadyForCapture() {
                        Log.d(TAG, "Ready for capture.");
                        onPreviewStarted();
                        mCamera.setFocusStateListener(CaptureModule.this);
                        mCamera.setReadyStateChangedListener(CaptureModule.this);
                    }
                });
            }
        });
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged");
        resetDefaultBufferSize();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureDestroyed");
        closeCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (mState == ModuleState.UPDATE_TRANSFORM_ON_NEXT_SURFACE_TEXTURE_UPDATE) {
            Log.d(TAG, "onSurfaceTextureUpdated --> updatePreviewTransform");
            mState = ModuleState.IDLE;
            CameraAppUI appUI = mAppController.getCameraAppUI();
            updatePreviewTransform(appUI.getSurfaceWidth(), appUI.getSurfaceHeight(), true);
        }
    }

    @Override
    public String getModuleStringIdentifier() {
        return PHOTO_MODULE_STRING_ID;
    }

    @Override
    public void resume() {
        // Add delay on resume from lock screen only, in order to to speed up
        // the onResume --> onPause --> onResume cycle from lock screen.
        // Don't do always because letting go of thread can cause delay.
        if (mIsResumeFromLockScreen) {
            Log.v(TAG, "Delayng onResumeTasks from lock screen. " + System.currentTimeMillis());
            // Note: onPauseAfterSuper() will delete this runnable, so we will
            // at most have 1 copy queued up.
            mMainHandler.postDelayed(mResumeTaskRunnable, ON_RESUME_TASKS_DELAY_MSEC);
        } else {
            onResumeTasks();
        }
    }

    private void onResumeTasks() {
        Log.d(TAG, "onResumeTasks + " + System.currentTimeMillis());
        mPaused = false;
        mAppController.getCameraAppUI().onChangeCamera();
        mAppController.addPreviewAreaSizeChangedListener(this);
        resetDefaultBufferSize();
        getServices().getRemoteShutterListener().onModuleReady(this);
        // TODO: Check if we can really take a photo right now (memory, camera
        // state, ... ).
        mAppController.setShutterEnabled(true);
    }

    @Override
    public void pause() {
        mPaused = true;
        resetTextureBufferSize();
        closeCamera();
        // Remove delayed resume trigger, if it hasn't been executed yet.
        mMainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void destroy() {
    }

    @Override
    public void onLayoutOrientationChanged(boolean isLandscape) {
        Log.d(TAG, "onLayoutOrientationChanged");
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
    }

    @Override
    public void onCameraAvailable(CameraProxy cameraProxy) {
        // Ignore since we manage the camera ourselves until we remove this.
    }

    @Override
    public void hardResetSettings(SettingsManager settingsManager) {
        // TODO Auto-generated method stub
    }

    @Override
    public HardwareSpec getHardwareSpec() {
        return new HardwareSpec() {
            @Override
            public boolean isFrontCameraSupported() {
                return true;
            }

            @Override
            public boolean isHdrSupported() {
                return false;
            }

            @Override
            public boolean isHdrPlusSupported() {
                // TODO: Enable once we support this.
                return false;
            }

            @Override
            public boolean isFlashSupported() {
                return true;
            }
        };
    }

    @Override
    public BottomBarUISpec getBottomBarSpec() {
        CameraAppUI.BottomBarUISpec bottomBarSpec = new CameraAppUI.BottomBarUISpec();
        bottomBarSpec.enableGridLines = true;
        bottomBarSpec.enableCamera = true;
        bottomBarSpec.cameraCallback = mCameraSwitchCallback;
        // TODO: Enable once we support this.
        bottomBarSpec.enableHdr = false;
        // TODO: Enable once we support this.
        bottomBarSpec.hdrCallback = null;
        // TODO: Enable once we support this.
        bottomBarSpec.enableSelfTimer = false;
        bottomBarSpec.showSelfTimer = false;
        // TODO: Deal with e.g. HDR+ if it doesn't support it.
        bottomBarSpec.enableFlash = true;
        return bottomBarSpec;
    }

    @Override
    public boolean isUsingBottomBar() {
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    /**
     * Focus sequence starts for zone around tap location for single tap.
     */
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        Log.v(TAG, "onSingleTapUp x=" + x + " y=" + y);
        // TODO: This should query actual capability.
        if (mCameraFacing == Facing.FRONT) {
            return;
        }
        triggerFocusAtScreenCoord(x, y);
    }

    // TODO: Consider refactoring FocusOverlayManager.
    // Currently AF state transitions are controlled in OneCameraImpl.
    // PhotoModule uses FocusOverlayManager which uses API1/portability
    // logic and coordinates.

    private void triggerFocusAtScreenCoord(int x, int y) {
        mTapToFocusInProgress = true;
        // Show UI immediately even though scan has not started yet.
        mUI.setAutoFocusTarget(x, y);
        mUI.showAutoFocusInProgress();
        mMainHandler.removeCallbacks(mHideAutoFocusTargetRunnable);
        mMainHandler.postDelayed(mHideAutoFocusTargetRunnable, FOCUS_HOLD_UI_TIMEOUT_MILLIS);

        // Normalize coordinates to [0,1] per CameraOne API.
        float points[] = new float[2];
        points[0] = (x - mPreviewArea.left) / mPreviewArea.width();
        points[1] = (y - mPreviewArea.top) / mPreviewArea.height();

        // Rotate coordinates to portrait orientation per CameraOne API.
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(mDisplayRotation, 0.5f, 0.5f);
        rotationMatrix.mapPoints(points);
        mCamera.triggerFocusAndMeterAtPoint(points[0], points[1]);

        // Log touch (screen coordinates).
        if (mZoomValue == 1f) {
            TouchCoordinate touchCoordinate = new TouchCoordinate(x - mPreviewArea.left,
                    y - mPreviewArea.top, mPreviewArea.width(), mPreviewArea.height());
            // TODO: Add to logging: duration, rotation.
            UsageStatistics.instance().tapToFocus(touchCoordinate, null);
        }
    }

    /**
     * This AF status listener does two things:
     * <ol>
     * <li>Ends tap-to-focus period when mode goes from AUTO to CONTINUOUS_PICTURE.</li>
     * <li>Updates AF UI if tap-to-focus is not in progress.</li>
     * </ol>
     */
    @Override
    public void onFocusStatusUpdate(final AutoFocusMode mode, final AutoFocusState state) {
        Log.v(TAG, "AF status is mode:" + mode + " state:" + state);

        if (FOCUS_DEBUG_UI) {
            // TODO: Add debug circle radius+color UI to FocusOverlay.
            // mMainHandler.post(...)
        }

        // After tap to focus SCAN completes, clear UI after FOCUS_HOLD_UI_MILLIS.
        if (mTapToFocusInProgress && mode == AutoFocusMode.AUTO &&
                (state == AutoFocusState.STOPPED_FOCUSED ||
                        state == AutoFocusState.STOPPED_UNFOCUSED)) {
            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mTapToFocusInProgress = false;
                    mMainHandler.removeCallbacks(mHideAutoFocusTargetRunnable);
                    mMainHandler.post(mHideAutoFocusTargetRunnable);
                }
            }, FOCUS_HOLD_UI_MILLIS);
        }

        // Use the OneCamera auto focus callbacks to show the UI, except for
        // tap to focus where we show UI right away at touch, and then turn
        // it off early at 0.5 sec, before the focus lock expires at 3 sec.
        if (!mTapToFocusInProgress) {
            switch (state) {
                case SCANNING:
                    mMainHandler.removeCallbacks(mHideAutoFocusTargetRunnable);
                    mMainHandler.post(mShowAutoFocusTargetInCenterRunnable);
                    break;
                case STOPPED_FOCUSED:
                case STOPPED_UNFOCUSED:
                    mMainHandler.removeCallbacks(mHideAutoFocusTargetRunnable);
                    mMainHandler.post(mHideAutoFocusTargetRunnable);
                    break;
            }
        }
    }

    @Override
    public void onReadyStateChanged(boolean readyForCapture) {
        mAppController.setShutterEnabled(readyForCapture);
    }

    @Override
    public String getPeekAccessibilityString() {
        return mAppController.getAndroidContext()
                .getResources().getString(R.string.photo_accessibility_peek);
    }

    @Override
    public void onThumbnailResult(Bitmap bitmap) {
        // TODO
    }

    @Override
    public void onPictureTaken(CaptureSession session) {
    }

    @Override
    public void onPictureSaved(Uri uri) {
        mAppController.notifyNewMedia(uri);
    }

    @Override
    public void onTakePictureProgress(int progressPercent) {
        // TODO once we have HDR+ hooked up.
    }

    @Override
    public void onPictureTakenFailed() {
    }

    @Override
    public void onSettingChanged(SettingsManager settingsManager, String key) {
        // TODO Auto-generated method stub
    }

    /**
     * Updates the preview transform matrix to adapt to the current preview
     * width, height, and orientation.
     */
    public void updatePreviewTransform() {
        int width;
        int height;
        synchronized (mDimensionLock) {
            width = mScreenWidth;
            height = mScreenHeight;
        }
        updatePreviewTransform(width, height);
    }

    /**
     * Called when the preview started. Informs the app controller and queues a
     * transform update when the next preview frame arrives.
     */
    private void onPreviewStarted() {
        if (mState == ModuleState.WATCH_FOR_NEXT_FRAME_AFTER_PREVIEW_STARTED) {
            mState = ModuleState.UPDATE_TRANSFORM_ON_NEXT_SURFACE_TEXTURE_UPDATE;
        }
        mAppController.onPreviewStarted();
    }

    /**
     * Update the preview transform based on the new dimensions. Will not force
     * an update, if it's not necessary.
     */
    private void updatePreviewTransform(int incomingWidth, int incomingHeight) {
        updatePreviewTransform(incomingWidth, incomingHeight, false);
    }

    /***
     * Update the preview transform based on the new dimensions.
     * TODO: Make work with all: aspect ratios/resolutions x screens/cameras.
     */
    private void updatePreviewTransform(int incomingWidth, int incomingHeight,
            boolean forceUpdate) {
        Log.d(TAG, "updatePreviewTransform: " + incomingWidth + " x " + incomingHeight);

        synchronized (mDimensionLock) {
            int incomingRotation = CameraUtil
                    .getDisplayRotation(mContext);
            // Check for an actual change:
            if (mScreenHeight == incomingHeight && mScreenWidth == incomingWidth &&
                    incomingRotation == mDisplayRotation && !forceUpdate) {
                return;
            }
            // Update display rotation and dimensions
            mDisplayRotation = incomingRotation;
            mScreenWidth = incomingWidth;
            mScreenHeight = incomingHeight;
            updateBufferDimension();

            mPreviewTranformationMatrix = mAppController.getCameraAppUI().getPreviewTransform(
                    mPreviewTranformationMatrix);
            int width = mScreenWidth;
            int height = mScreenHeight;

            // Assumptions:
            // - Aspect ratio for the sensor buffers is in landscape
            // orientation,
            // - Dimensions of buffers received are rotated to the natural
            // device orientation.
            // - The contents of each buffer are rotated by the inverse of
            // the display rotation.
            // - Surface scales the buffer to fit the current view bounds.

            // Get natural orientation and buffer dimensions
            int naturalOrientation = CaptureModuleUtil
                    .getDeviceNaturalOrientation(mContext);
            int effectiveWidth = mPreviewBufferWidth;
            int effectiveHeight = mPreviewBufferHeight;

            if (DEBUG) {
                Log.v(TAG, "Rotation: " + mDisplayRotation);
                Log.v(TAG, "Screen Width: " + mScreenWidth);
                Log.v(TAG, "Screen Height: " + mScreenHeight);
                Log.v(TAG, "Buffer width: " + mPreviewBufferWidth);
                Log.v(TAG, "Buffer height: " + mPreviewBufferHeight);
                Log.v(TAG, "Natural orientation: " + naturalOrientation);
            }

            // If natural orientation is portrait, rotate the buffer
            // dimensions
            if (naturalOrientation == Configuration.ORIENTATION_PORTRAIT) {
                int temp = effectiveWidth;
                effectiveWidth = effectiveHeight;
                effectiveHeight = temp;
            }

            // Find and center view rect and buffer rect
            RectF viewRect = new RectF(0, 0, width, height);
            RectF bufRect = new RectF(0, 0, effectiveWidth, effectiveHeight);
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();
            bufRect.offset(centerX - bufRect.centerX(), centerY - bufRect.centerY());

            // Undo ScaleToFit.FILL done by the surface
            mPreviewTranformationMatrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.FILL);

            // Rotate buffer contents to proper orientation
            mPreviewTranformationMatrix.postRotate(getPreviewOrientation(mDisplayRotation),
                    centerX, centerY);

            // TODO: This is probably only working for the N5. Need to test
            // on a device like N10 with different sensor orientation.
            if ((mDisplayRotation % 180) == 90) {
                int temp = effectiveWidth;
                effectiveWidth = effectiveHeight;
                effectiveHeight = temp;
            }

            boolean is16by9 = false;

            // TODO: BACK/FRONT.
            Size pictureSize = getPictureSizeFromSettings();
            if (pictureSize != null) {
                pictureSize = ResolutionUtil.getApproximateSize(pictureSize);
                if (pictureSize.equals(new Size(16, 9))) {
                    is16by9 = true;
                }
            }

            float scale;
            if (is16by9) {
                // We are going to be clipping off edges to achieve the 16
                // by 9 aspect ratio so we will choose the max here to fill,
                // instead of fit.
                scale =
                        Math.max(width / (float) effectiveWidth, height
                                / (float) effectiveHeight);
            } else {
                // Scale to fit view, cropping the longest dimension
                scale =
                        Math.min(width / (float) effectiveWidth, height
                                / (float) effectiveHeight);
            }
            mPreviewTranformationMatrix.postScale(scale, scale, centerX, centerY);

            // TODO: Take these quantities from mPreviewArea.
            float previewWidth = effectiveWidth * scale;
            float previewHeight = effectiveHeight * scale;
            float previewCenterX = previewWidth / 2;
            float previewCenterY = previewHeight / 2;
            mPreviewTranformationMatrix.postTranslate(previewCenterX - centerX, previewCenterY
                    - centerY);

            if (is16by9) {
                float aspectRatio = FULLSCREEN_ASPECT_RATIO;
                RectF renderedPreviewRect = mAppController.getFullscreenRect();
                float desiredPreviewWidth = Math.max(renderedPreviewRect.height(),
                        renderedPreviewRect.width()) * 1 / aspectRatio;
                int letterBoxWidth = (int) Math.ceil((Math.min(renderedPreviewRect.width(),
                        renderedPreviewRect.height()) - desiredPreviewWidth) / 2.0f);
                mAppController.getCameraAppUI().addLetterboxing(letterBoxWidth);

                float wOffset = -(previewWidth - renderedPreviewRect.width()) / 2.0f;
                float hOffset = -(previewHeight - renderedPreviewRect.height()) / 2.0f;
                mPreviewTranformationMatrix.postTranslate(wOffset, hOffset);
                mAppController.updatePreviewTransformFullscreen(mPreviewTranformationMatrix,
                        aspectRatio);
            } else {
                mAppController.updatePreviewTransform(mPreviewTranformationMatrix);
                mAppController.getCameraAppUI().hideLetterboxing();
            }
            // if (mGcamProxy != null) {
            // mGcamProxy.postSetAspectRatio(mFinalAspectRatio);
            // }
            // mUI.updatePreviewAreaRect(new RectF(0, 0, previewWidth,
            // previewHeight));

            // TODO: Add face detection.
            // Characteristics info =
            // mapp.getCameraProvider().getCharacteristics(0);
            // mUI.setupFaceDetection(CameraUtil.getDisplayOrientation(incomingRotation,
            // info), false);
            // updateCamera2FaceBoundTransform(new
            // RectF(mEffectiveCropRegion),
            // new RectF(0, 0, mBufferWidth, mBufferHeight),
            // new RectF(0, 0, previewWidth, previewHeight), getRotation());
        }
    }

    private void updateBufferDimension() {
        if (mCamera == null) {
            return;
        }

        Size picked = CaptureModuleUtil.pickBufferDimensions(
                mCamera.getSupportedSizes(),
                mCamera.getFullSizeAspectRatio(),
                mContext);
        mPreviewBufferWidth = picked.getWidth();
        mPreviewBufferHeight = picked.getHeight();
    }

    /**
     * Resets the default buffer size to the initially calculated size.
     */
    private void resetDefaultBufferSize() {
        synchronized (mSurfaceLock) {
            if (mPreviewTexture != null) {
                mPreviewTexture.setDefaultBufferSize(mPreviewBufferWidth, mPreviewBufferHeight);
            }
        }
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.setFocusStateListener(null);
            mCamera.close(null);
            mCamera = null;
        }
    }

    private int getOrientation() {
        if (mAppController.isAutoRotateScreen()) {
            return mDisplayRotation;
        } else {
            return mOrientation;
        }
    }

    /**
     * @return Whether we are resuming from within the lockscreen.
     */
    private static boolean isResumeFromLockscreen(Activity activity) {
        String action = activity.getIntent().getAction();
        return (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action)
        || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action));
    }

    private void switchCamera(Facing switchTo) {
        if (mPaused || mCameraFacing == switchTo) {
            return;
        }
        // TODO: Un-comment once we have timer back.
        // cancelCountDown();

        mAppController.freezeScreenUntilPreviewReady();

        mCameraFacing = switchTo;
        initSurface(mPreviewTexture);

        // TODO: Un-comment once we have focus back.
        // if (mFocusManager != null) {
        // mFocusManager.removeMessages();
        // }
        // mFocusManager.setMirror(mMirror);
    }

    private Size getPictureSizeFromSettings() {
        String pictureSizeKey = mCameraFacing == Facing.FRONT ? Keys.KEY_PICTURE_SIZE_FRONT
                : Keys.KEY_PICTURE_SIZE_BACK;
        return mSettingsManager.getSize(SettingsManager.SCOPE_GLOBAL, pictureSizeKey);
    }

    private int getPreviewOrientation(int deviceOrientationDegrees) {
        // Important: Camera2 buffers are already rotated to the natural
        // orientation of the device (at least for the back-camera).

        // TODO: Remove this hack for the front camera as soon as b/16637957 is
        // fixed.
        if (mCameraFacing == Facing.FRONT) {
            deviceOrientationDegrees += 180;
        }
        return (360 - deviceOrientationDegrees) % 360;
    }

    /**
     * Returns which way around the camera is facing, based on it's ID.
     * <p>
     * TODO: This needs to change so that we store the direction directly in the
     * settings, rather than a Camera ID.
     */
    private static Facing getFacingFromCameraId(int cameraId) {
        return cameraId == 1 ? Facing.FRONT : Facing.BACK;
    }

    private void resetTextureBufferSize() {
        // Reset the default buffer sizes on the shared SurfaceTexture
        // so they are not scaled for gcam.
        //
        // According to the documentation for
        // SurfaceTexture.setDefaultBufferSize,
        // photo and video based image producers (presumably only Camera 1 api),
        // override this buffer size. Any module that uses egl to render to a
        // SurfaceTexture must have these buffer sizes reset manually. Otherwise
        // the SurfaceTexture cannot be transformed by matrix set on the
        // TextureView.
        if (mPreviewTexture != null) {
            mPreviewTexture.setDefaultBufferSize(mAppController.getCameraAppUI().getSurfaceWidth(),
                    mAppController.getCameraAppUI().getSurfaceHeight());
        }
    }

    /**
     * @return The currently set Flash settings. Defaults to AUTO if the setting
     *         could not be parsed.
     */
    private Flash getFlashModeFromSettings() {
        String flashSetting = mSettingsManager.getString(mAppController.getCameraScope(),
                Keys.KEY_FLASH_MODE);
        try {
            return Flash.valueOf(flashSetting.toUpperCase());
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Could not parse Flash Setting. Defaulting to AUTO.");
            return Flash.AUTO;
        }
    }
}
