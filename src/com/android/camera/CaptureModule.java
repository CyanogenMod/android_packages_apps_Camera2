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

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.location.Location;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.CameraAppUI.BottomBarUISpec;
import com.android.camera.app.LocationManager;
import com.android.camera.app.OrientationManager.DeviceOrientation;
import com.android.camera.async.MainThread;
import com.android.camera.burst.BurstFacade;
import com.android.camera.burst.BurstFacadeFactory;
import com.android.camera.burst.BurstReadyStateChangeListener;
import com.android.camera.burst.OrientationLockController;
import com.android.camera.captureintent.PreviewTransformCalculator;
import com.android.camera.debug.DebugPropertyHelper;
import com.android.camera.debug.Log;
import com.android.camera.debug.Log.Tag;
import com.android.camera.device.CameraId;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.hardware.HeadingSensor;
import com.android.camera.module.ModuleController;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.AutoFocusState;
import com.android.camera.one.OneCamera.CaptureReadyCallback;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCamera.OpenCallback;
import com.android.camera.one.OneCamera.PhotoCaptureParameters;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCaptureSetting;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraException;
import com.android.camera.one.OneCameraManager;
import com.android.camera.one.OneCameraModule;
import com.android.camera.one.OneCameraOpener;
import com.android.camera.one.config.OneCameraFeatureConfig;
import com.android.camera.one.v2.photo.ImageRotationCalculator;
import com.android.camera.one.v2.photo.ImageRotationCalculatorImpl;
import com.android.camera.remote.RemoteCameraModule;
import com.android.camera.session.CaptureSession;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.stats.CaptureStats;
import com.android.camera.stats.UsageStatistics;
import com.android.camera.stats.profiler.Profile;
import com.android.camera.stats.profiler.Profiler;
import com.android.camera.stats.profiler.Profilers;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.ui.focus.FocusController;
import com.android.camera.ui.focus.FocusSound;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent.CameraProxy;
import com.google.common.logging.eventprotos;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

/**
 * New Capture module that is made to support photo and video capture on top of
 * the OneCamera API, to transparently support GCam.
 * <p>
 * This has been a re-write with pieces taken and improved from GCamModule and
 * PhotoModule, which are to be retired eventually.
 * <p>
 */
