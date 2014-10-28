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
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
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
import com.android.camera.app.LocationManager;
import com.android.camera.app.MediaSaver;
import com.android.camera.debug.DebugPropertyHelper;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.module.ModuleController;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.AutoFocusState;
import com.android.camera.one.OneCamera.CaptureReadyCallback;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.OneCamera.PhotoCaptureParameters;
import com.android.camera.one.OneCamera.PhotoCaptureParameters.Flash;
import com.android.camera.one.OneCameraManager;
import com.android.camera.one.Settings3A;
import com.android.camera.one.v2.OneCameraManagerImpl;
import com.android.camera.remote.RemoteCameraModule;
import com.android.camera.session.CaptureSession;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.Size;
import com.android.camera.util.UsageStatistics;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent.CameraProxy;

import java.io.File;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
 * <li>Focusing (managed by OneCamera implementations)
 * <li>Show location dialog on first start
 * <li>Show resolution dialog on certain devices
 * <li>Store location
 * <li>Capture intent
 * </ul>
 */
public class CaptureModule extends CameraModule
        implements MediaSaver.QueueListener,
        ModuleController,
        CountDownView.OnCountDownStatusListener,
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
     * Hide AF target UI element.
     */
    Runnable mHideAutoFocusTargetRunnable = new Runnable() {
        @Override
        public void run() {
            // For debug UI off, showAutoFocusSuccess() just hides the AF UI.
            if (mFocusedAtEnd) {
                mUI.showAutoFocusSuccess();
            } else {
                mUI.showAutoFocusFailure();
            }
        }
    };

    private static final Tag TAG = new Tag("CaptureModule");
    private static final String PHOTO_MODULE_STRING_ID = "PhotoModule";
    /** Enable additional debug output. */
    private static final boolean DEBUG = true;

    /** Timeout for camera open/close operations. */
    private static final int CAMERA_OPEN_CLOSE_TIMEOUT_MILLIS = 2500;

    /** System Properties switch to enable debugging focus UI. */
    private static final boolean CAPTURE_DEBUG_UI = DebugPropertyHelper.showCaptureDebugUI();

    private final Object mDimensionLock = new Object();

    /**
     * Sticky Gcam mode is when this module's sole purpose it to be the Gcam
     * mode. If true, the device uses {@link PhotoModule} for normal picture
     * taking.
     */
    private final boolean mStickyGcamCamera;

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
    /** The currently opened camera device, or null if the camera is closed. */
    private OneCamera mCamera;
    /** Held when opening or closing the camera. */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    /** The direction the currently opened camera is facing to. */
    private Facing mCameraFacing = Facing.BACK;
    /** Whether HDR is currently enabled. */
    private boolean mHdrEnabled = false;

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
    private float mZoomValue = 1f;
    /** Current duration of capture timer in seconds. */
    private int mTimerDuration;
    // TODO: Get image capture intent UI working.
    private boolean mIsImageCaptureIntent;

    /** True if in AF tap-to-focus sequence. */
    private boolean mTapToFocusWaitForActiveScan = false;
    /** Records beginning frame of each AF scan. */
    private long mAutoFocusScanStartFrame = -1;
    /** Records beginning time of each AF scan in uptimeMillis. */
    private long mAutoFocusScanStartTime;

    /** Persistence of Tap to Focus target UI after scan complete. */
    private static final int FOCUS_HOLD_UI_MILLIS = 0;
    /** Worst case persistence of TTF target UI. */
    private static final int FOCUS_UI_TIMEOUT_MILLIS = 2000;
    /** Results from last tap to focus scan */
    private boolean mFocusedAtEnd;
    /** Sensor manager we use to get the heading of the device. */
    private SensorManager mSensorManager;
    /** Accelerometer. */
    private Sensor mAccelerometerSensor;
    /** Compass. */
    private Sensor mMagneticSensor;

    /** Accelerometer data. */
    private final float[] mGData = new float[3];
    /** Magnetic sensor data. */
    private final float[] mMData = new float[3];
    /** Temporary rotation matrix. */
    private final float[] mR = new float[16];
    /** Current compass heading. */
    private int mHeading = -1;

    /** Used to fetch and embed the location into captured images. */
    private LocationManager mLocationManager;
    /** Plays sounds for countdown timer. */
    private SoundPlayer mCountdownSoundPlayer;

    /** Whether the module is paused right now. */
    private boolean mPaused;

    /** Main thread handler. */
    private Handler mMainHandler;
    /** Handler thread for camera-related operations. */
    private Handler mCameraHandler;

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
    // private boolean mFirstLayout;
    // private int[] mTargetFPSRanges;
    // private float mZoomValue;
    // private int mSensorOrientation;
    // private int mLensFacing;
    // private String mFlashMode;
    /** CLEAN UP END */

    public CaptureModule(AppController appController) {
        this(appController, false);
    }

    /** Constructs a new capture module. */
    public CaptureModule(AppController appController, boolean stickyHdr) {
        super(appController);
        mAppController = appController;
        mContext = mAppController.getAndroidContext();
        mSettingsManager = mAppController.getSettingsManager();
        mSettingsManager.addListener(this);
        mDebugDataDir = mContext.getExternalCacheDir();
        mStickyGcamCamera = stickyHdr;
    }

    @Override
    public void init(CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent) {
        Log.d(TAG, "init");
        mMainHandler = new Handler(activity.getMainLooper());
        HandlerThread thread = new HandlerThread("CaptureModule.mCameraHandler");
        thread.start();
        mCameraHandler = new Handler(thread.getLooper());
        mCameraManager = mAppController.getCameraManager();
        mLocationManager = mAppController.getLocationManager();
        mDisplayRotation = CameraUtil.getDisplayRotation(mContext);
        mCameraFacing = getFacingFromCameraId(mSettingsManager.getInteger(
                mAppController.getModuleScope(),
                Keys.KEY_CAMERA_ID));
        mUI = new CaptureModuleUI(activity, this, mAppController.getModuleLayoutRoot(),
                mLayoutListener);
        mAppController.setPreviewStatusListener(mUI);
        mPreviewTexture = mAppController.getCameraAppUI().getSurfaceTexture();
        mSensorManager = (SensorManager) (mContext.getSystemService(Context.SENSOR_SERVICE));
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mCountdownSoundPlayer = new SoundPlayer(mContext);

        String action = activity.getIntent().getAction();
        mIsImageCaptureIntent = (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                || CameraActivity.ACTION_IMAGE_CAPTURE_SECURE.equals(action));
        View cancelButton = activity.findViewById(R.id.shutter_cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelCountDown();
            }
        });
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
        if (mCamera == null) {
            return;
        }

        int countDownDuration = mSettingsManager
                .getInteger(SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION);
        mTimerDuration = countDownDuration;
        if (countDownDuration > 0) {
            // Start count down.
            mAppController.getCameraAppUI().transitionToCancel();
            mAppController.getCameraAppUI().hideModeOptions();
            mUI.setCountdownFinishedListener(this);
            mUI.startCountdown(countDownDuration);
            // Will take picture later via listener callback.
        } else {
            takePictureNow();
        }
    }

    private void takePictureNow() {
        Location location = mLocationManager.getCurrentLocation();

        // Set up the capture session.
        long sessionTime = System.currentTimeMillis();
        String title = CameraUtil.createJpegName(sessionTime);
        CaptureSession session = getServices().getCaptureSessionManager()
                .createNewSession(title, sessionTime, location);

        // Set up the parameters for this capture.
        PhotoCaptureParameters params = new PhotoCaptureParameters();
        params.title = title;
        params.callback = this;
        params.orientation = getOrientation();
        params.flashMode = getFlashModeFromSettings();
        params.heading = mHeading;
        params.debugDataFolder = mDebugDataDir;
        params.location = location;
        params.zoom = mZoomValue;
        params.timerSeconds = mTimerDuration > 0 ? (float) mTimerDuration : null;

        mCamera.takePicture(params, session);
    }

    @Override
    public void onCountDownFinished() {
        mAppController.getCameraAppUI().transitionToCapture();
        mAppController.getCameraAppUI().showModeOptions();
        if (mPaused) {
            return;
        }
        takePictureNow();
    }

    @Override
    public void onRemainingSecondsChanged(int remainingSeconds) {
        if (remainingSeconds == 1) {
            mCountdownSoundPlayer.play(R.raw.timer_final_second, 0.6f);
        } else if (remainingSeconds == 2 || remainingSeconds == 3) {
            mCountdownSoundPlayer.play(R.raw.timer_increment, 0.6f);
        }
    }

    private void cancelCountDown() {
        if (mUI.isCountingDown()) {
            // Cancel on-going countdown.
            mUI.cancelCountDown();
        }
        mAppController.getCameraAppUI().showModeOptions();
        mAppController.getCameraAppUI().transitionToCapture();
    }

    @Override
    public void onQuickExpose() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                // Starts the short version of the capture animation UI.
                mAppController.startPreCaptureAnimation(true);
            }
        });
    }

    @Override
    public void onPreviewAreaChanged(RectF previewArea) {
        mPreviewArea = previewArea;
        mUI.onPreviewAreaChanged(previewArea);
        // mUI.updatePreviewAreaRect(previewArea);
        mUI.positionProgressOverlay(previewArea);
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
        Log.d(TAG, "onRemoteShutterPress");
        // TODO: Check whether shutter is enabled.
        takePictureNow();
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
        openCameraAndStartPreview();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged");
        resetDefaultBufferSize();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "onSurfaceTextureDestroyed");
        mPreviewTexture = null;
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
        mPaused = false;
        mAppController.getCameraAppUI().onChangeCamera();
        mAppController.addPreviewAreaSizeChangedListener(this);
        resetDefaultBufferSize();
        getServices().getRemoteShutterListener().onModuleReady(this);
        // TODO: Check if we can really take a photo right now (memory, camera
        // state, ... ).
        mAppController.getCameraAppUI().enableModeOptions();
        mAppController.setShutterEnabled(true);

        // Get events from the accelerometer and magnetic sensor.
        if (mAccelerometerSensor != null) {
            mSensorManager.registerListener(this, mAccelerometerSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (mMagneticSensor != null) {
            mSensorManager.registerListener(this, mMagneticSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
        mHdrEnabled = mStickyGcamCamera || mAppController.getSettingsManager().getInteger(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR_PLUS) == 1;

        // This means we are resuming with an existing preview texture. This
        // means we will never get the onSurfaceTextureAvailable call. So we
        // have to open the camera and start the preview here.
        if (mPreviewTexture != null) {
            initSurface(mPreviewTexture);
        }

        mCountdownSoundPlayer.loadSound(R.raw.timer_final_second);
        mCountdownSoundPlayer.loadSound(R.raw.timer_increment);
    }

    @Override
    public void pause() {
        mPaused = true;
        getServices().getRemoteShutterListener().onModuleExit();
        cancelCountDown();
        closeCamera();
        resetTextureBufferSize();
        mCountdownSoundPlayer.unloadSound(R.raw.timer_final_second);
        mCountdownSoundPlayer.unloadSound(R.raw.timer_increment);
        // Remove delayed resume trigger, if it hasn't been executed yet.
        mMainHandler.removeCallbacksAndMessages(null);

        // Unregister the sensors.
        if (mAccelerometerSensor != null) {
            mSensorManager.unregisterListener(this, mAccelerometerSensor);
        }
        if (mMagneticSensor != null) {
            mSensorManager.unregisterListener(this, mMagneticSensor);
        }
    }

    @Override
    public void destroy() {
        mCountdownSoundPlayer.release();
        mCameraHandler.getLooper().quitSafely();
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

        // TODO: Document orientation compute logic and unify them in OrientationManagerImpl.
        // b/17443789
        // Flip to counter-clockwise orientation.
        mOrientation = (360 - orientation) % 360;
    }

    @Override
    public void onCameraAvailable(CameraProxy cameraProxy) {
        // Ignore since we manage the camera ourselves until we remove this.
    }

    @Override
    public void hardResetSettings(SettingsManager settingsManager) {
        if (mStickyGcamCamera) {
            // Sitcky HDR+ mode should hard reset HDR+ to on, and camera back
            // facing.
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR_PLUS, true);
            settingsManager.set(mAppController.getModuleScope(), Keys.KEY_CAMERA_ID,
                    getBackFacingCameraId());
        }
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
                // TODO: Check if the device has HDR and not HDR+.
                return false;
            }

            @Override
            public boolean isHdrPlusSupported() {
                return GcamHelper.hasGcamCapture();
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
        bottomBarSpec.cameraCallback = getCameraCallback();
        bottomBarSpec.enableHdr = GcamHelper.hasGcamCapture();
        bottomBarSpec.hdrCallback = getHdrButtonCallback();
        bottomBarSpec.enableSelfTimer = true;
        bottomBarSpec.showSelfTimer = true;
        if (!mHdrEnabled) {
            bottomBarSpec.enableFlash = true;
        }
        // Added to handle case of CaptureModule being used only for Gcam.
        if (mStickyGcamCamera) {
            bottomBarSpec.enableFlash = false;
        }
        return bottomBarSpec;
    }

    @Override
    public boolean isUsingBottomBar() {
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mUI.isCountingDown()) {
                    cancelCountDown();
                } else if (event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                // Prevent default.
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                onShutterButtonClick();
                return true;
        }
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
        if (mCamera == null) {
            // If we receive this after the camera is closed, do nothing.
            return;
        }

        mTapToFocusWaitForActiveScan = true;
        // Show UI immediately even though scan has not started yet.
        float minEdge = Math.min(mPreviewArea.width(), mPreviewArea.height());
        mUI.setAutoFocusTarget(x, y, false,
                (int) (Settings3A.getAutoFocusRegionWidth() * mZoomValue * minEdge),
                (int) (Settings3A.getMeteringRegionWidth() * mZoomValue * minEdge));
        mUI.showAutoFocusInProgress();

        // Cancel any scheduled auto focus target UI actions.
        mMainHandler.removeCallbacks(mHideAutoFocusTargetRunnable);
        // Timeout in case camera fails to stop (unlikely).
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMainHandler.post(mHideAutoFocusTargetRunnable);
            }
        }, FOCUS_UI_TIMEOUT_MILLIS);

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
     * Show AF target in center of preview.
     */
    private void setAutoFocusTargetPassive() {
        float minEdge = Math.min(mPreviewArea.width(), mPreviewArea.height());
        mUI.setAutoFocusTarget((int) mPreviewArea.centerX(), (int) mPreviewArea.centerY(),
                true,
                (int) (Settings3A.getAutoFocusRegionWidth() * mZoomValue * minEdge),
                (int) (Settings3A.getMeteringRegionWidth() * mZoomValue * minEdge));
        mUI.showAutoFocusInProgress();
    }

    /**
     * Update UI based on AF state changes.
     */
    @Override
    public void onFocusStatusUpdate(final AutoFocusState state, long frameNumber) {
        Log.v(TAG, "AF status is state:" + state);

        switch (state) {
            case PASSIVE_SCAN:
                mMainHandler.removeCallbacks(mHideAutoFocusTargetRunnable);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setAutoFocusTargetPassive();
                    }
                });
                break;
            case ACTIVE_SCAN:
                mTapToFocusWaitForActiveScan = false;
                break;
            case PASSIVE_FOCUSED:
            case PASSIVE_UNFOCUSED:
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mUI.setPassiveFocusSuccess(state == AutoFocusState.PASSIVE_FOCUSED);
                    }
                });
                break;
            case ACTIVE_FOCUSED:
            case ACTIVE_UNFOCUSED:
                if (!mTapToFocusWaitForActiveScan) {
                    mFocusedAtEnd = state != AutoFocusState.ACTIVE_UNFOCUSED;
                    mMainHandler.removeCallbacks(mHideAutoFocusTargetRunnable);
                    mMainHandler.post(mHideAutoFocusTargetRunnable);
                }
                break;
        }

        if (CAPTURE_DEBUG_UI) {
            measureAutoFocusScans(state, frameNumber);
        }
    }

    private void measureAutoFocusScans(final AutoFocusState state, long frameNumber) {
        // Log AF scan lengths.
        boolean passive = false;
        switch (state) {
            case PASSIVE_SCAN:
            case ACTIVE_SCAN:
                if (mAutoFocusScanStartFrame == -1) {
                    mAutoFocusScanStartFrame = frameNumber;
                    mAutoFocusScanStartTime = SystemClock.uptimeMillis();
                }
                break;
            case PASSIVE_FOCUSED:
            case PASSIVE_UNFOCUSED:
                passive = true;
            case ACTIVE_FOCUSED:
            case ACTIVE_UNFOCUSED:
                if (mAutoFocusScanStartFrame != -1) {
                    long frames = frameNumber - mAutoFocusScanStartFrame;
                    long dt = SystemClock.uptimeMillis() - mAutoFocusScanStartTime;
                    int fps = Math.round(frames * 1000f / dt);
                    String report = String.format("%s scan: fps=%d frames=%d",
                            passive ? "CAF" : "AF", fps, frames);
                    Log.v(TAG, report);
                    mUI.showDebugMessage(String.format("%d / %d", frames, fps));
                    mAutoFocusScanStartFrame = -1;
                }
                break;
        }
    }

    @Override
    public void onReadyStateChanged(boolean readyForCapture) {
        if (readyForCapture) {
            mAppController.getCameraAppUI().enableModeOptions();
        }
        mAppController.setShutterEnabled(readyForCapture);
    }

    @Override
    public String getPeekAccessibilityString() {
        return mAppController.getAndroidContext()
                .getResources().getString(R.string.photo_accessibility_peek);
    }

    @Override
    public void onThumbnailResult(byte[] jpegData) {
        getServices().getRemoteShutterListener().onPictureTaken(jpegData);
    }

    @Override
    public void onPictureTaken(CaptureSession session) {
        mAppController.getCameraAppUI().enableModeOptions();
    }

    @Override
    public void onPictureSaved(Uri uri) {
        mAppController.notifyNewMedia(uri);
    }

    @Override
    public void onTakePictureProgress(float progress) {
        mUI.setPictureTakingProgress((int) (progress * 100));
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
     * Set zoom value.
     *
     * @param zoom Zoom value, must be between 1.0 and mCamera.getMaxZoom().
     */
    public void setZoom(float zoom) {
        mZoomValue = zoom;
        if (mCamera != null) {
            mCamera.setZoom(zoom);
        }
    }

    /**
     * TODO: Remove this method once we are in pure CaptureModule land.
     */
    private String getBackFacingCameraId() {
        if (!(mCameraManager instanceof OneCameraManagerImpl)) {
            throw new IllegalStateException("This should never be called with Camera API V1");
        }
        OneCameraManagerImpl manager = (OneCameraManagerImpl) mCameraManager;
        return manager.getFirstBackCameraId();
    }

    /**
     * @return Depending on whether we're in sticky-HDR mode or not, return the
     *         proper callback to be used for when the HDR/HDR+ button is
     *         pressed.
     */
    private ButtonManager.ButtonCallback getHdrButtonCallback() {
        if (mStickyGcamCamera) {
            return new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int state) {
                    if (mPaused) {
                        return;
                    }
                    if (state == ButtonManager.ON) {
                        throw new IllegalStateException(
                                "Can't leave hdr plus mode if switching to hdr plus mode.");
                    }
                    SettingsManager settingsManager = mAppController.getSettingsManager();
                    settingsManager.set(mAppController.getModuleScope(),
                            Keys.KEY_REQUEST_RETURN_HDR_PLUS, false);
                    switchToRegularCapture();
                }
            };
        } else {
            return new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int hdrEnabled) {
                    if (mPaused) {
                        return;
                    }
                    Log.d(TAG, "HDR enabled =" + hdrEnabled);
                    mHdrEnabled = hdrEnabled == 1;
                    switchCamera();
                }
            };
        }
    }

    /**
     * @return Depending on whether we're in sticky-HDR mode or not, this
     *         returns the proper callback to be used for when the camera
     *         (front/back switch) button is pressed.
     */
    private ButtonManager.ButtonCallback getCameraCallback() {
        if (mStickyGcamCamera) {
            return new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int state) {
                    if (mPaused) {
                        return;
                    }

                    // At the time this callback is fired, the camera id setting
                    // has changed to the desired camera.
                    SettingsManager settingsManager = mAppController.getSettingsManager();
                    if (Keys.isCameraBackFacing(settingsManager,
                            mAppController.getModuleScope())) {
                        throw new IllegalStateException(
                                "Hdr plus should never be switching from front facing camera.");
                    }

                    // Switch to photo mode, but request a return to hdr plus on
                    // switching to back camera again.
                    settingsManager.set(mAppController.getModuleScope(),
                            Keys.KEY_REQUEST_RETURN_HDR_PLUS, true);
                    switchToRegularCapture();
                }
            };
        } else {
            return new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int cameraId) {
                    if (mPaused) {
                        return;
                    }

                    // At the time this callback is fired, the camera id
                    // has be set to the desired camera.
                    mSettingsManager.set(mAppController.getModuleScope(), Keys.KEY_CAMERA_ID,
                            cameraId);

                    Log.d(TAG, "Start to switch camera. cameraId=" + cameraId);
                    mCameraFacing = getFacingFromCameraId(cameraId);
                    switchCamera();
                }
            };
        }
    }

    /**
     * Switches to PhotoModule to do regular photo captures.
     * <p>
     * TODO: Remove this once we use CaptureModule for photo taking.
     */
    private void switchToRegularCapture() {
        // Turn off HDR+ before switching back to normal photo mode.
        SettingsManager settingsManager = mAppController.getSettingsManager();
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR_PLUS, false);

        // Disable this button to prevent callbacks from this module from firing
        // while we are transitioning modules.
        ButtonManager buttonManager = mAppController.getButtonManager();
        buttonManager.disableButtonClick(ButtonManager.BUTTON_HDR_PLUS);
        mAppController.getCameraAppUI().freezeScreenUntilPreviewReady();
        mAppController.onModeSelected(mContext.getResources().getInteger(
                R.integer.camera_mode_photo));
        buttonManager.enableButtonClick(ButtonManager.BUTTON_HDR_PLUS);
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
     * Update the preview transform based on the new dimensions. TODO: Make work
     * with all: aspect ratios/resolutions x screens/cameras.
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
            updatePreviewBufferDimension();

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

            // Scale to fit view, cropping the longest dimension
            float scale =
                    Math.min(width / (float) effectiveWidth, height
                            / (float) effectiveHeight);
            mPreviewTranformationMatrix.postScale(scale, scale, centerX, centerY);

            // TODO: Take these quantities from mPreviewArea.
            float previewWidth = effectiveWidth * scale;
            float previewHeight = effectiveHeight * scale;
            float previewCenterX = previewWidth / 2;
            float previewCenterY = previewHeight / 2;
            mPreviewTranformationMatrix.postTranslate(previewCenterX - centerX, previewCenterY
                    - centerY);

            mAppController.updatePreviewTransform(mPreviewTranformationMatrix);
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

    /**
     * Based on the current picture size, selects the best preview dimension and
     * stores it in {@link #mPreviewBufferWidth} and
     * {@link #mPreviewBufferHeight}.
     */
    private void updatePreviewBufferDimension() {
        if (mCamera == null) {
            return;
        }

        Size pictureSize = getPictureSizeFromSettings();
        Size previewBufferSize = mCamera.pickPreviewSize(pictureSize, mContext);
        mPreviewBufferWidth = previewBufferSize.getWidth();
        mPreviewBufferHeight = previewBufferSize.getHeight();
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

    /**
     * Open camera and start the preview.
     */
    private void openCameraAndStartPreview() {
        // Only enable HDR on the back camera
        boolean useHdr = mHdrEnabled && mCameraFacing == Facing.BACK;

        try {
            // TODO Given the current design, we cannot guarantee that one of
            // CaptureReadyCallback.onSetupFailed or onReadyForCapture will
            // be called (see below), so it's possible that
            // mCameraOpenCloseLock.release() is never called under extremely
            // rare cases.  If we leak the lock, this timeout ensures that we at
            // least crash so we don't deadlock the app.
            if (!mCameraOpenCloseLock.tryAcquire(CAMERA_OPEN_CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to acquire camera-open lock.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting to acquire camera-open lock.", e);
        }
        if (mCamera != null) {
            // If the camera is already open, do nothing.
            Log.d(TAG, "Camera already open, not re-opening.");
            mCameraOpenCloseLock.release();
            return;
        }
        mCameraManager.open(mCameraFacing, useHdr, getPictureSizeFromSettings(),
                new OpenCallback() {
                    @Override
                    public void onFailure() {
                        Log.e(TAG, "Could not open camera.");
                        mCamera = null;
                        mCameraOpenCloseLock.release();
                        mAppController.showErrorAndFinish(R.string.cannot_connect_camera);
                    }

                    @Override
                    public void onCameraClosed() {
                        mCamera = null;
                        mCameraOpenCloseLock.release();
                    }

                    @Override
                    public void onCameraOpened(final OneCamera camera) {
                        Log.d(TAG, "onCameraOpened: " + camera);
                        mCamera = camera;
                        updatePreviewBufferDimension();

                        // If the surface texture is not destroyed, it may have
                        // the last frame lingering. We need to hold off setting
                        // transform until preview is started.
                        resetDefaultBufferSize();
                        mState = ModuleState.WATCH_FOR_NEXT_FRAME_AFTER_PREVIEW_STARTED;
                        Log.d(TAG, "starting preview ...");

                        // TODO: Consider rolling these two calls into one.
                        camera.startPreview(new Surface(mPreviewTexture),
                                new CaptureReadyCallback() {
                                    @Override
                                    public void onSetupFailed() {
                                        // We must release this lock here, before posting
                                        // to the main handler since we may be blocked
                                        // in pause(), getting ready to close the camera.
                                        mCameraOpenCloseLock.release();
                                        Log.e(TAG, "Could not set up preview.");
                                        mMainHandler.post(new Runnable() {
                                           @Override
                                           public void run() {
                                               if (mCamera == null) {
                                                   Log.d(TAG, "Camera closed, aborting.");
                                                   return;
                                               }
                                               mCamera.close(null);
                                               mCamera = null;
                                               // TODO: Show an error message and exit.
                                           }
                                        });
                                    }

                                    @Override
                                    public void onReadyForCapture() {
                                        // We must release this lock here, before posting
                                        // to the main handler since we may be blocked
                                        // in pause(), getting ready to close the camera.
                                        mCameraOpenCloseLock.release();
                                        mMainHandler.post(new Runnable() {
                                           @Override
                                           public void run() {
                                               Log.d(TAG, "Ready for capture.");
                                               if (mCamera == null) {
                                                   Log.d(TAG, "Camera closed, aborting.");
                                                   return;
                                               }
                                               onPreviewStarted();
                                               // Enable zooming after preview has
                                               // started.
                                               mUI.initializeZoom(mCamera.getMaxZoom());
                                               mCamera.setFocusStateListener(CaptureModule.this);
                                               mCamera.setReadyStateChangedListener(CaptureModule.this);
                                           }
                                        });
                                    }
                                });
                    }
                }, mCameraHandler);
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
        } catch(InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting to acquire camera-open lock.", e);
        }
        try {
            if (mCamera != null) {
                mCamera.close(null);
                mCamera.setFocusStateListener(null);
                mCamera = null;
            }
        } finally {
            mCameraOpenCloseLock.release();
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

    /**
     * Re-initialize the camera if e.g. the HDR mode or facing property changed.
     */
    private void switchCamera() {
        if (mPaused) {
            return;
        }
        cancelCountDown();
        mAppController.freezeScreenUntilPreviewReady();
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
