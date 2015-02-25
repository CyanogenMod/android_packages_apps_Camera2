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

import com.google.common.base.Optional;
import com.android.camera.ButtonManager;
import com.android.camera.CameraActivity;
import com.android.camera.CameraModule;
import com.android.camera.SoundPlayer;
import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.LocationManager;
import com.android.camera.async.MainThread;
import com.android.camera.captureintent.state.Event;
import com.android.camera.captureintent.state.State;
import com.android.camera.captureintent.state.StateBackground;
import com.android.camera.captureintent.state.StateMachine;
import com.android.camera.debug.Log;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.hardware.HeadingSensor;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.AutoFocusState;
import com.android.camera.one.OneCamera.FocusStateListener;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraManager;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.session.CaptureSessionManagerImpl;
import com.android.camera.session.SessionStorageManager;
import com.android.camera.session.SessionStorageManagerImpl;
import com.android.camera.settings.CameraFacingSetting;
import com.android.camera.settings.ResolutionSetting;
import com.android.camera.settings.SettingsManager;
import com.android.camera.ui.CountDownView.OnCountDownStatusListener;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

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

    /** The heading sensor. */
    private final HeadingSensor mHeadingSensor;

    /** The app settings manager. */
    private final SettingsManager mSettingsManager;

    /** The location manager. */
    private final LocationManager mLocationManager;

    /** The sound player. */
    private final SoundPlayer mSoundPlayer;

    /** The capture session manager. */
    private final CaptureSessionManager mCaptureSessionManager;

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
        mHeadingSensor = new HeadingSensor(AndroidServices.instance().provideSensorManager());
        mSettingsManager = appController.getSettingsManager();
        mLocationManager = appController.getLocationManager();
        mSoundPlayer = new SoundPlayer(mContext);
        mCaptureSessionManager = new CaptureSessionManagerImpl(
                new CaptureIntentSessionFactory(),
                SessionStorageManagerImpl.create(mContext),
                MainThread.create());
        mStateMachine = new StateMachine();
        mSettingScopeNamespace = settingScopeNamespace;
        mAppController = appController;

        // Set the initial state.
        final CameraFacingSetting cameraFacingSetting = new CameraFacingSetting(
                mContext.getResources(), mSettingsManager, mSettingScopeNamespace);
        final ResolutionSetting resolutionSetting = new ResolutionSetting(
                mSettingsManager, mCameraManager);
        final State initialState = StateBackground.create(
                intent, mStateMachine, mModuleUI, MainThread.create(), mContext, mCameraManager,
                appController.getOrientationManager(), cameraFacingSetting, resolutionSetting,
                appController);
        mStateMachine.jumpToState(initialState);
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
        mStateMachine.processEvent(new Event() {
            @Override
            public Optional<State> apply(State state) {
                return state.processOnShutterButtonClicked();
            }
        });
    }

    @Override
    public void onShutterButtonLongPressed() {
        // Do nothing for capture intent.
    }

    @Override
    public void init(
            final CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent) {
        // Do nothing.
    }

    @Override
    public void resume() {
        mModuleUI.onModuleResumed();
        mAppController.setPreviewStatusListener(mPreviewStatusListener);
        mAppController.addPreviewAreaSizeChangedListener(mModuleUI);
        mCaptureSessionManager.addSessionListener(mCaptureSessionListener);
        mHeadingSensor.activate();
        mSoundPlayer.loadSound(R.raw.timer_final_second);
        mSoundPlayer.loadSound(R.raw.timer_increment);
        mModuleUI.setCountdownFinishedListener(mOnCountDownStatusListener);

        mStateMachine.processEvent(new Event() {
            @Override
            public Optional<State> apply(State state) {
                return state.processResume();
            }
        });
    }

    @Override
    public void pause() {
        mModuleUI.setCountdownFinishedListener(null);
        mSoundPlayer.unloadSound(R.raw.timer_increment);
        mSoundPlayer.unloadSound(R.raw.timer_final_second);
        mHeadingSensor.deactivate();
        mCaptureSessionManager.removeSessionListener(mCaptureSessionListener);
        mAppController.removePreviewAreaSizeChangedListener(mModuleUI);
        mAppController.setPreviewStatusListener(null);
        mModuleUI.onModulePaused();

        mStateMachine.processEvent(new Event() {
            @Override
            public Optional<State> apply(State state) {
                return state.processPause();
            }
        });
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
                mStateMachine.processEvent(new Event() {
                    @Override
                    public Optional<State> apply(State state) {
                        return state.processOnSwitchButtonClicked();
                    }
                });
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
        bottomBarSpec.enableFlash = false;
        bottomBarSpec.hideFlash = true;

        /** Intent image review UI spec. */
        bottomBarSpec.showCancel = true;
        bottomBarSpec.cancelCallback = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStateMachine.processEvent(new Event() {
                    @Override
                    public Optional<State> apply(State state) {
                        return state.processOnCancelButtonClicked();
                    }
                });
            }
        };
        bottomBarSpec.showDone = true;
        bottomBarSpec.doneCallback = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStateMachine.processEvent(new Event() {
                    @Override
                    public Optional<State> apply(State state) {
                        return state.processOnDoneButtonClicked();
                    }
                });
            }
        };
        bottomBarSpec.showRetake = true;
        bottomBarSpec.retakeCallback = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStateMachine.processEvent(new Event() {
                    @Override
                    public Optional<State> apply(State state) {
                        return state.processOnRetakeButtonClicked();
                    }
                });
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
                    mStateMachine.processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnZoomRatioChanged(zoomRatio);
                        }
                    });
                }
            };

    /** The listener to listen events from the preview. */
    private final PreviewStatusListener mPreviewStatusListener = new PreviewStatusListener() {
        @Override
        public void onPreviewLayoutChanged(View v, int left, int top, int right,
                int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            final Size previewLayoutSize = new Size(right - left, bottom - top);
            mStateMachine.processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnTextureViewLayoutChanged(previewLayoutSize);
                }
            });
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
                    mStateMachine.processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnSingleTapOnPreview(tapPoint);
                        }
                    });
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
            mStateMachine.processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnSurfaceTextureAvailable(
                            surfaceTexture, mCameraOpenCallback);
                }
            });
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(
                SurfaceTexture surfaceTexture, int width, int height) {
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private OneCamera.CaptureReadyCallback mCaptureReadyCallback =
            new OneCamera.CaptureReadyCallback() {
        @Override
        public void onSetupFailed() {
            mStateMachine.processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnPreviewSetupFailed();
                }
            });
        }

        @Override
        public void onReadyForCapture() {
            mStateMachine.processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnPreviewSetupSucceeded(
                            mCaptureSessionManager, mLocationManager, mHeadingSensor, mSoundPlayer,
                            mReadyStateChangedListener, mPictureCallback, mPictureSaverCallback,
                            mFocusStateListener);
                }
            });
        }
    };

    private OneCamera.OpenCallback mCameraOpenCallback = new OneCamera.OpenCallback() {
        @Override
        public void onFailure() {
            mStateMachine.processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnCameraOpenFailure();
                }
            });
        }

        @Override
        public void onCameraClosed() {
            // Not used anymore.
        }

        @Override
        public void onCameraOpened(final OneCamera camera) {
            mStateMachine.processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnCameraOpened(camera, mCaptureReadyCallback);
                }
            });
        }
    };

    private final OneCamera.ReadyStateChangedListener mReadyStateChangedListener =
            new OneCamera.ReadyStateChangedListener() {
                /**
                 * Called when the camera is either ready or not ready to take a picture
                 * right now.
                 */
                @Override
                public void onReadyStateChanged(final boolean readyForCapture) {
                    mStateMachine.processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnReadyStateChanged(readyForCapture);
                        }
                    });
                }
            };

    private final OneCamera.PictureSaverCallback mPictureSaverCallback =
            new OneCamera.PictureSaverCallback() {
                @Override
                public void onRemoteThumbnailAvailable(byte[] jpegImage) {
                }
            };

    private final OneCamera.PictureCallback mPictureCallback = new OneCamera.PictureCallback() {
        @Override
        public void onQuickExpose() {
            mStateMachine.processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnQuickExpose();
                }
            });
        }

        @Override
        public void onThumbnailResult(byte[] jpegData) {
        }

        @Override
        public void onPictureTaken(CaptureSession session) {
        }

        @Override
        public void onPictureSaved(Uri uri) {
        }

        @Override
        public void onPictureTakingFailed() {
        }

        @Override
        public void onTakePictureProgress(float progress) {
        }
    };

    private final FocusStateListener mFocusStateListener = new FocusStateListener() {
        @Override
        public void onFocusStatusUpdate(final AutoFocusState focusState, final long frameNumber) {
            mStateMachine.processEvent(new Event() {
                @Override
                public Optional<State> apply(State state) {
                    return state.processOnFocusStateUpdated(focusState, frameNumber);
                }
            });
        }
    };

    private final CaptureSessionManager.SessionListener mCaptureSessionListener =
            new CaptureSessionManager.SessionListener() {
                @Override
                public void onSessionThumbnailUpdate(final Bitmap thumbnailBitmap) {
                    mStateMachine.processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnPictureBitmapAvailable(thumbnailBitmap);
                        }
                    });
                }

                @Override
                public void onSessionPictureDataUpdate(
                        final byte[] pictureData, final int orientation) {
                    mStateMachine.processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnPictureCompressed(pictureData, orientation);
                        }
                    });
                }

                @Override
                public void onSessionQueued(Uri sessionUri) {
                }

                @Override
                public void onSessionUpdated(Uri sessionUri) {
                }

                @Override
                public void onSessionCaptureIndicatorUpdate(Bitmap bitmap, int rotationDegrees) {
                }

                @Override
                public void onSessionDone(Uri sessionUri) {
                }

                @Override
                public void onSessionFailed(Uri sessionUri, CharSequence reason) {
                }

                @Override
                public void onSessionProgress(Uri sessionUri, int progress) {
                }

                @Override
                public void onSessionProgressText(Uri sessionUri, CharSequence message) {
                }
            };

    private final OnCountDownStatusListener mOnCountDownStatusListener =
            new OnCountDownStatusListener() {
                @Override
                public void onRemainingSecondsChanged(int remainingSeconds) {
                    if (remainingSeconds == 1) {
                        mSoundPlayer.play(R.raw.timer_final_second, 0.6f);
                    } else if (remainingSeconds == 2 || remainingSeconds == 3) {
                        mSoundPlayer.play(R.raw.timer_increment, 0.6f);
                    }
                }

                @Override
                public void onCountDownFinished() {
                    mStateMachine.processEvent(new Event() {
                        @Override
                        public Optional<State> apply(State state) {
                            return state.processOnCountDownFinished();
                        }
                    });
                }
            };
}
