/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.captureintent;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.camera.ButtonManager;
import com.android.camera.CameraActivity;
import com.android.camera.CameraModule;
import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.async.MainThread;
import com.android.camera.captureintent.event.*;
import com.android.camera.captureintent.stateful.State;
import com.android.camera.captureintent.state.StateBackground;
import com.android.camera.captureintent.stateful.StateMachine;
import com.android.camera.captureintent.stateful.StateMachineImpl;
import com.android.camera.debug.Log;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraManager;
import com.android.camera.settings.CameraFacingSetting;
import com.android.camera.settings.ResolutionSetting;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent;

/**
 * The camera module that handles image capture intent.
 */
public class CaptureIntentModule extends CameraModule {
    private static final Log.Tag TAG = new Log.Tag("CapIntModule");

    /** The module UI. */
    private final CaptureIntentModuleUI mModuleUI;

    /** The Android application context. */
    private final Context mContext;

    /** The camera manager. */
    private final OneCameraManager mCameraManager;

    /** The app settings manager. */
    private final SettingsManager mSettingsManager;

    /** The module state machine. */
    private final StateMachine mStateMachine;

    /** The setting scope namespace. */
    private final String mSettingScopeNamespace;

    /** The app controller. */
    // TODO: Put this in the end and hope one day we can get rid of it.
    private final AppController mAppController;

    public CaptureIntentModule(AppController appController, Intent intent,
            String settingScopeNamespace) {
        super(appController);
        mModuleUI = new CaptureIntentModuleUI(
                appController.getCameraAppUI(),
                appController.getModuleLayoutRoot(),
                mUIListener);
        mContext = appController.getAndroidContext();
        mCameraManager = appController.getCameraManager();
        mSettingsManager = appController.getSettingsManager();
        mStateMachine = new StateMachineImpl();
        mSettingScopeNamespace = settingScopeNamespace;
        mAppController = appController;

        // Set the initial state.
        final CameraFacingSetting cameraFacingSetting = new CameraFacingSetting(
                mContext.getResources(), mSettingsManager, mSettingScopeNamespace);
        final ResolutionSetting resolutionSetting = new ResolutionSetting(
                mSettingsManager, mCameraManager);
        final State initialState = StateBackground.create(
                intent, mStateMachine, mModuleUI, MainThread.create(), mContext, mCameraManager,
                appController.getLocationManager(), appController.getOrientationManager(),
                cameraFacingSetting, resolutionSetting, appController);
        mStateMachine.setInitialState(initialState);
    }

    @Override
    public void onCameraAvailable(CameraAgent.CameraProxy cameraProxy) {
        // Do nothing for capture intent.
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // Do nothing for capture intent.
    }

    @Override
    public void onShutterCoordinate(TouchCoordinate coord) {
        // Do nothing for capture intent.
    }

    @Override
    public void onShutterButtonClick() {
        mStateMachine.processEvent(new EventTapOnShutterButton());
    }

    @Override
    public void onShutterButtonLongPressed() {
        // Do nothing for capture intent.
    }

