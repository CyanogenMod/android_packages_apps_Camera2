/*
 * Copyright (C) 2014 The CyanogenMod Project
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
 * limitations under the License
 */
package com.android.camera;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.android.camera.app.CameraAppUI;
import com.android.camera.app.CameraController;
import com.android.camera.app.CameraProvider;
import com.android.camera.app.CameraServices;
import com.android.camera.app.LocationManager;
import com.android.camera.app.ModuleManager;
import com.android.camera.app.ModuleManagerImpl;
import com.android.camera.app.OrientationManager;
import com.android.camera.debug.Log;
import com.android.camera.module.ModuleController;
import com.android.camera.module.ModulesInfo;
import com.android.camera.one.OneCameraManager;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.AbstractTutorialOverlay;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.util.GcamHelper;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent;
import com.android.ex.camera2.portability.CameraAgentFactory;
import com.android.ex.camera2.portability.CameraSettings;

public class CameraService extends Service implements ICameraActivity, CameraAgent.CameraOpenCallback, CameraServiceSensor.IRotationListener {
    private static final Log.Tag TAG = new Log.Tag("CameraService");

    protected ModuleManager mModuleManager;
    protected VideoModule mCurrentModule;
    protected LocationManager mLocationManager;
    protected int mCurrentModeIndex;

    private int mServiceStartId = -1;
    static int sSavedCameraId = -1;
    private AlarmManager mAlarmManager;
    private PendingIntent mShutdownIntent;
    private boolean mShutdownScheduled;
    private SurfaceView mSurfaceView;

    public Handler mMainHandler;

    public CameraController mCameraController;
    private SurfaceTexture mSurfaceTexture;
    private CameraServiceSensor mCameraSensor;

    private boolean mRecordRequested;
    private boolean mPreviewReady;

    private final IBinder mBinder = new LocalBinder();

    private static final int IDLE_DELAY = 8 * 1000;


    public static final String SECURE_CAMERA_EXTRA = "secure_camera";

    public static final String MODULE_SCOPE_PREFIX = "_preferences_module_";
    public static final String CAMERA_SCOPE_PREFIX = "_preferences_camera_";

    private static final String START = "com.android.camera2.start";
    private static final String RECORD = "com.android.camera2.record";
    private static final String SHUTDOWN = "com.android.camera2.shutdown";

    private final CameraAgent.CameraExceptionCallback mCameraDefaultExceptionCallback
            = new CameraAgent.CameraExceptionCallback() {
        @Override
        public void onCameraException(RuntimeException e) {
            Log.e(TAG, "Camera Exception", e);
        }
    };

    protected void loadModules() {
        mModuleManager = ModuleManagerImpl.getInstance();
        GcamHelper.init(getContentResolver());
        ModulesInfo.setupModules(this, mModuleManager);

        mCurrentModeIndex = getResources()
                .getInteger(R.integer.camera_mode_video);
        for (ModuleManager.ModuleAgent agent : mModuleManager.getRegisteredModuleAgents()) {
            if (agent.getModuleId() == mCurrentModeIndex) {
                mCurrentModule = (VideoModule) agent.createModule(this);
                mCurrentModule.initHack(this, false, false);
            }
        }
    }

    protected void loadLocationManager() {
        mLocationManager = new LocationManager(getAndroidContext());
    }

    protected void loadCameraController() {
        mMainHandler = new Handler(getMainLooper());
        mCameraController = new CameraController(this, this, mMainHandler,
                CameraAgentFactory.getAndroidCameraAgent(this, CameraAgentFactory.CameraApi.API_1),
                CameraAgentFactory.getAndroidCameraAgent(this, CameraAgentFactory.CameraApi.AUTO));
        mCameraController.setCameraDefaultExceptionCallback(mCameraDefaultExceptionCallback,
                mMainHandler);
    }

    protected void loadDummySurface() {
        mSurfaceView = new SurfaceView(this);
    }

    @Override
    public Context getAndroidContext() {
        return getApplicationContext();
    }