public class CaptureModule extends CameraModule implements
        ModuleController,
        CountDownView.OnCountDownStatusListener,
        OneCamera.PictureCallback,
        OneCamera.FocusStateListener,
        OneCamera.ReadyStateChangedListener,
        RemoteCameraModule {

    private static final Tag TAG = new Tag("CaptureModule");
    /** Enable additional debug output. */
    private static final boolean DEBUG = true;
    /** Workaround Flag for b/19271661 to use autotransformation in Capture Layout in Nexus4 **/
    private static final boolean USE_AUTOTRANSFORM_UI_LAYOUT = ApiHelper.IS_NEXUS_4;

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

    /** Controller giving us access to other services. */
    private final AppController mAppController;
    /** The applications settings manager. */
    private final SettingsManager mSettingsManager;
    /** Application context. */
    private final Context mContext;
    /** Module UI. */
    private CaptureModuleUI mUI;
    /** The camera manager used to open cameras. */
    private OneCameraOpener mOneCameraOpener;
    /** The manager to query for camera device information */
    private OneCameraManager mOneCameraManager;
    /** The currently opened camera device, or null if the camera is closed. */
    private OneCamera mCamera;
    /** The selected picture size. */
    private Size mPictureSize;
    /** Fair semaphore held when opening or closing the camera. */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1, true);
    /** The direction the currently opened camera is facing to. */
    private Facing mCameraFacing;
    /** Whether HDR Scene mode is currently enabled. */
    private boolean mHdrSceneEnabled = false;
    private boolean mHdrPlusEnabled = false;
    private final Object mSurfaceTextureLock = new Object();
    /**
     * Flag that is used when Fatal Error Handler is running and the app should
     * not continue execution
     */
    private boolean mShowErrorAndFinish;
    private TouchCoordinate mLastShutterTouchCoordinate = null;

    private FocusController mFocusController;
    private OneCameraCharacteristics mCameraCharacteristics;
    final private PreviewTransformCalculator mPreviewTransformCalculator;

    /** The listener to listen events from the CaptureModuleUI. */
    private final CaptureModuleUI.CaptureModuleUIListener mUIListener =
            new CaptureModuleUI.CaptureModuleUIListener() {
                @Override
                public void onZoomRatioChanged(float zoomRatio) {
                    mZoomValue = zoomRatio;
                    if (mCamera != null) {
                        mCamera.setZoom(zoomRatio);
                    }
                }
            };

    /** The listener to respond preview area changes. */
    private final PreviewStatusListener.PreviewAreaChangedListener mPreviewAreaChangedListener =
            new PreviewStatusListener.PreviewAreaChangedListener() {
                @Override
                public void onPreviewAreaChanged(RectF previewArea) {
                    mPreviewArea = previewArea;
                    mFocusController.configurePreviewDimensions(previewArea);
                }
            };

    /** The listener to listen events from the preview. */
    private final PreviewStatusListener mPreviewStatusListener = new PreviewStatusListener() {
        @Override
        public void onPreviewLayoutChanged(View v, int left, int top, int right,
                int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            int width = right - left;
            int height = bottom - top;
            updatePreviewTransform(width, height, false);
        }

        @Override
        public boolean shouldAutoAdjustTransformMatrixOnLayout() {
            return USE_AUTOTRANSFORM_UI_LAYOUT;
        }

        @Override
        public void onPreviewFlipped() {
            // Do nothing because when preview is flipped, TextureView will lay
            // itself out again, which will then trigger a transform matrix
            // update.
        }

        @Override
        public GestureDetector.OnGestureListener getGestureListener() {
            return new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent ev) {
                    Point tapPoint = new Point((int) ev.getX(), (int) ev.getY());
                    Log.v(TAG, "onSingleTapUpPreview location=" + tapPoint);
                    if (!mCameraCharacteristics.isAutoExposureSupported() &&
                          !mCameraCharacteristics.isAutoFocusSupported()) {
                        return false;
                    }
                    startActiveFocusAt(tapPoint.x, tapPoint.y);
                    return true;
                }
            };
        }

        @Override
        public View.OnTouchListener getTouchListener() {
            return null;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            // Force to re-apply transform matrix here as a workaround for
            // b/11168275
            updatePreviewTransform(width, height, true);
            synchronized (mSurfaceTextureLock) {
                mPreviewSurfaceTexture = surface;
            }
            reopenCamera();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(TAG, "onSurfaceTextureDestroyed");
            synchronized (mSurfaceTextureLock) {
                mPreviewSurfaceTexture = null;
            }
            closeCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
            updatePreviewBufferSize();
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
    };

    private final OneCamera.PictureSaverCallback mPictureSaverCallback =
            new OneCamera.PictureSaverCallback() {
                @Override
                public void onRemoteThumbnailAvailable(final byte[] jpegImage) {
                    mMainThread.execute(new Runnable() {
                        @Override
                        public void run() {
                            mAppController.getServices().getRemoteShutterListener()
                                    .onPictureTaken(jpegImage);
                        }
                    });
                }
            };

    /** State by the module state machine. */
    private static enum ModuleState {
        IDLE,
        WATCH_FOR_NEXT_FRAME_AFTER_PREVIEW_STARTED,
        UPDATE_TRANSFORM_ON_NEXT_SURFACE_TEXTURE_UPDATE,
    }

    /** The current state of the module. */
    private ModuleState mState = ModuleState.IDLE;
    /** Current zoom value. */
    private float mZoomValue = 1f;

    /** Records beginning frame of each AF scan. */
    private long mAutoFocusScanStartFrame = -1;
    /** Records beginning time of each AF scan in uptimeMillis. */
    private long mAutoFocusScanStartTime;

    /** Heading sensor. */
    private HeadingSensor mHeadingSensor;

    /** Used to fetch and embed the location into captured images. */
    private final LocationManager mLocationManager;
    /** Plays sounds for countdown timer. */
    private SoundPlayer mSoundPlayer;
    private final MediaActionSound mMediaActionSound;

    /** Whether the module is paused right now. */
    private boolean mPaused;

    /** Main thread. */
    private final MainThread mMainThread;
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

    /** The surface texture for the preview. */
    private SurfaceTexture mPreviewSurfaceTexture;

    /** The burst manager for controlling the burst. */
    private final BurstFacade mBurstController;
    private static final String BURST_SESSIONS_DIR = "burst_sessions";

    private final Profiler mProfiler = Profilers.instance().guard();

    public CaptureModule(AppController appController) {
        this(appController, false);
    }

    /** Constructs a new capture module. */
    public CaptureModule(AppController appController, boolean stickyHdr) {
        super(appController);
        Profile guard = mProfiler.create("new CaptureModule").start();
        mPaused = true;
        mMainThread = MainThread.create();
        mAppController = appController;
        mContext = mAppController.getAndroidContext();
        mSettingsManager = mAppController.getSettingsManager();
        mStickyGcamCamera = stickyHdr;
        mLocationManager = mAppController.getLocationManager();
        mPreviewTransformCalculator = new PreviewTransformCalculator(
                mAppController.getOrientationManager());

        mBurstController = BurstFacadeFactory.create(mContext,
                new OrientationLockController() {
                    @Override
                    public void unlockOrientation() {
                        mAppController.getOrientationManager().unlockOrientation();
                    }

                        @Override
                    public void lockOrientation() {
                        mAppController.getOrientationManager().lockOrientation();
                    }
                },
                new BurstReadyStateChangeListener() {
                   @Override
                    public void onBurstReadyStateChanged(boolean ready) {
                        // TODO: This needs to take into account the state of
                        // the whole system, not just burst.
                       onReadyStateChanged(false);
                    }
                });
        mMediaActionSound = new MediaActionSound();
        guard.stop();
    }

    private boolean updateCameraCharacteristics() {
        try {
            CameraId cameraId = mOneCameraManager.findFirstCameraFacing(mCameraFacing);
            if (cameraId != null && cameraId.getValue() != null) {
                mCameraCharacteristics = mOneCameraManager.getOneCameraCharacteristics(cameraId);
                return mCameraCharacteristics != null;
            }
        } catch (OneCameraAccessException ignored) { }
            mAppController.getFatalErrorHandler().onGenericCameraAccessFailure();
            return false;
    }

    @Override
    public void init(CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent) {
        Profile guard = mProfiler.create("CaptureModule.init").start();
        Log.d(TAG, "init UseAutotransformUiLayout = " + USE_AUTOTRANSFORM_UI_LAYOUT);
        HandlerThread thread = new HandlerThread("CaptureModule.mCameraHandler");
        thread.start();
        mCameraHandler = new Handler(thread.getLooper());
        mOneCameraOpener = mAppController.getCameraOpener();

        try {
            mOneCameraManager = OneCameraModule.provideOneCameraManager();
        } catch (OneCameraException e) {
            Log.e(TAG, "Unable to provide a OneCameraManager. ", e);
        }
        mDisplayRotation = CameraUtil.getDisplayRotation();
        mCameraFacing = getFacingFromCameraId(
              mSettingsManager.getInteger(mAppController.getModuleScope(), Keys.KEY_CAMERA_ID));
        mShowErrorAndFinish = !updateCameraCharacteristics();
        if (mShowErrorAndFinish) {
            return;
        }
        mUI = new CaptureModuleUI(activity, mAppController.getModuleLayoutRoot(), mUIListener);
        mAppController.setPreviewStatusListener(mPreviewStatusListener);
        synchronized (mSurfaceTextureLock) {
            mPreviewSurfaceTexture = mAppController.getCameraAppUI().getSurfaceTexture();
        }
        mSoundPlayer = new SoundPlayer(mContext);

        FocusSound focusSound = new FocusSound(mSoundPlayer, R.raw.material_camera_focus);
        mFocusController = new FocusController(mUI.getFocusRing(), focusSound, mMainThread);

        mHeadingSensor = new HeadingSensor(AndroidServices.instance().provideSensorManager());

        View cancelButton = activity.findViewById(R.id.shutter_cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelCountDown();
            }
        });

        mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
        guard.stop();
    }

    @Override
    public void onShutterButtonLongPressed() {
        try {
            OneCameraCharacteristics cameraCharacteristics;
            CameraId cameraId = mOneCameraManager.findFirstCameraFacing(mCameraFacing);
            cameraCharacteristics = mOneCameraManager.getOneCameraCharacteristics(cameraId);
            DeviceOrientation deviceOrientation = mAppController.getOrientationManager()
                    .getDeviceOrientation();
            ImageRotationCalculator imageRotationCalculator = ImageRotationCalculatorImpl
                    .from(mAppController.getOrientationManager(), cameraCharacteristics);

            mBurstController.startBurst(
                    new CaptureSession.CaptureSessionCreator() {
                        @Override
                        public CaptureSession createAndStartEmpty() {
                            return createAndStartUntrackedCaptureSession();
                        }
                    },
                    deviceOrientation,
                    mCamera.getDirection(),
                    imageRotationCalculator.toImageRotation().getDegrees());

        } catch (OneCameraAccessException e) {
            Log.e(TAG, "Cannot start burst", e);
            return;
        }
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        if (!pressed) {
            // the shutter button was released, stop any bursts.
            mBurstController.stopBurst();
        }
    }

    @Override
    public void onShutterCoordinate(TouchCoordinate coord) {
        mLastShutterTouchCoordinate = coord;
    }

    @Override
    public void onShutterButtonClick() {
        if (mCamera == null) {
            return;
        }

        int countDownDuration = mSettingsManager
                .getInteger(SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION);
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


    private void decorateSessionAtCaptureTime(CaptureSession session) {
        String flashSetting =
                mSettingsManager.getString(mAppController.getCameraScope(),
                        Keys.KEY_FLASH_MODE);
        boolean gridLinesOn = Keys.areGridLinesOn(mSettingsManager);
        float timerDuration = mSettingsManager
                .getInteger(SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION);

        session.getCollector().decorateAtTimeCaptureRequest(
                eventprotos.NavigationChange.Mode.PHOTO_CAPTURE,
                session.getTitle() + ".jpg",
                (mCameraFacing == Facing.FRONT),
                mHdrSceneEnabled,
                mZoomValue,
                flashSetting,
                gridLinesOn,
                timerDuration,
                mLastShutterTouchCoordinate,
                null /* TODO: Implement Volume Button Shutter Click Instrumentation */,
                mCameraCharacteristics.getSensorInfoActiveArraySize()
        );
    }

    private void takePictureNow() {
        if (mCamera == null) {
            Log.i(TAG, "Not taking picture since Camera is closed.");
            return;
        }

        CaptureSession session = createAndStartCaptureSession();
        int orientation = mAppController.getOrientationManager().getDeviceOrientation()
                .getDegrees();

        // TODO: This should really not use getExternalCacheDir and instead use
        // the SessionStorage API. Need to sync with gcam if that's OK.
        PhotoCaptureParameters params = new PhotoCaptureParameters(
                session.getTitle(), orientation, session.getLocation(),
                mContext.getExternalCacheDir(), this, mPictureSaverCallback,
                mHeadingSensor.getCurrentHeading(), mZoomValue, 0);
        decorateSessionAtCaptureTime(session);
        mCamera.takePicture(params, session);
    }

    /**
     * Creates, starts and returns a new capture session. The returned session
     * will have been started with an empty placeholder image.
     */
    private CaptureSession createAndStartCaptureSession() {
        long sessionTime = getSessionTime();
        Location location = mLocationManager.getCurrentLocation();
        String title = CameraUtil.instance().createJpegName(sessionTime);
        CaptureSession session = getServices().getCaptureSessionManager()
                .createNewSession(title, sessionTime, location);

        session.startEmpty(new CaptureStats(mHdrPlusEnabled),
              new Size((int) mPreviewArea.width(), (int) mPreviewArea.height()));
        return session;
    }

    private CaptureSession createAndStartUntrackedCaptureSession() {
        long sessionTime = getSessionTime();
        Location location = mLocationManager.getCurrentLocation();
        String title = CameraUtil.instance().createJpegName(sessionTime);
        CaptureSession session = getServices().getCaptureSessionManager()
              .createNewSession(title, sessionTime, location);

        session.startEmpty(null,
              new Size((int) mPreviewArea.width(), (int) mPreviewArea.height()));
        return session;
    }

    private long getSessionTime() {
        // TODO: Replace with a mockable TimeProvider interface.
        return System.currentTimeMillis();
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
            mSoundPlayer.play(R.raw.timer_final_second, 0.6f);
        } else if (remainingSeconds == 2 || remainingSeconds == 3) {
            mSoundPlayer.play(R.raw.timer_increment, 0.6f);
        }
    }

    private void cancelCountDown() {
        if (mUI.isCountingDown()) {
            // Cancel on-going countdown.
            mUI.cancelCountDown();
        }

        if (!mPaused) {
            mAppController.getCameraAppUI().showModeOptions();
            mAppController.getCameraAppUI().transitionToCapture();
        }
    }

    @Override
    public void onQuickExpose() {
        mMainThread.execute(new Runnable() {
            @Override
            public void run() {
                // Starts the short version of the capture animation UI.
                mAppController.startFlashAnimation(true);
                mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
            }
        });
    }

    @Override
    public void onRemoteShutterPress() {
        Log.d(TAG, "onRemoteShutterPress");
        // TODO: Check whether shutter is enabled.
        takePictureNow();
    }

    private void initSurfaceTextureConsumer() {
        synchronized (mSurfaceTextureLock) {
            if (mPreviewSurfaceTexture != null) {
                mPreviewSurfaceTexture.setDefaultBufferSize(
                        mAppController.getCameraAppUI().getSurfaceWidth(),
                        mAppController.getCameraAppUI().getSurfaceHeight());
            }
        }
        reopenCamera();
    }

    private void reopenCamera() {
        if (mPaused) {
            return;
        }
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                closeCamera();
                if(!mAppController.isPaused()) {
                    openCameraAndStartPreview();
                }
            }
        });
    }

    private SurfaceTexture getPreviewSurfaceTexture() {
        synchronized (mSurfaceTextureLock) {
            return mPreviewSurfaceTexture;
        }
    }

    private void updatePreviewBufferSize() {
        synchronized (mSurfaceTextureLock) {
            if (mPreviewSurfaceTexture != null) {
                mPreviewSurfaceTexture.setDefaultBufferSize(mPreviewBufferWidth,
                        mPreviewBufferHeight);
            }
        }
    }

    @Override
    public void resume() {
        if (mShowErrorAndFinish) {
            return;
        }
        Profile guard = mProfiler.create("CaptureModule.resume").start();

        // We'll transition into 'ready' once the preview is started.
        onReadyStateChanged(false);
        mPaused = false;
        mAppController.addPreviewAreaSizeChangedListener(mPreviewAreaChangedListener);
        mAppController.addPreviewAreaSizeChangedListener(mUI);

        guard.mark();
        getServices().getRemoteShutterListener().onModuleReady(this);
        guard.mark("getRemoteShutterListener.onModuleReady");
        mBurstController.initialize(new SurfaceTexture(0));

        // TODO: Check if we can really take a photo right now (memory, camera
        // state, ... ).
        mAppController.getCameraAppUI().enableModeOptions();
        mAppController.setShutterEnabled(true);
        mAppController.getCameraAppUI().showAccessibilityZoomUI(
                mCameraCharacteristics.getAvailableMaxDigitalZoom());

        mHdrPlusEnabled = mStickyGcamCamera || mAppController.getSettingsManager().getInteger(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR_PLUS) == 1;

        mHdrSceneEnabled = !mStickyGcamCamera && mAppController.getSettingsManager().getBoolean(
              SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR);

        // This means we are resuming with an existing preview texture. This
        // means we will never get the onSurfaceTextureAvailable call. So we
        // have to open the camera and start the preview here.
        SurfaceTexture texture = getPreviewSurfaceTexture();

        guard.mark();
        if (texture != null) {
            initSurfaceTextureConsumer();
            guard.mark("initSurfaceTextureConsumer");
        }

        mSoundPlayer.loadSound(R.raw.timer_final_second);
        mSoundPlayer.loadSound(R.raw.timer_increment);

        guard.mark();
        mHeadingSensor.activate();
        guard.stop("mHeadingSensor.activate()");
    }

    @Override
    public void pause() {
        if (mShowErrorAndFinish) {
            return;
        }
        cancelCountDown();
        mPaused = true;
        mHeadingSensor.deactivate();

        mAppController.removePreviewAreaSizeChangedListener(mUI);
        mAppController.removePreviewAreaSizeChangedListener(mPreviewAreaChangedListener);
        getServices().getRemoteShutterListener().onModuleExit();
        mBurstController.release();
        closeCamera();
        resetTextureBufferSize();
        mSoundPlayer.unloadSound(R.raw.timer_final_second);
        mSoundPlayer.unloadSound(R.raw.timer_increment);
    }

    @Override
    public void destroy() {
        mSoundPlayer.release();
        mMediaActionSound.release();
        mCameraHandler.getLooper().quitSafely();
    }

    @Override
    public void onLayoutOrientationChanged(boolean isLandscape) {
        Log.d(TAG, "onLayoutOrientationChanged");
    }

    @Override
    public void onCameraAvailable(CameraProxy cameraProxy) {
        // Ignore since we manage the camera ourselves until we remove this.
    }

    @Override
    public void hardResetSettings(SettingsManager settingsManager) {
        if (mStickyGcamCamera) {
            // Sticky HDR+ mode should hard reset HDR+ to on, and camera back
            // facing.
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR_PLUS, true);
            settingsManager.set(mAppController.getModuleScope(), Keys.KEY_CAMERA_ID,
                  mOneCameraManager.findFirstCameraFacing(Facing.BACK).getValue());
        }
    }

    @Override
    public HardwareSpec getHardwareSpec() {
        return new HardwareSpec() {
            @Override
            public boolean isFrontCameraSupported() {
                return mOneCameraManager.hasCameraFacing(Facing.FRONT);
            }

            @Override
            public boolean isHdrSupported() {
                if (ApiHelper.IS_NEXUS_4 && is16by9AspectRatio(mPictureSize)) {
                    Log.v(TAG, "16:9 N4, no HDR support");
                    return false;
                } else {
                    return mCameraCharacteristics.isHdrSceneSupported();
                }
            }

            @Override
            public boolean isHdrPlusSupported() {
                OneCameraFeatureConfig featureConfig = mAppController.getCameraFeatureConfig();
                return featureConfig.getHdrPlusSupportLevel(mCameraFacing) !=
                        OneCameraFeatureConfig.HdrPlusSupportLevel.NONE;
            }

            @Override
            public boolean isFlashSupported() {
                return mCameraCharacteristics.isFlashSupported();
            }
        };
    }

    @Override
    public BottomBarUISpec getBottomBarSpec() {
        HardwareSpec hardwareSpec = getHardwareSpec();
        BottomBarUISpec bottomBarSpec = new BottomBarUISpec();
        bottomBarSpec.enableGridLines = true;
        bottomBarSpec.enableCamera = true;
        bottomBarSpec.cameraCallback = getCameraCallback();
        bottomBarSpec.enableHdr =
                hardwareSpec.isHdrSupported() || hardwareSpec.isHdrPlusSupported();
        bottomBarSpec.hdrCallback = getHdrButtonCallback();
        bottomBarSpec.enableSelfTimer = true;
        bottomBarSpec.showSelfTimer = true;
        bottomBarSpec.isExposureCompensationSupported = mCameraCharacteristics
                .isExposureCompensationSupported();
        bottomBarSpec.enableExposureCompensation = bottomBarSpec.isExposureCompensationSupported;

        // We must read the key from the settings because the button callback
        // is not executed until after this method is called.
        if ((hardwareSpec.isHdrPlusSupported() &&
                mAppController.getSettingsManager().getBoolean(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR_PLUS)) ||
              ( hardwareSpec.isHdrSupported() &&
                mAppController.getSettingsManager().getBoolean(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR))) {
            // Disable flash if this is a sticky gcam camera, or if
            // HDR is enabled.
            bottomBarSpec.enableFlash = false;
            // Disable manual exposure if HDR is enabled.
            bottomBarSpec.enableExposureCompensation = false;
        } else {
            // If we are not in HDR / GCAM mode, fallback on the
            // flash supported property and manual exposure supported property
            // for this camera.
            bottomBarSpec.enableFlash = mCameraCharacteristics.isFlashSupported();
        }

        bottomBarSpec.minExposureCompensation =
                mCameraCharacteristics.getMinExposureCompensation();
        bottomBarSpec.maxExposureCompensation =
                mCameraCharacteristics.getMaxExposureCompensation();
        bottomBarSpec.exposureCompensationStep =
                mCameraCharacteristics.getExposureCompensationStep();
        bottomBarSpec.exposureCompensationSetCallback =
                new BottomBarUISpec.ExposureCompensationSetCallback() {
                    @Override
                    public void setExposure(int value) {
                        mSettingsManager.set(
                                mAppController.getCameraScope(), Keys.KEY_EXPOSURE, value);
                    }
                };

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

    // TODO: Consider refactoring FocusOverlayManager.
    // Currently AF state transitions are controlled in OneCameraImpl.
    // PhotoModule uses FocusOverlayManager which uses API1/portability
    // logic and coordinates.
    private void startActiveFocusAt(int viewX, int viewY) {
        if (mCamera == null) {
            // If we receive this after the camera is closed, do nothing.
            return;
        }

        // TODO: make mFocusController final and remove null check.
        if (mFocusController == null) {
            Log.v(TAG, "CaptureModule mFocusController is null!");
            return;
        }
        mFocusController.showActiveFocusAt(viewX, viewY);

        // Normalize coordinates to [0,1] per CameraOne API.
        float points[] = new float[2];
        points[0] = (viewX - mPreviewArea.left) / mPreviewArea.width();
        points[1] = (viewY - mPreviewArea.top) / mPreviewArea.height();

        // Rotate coordinates to portrait orientation per CameraOne API.
        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(mDisplayRotation, 0.5f, 0.5f);
        rotationMatrix.mapPoints(points);

        // Invert X coordinate on front camera since the display is mirrored.
        if (mCameraCharacteristics.getCameraDirection() == Facing.FRONT) {
            points[0] = 1 - points[0];
        }

        mCamera.triggerFocusAndMeterAtPoint(points[0], points[1]);

        // Log touch (screen coordinates).
        if (mZoomValue == 1f) {
            TouchCoordinate touchCoordinate = new TouchCoordinate(
                    viewX - mPreviewArea.left,
                    viewY - mPreviewArea.top,
                    mPreviewArea.width(),
                    mPreviewArea.height());
            // TODO: Add to logging: duration, rotation.
            UsageStatistics.instance().tapToFocus(touchCoordinate, null);
        }
    }

    /**
     * Show AF target in center of preview.
     */
    private void startPassiveFocus() {
        // TODO: make mFocusController final and remove null check.
        if (mFocusController == null) {
            return;
        }

        // TODO: Some passive focus scans may trigger on a location
        // instead of the center of the screen.
        mFocusController.showPassiveFocusAtCenter();
    }

    /**
     * Update UI based on AF state changes.
     */
    @Override
    public void onFocusStatusUpdate(final AutoFocusState state, long frameNumber) {
        Log.v(TAG, "AF status is state:" + state);

        switch (state) {
            case PASSIVE_SCAN:
                startPassiveFocus();
                break;
            case ACTIVE_SCAN:
                // Unused, manual scans are triggered via the UI
                break;
            case PASSIVE_FOCUSED:
            case PASSIVE_UNFOCUSED:
                // Unused
                break;
            case ACTIVE_FOCUSED:
            case ACTIVE_UNFOCUSED:
                // Unused
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
    public void onPictureTakingFailed() {
        mAppController.getFatalErrorHandler().onMediaStorageFailure();
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

                    // Only reload the camera if we are toggling HDR+.
                    if (GcamHelper.hasGcamCapture(mAppController.getCameraFeatureConfig())) {
                        mHdrPlusEnabled = hdrEnabled == 1;
                        switchCamera();
                    } else {
                        mHdrSceneEnabled = hdrEnabled == 1;
                    }
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

                    ButtonManager buttonManager = mAppController.getButtonManager();
                    buttonManager.disableCameraButtonAndBlock();

                    // At the time this callback is fired, the camera id
                    // has be set to the desired camera.
                    mSettingsManager.set(mAppController.getModuleScope(), Keys.KEY_CAMERA_ID,
                            cameraId);

                    Log.d(TAG, "Start to switch camera. cameraId=" + cameraId);
                    mCameraFacing = getFacingFromCameraId(cameraId);
                    mShowErrorAndFinish = !updateCameraCharacteristics();
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

    /**
     * Returns whether it is necessary to apply device-specific fix for b/19271661
     * on the AutoTransform Path, i.e. USE_AUTOTRANSFORM_UI_LAYOUT == true
     *
     * @return whether to apply workaround fix for b/19271661
     */
    private boolean requiresNexus4SpecificFixFor16By9Previews() {
        return USE_AUTOTRANSFORM_UI_LAYOUT && ApiHelper.IS_NEXUS_4
                && is16by9AspectRatio(mPictureSize);
    }

    /***
     * Update the preview transform based on the new dimensions. TODO: Make work
     * with all: aspect ratios/resolutions x screens/cameras.
     */
    private void updatePreviewTransform(int incomingWidth, int incomingHeight,
            boolean forceUpdate) {
        Log.d(TAG, "updatePreviewTransform: " + incomingWidth + " x " + incomingHeight);

        synchronized (mDimensionLock) {
            int incomingRotation = CameraUtil.getDisplayRotation();
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

            // Assumptions:
            // - Aspect ratio for the sensor buffers is in landscape
            // orientation,
            // - Dimensions of buffers received are rotated to the natural
            // device orientation.
            // - The contents of each buffer are rotated by the inverse of
            // the display rotation.
            // - Surface scales the buffer to fit the current view bounds.

            // Get natural orientation and buffer dimensions

            if(USE_AUTOTRANSFORM_UI_LAYOUT) {
                // Use PhotoUI-based AutoTransformation Interface
                if (mPreviewBufferWidth != 0 && mPreviewBufferHeight != 0) {
                    if (requiresNexus4SpecificFixFor16By9Previews()) {
                        // Force preview size to be 16:9, even though surface is 4:3
                        // Surface content is assumed to be 16:9.
                        mAppController.updatePreviewAspectRatio(16.f / 9.f);
                    } else {
                        mAppController.updatePreviewAspectRatio(
                                mPreviewBufferWidth / (float) mPreviewBufferHeight);
                    }
                }
            } else {
                Matrix transformMatrix = mPreviewTransformCalculator.toTransformMatrix(
                        new Size(mScreenWidth, mScreenHeight),
                        new Size(mPreviewBufferWidth, mPreviewBufferHeight));
                mAppController.updatePreviewTransform(transformMatrix);
            }
        }
    }


    /**
     * Calculates whether a picture size is 16:9 ratio, regardless of its
     * orientation.
     *
     * @param size the size of the picture to be considered
     * @return true, if the picture is 16:9; false if it's invalid or size is null
     */
    private boolean is16by9AspectRatio(Size size) {
        if (size == null || size.getWidth() == 0 || size.getHeight() == 0) {
            return false;
        }

        // Normalize aspect ratio to be greater than 1.
        final float aspectRatio = (size.getHeight() > size.getWidth())
                ? (size.getHeight() / (float) size.getWidth())
                : (size.getWidth() / (float) size.getHeight());

        return Math.abs(aspectRatio - (16.f / 9.f)) < 0.001f;
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

        Size previewBufferSize = mCamera.pickPreviewSize(mPictureSize, mContext);
        mPreviewBufferWidth = previewBufferSize.getWidth();
        mPreviewBufferHeight = previewBufferSize.getHeight();

        // Workaround for N4 TextureView/HAL issues b/19271661 for 16:9 preview
        // streams.
        if (requiresNexus4SpecificFixFor16By9Previews()) {
            // Override the preview selection logic to the largest N4 4:3
            // preview size but pass in 16:9 aspect ratio in
            // UpdatePreviewAspectRatio later.
            mPreviewBufferWidth = 1280;
            mPreviewBufferHeight = 960;
        }
        updatePreviewBufferSize();
    }

    /**
     * Open camera and start the preview.
     */
    private void openCameraAndStartPreview() {
        Profile guard = mProfiler.create("CaptureModule.openCameraAndStartPreview()").start();
        try {
            // TODO Given the current design, we cannot guarantee that one of
            // CaptureReadyCallback.onSetupFailed or onReadyForCapture will
            // be called (see below), so it's possible that
            // mCameraOpenCloseLock.release() is never called under extremely
            // rare cases. If we leak the lock, this timeout ensures that we at
            // least crash so we don't deadlock the app.
            if (!mCameraOpenCloseLock.tryAcquire(CAMERA_OPEN_CLOSE_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to acquire camera-open lock.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting to acquire camera-open lock.", e);
        }

        guard.mark("Acquired mCameraOpenCloseLock");

        if (mOneCameraOpener == null) {
            Log.e(TAG, "no available OneCameraManager, showing error dialog");
            mCameraOpenCloseLock.release();
            mAppController.getFatalErrorHandler().onGenericCameraAccessFailure();
            guard.stop("No OneCameraManager");
            return;
        }
        if (mCamera != null) {
            // If the camera is already open, do nothing.
            Log.d(TAG, "Camera already open, not re-opening.");
            mCameraOpenCloseLock.release();
            guard.stop("Camera is already open");
            return;
        }

        // Derive objects necessary for camera creation.
        MainThread mainThread = MainThread.create();
        ImageRotationCalculator imageRotationCalculator = ImageRotationCalculatorImpl
                .from(mAppController.getOrientationManager(), mCameraCharacteristics);

        // Only enable GCam on the back camera
        boolean useHdr = mHdrPlusEnabled && mCameraFacing == Facing.BACK;

        CameraId cameraId = mOneCameraManager.findFirstCameraFacing(mCameraFacing);
        final String settingScope = SettingsManager.getCameraSettingScope(cameraId.getValue());

        OneCameraCaptureSetting captureSetting;
        // Read the preferred picture size from the setting.
        try {
            mPictureSize = mAppController.getResolutionSetting().getPictureSize(
                    cameraId, mCameraFacing);
            captureSetting = OneCameraCaptureSetting.create(mPictureSize, mSettingsManager,
                    getHardwareSpec(), settingScope, useHdr);
        } catch (OneCameraAccessException ex) {
            mAppController.getFatalErrorHandler().onGenericCameraAccessFailure();
            return;
        }

        mOneCameraOpener.open(cameraId, captureSetting, mCameraHandler, mainThread,
              imageRotationCalculator, mBurstController, mSoundPlayer,
              new OpenCallback() {
                  @Override
                  public void onFailure() {
                      Log.e(TAG, "Could not open camera.");
                      // Sometimes the failure happens due to the controller
                      // being in paused state but mCamera is already
                      // initialized.  In these cases we just need to close the
                      // camera device without showing the error dialog.
                      // Application will properly reopen the camera on the next
                      // resume operation (b/21025113).
                      boolean isControllerPaused = mAppController.isPaused();
                      if (mCamera != null) {
                          mCamera.close();
                      }
                      mCamera = null;
                      mCameraOpenCloseLock.release();
                      if (!isControllerPaused) {
                          mAppController.getFatalErrorHandler().onCameraOpenFailure();
                      }
                  }

                  @Override
                  public void onCameraClosed() {
                      mCamera = null;
                      mCameraOpenCloseLock.release();
                  }

                  @Override
                  public void onCameraOpened(@Nonnull final OneCamera camera) {
                      Log.d(TAG, "onCameraOpened: " + camera);
                      mCamera = camera;

                      // A race condition exists where the camera may be in the process
                      // of opening (blocked), but the activity gets destroyed. If the
                      // preview is initialized or callbacks are invoked on a destroyed
                      // activity, bad things can happen.
                      if (mAppController.isPaused()) {
                          onFailure();
                          return;
                      }

                      // When camera is opened, the zoom is implicitly reset to 1.0f
                      mZoomValue = 1.0f;

                      updatePreviewBufferDimension();

                      // If the surface texture is not destroyed, it may have
                      // the last frame lingering. We need to hold off setting
                      // transform until preview is started.
                      updatePreviewBufferSize();
                      mState = ModuleState.WATCH_FOR_NEXT_FRAME_AFTER_PREVIEW_STARTED;
                      Log.d(TAG, "starting preview ...");

                      // TODO: make mFocusController final and remove null
                      // check.
                      if (mFocusController != null) {
                          camera.setFocusDistanceListener(mFocusController);
                      }

                      mMainThread.execute(new Runnable() {
                          @Override
                          public void run() {
                              mAppController.getCameraAppUI().onChangeCamera();
                              mAppController.getButtonManager().enableCameraButton();
                          }
                      });

                      // TODO: Consider rolling these two calls into one.
                      camera.startPreview(new Surface(getPreviewSurfaceTexture()),
                            new CaptureReadyCallback() {
                                @Override
                                public void onSetupFailed() {
                                    // We must release this lock here,
                                    // before posting to the main handler
                                    // since we may be blocked in pause(),
                                    // getting ready to close the camera.
                                    mCameraOpenCloseLock.release();
                                    Log.e(TAG, "Could not set up preview.");
                                    mMainThread.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (mCamera == null) {
                                                Log.d(TAG, "Camera closed, aborting.");
                                                return;
                                            }
                                            mCamera.close();
                                            mCamera = null;
                                            // TODO: Show an error message
                                            // and exit.
                                        }
                                    });
                                }

                                @Override
                                public void onReadyForCapture() {
                                    // We must release this lock here,
                                    // before posting to the main handler
                                    // since we may be blocked in pause(),
                                    // getting ready to close the camera.
                                    mCameraOpenCloseLock.release();
                                    mMainThread.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d(TAG, "Ready for capture.");
                                            if (mCamera == null) {
                                                Log.d(TAG, "Camera closed, aborting.");
                                                return;
                                            }
                                            onPreviewStarted();
                                            // May be overridden by
                                            // subsequent call to
                                            // onReadyStateChanged().
                                            onReadyStateChanged(true);
                                            mCamera.setReadyStateChangedListener(
                                                  CaptureModule.this);
                                            // Enable zooming after preview
                                            // has started.
                                            mUI.initializeZoom(mCamera.getMaxZoom());
                                            mCamera.setFocusStateListener(CaptureModule.this);
                                        }
                                    });
                                }
                            });
                  }
              }, mAppController.getFatalErrorHandler());
        guard.stop("mOneCameraOpener.open()");
    }

    private void closeCamera() {
        Profile profile = mProfiler.create("CaptureModule.closeCamera()").start();
        try {
            mCameraOpenCloseLock.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting to acquire camera-open lock.", e);
        }
        profile.mark("mCameraOpenCloseLock.acquire()");
        try {
            if (mCamera != null) {
                mCamera.close();
                profile.mark("mCamera.close()");
                mCamera.setFocusStateListener(null);
                mCamera = null;
            }
        } finally {
            mCameraOpenCloseLock.release();
        }
        profile.stop();
    }

    /**
     * Re-initialize the camera if e.g. the HDR mode or facing property changed.
     */
    private void switchCamera() {
        if (mShowErrorAndFinish) {
            return;
        }
        if (mPaused) {
            return;
        }
        cancelCountDown();
        mAppController.freezeScreenUntilPreviewReady();
        initSurfaceTextureConsumer();
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
        // According to the documentation for
        // SurfaceTexture.setDefaultBufferSize,
        // photo and video based image producers (presumably only Camera 1 api),
        // override this buffer size. Any module that uses egl to render to a
        // SurfaceTexture must have these buffer sizes reset manually. Otherwise
        // the SurfaceTexture cannot be transformed by matrix set on the
        // TextureView.
        updatePreviewBufferSize();
    }
}