    @Override
    public void init(
            final CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent) {
        mAppController.setPreviewStatusListener(mPreviewStatusListener);

        // Issue cancel countdown event when the button is pressed.
        // TODO: Make this part of the official API the way shutter button events are.
        mAppController.getCameraAppUI().setCancelShutterButtonListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStateMachine.processEvent(new EventTapOnCancelShutterButton());
            }
        });

    }

    @Override
    public void resume() {
        mModuleUI.onModuleResumed();
        mAppController.addPreviewAreaSizeChangedListener(mModuleUI);
        mAppController.getCameraAppUI().onChangeCamera();
        mStateMachine.processEvent(new EventResume());
    }

    @Override
    public void pause() {
        mModuleUI.setCountdownFinishedListener(null);
        mAppController.removePreviewAreaSizeChangedListener(mModuleUI);
        mModuleUI.onModulePaused();
        mStateMachine.processEvent(new EventPause());
    }

    @Override
    public void destroy() {
        // Never called. Do nothing here.
    }

    @Override
    public void onPreviewVisibilityChanged(int visibility) {
        // Do nothing.
    }

    @Override
    public void onLayoutOrientationChanged(boolean isLandscape) {
        // Do nothing.
    }

    @Override
    public void hardResetSettings(SettingsManager settingsManager) {
        // Do nothing.
    }

    @Override
    public HardwareSpec getHardwareSpec() {
        final CameraFacingSetting cameraFacingSetting = new CameraFacingSetting(
                mContext.getResources(), mSettingsManager, mSettingScopeNamespace);
        final OneCameraCharacteristics characteristics;
        try {
            characteristics = mCameraManager.getCameraCharacteristics(
                    cameraFacingSetting.getCameraFacing());
        } catch (OneCameraAccessException ocae) {
            mAppController.showErrorAndFinish(R.string.cannot_connect_camera);
            return null;
        }

        return new HardwareSpec() {
            @Override
            public boolean isFrontCameraSupported() {
                return mCameraManager.hasCameraFacing(OneCamera.Facing.FRONT);
            }

            @Override
            public boolean isHdrSupported() {
                return false;
            }

            @Override
            public boolean isHdrPlusSupported() {
                return false;
            }

            @Override
            public boolean isFlashSupported() {
                return characteristics.isFlashSupported();
            }
        };
    }

    @Override
    public CameraAppUI.BottomBarUISpec getBottomBarSpec() {
        CameraAppUI.BottomBarUISpec bottomBarSpec = new CameraAppUI.BottomBarUISpec();
        /** Camera switch button UI spec. */
        bottomBarSpec.enableCamera = true;
        bottomBarSpec.cameraCallback = new ButtonManager.ButtonCallback() {
            @Override
            public void onStateChanged(int cameraId) {
                mStateMachine.processEvent(new EventTapOnSwitchCameraButton());
            }
        };
        /** Grid lines button UI spec. */
        bottomBarSpec.enableGridLines = true;
        /** HDR button UI spec. */
        bottomBarSpec.enableHdr = false;
        bottomBarSpec.hideHdr = true;
        bottomBarSpec.hdrCallback = null;
        /** Timer button UI spec. */
        bottomBarSpec.enableSelfTimer = true;
        bottomBarSpec.showSelfTimer = true;
        /** Flash button UI spec. */
        bottomBarSpec.enableFlash = true;
        bottomBarSpec.hideFlash = false;

        /** Intent image review UI spec. */
        bottomBarSpec.showCancel = true;
        bottomBarSpec.cancelCallback = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStateMachine.processEvent(new EventTapOnCancelIntentButton());
            }
        };
        bottomBarSpec.showDone = true;
        bottomBarSpec.doneCallback = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStateMachine.processEvent(new EventTapOnConfirmPhotoButton());
            }
        };
        bottomBarSpec.showRetake = true;
        bottomBarSpec.retakeCallback = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStateMachine.processEvent(new EventTapOnRetakePhotoButton());
            }
        };
        return bottomBarSpec;
    }

    @Override
    public boolean isUsingBottomBar() {
        return true;
    }

    @Override
    public String getPeekAccessibilityString() {
        return mContext.getResources().getString(R.string.photo_accessibility_peek);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    /** The listener to listen events from the UI. */
    private final CaptureIntentModuleUI.Listener mUIListener =
            new CaptureIntentModuleUI.Listener() {
                @Override
                public void onZoomRatioChanged(final float zoomRatio) {
                    mStateMachine.processEvent(new EventZoomRatioChanged(zoomRatio));
                }
            };

    /** The listener to listen events from the preview. */
    private final PreviewStatusListener mPreviewStatusListener = new PreviewStatusListener() {
        @Override
        public void onPreviewLayoutChanged(View v, int left, int top, int right,
                int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            final Size previewLayoutSize = new Size(right - left, bottom - top);
            mStateMachine.processEvent(new EventOnTextureViewLayoutChanged(previewLayoutSize));
        }

        @Override
        public boolean shouldAutoAdjustTransformMatrixOnLayout() {
            return false;
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
                    final Point tapPoint = new Point((int) ev.getX(), (int) ev.getY());
                    mStateMachine.processEvent(new EventTapOnPreview(tapPoint));
                    return true;
                }
            };
        }

        @Override
        public View.OnTouchListener getTouchListener() {
            return null;
        }

        @Override
        public void onSurfaceTextureAvailable(
                final SurfaceTexture surfaceTexture, int width, int height) {
            mStateMachine.processEvent(new EventOnSurfaceTextureAvailable(surfaceTexture));
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            mStateMachine.processEvent(new EventOnSurfaceTextureDestroyed());
            return true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(
                SurfaceTexture surfaceTexture, int width, int height) {
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            mStateMachine.processEvent(new EventOnSurfaceTextureUpdated());
        }
    };
}