    @Override
    public int getCurrentCameraId() {
        return mCameraController.getCurrentCameraId();
    }

    @Override
    public String getModuleScope() {
        return MODULE_SCOPE_PREFIX + mCurrentModule.getModuleStringIdentifier();
    }

    @Override
    public String getCameraScope() {
        int currentCameraId = getCurrentCameraId();
        if (currentCameraId < 0) {
            currentCameraId = sSavedCameraId;
            sSavedCameraId = sSavedCameraId - 1;
            // if an unopen camera i.e. negative ID is returned, which we've observed in
            // some automated scenarios, just return it as a valid separate scope
            // this could cause user issues, so log a stack trace noting the call path
            // which resulted in this scenario.
            Log.w(TAG, "getting camera scope with no open camera, using id: " + currentCameraId);
        }
        return CAMERA_SCOPE_PREFIX + Integer.toString(currentCameraId);
    }

    @Override
    public void launchActivityByIntent(Intent intent) {
        Log.w(TAG, "Launching Intent: " + intent + " not supported");
    }

    @Override
    public void openContextMenu(View view) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void registerForContextMenu(View view) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public ModuleController getCurrentModuleController() {
        return mCurrentModule;
    }

    @Override
    public int getCurrentModuleIndex() {
        return mCurrentModeIndex;
    }

    @Override
    public int getQuickSwitchToModuleId(int currentModuleIndex) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public int getPreferredChildModeIndex(int modeIndex) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void onModeSelected(int moduleIndex) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void onSettingsSelected() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void freezeScreenUntilPreviewReady() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public SurfaceTexture getPreviewBuffer() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void onPreviewReadyToStart() {
        Log.w(TAG, "onPreviewReadyToStart doing nothing");
    }

    @Override
    public void onPreviewStarted() {
        mPreviewReady = true;
        Log.w(TAG, "onPreviewStarted begin recording");
        final Intent recordIntent = new Intent(this, CameraService.class);
        recordIntent.setAction(RECORD);
        startService(recordIntent);
    }

    @Override
    public void addPreviewAreaSizeChangedListener(PreviewStatusListener.PreviewAreaChangedListener listener) {
        Log.w(TAG, "addPreviewAreaSizeChangedListener doing nothing");
    }

    @Override
    public void removePreviewAreaSizeChangedListener(PreviewStatusListener.PreviewAreaChangedListener listener) {
        Log.w(TAG, "removePreviewAreaSizeChangedListener doing nothing");
    }

    @Override
    public void setupOneShotPreviewListener() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void updatePreviewAspectRatio(float aspectRatio) {
        Log.w(TAG, "updatePreviewAspectRatio doing nothing");
    }

    @Override
    public void updatePreviewTransformFullscreen(Matrix matrix, float aspectRatio) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public RectF getFullscreenRect() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void updatePreviewTransform(Matrix matrix) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void setPreviewStatusListener(PreviewStatusListener previewStatusListener) {
        Log.w(TAG, "setPreviewStatusListener doing nothing");
    }

    @Override
    public FrameLayout getModuleLayoutRoot() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void lockOrientation() {
        Log.w(TAG, "lockOrientation doing nothing");
    }

    @Override
    public void unlockOrientation() {
        Log.w(TAG, "unlockOrientation doing nothing");
    }

    @Override
    public void setShutterEventsListener(ShutterEventsListener listener) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void setShutterEnabled(boolean enabled) {
        Log.w(TAG, "setShutterEnabled doing nothing");
    }

    @Override
    public boolean isShutterEnabled() {
        Log.w(TAG, "isShutterEnabled doing nothing");
        return false;
    }

    @Override
    public void startPreCaptureAnimation(boolean shortFlash) {
        Log.w(TAG, "startPreCaptureAnimation doing nothing");
    }

    @Override
    public void startPreCaptureAnimation() {
        Log.w(TAG, "startPreCaptureAnimation doing nothing");
    }

    @Override
    public void cancelPreCaptureAnimation() {
        Log.w(TAG, "cancelPreCaptureAnimation doing nothing");
    }

    @Override
    public void startPostCaptureAnimation() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void startPostCaptureAnimation(Bitmap thumbnail) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void cancelPostCaptureAnimation() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void notifyNewMedia(Uri uri) {
        Log.w(TAG, "notifyNewMedia doing nothing");
    }

    @Override
    public void enableKeepScreenOn(boolean enabled) {
        Log.w(TAG, "enableKeepScreenOn doing nothing");
    }

    @Override
    public CameraProvider getCameraProvider() {
        return mCameraController;
    }

    @Override
    public OneCameraManager getCameraManager() {
        return null;
    }

    @Override
    public OrientationManager getOrientationManager() {
        return null;
    }

    @Override
    public LocationManager getLocationManager() {
        return mLocationManager;
    }

    @Override
    public SettingsManager getSettingsManager() {
        return getServices().getSettingsManager();
    }

    @Override
    public CameraServices getServices() {
        return (CameraServices) getApplication();
    }

    @Override
    public CameraAppUI getCameraAppUI() {
        return null;
    }

    @Override
    public ModuleManager getModuleManager() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public ButtonManager getButtonManager() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public SoundPlayer getSoundPlayer() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public boolean isAutoRotateScreen() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void showTutorial(AbstractTutorialOverlay tutorial) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public void showErrorAndFinish(int messageId) {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public Intent getIntent() {
        return new Intent("");
    }

    @Override
    public CameraActivity getCameraActivity() {
        return null;
    }

    @Override
    public boolean isCameraActivity() {
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Got intent: " + intent);
        mServiceStartId = startId;

        if (intent != null) {
            final String action = intent.getAction();

            if (SHUTDOWN.equals(action)) {
                prepShutdown();
            } else if (RECORD.equals(action)) {
                beginRecording();
                scheduleDelayedShutdown();
            }
        }


        return START_STICKY;
    }

    private void scheduleDelayedShutdown() {
        cancelShutdown();

        Log.d(TAG, "Scheduling shutdown in " + IDLE_DELAY + " ms");
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + IDLE_DELAY, mShutdownIntent);
        mShutdownScheduled = true;
    }

    private void cancelShutdown() {
        Log.d(TAG, "Cancelling delayed shutdown, scheduled = " + mShutdownScheduled);
        if (mShutdownScheduled) {
            mAlarmManager.cancel(mShutdownIntent);
            mShutdownScheduled = false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mRecordRequested = false;
        mPreviewReady = false;

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);

        loadLocationManager();
        loadDummySurface();
        loadCameraController();
        loadModules();

        // Initialize the delayed shutdown intent
        final Intent shutdownIntent = new Intent(this, CameraService.class);
        shutdownIntent.setAction(SHUTDOWN);

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mShutdownIntent = PendingIntent.getService(this, 0, shutdownIntent, 0);

        mCameraSensor = new CameraServiceSensor(this, this);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mPreviewReady = false;
        mRecordRequested = false;

        mCameraSensor.destroy();
        mCameraSensor = null;

        // remove any pending alarms
        mAlarmManager.cancel(mShutdownIntent);

        if (mCameraController != null) {
            mCameraController.closeCamera(false);
            mCameraController.removeCallbackReceiver();
        }

        if (mCurrentModule != null && mCurrentModule.isRecording()) {
            mCurrentModule.onShutterButtonClick();
        }

        mLocationManager = null;
        mCurrentModule = null;
        mCurrentModeIndex = -1;
        mCameraController = null;
    }

    @Override
    public void onCameraOpened(CameraAgent.CameraProxy camera) {
        if (!mModuleManager.getModuleAgent(mCurrentModeIndex).requestAppForCamera()) {
            // We shouldn't be here. Just close the camera and leave.
            mCameraController.closeCamera(false);
            throw new IllegalStateException("Camera opened but the module shouldn't be " +
                    "requesting");
        }

        if (mCurrentModule != null) {
            resetExposureCompensationToDefault(camera);
            mCurrentModule.onCameraAvailable(camera);
        } else {
            Log.v(TAG, "mCurrentModule null, not invoking onCameraAvailable");
        }
    }

    private void resetExposureCompensationToDefault(CameraAgent.CameraProxy camera) {
        // Reset the exposure compensation before handing the camera to module.
        CameraSettings cameraSettings = camera.getSettings();
        cameraSettings.setExposureCompensationIndex(0);
        camera.applySettings(cameraSettings);
    }

    @Override
    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceView.getHolder();
    }

    public SurfaceTexture getSurfaceTexture() {
        if (mSurfaceTexture == null) {
            int textures[] = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            int width = 1024; // size of preview
            int height = 1776;  // size of preview
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width,
                    height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

            mSurfaceTexture = new SurfaceTexture(textures[0]);
            mSurfaceTexture.setDefaultBufferSize(width, height);
        }

        return mSurfaceTexture;
    }

    @Override
    public void onCameraDisabled(int cameraId) {
        Log.w(TAG, "Camera disabled: " + cameraId);
    }

    @Override
    public void onDeviceOpenFailure(int cameraId, String info) {
        Log.w(TAG, "Camera open failure: " + info);
    }

    @Override
    public void onDeviceOpenedAlready(int cameraId, String info) {
        Log.w(TAG, "Camera open already: " + cameraId + "," + info);
        beginRecording();
    }

    @Override
    public void onReconnectionFailure(CameraAgent mgr, String info) {
        Log.w(TAG, "Camera reconnection failure:" + info);
    }

    public boolean isRecording() {
        return mCurrentModule != null && mCurrentModule.isRecording();
    }

    private void beginRecording() {
        if (mRecordRequested && mPreviewReady) {
            Log.w(TAG, "beginRecording");
            if (mCurrentModule != null && !mCurrentModule.isRecording()) {
                mCurrentModule.onShutterButtonClick();
            }
        }
    }

    private void stopRecording() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCurrentModule != null && mCurrentModule.isRecording()) {
                    mCurrentModule.onShutterButtonClick();
                    Log.d(new Log.Tag("Linus"), "Stopping Recording");
                }
            }
        });
    }

    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                Log.d(TAG, "Got ACTION_SCREEN_ON");
                mRecordRequested = true;
                checkStartup();
                mCameraSensor.resume();
                scheduleDelayedShutdown();
            } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mRecordRequested = false;
                Log.d(TAG, "Got ACTION_SCREEN_OFF");
                prepShutdown();
                // TODO: Add wake lock until it completes

            }
        }
    };

    private void prepShutdown() {
        stopRecording();
        cancelShutdown();
        mCameraSensor.pause();
    }

    @Override
    public void onRotationChanged(boolean satisfies) {
        if (satisfies) {
            beginRecording();
        } else {
            stopRecording();
        }
    }

    public class LocalBinder extends Binder {
        CameraService getService() {
            return CameraService.this;
        }
    }

    public void clear() {
        Log.d(new Log.Tag("Linus"), "---Clear!");
        mCameraController = null;
        mCurrentModule = null;
        mSurfaceTexture = null;
        mRecordRequested = false;
        mPreviewReady = false;
        cancelShutdown();
        mCameraSensor.pause();
    }

    public void checkStartup() {
        if (mCurrentModule == null) {
            loadDummySurface();

            mModuleManager.getModuleAgent(mCurrentModeIndex);

            mCameraController = new CameraController(this, this, mMainHandler,
                    CameraAgentFactory.getAndroidCameraAgent(this, CameraAgentFactory.CameraApi.API_1),
                    CameraAgentFactory.getAndroidCameraAgent(this, CameraAgentFactory.CameraApi.AUTO));
            mCameraController.setCameraDefaultExceptionCallback(mCameraDefaultExceptionCallback,
                    mMainHandler);

            for (ModuleManager.ModuleAgent agent : mModuleManager.getRegisteredModuleAgents()) {
                if (agent.getModuleId() == mCurrentModeIndex) {
                    mCurrentModule = (VideoModule) agent.createModule(this);
                    mCurrentModule.initHack(this, false, false);
                }
            }
        }
    }
}
