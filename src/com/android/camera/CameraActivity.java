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

import android.animation.Animator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.CameraPerformanceTracker;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ShareActionProvider;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.CameraController;
import com.android.camera.app.CameraProvider;
import com.android.camera.app.CameraServices;
import com.android.camera.app.LocationManager;
import com.android.camera.app.MemoryManager;
import com.android.camera.app.MemoryQuery;
import com.android.camera.app.ModuleManager;
import com.android.camera.app.ModuleManagerImpl;
import com.android.camera.app.MotionManager;
import com.android.camera.app.OrientationManager;
import com.android.camera.app.OrientationManagerImpl;
import com.android.camera.data.CameraDataAdapter;
import com.android.camera.data.FixedLastDataAdapter;
import com.android.camera.data.LocalData;
import com.android.camera.data.LocalDataAdapter;
import com.android.camera.data.LocalDataUtil;
import com.android.camera.data.LocalDataViewType;
import com.android.camera.data.LocalMediaData;
import com.android.camera.data.LocalMediaObserver;
import com.android.camera.data.LocalSessionData;
import com.android.camera.data.MediaDetails;
import com.android.camera.data.MetadataLoader;
import com.android.camera.data.PanoramaMetadataLoader;
import com.android.camera.data.RgbzMetadataLoader;
import com.android.camera.data.SimpleViewData;
import com.android.camera.debug.Log;
import com.android.camera.filmstrip.FilmstripContentPanel;
import com.android.camera.filmstrip.FilmstripController;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.hardware.HardwareSpecImpl;
import com.android.camera.module.ModuleController;
import com.android.camera.module.ModulesInfo;
import com.android.camera.one.OneCameraException;
import com.android.camera.one.OneCameraManager;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.session.CaptureSessionManager.SessionListener;
import com.android.camera.settings.AppUpgrader;
import com.android.camera.settings.CameraSettingsActivity;
import com.android.camera.settings.Keys;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsUtil;
import com.android.camera.tinyplanet.TinyPlanetFragment;
import com.android.camera.ui.AbstractTutorialOverlay;
import com.android.camera.ui.DetailsDialog;
import com.android.camera.ui.MainActivityLayout;
import com.android.camera.ui.ModeListView;
import com.android.camera.ui.ModeListView.ModeListVisibilityChangedListener;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Callback;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GalleryHelper;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.GoogleHelpHelper;
import com.android.camera.util.IntentHelper;
import com.android.camera.util.PhotoSphereHelper.PanoramaViewHelper;
import com.android.camera.util.QuickActivity;
import com.android.camera.util.ReleaseHelper;
import com.android.camera.util.UsageStatistics;
import com.android.camera.widget.FilmstripView;
import com.android.camera.widget.Preloader;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent;
import com.android.ex.camera2.portability.CameraAgentFactory;
import com.android.ex.camera2.portability.CameraExceptionHandler;
import com.android.ex.camera2.portability.CameraSettings;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.MemoryCategory;
import com.bumptech.glide.load.engine.executor.FifoPriorityThreadPoolExecutor;
import com.google.common.logging.eventprotos;
import com.google.common.logging.eventprotos.ForegroundEvent.ForegroundSource;
import com.google.common.logging.eventprotos.MediaInteraction;
import com.google.common.logging.eventprotos.NavigationChange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends QuickActivity
        implements AppController, CameraAgent.CameraOpenCallback,
        ShareActionProvider.OnShareTargetSelectedListener,
        OrientationManager.OnOrientationChangeListener {

    private static final Log.Tag TAG = new Log.Tag("CameraActivity");

    private static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE =
            "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE =
            "android.media.action.IMAGE_CAPTURE_SECURE";

    // The intent extra for camera from secure lock screen. True if the gallery
    // should only show newly captured pictures. sSecureAlbumId does not
    // increment. This is used when switching between camera, camcorder, and
    // panorama. If the extra is not set, it is in the normal camera mode.
    public static final String SECURE_CAMERA_EXTRA = "secure_camera";

    public static final String MODULE_SCOPE_PREFIX = "_preferences_module_";
    public static final String CAMERA_SCOPE_PREFIX = "_preferences_camera_";

    private static final int MSG_CLEAR_SCREEN_ON_FLAG = 2;
    private static final long SCREEN_DELAY_MS = 2 * 60 * 1000; // 2 mins.
    private static final int MAX_PEEK_BITMAP_PIXELS = 1600000; // 1.6 * 4 MBs.
    /** Load metadata for 10 items ahead of our current. */
    private static final int FILMSTRIP_PRELOAD_AHEAD_ITEMS = 10;

    /** Should be used wherever a context is needed. */
    private Context mAppContext;

    /**
     * Camera fatal error handling:
     * 1) Present error dialog to guide users to exit the app.
     * 2) If users hit home button, onPause should just call finish() to exit the app.
     */
    private boolean mCameraFatalError = false;

    /**
     * Whether onResume should reset the view to the preview.
     */
    private boolean mResetToPreviewOnResume = true;

    /**
     * This data adapter is used by FilmStripView.
     */
    private LocalDataAdapter mDataAdapter;

    private OneCameraManager mCameraManager;
    private SettingsManager mSettingsManager;
    private ModeListView mModeListView;
    private boolean mModeListVisible = false;
    private int mCurrentModeIndex;
    private CameraModule mCurrentModule;
    private ModuleManagerImpl mModuleManager;
    private FrameLayout mAboveFilmstripControlLayout;
    private FilmstripController mFilmstripController;
    private boolean mFilmstripVisible;
    /** Whether the filmstrip fully covers the preview. */
    private boolean mFilmstripCoversPreview = false;
    private int mResultCodeForTesting;
    private Intent mResultDataForTesting;
    private OnScreenHint mStorageHint;
    private final Object mStorageSpaceLock = new Object();
    private long mStorageSpaceBytes = Storage.LOW_STORAGE_THRESHOLD_BYTES;
    private boolean mAutoRotateScreen;
    private boolean mSecureCamera;
    private int mLastRawOrientation;
    private OrientationManagerImpl mOrientationManager;
    private LocationManager mLocationManager;
    private ButtonManager mButtonManager;
    private Handler mMainHandler;
    private PanoramaViewHelper mPanoramaViewHelper;
    private ActionBar mActionBar;
    private ViewGroup mUndoDeletionBar;
    private boolean mIsUndoingDeletion = false;
    private boolean mIsActivityRunning = false;

    private final Uri[] mNfcPushUris = new Uri[1];

    private LocalMediaObserver mLocalImagesObserver;
    private LocalMediaObserver mLocalVideosObserver;

    private boolean mPendingDeletion = false;

    private CameraController mCameraController;
    private boolean mPaused;
    private CameraAppUI mCameraAppUI;

    private PeekAnimationHandler mPeekAnimationHandler;
    private HandlerThread mPeekAnimationThread;

    private Intent mGalleryIntent;
    private long mOnCreateTime;

    private Menu mActionBarMenu;
    private Preloader<Integer, AsyncTask> mPreloader;

    /** Can be used to play custom sounds. */
    private SoundPlayer mSoundPlayer;

    private static final int LIGHTS_OUT_DELAY_MS = 4000;
    private final int BASE_SYS_UI_VISIBILITY =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    private final Runnable mLightsOutRunnable = new Runnable() {
        @Override
        public void run() {
            getWindow().getDecorView().setSystemUiVisibility(
                    BASE_SYS_UI_VISIBILITY | View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    };
    private MemoryManager mMemoryManager;
    private MotionManager mMotionManager;

    @Override
    public CameraAppUI getCameraAppUI() {
        return mCameraAppUI;
    }

    @Override
    public ModuleManager getModuleManager() {
        return mModuleManager;
    }

    /**
     * Close activity when secure app passes lock screen or screen turns
     * off.
     */
    private final BroadcastReceiver mShutdownReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    /**
     * Whether the screen is kept turned on.
     */
    private boolean mKeepScreenOn;
    private int mLastLayoutOrientation;
    private final CameraAppUI.BottomPanel.Listener mMyFilmstripBottomControlListener =
            new CameraAppUI.BottomPanel.Listener() {

                /**
                 * If the current photo is a photo sphere, this will launch the
                 * Photo Sphere panorama viewer.
                 */
                @Override
                public void onExternalViewer() {
                    if (mPanoramaViewHelper == null) {
                        return;
                    }
                    final LocalData data = getCurrentLocalData();
                    if (data == null) {
                        Log.w(TAG, "Cannot open null data.");
                        return;
                    }
                    final Uri contentUri = data.getUri();
                    if (contentUri == Uri.EMPTY) {
                        Log.w(TAG, "Cannot open empty URL.");
                        return;
                    }

                    if (PanoramaMetadataLoader.isPanoramaAndUseViewer(data)) {
                        mPanoramaViewHelper.showPanorama(CameraActivity.this, contentUri);
                    } else if (RgbzMetadataLoader.hasRGBZData(data)) {
                        mPanoramaViewHelper.showRgbz(contentUri);
                        if (mSettingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING)) {
                            mSettingsManager.set(SettingsManager.SCOPE_GLOBAL,
                                    Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING, false);
                            mCameraAppUI.clearClingForViewer(
                                    CameraAppUI.BottomPanel.VIEWER_REFOCUS);
                        }
                    }
                }

                @Override
                public void onEdit() {
                    LocalData data = getCurrentLocalData();
                    if (data == null) {
                        Log.w(TAG, "Cannot edit null data.");
                        return;
                    }
                    final int currentDataId = getCurrentDataId();
                    UsageStatistics.instance().mediaInteraction(fileNameFromDataID(currentDataId),
                            MediaInteraction.InteractionType.EDIT,
                            NavigationChange.InteractionCause.BUTTON,
                            fileAgeFromDataID(currentDataId));
                    launchEditor(data);
                }

                @Override
                public void onTinyPlanet() {
                    LocalData data = getCurrentLocalData();
                    if (data == null) {
                        Log.w(TAG, "Cannot edit tiny planet on null data.");
                        return;
                    }
                    launchTinyPlanetEditor(data);
                }

                @Override
                public void onDelete() {
                    final int currentDataId = getCurrentDataId();
                    UsageStatistics.instance().mediaInteraction(fileNameFromDataID(currentDataId),
                            MediaInteraction.InteractionType.DELETE,
                            NavigationChange.InteractionCause.BUTTON,
                            fileAgeFromDataID(currentDataId));
                    removeData(currentDataId);
                }

                @Override
                public void onShare() {
                    final LocalData data = getCurrentLocalData();
                    if (data == null) {
                        Log.w(TAG, "Cannot share null data.");
                        return;
                    }

                    final int currentDataId = getCurrentDataId();
                    UsageStatistics.instance().mediaInteraction(fileNameFromDataID(currentDataId),
                            MediaInteraction.InteractionType.SHARE,
                            NavigationChange.InteractionCause.BUTTON,
                            fileAgeFromDataID(currentDataId));
                    // If applicable, show release information before this item
                    // is shared.
                    if (ReleaseHelper.shouldShowReleaseInfoDialogOnShare(data)) {
                        ReleaseHelper.showReleaseInfoDialog(CameraActivity.this,
                                new Callback<Void>() {
                                    @Override
                                    public void onCallback(Void result) {
                                        share(data);
                                    }
                                });
                    } else {
                        share(data);
                    }
                }

                private void share(LocalData data) {
                    Intent shareIntent = getShareIntentByData(data);
                    if (shareIntent != null) {
                        try {
                            launchActivityByIntent(shareIntent);
                            mCameraAppUI.getFilmstripBottomControls().setShareEnabled(false);
                        } catch (ActivityNotFoundException ex) {
                            // Nothing.
                        }
                    }
                }

                private int getCurrentDataId() {
                    return mFilmstripController.getCurrentId();
                }

                private LocalData getCurrentLocalData() {
                    return mDataAdapter.getLocalData(getCurrentDataId());
                }

                /**
                 * Sets up the share intent and NFC properly according to the
                 * data.
                 *
                 * @param data The data to be shared.
                 */
                private Intent getShareIntentByData(final LocalData data) {
                    Intent intent = null;
                    final Uri contentUri = data.getUri();
                    final String msgShareTo = getResources().getString(R.string.share_to);

                    if (PanoramaMetadataLoader.isPanorama360(data) &&
                            data.getUri() != Uri.EMPTY) {
                        intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("application/vnd.google.panorama360+jpg");
                        intent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    } else if (data.isDataActionSupported(LocalData.DATA_ACTION_SHARE)) {
                        final String mimeType = data.getMimeType();
                        intent = getShareIntentFromType(mimeType);
                        if (intent != null) {
                            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        intent = Intent.createChooser(intent, msgShareTo);
                    }
                    return intent;
                }

                /**
                 * Get the share intent according to the mimeType
                 *
                 * @param mimeType The mimeType of current data.
                 * @return the video/image's ShareIntent or null if mimeType is
                 *         invalid.
                 */
                private Intent getShareIntentFromType(String mimeType) {
                    // Lazily create the intent object.
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    if (mimeType.startsWith("video/")) {
                        intent.setType("video/*");
                    } else {
                        if (mimeType.startsWith("image/")) {
                            intent.setType("image/*");
                        } else {
                            Log.w(TAG, "unsupported mimeType " + mimeType);
                        }
                    }
                    return intent;
                }

                @Override
                public void onProgressErrorClicked() {
                    LocalData data = getCurrentLocalData();
                    getServices().getCaptureSessionManager().removeErrorMessage(
                            data.getUri());
                    updateBottomControlsByData(data);
                }
            };

    @Override
    public void onCameraOpened(CameraAgent.CameraProxy camera) {
        Log.v(TAG, "onCameraOpened");
        if (mPaused) {
            // We've paused, but just asynchronously opened the camera. Close it
            // because we should be releasing the camera when paused to allow
            // other apps to access it.
            Log.v(TAG, "received onCameraOpened but activity is paused, closing Camera");
            mCameraController.closeCamera(false);
            return;
        }
        /**
         * The current UI requires that the flash option visibility in front-facing
         * camera be
         *   * disabled if back facing camera supports flash
         *   * hidden if back facing camera does not support flash
         * We save whether back facing camera supports flash because we cannot get
         * this in front facing camera without a camera switch.
         *
         * If this preference is cleared, we also need to clear the camera facing
         * setting so we default to opening the camera in back facing camera, and
         * can save this flash support value again.
         */
        if (!mSettingsManager.isSet(SettingsManager.SCOPE_GLOBAL,
                                    Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA)) {
            HardwareSpec hardware =
                    new HardwareSpecImpl(getCameraProvider(), camera.getCapabilities());
            mSettingsManager.set(SettingsManager.SCOPE_GLOBAL,
                                 Keys.KEY_FLASH_SUPPORTED_BACK_CAMERA,
                                 hardware.isFlashSupported());
        }

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
        Log.v(TAG, "invoking onChangeCamera");
        mCameraAppUI.onChangeCamera();
    }

    private void resetExposureCompensationToDefault(CameraAgent.CameraProxy camera) {
        // Reset the exposure compensation before handing the camera to module.
        CameraSettings cameraSettings = camera.getSettings();
        cameraSettings.setExposureCompensationIndex(0);
        camera.applySettings(cameraSettings);
    }

    @Override
    public void onCameraDisabled(int cameraId) {
        UsageStatistics.instance().cameraFailure(
                eventprotos.CameraFailure.FailureReason.SECURITY, null,
                UsageStatistics.NONE, UsageStatistics.NONE);
        Log.w(TAG, "Camera disabled: " + cameraId);
        CameraUtil.showErrorAndFinish(this, R.string.camera_disabled);
    }

    @Override
    public void onDeviceOpenFailure(int cameraId, String info) {
        UsageStatistics.instance().cameraFailure(
                eventprotos.CameraFailure.FailureReason.OPEN_FAILURE, info,
                UsageStatistics.NONE, UsageStatistics.NONE);
        Log.w(TAG, "Camera open failure: " + info);
        CameraUtil.showErrorAndFinish(this, R.string.cannot_connect_camera);
    }

    @Override
    public void onDeviceOpenedAlready(int cameraId, String info) {
        Log.w(TAG, "Camera open already: " + cameraId + "," + info);
        CameraUtil.showErrorAndFinish(this, R.string.cannot_connect_camera);
    }

    @Override
    public void onReconnectionFailure(CameraAgent mgr, String info) {
        UsageStatistics.instance().cameraFailure(
                eventprotos.CameraFailure.FailureReason.RECONNECT_FAILURE, null,
                UsageStatistics.NONE, UsageStatistics.NONE);
        Log.w(TAG, "Camera reconnection failure:" + info);
        CameraUtil.showErrorAndFinish(this, R.string.cannot_connect_camera);
    }

    private static class MainHandler extends Handler {
        final WeakReference<CameraActivity> mActivity;

        public MainHandler(CameraActivity activity, Looper looper) {
            super(looper);
            mActivity = new WeakReference<CameraActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            CameraActivity activity = mActivity.get();
            if (activity == null) {
                return;
            }
            switch (msg.what) {

                case MSG_CLEAR_SCREEN_ON_FLAG: {
                    if (!activity.mPaused) {
                        activity.getWindow().clearFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                    break;
                }
            }
        }
    }

    private String fileNameFromDataID(int dataID) {
        final LocalData localData = mDataAdapter.getLocalData(dataID);
        if (localData == null) {
            return "";
        }

        File localFile = new File(localData.getPath());
        return localFile.getName();
    }

    private float fileAgeFromDataID(int dataID) {
        final LocalData localData = mDataAdapter.getLocalData(dataID);
        if (localData == null) {
            return 0;
        }

        File localFile = new File(localData.getPath());
        return 0.001f * (System.currentTimeMillis() - localFile.lastModified());
    }

    private final FilmstripContentPanel.Listener mFilmstripListener =
            new FilmstripContentPanel.Listener() {

                @Override
                public void onSwipeOut() {
                }

                @Override
                public void onSwipeOutBegin() {
                    mActionBar.hide();
                    mCameraAppUI.hideBottomControls();
                    mFilmstripCoversPreview = false;
                    updatePreviewVisibility();
                }

                @Override
                public void onFilmstripHidden() {
                    mFilmstripVisible = false;
                    UsageStatistics.instance().changeScreen(currentUserInterfaceMode(),
                            NavigationChange.InteractionCause.SWIPE_RIGHT);
                    CameraActivity.this.setFilmstripUiVisibility(false);
                    // When the user hide the filmstrip (either swipe out or
                    // tap on back key) we move to the first item so next time
                    // when the user swipe in the filmstrip, the most recent
                    // one is shown.
                    mFilmstripController.goToFirstItem();
                }

                @Override
                public void onFilmstripShown() {
                    mFilmstripVisible = true;
                    UsageStatistics.instance().changeScreen(currentUserInterfaceMode(),
                            NavigationChange.InteractionCause.SWIPE_LEFT);
                    updateUiByData(mFilmstripController.getCurrentId());
                }

                @Override
                public void onFocusedDataLongPressed(int dataId) {
                    // Do nothing.
                }

                @Override
                public void onFocusedDataPromoted(int dataID) {
                    UsageStatistics.instance().mediaInteraction(fileNameFromDataID(dataID),
                            MediaInteraction.InteractionType.DELETE,
                            NavigationChange.InteractionCause.SWIPE_UP, fileAgeFromDataID(dataID));
                    removeData(dataID);
                }

                @Override
                public void onFocusedDataDemoted(int dataID) {
                    UsageStatistics.instance().mediaInteraction(fileNameFromDataID(dataID),
                            MediaInteraction.InteractionType.DELETE,
                            NavigationChange.InteractionCause.SWIPE_DOWN,
                            fileAgeFromDataID(dataID));
                    removeData(dataID);
                }

                @Override
                public void onEnterFullScreenUiShown(int dataId) {
                    if (mFilmstripVisible) {
                        CameraActivity.this.setFilmstripUiVisibility(true);
                    }
                }

                @Override
                public void onLeaveFullScreenUiShown(int dataId) {
                    // Do nothing.
                }

                @Override
                public void onEnterFullScreenUiHidden(int dataId) {
                    if (mFilmstripVisible) {
                        CameraActivity.this.setFilmstripUiVisibility(false);
                    }
                }

                @Override
                public void onLeaveFullScreenUiHidden(int dataId) {
                    // Do nothing.
                }

                @Override
                public void onEnterFilmstrip(int dataId) {
                    if (mFilmstripVisible) {
                        CameraActivity.this.setFilmstripUiVisibility(true);
                    }
                }

                @Override
                public void onLeaveFilmstrip(int dataId) {
                    // Do nothing.
                }

                @Override
                public void onDataReloaded() {
                    if (!mFilmstripVisible) {
                        return;
                    }
                    updateUiByData(mFilmstripController.getCurrentId());
                }

                @Override
                public void onDataUpdated(int dataId) {
                    if (!mFilmstripVisible) {
                        return;
                    }
                    updateUiByData(mFilmstripController.getCurrentId());
                }

                @Override
                public void onEnterZoomView(int dataID) {
                    if (mFilmstripVisible) {
                        CameraActivity.this.setFilmstripUiVisibility(false);
                    }
                }

                @Override
                public void onZoomAtIndexChanged(int dataId, float zoom) {
                    final LocalData localData = mDataAdapter.getLocalData(dataId);
                    long ageMillis = System.currentTimeMillis() - localData.getDateModified() * 1000;

                    // Do not log if items is to old or does not have a path (which is
                    // being used as a key).
                    if (TextUtils.isEmpty(localData.getPath()) ||
                            ageMillis > UsageStatistics.VIEW_TIMEOUT_MILLIS) {
                        return;
                    }
                    File localFile = new File(localData.getPath());
                    UsageStatistics.instance().mediaView(localFile.getName(),
                            TimeUnit.SECONDS.toMillis(localData.getDateModified()), zoom);
               }

                @Override
                public void onDataFocusChanged(final int prevDataId, final int newDataId) {
                    if (!mFilmstripVisible) {
                        return;
                    }
                    // TODO: This callback is UI event callback, should always
                    // happen on UI thread. Find the reason for this
                    // runOnUiThread() and fix it.
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateUiByData(newDataId);
                        }
                    });
                }

                @Override
                public void onScroll(int firstVisiblePosition, int visibleItemCount, int totalItemCount) {
                    mPreloader.onScroll(null /*absListView*/, firstVisiblePosition, visibleItemCount, totalItemCount);
                }
            };

    private final LocalDataAdapter.LocalDataListener mLocalDataListener =
            new LocalDataAdapter.LocalDataListener() {
                @Override
                public void onMetadataUpdated(List<Integer> updatedData) {
                    if (mPaused) {
                        // Callback after the activity is paused.
                        return;
                    }
                    int currentDataId = mFilmstripController.getCurrentId();
                    for (Integer dataId : updatedData) {
                        if (dataId == currentDataId) {
                            updateBottomControlsByData(mDataAdapter.getLocalData(dataId));
                            // Currently we have only 1 data can be matched.
                            // No need to look for more, break.
                            break;
                        }
                    }
                }
            };

    public void gotoGallery() {
        UsageStatistics.instance().changeScreen(NavigationChange.Mode.FILMSTRIP,
                NavigationChange.InteractionCause.BUTTON);

        mFilmstripController.goToNextItem();
    }

    /**
     * If 'visible' is false, this hides the action bar. Also maintains
     * lights-out at all times.
     *
     * @param visible is false, this hides the action bar and filmstrip bottom
     *            controls.
     */
    private void setFilmstripUiVisibility(boolean visible) {
        mLightsOutRunnable.run();
        mCameraAppUI.getFilmstripBottomControls().setVisible(visible);
        if (visible != mActionBar.isShowing()) {
            if (visible) {
                mActionBar.show();
                mCameraAppUI.showBottomControls();
            } else {
                mActionBar.hide();
                mCameraAppUI.hideBottomControls();
            }
        }
        mFilmstripCoversPreview = visible;
        updatePreviewVisibility();
    }

    private void hideSessionProgress() {
        mCameraAppUI.getFilmstripBottomControls().hideProgress();
    }

    private void showSessionProgress(CharSequence message) {
        CameraAppUI.BottomPanel controls = mCameraAppUI.getFilmstripBottomControls();
        controls.setProgressText(message);
        controls.hideControls();
        controls.hideProgressError();
        controls.showProgress();
    }

    private void showProcessError(CharSequence message) {
        mCameraAppUI.getFilmstripBottomControls().showProgressError(message);
    }

    private void updateSessionProgress(int progress) {
        mCameraAppUI.getFilmstripBottomControls().setProgress(progress);
    }

    private void updateSessionProgressText(CharSequence message) {
        mCameraAppUI.getFilmstripBottomControls().setProgressText(message);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setupNfcBeamPush() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mAppContext);
        if (adapter == null) {
            return;
        }

        if (!ApiHelper.HAS_SET_BEAM_PUSH_URIS) {
            // Disable beaming
            adapter.setNdefPushMessage(null, CameraActivity.this);
            return;
        }

        adapter.setBeamPushUris(null, CameraActivity.this);
        adapter.setBeamPushUrisCallback(new CreateBeamUrisCallback() {
            @Override
            public Uri[] createBeamUris(NfcEvent event) {
                return mNfcPushUris;
            }
        }, CameraActivity.this);
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider shareActionProvider, Intent intent) {
        int currentDataId = mFilmstripController.getCurrentId();
        if (currentDataId < 0) {
            return false;
        }
        UsageStatistics.instance().mediaInteraction(fileNameFromDataID(currentDataId),
                MediaInteraction.InteractionType.SHARE,
                NavigationChange.InteractionCause.BUTTON, fileAgeFromDataID(currentDataId));
        // TODO add intent.getComponent().getPackageName()
        return true;
    }

    // Note: All callbacks come back on the main thread.
    private final SessionListener mSessionListener =
            new SessionListener() {
                @Override
                public void onSessionQueued(final Uri uri) {
                    Log.v(TAG, "onSessionQueued: " + uri);
                    if (!Storage.isSessionUri(uri)) {
                        return;
                    }
                    LocalSessionData newData = new LocalSessionData(uri);
                    mDataAdapter.addData(newData);
                }

                @Override
                public void onSessionDone(final Uri sessionUri) {
                    Log.v(TAG, "onSessionDone:" + sessionUri);
                    Uri contentUri = Storage.getContentUriForSessionUri(sessionUri);
                    if (contentUri == null) {
                        mDataAdapter.refresh(sessionUri);
                        return;
                    }
                    LocalData newData = LocalMediaData.PhotoData.fromContentUri(
                            getContentResolver(), contentUri);

                    // This can be null if e.g. a session is canceled (e.g.
                    // through discard panorama). It might be worth adding
                    // onSessionCanceled or the like this interface.
                    if (newData == null) {
                        Log.i(TAG, "onSessionDone: Could not find LocalData for URI: " + contentUri);
                        return;
                    }

                    final int pos = mDataAdapter.findDataByContentUri(sessionUri);
                    if (pos == -1) {
                        // We do not have a placeholder for this image, perhaps
                        // due to the activity crashing or being killed.
                        mDataAdapter.addData(newData);
                    } else {
                        mDataAdapter.updateData(pos, newData);
                    }
                }

                @Override
                public void onSessionProgress(final Uri uri, final int progress) {
                    if (progress < 0) {
                        // Do nothing, there is no task for this URI.
                        return;
                    }
                    int currentDataId = mFilmstripController.getCurrentId();
                    if (currentDataId == -1) {
                        return;
                    }
                    if (uri.equals(
                            mDataAdapter.getLocalData(currentDataId).getUri())) {
                        updateSessionProgress(progress);
                    }
                }

                @Override
                public void onSessionProgressText(final Uri uri, final CharSequence message) {
                    int currentDataId = mFilmstripController.getCurrentId();
                    if (currentDataId == -1) {
                        return;
                    }
                    if (uri.equals(
                            mDataAdapter.getLocalData(currentDataId).getUri())) {
                        updateSessionProgressText(message);
                    }
                }

                @Override
                public void onSessionUpdated(Uri uri) {
                    Log.v(TAG, "onSessionUpdated: " + uri);
                    mDataAdapter.refresh(uri);
                }

                @Override
                public void onSessionPreviewAvailable(Uri uri) {
                    Log.v(TAG, "onSessionPreviewAvailable: " + uri);
                    mDataAdapter.refresh(uri);
                    int dataId = mDataAdapter.findDataByContentUri(uri);
                    if (dataId != -1) {
                        startPeekAnimation(mDataAdapter.getLocalData(dataId),
                                mCurrentModule.getPeekAccessibilityString());
                    }
                }

                @Override
                public void onSessionFailed(Uri uri, CharSequence reason) {
                    Log.v(TAG, "onSessionFailed:" + uri);

                    int failedDataId = mDataAdapter.findDataByContentUri(uri);
                    int currentDataId = mFilmstripController.getCurrentId();

                    if (currentDataId == failedDataId) {
                        updateSessionProgress(0);
                        showProcessError(reason);
                    }
                    // HERE
                    mDataAdapter.refresh(uri);
                }
            };

    @Override
    public Context getAndroidContext() {
        return mAppContext;
    }

    @Override
    public void launchActivityByIntent(Intent intent) {
        // Starting from L, we prefer not to start edit activity within camera's task.
        mResetToPreviewOnResume = false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);

        startActivity(intent);
    }

    @Override
    public int getCurrentModuleIndex() {
        return mCurrentModeIndex;
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
            // if an unopen camera i.e. negative ID is returned, which we've observed in
            // some automated scenarios, just return it as a valid separate scope
            // this could cause user issues, so log a stack trace noting the call path
            // which resulted in this scenario.
            Log.w(TAG, "getting camera scope with no open camera, using id: " + currentCameraId);
        }
        return CAMERA_SCOPE_PREFIX + Integer.toString(currentCameraId);
    }

    @Override
    public ModuleController getCurrentModuleController() {
        return mCurrentModule;
    }

    @Override
    public int getQuickSwitchToModuleId(int currentModuleIndex) {
        return mModuleManager.getQuickSwitchToModuleId(currentModuleIndex, mSettingsManager,
                mAppContext);
    }

    @Override
    public SurfaceTexture getPreviewBuffer() {
        // TODO: implement this
        return null;
    }

    @Override
    public void onPreviewReadyToStart() {
        mCameraAppUI.onPreviewReadyToStart();
    }

    @Override
    public void onPreviewStarted() {
        mCameraAppUI.onPreviewStarted();
    }

    @Override
    public void addPreviewAreaSizeChangedListener(
            PreviewStatusListener.PreviewAreaChangedListener listener) {
        mCameraAppUI.addPreviewAreaChangedListener(listener);
    }

    @Override
    public void removePreviewAreaSizeChangedListener(
            PreviewStatusListener.PreviewAreaChangedListener listener) {
        mCameraAppUI.removePreviewAreaChangedListener(listener);
    }

    @Override
    public void setupOneShotPreviewListener() {
        mCameraController.setOneShotPreviewCallback(mMainHandler,
                new CameraAgent.CameraPreviewDataCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, CameraAgent.CameraProxy camera) {
                        mCurrentModule.onPreviewInitialDataReceived();
                        mCameraAppUI.onNewPreviewFrame();
                    }
                }
        );
    }

    @Override
    public void updatePreviewAspectRatio(float aspectRatio) {
        mCameraAppUI.updatePreviewAspectRatio(aspectRatio);
    }

    @Override
    public void updatePreviewTransformFullscreen(Matrix matrix, float aspectRatio) {
        mCameraAppUI.updatePreviewTransformFullscreen(matrix, aspectRatio);
    }

    @Override
    public RectF getFullscreenRect() {
        return mCameraAppUI.getFullscreenRect();
    }

    @Override
    public void updatePreviewTransform(Matrix matrix) {
        mCameraAppUI.updatePreviewTransform(matrix);
    }

    @Override
    public void setPreviewStatusListener(PreviewStatusListener previewStatusListener) {
        mCameraAppUI.setPreviewStatusListener(previewStatusListener);
    }

    @Override
    public FrameLayout getModuleLayoutRoot() {
        return mCameraAppUI.getModuleRootView();
    }

    @Override
    public void setShutterEventsListener(ShutterEventsListener listener) {
        // TODO: implement this
    }

    @Override
    public void setShutterEnabled(boolean enabled) {
        mCameraAppUI.setShutterButtonEnabled(enabled);
    }

    @Override
    public boolean isShutterEnabled() {
        return mCameraAppUI.isShutterButtonEnabled();
    }

    @Override
    public void startPreCaptureAnimation(boolean shortFlash) {
        mCameraAppUI.startPreCaptureAnimation(shortFlash);
    }

    @Override
    public void startPreCaptureAnimation() {
        mCameraAppUI.startPreCaptureAnimation(false);
    }

    @Override
    public void cancelPreCaptureAnimation() {
        // TODO: implement this
    }

    @Override
    public void startPostCaptureAnimation() {
        // TODO: implement this
    }

    @Override
    public void startPostCaptureAnimation(Bitmap thumbnail) {
        // TODO: implement this
    }

    @Override
    public void cancelPostCaptureAnimation() {
        // TODO: implement this
    }

    @Override
    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    @Override
    public LocationManager getLocationManager() {
        return mLocationManager;
    }

    @Override
    public void lockOrientation() {
        if (mOrientationManager != null) {
            mOrientationManager.lockOrientation();
        }
    }

    @Override
    public void unlockOrientation() {
        if (mOrientationManager != null) {
            mOrientationManager.unlockOrientation();
        }
    }

    /**
     * Starts the filmstrip peek animation if the filmstrip is not visible.
     * Only {@link LocalData#LOCAL_IMAGE}, {@link
     * LocalData#LOCAL_IN_PROGRESS_DATA} and {@link
     * LocalData#LOCAL_VIDEO} are supported.
     *
     * @param data The data to peek.
     * @param accessibilityString Accessibility string to announce on peek animation.
     */
    private void startPeekAnimation(final LocalData data, final String accessibilityString) {
        if (mFilmstripVisible || mPeekAnimationHandler == null) {
            return;
        }

        int dataType = data.getLocalDataType();
        if (dataType != LocalData.LOCAL_IMAGE && dataType != LocalData.LOCAL_IN_PROGRESS_DATA &&
                dataType != LocalData.LOCAL_VIDEO) {
            return;
        }

        mPeekAnimationHandler.startDecodingJob(data, new Callback<Bitmap>() {
            @Override
            public void onCallback(Bitmap result) {
                mCameraAppUI.startPeekAnimation(result, true, accessibilityString);
            }
        });
    }

    @Override
    public void notifyNewMedia(Uri uri) {
        // TODO: This method is running on the main thread. Also we should get
        // rid of that AsyncTask.

        updateStorageSpaceAndHint(null);
        ContentResolver cr = getContentResolver();
        String mimeType = cr.getType(uri);
        LocalData newData = null;
        if (LocalDataUtil.isMimeTypeVideo(mimeType)) {
            sendBroadcast(new Intent(CameraUtil.ACTION_NEW_VIDEO, uri));
            newData = LocalMediaData.VideoData.fromContentUri(getContentResolver(), uri);
            if (newData == null) {
                Log.e(TAG, "Can't find video data in content resolver:" + uri);
                return;
            }
        } else if (LocalDataUtil.isMimeTypeImage(mimeType)) {
            CameraUtil.broadcastNewPicture(mAppContext, uri);
            newData = LocalMediaData.PhotoData.fromContentUri(getContentResolver(), uri);
            if (newData == null) {
                Log.e(TAG, "Can't find photo data in content resolver:" + uri);
                return;
            }
        } else {
            Log.w(TAG, "Unknown new media with MIME type:" + mimeType + ", uri:" + uri);
            return;
        }

        // We are preloading the metadata for new video since we need the
        // rotation info for the thumbnail.
        new AsyncTask<LocalData, Void, LocalData>() {
            @Override
            protected LocalData doInBackground(LocalData... params) {
                LocalData data = params[0];
                MetadataLoader.loadMetadata(getAndroidContext(), data);
                return data;
            }

            @Override
            protected void onPostExecute(LocalData data) {
                // TODO: Figure out why sometimes the data is aleady there.
                mDataAdapter.addData(data);
                startPeekAnimation(data, mCurrentModule.getPeekAccessibilityString());
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, newData);
    }

    @Override
    public void enableKeepScreenOn(boolean enabled) {
        if (mPaused) {
            return;
        }

        mKeepScreenOn = enabled;
        if (mKeepScreenOn) {
            mMainHandler.removeMessages(MSG_CLEAR_SCREEN_ON_FLAG);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            keepScreenOnForAWhile();
        }
    }

    @Override
    public CameraProvider getCameraProvider() {
        return mCameraController;
    }

    @Override
    public OneCameraManager getCameraManager() {
        return mCameraManager;
    }

    private void removeData(int dataID) {
        mDataAdapter.removeData(dataID);
        if (mDataAdapter.getTotalNumber() > 1) {
            showUndoDeletionBar();
        } else {
            // If camera preview is the only view left in filmstrip,
            // no need to show undo bar.
            mPendingDeletion = true;
            performDeletion();
            if (mFilmstripVisible) {
                mCameraAppUI.getFilmstripContentPanel().animateHide();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_details:
                showDetailsDialog(mFilmstripController.getCurrentId());
                return true;
            case R.id.action_help_and_feedback:
                mResetToPreviewOnResume = false;
                GoogleHelpHelper.launchGoogleHelp(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean isCaptureIntent() {
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE.equals(getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(getIntent().getAction())) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Note: Make sure this callback is unregistered properly when the activity
     * is destroyed since we're otherwise leaking the Activity reference.
     */
    private final CameraExceptionHandler.CameraExceptionCallback mCameraExceptionCallback
        = new CameraExceptionHandler.CameraExceptionCallback() {
                @Override
                public void onCameraError(int errorCode) {
                    // Not a fatal error. only do Log.e().
                    Log.e(TAG, "Camera error callback. error=" + errorCode);
                }
                @Override
                public void onCameraException(
                        RuntimeException ex, String commandHistory, int action, int state) {
                    Log.e(TAG, "Camera Exception", ex);
                    UsageStatistics.instance().cameraFailure(
                            eventprotos.CameraFailure.FailureReason.API_RUNTIME_EXCEPTION,
                            commandHistory, action, state);
                    onFatalError();
                }
                @Override
                public void onDispatchThreadException(RuntimeException ex) {
                    Log.e(TAG, "DispatchThread Exception", ex);
                    UsageStatistics.instance().cameraFailure(
                            eventprotos.CameraFailure.FailureReason.API_TIMEOUT,
                            null, UsageStatistics.NONE, UsageStatistics.NONE);
                    onFatalError();
                }
                private void onFatalError() {
                    if (mCameraFatalError) {
                        return;
                    }
                    mCameraFatalError = true;

                    // If the activity receives exception during onPause, just exit the app.
                    if (mPaused && !isFinishing()) {
                        Log.e(TAG, "Fatal error during onPause, call Activity.finish()");
                        finish();
                    } else {
                        CameraUtil.showErrorAndFinish(CameraActivity.this,
                                R.string.cannot_connect_camera);
                    }
                }
            };

    @Override
    public void onNewIntentTasks(Intent intent) {
        onModeSelected(getModeIndex());
    }

    @Override
    public void onCreateTasks(Bundle state) {
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_START);
        mAppContext = getApplication().getBaseContext();

        if (!Glide.isSetup()) {
            Glide.setup(new GlideBuilder(getAndroidContext())
                .setResizeService(new FifoPriorityThreadPoolExecutor(2)));
            Glide.get(getAndroidContext()).setMemoryCategory(MemoryCategory.HIGH);
        }

        mOnCreateTime = System.currentTimeMillis();
        mSoundPlayer = new SoundPlayer(mAppContext);

        try {
            mCameraManager = OneCameraManager.get(this);
        } catch (OneCameraException e) {
            Log.d(TAG, "Creating camera manager failed.", e);
            CameraUtil.showErrorAndFinish(this, R.string.cannot_connect_camera);
            return;
        }

        // TODO: Try to move all the resources allocation to happen as soon as
        // possible so we can call module.init() at the earliest time.
        mModuleManager = new ModuleManagerImpl();
        GcamHelper.init(getContentResolver());
        ModulesInfo.setupModules(mAppContext, mModuleManager);

        mSettingsManager = getServices().getSettingsManager();
        AppUpgrader appUpgrader = new AppUpgrader(this);
        appUpgrader.upgrade(mSettingsManager);
        Keys.setDefaults(mSettingsManager, mAppContext);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.activity_main);

        mActionBar = getActionBar();
        // set actionbar background to 100% or 50% transparent
        if (ApiHelper.isLOrHigher()) {
            mActionBar.setBackgroundDrawable(new ColorDrawable(0x00000000));
        } else {
            mActionBar.setBackgroundDrawable(new ColorDrawable(0x80000000));
        }

        mMainHandler = new MainHandler(this, getMainLooper());
        mCameraController = new CameraController(mAppContext, this, mMainHandler,
                CameraAgentFactory.getAndroidCameraAgent(mAppContext,
                        CameraAgentFactory.CameraApi.API_1),
                CameraAgentFactory.getAndroidCameraAgent(mAppContext,
                        CameraAgentFactory.CameraApi.AUTO));
        mCameraController.setCameraExceptionHandler(
                new CameraExceptionHandler(mCameraExceptionCallback, mMainHandler));

        mModeListView = (ModeListView) findViewById(R.id.mode_list_layout);
        mModeListView.init(mModuleManager.getSupportedModeIndexList());
        if (ApiHelper.HAS_ROTATION_ANIMATION) {
            setRotationAnimation();
        }
        mModeListView.setVisibilityChangedListener(new ModeListVisibilityChangedListener() {
            @Override
            public void onVisibilityChanged(boolean visible) {
                mModeListVisible = visible;
                mCameraAppUI.setShutterButtonImportantToA11y(!visible);
                updatePreviewVisibility();
            }
        });

        // Check if this is in the secure camera mode.
        Intent intent = getIntent();
        String action = intent.getAction();
        if (INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)
                || ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            mSecureCamera = true;
        } else {
            mSecureCamera = intent.getBooleanExtra(SECURE_CAMERA_EXTRA, false);
        }

        if (mSecureCamera) {
            // Change the window flags so that secure camera can show when
            // locked
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            win.setAttributes(params);

            // Filter for screen off so that we can finish secure camera
            // activity when screen is off.
            IntentFilter filter_screen_off = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(mShutdownReceiver, filter_screen_off);

            // Filter for phone unlock so that we can finish secure camera
            // via this UI path:
            //    1. from secure lock screen, user starts secure camera
            //    2. user presses home button
            //    3. user unlocks phone
            IntentFilter filter_user_unlock = new IntentFilter(Intent.ACTION_USER_PRESENT);
            registerReceiver(mShutdownReceiver, filter_user_unlock);
        }
        mCameraAppUI = new CameraAppUI(this,
                (MainActivityLayout) findViewById(R.id.activity_root_view), isCaptureIntent());

        mCameraAppUI.setFilmstripBottomControlsListener(mMyFilmstripBottomControlListener);

        mAboveFilmstripControlLayout =
                (FrameLayout) findViewById(R.id.camera_filmstrip_content_layout);

        // Add the session listener so we can track the session progress
        // updates.
        getServices().getCaptureSessionManager().addSessionListener(mSessionListener);
        mFilmstripController = ((FilmstripView) findViewById(R.id.filmstrip_view)).getController();
        mFilmstripController.setImageGap(
                getResources().getDimensionPixelSize(R.dimen.camera_film_strip_gap));
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        // Set up the camera preview first so the preview shows up ASAP.
        mDataAdapter = new CameraDataAdapter(mAppContext, R.color.photo_placeholder);
        mDataAdapter.setLocalDataListener(mLocalDataListener);

        mPreloader = new Preloader<Integer, AsyncTask>(FILMSTRIP_PRELOAD_AHEAD_ITEMS, mDataAdapter,
                mDataAdapter);

        mCameraAppUI.getFilmstripContentPanel().setFilmstripListener(mFilmstripListener);
        if (mSettingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                        Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING)) {
            mCameraAppUI.setupClingForViewer(CameraAppUI.BottomPanel.VIEWER_REFOCUS);
        }

        mLocationManager = new LocationManager(mAppContext);

        mOrientationManager = new OrientationManagerImpl(this);
        mOrientationManager.addOnOrientationChangeListener(mMainHandler, this);

        setModuleFromModeIndex(getModeIndex());
        mCameraAppUI.prepareModuleUI();
        mCurrentModule.init(this, isSecureCamera(), isCaptureIntent());

        if (!mSecureCamera) {
            mFilmstripController.setDataAdapter(mDataAdapter);
            if (!isCaptureIntent()) {
                mDataAdapter.requestLoad(new Callback<Void>() {
                    @Override
                    public void onCallback(Void result) {
                        fillTemporarySessions();
                    }
                });
            }
        } else {
            // Put a lock placeholder as the last image by setting its date to
            // 0.
            ImageView v = (ImageView) getLayoutInflater().inflate(
                    R.layout.secure_album_placeholder, null);
            v.setTag(R.id.mediadata_tag_viewtype, LocalDataViewType.SECURE_ALBUM_PLACEHOLDER.ordinal());
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    UsageStatistics.instance().changeScreen(NavigationChange.Mode.GALLERY,
                            NavigationChange.InteractionCause.BUTTON);
                    startGallery();
                    finish();
                }
            });
            v.setContentDescription(getString(R.string.accessibility_unlock_to_camera));
            mDataAdapter = new FixedLastDataAdapter(
                    mAppContext,
                    mDataAdapter,
                    new SimpleViewData(
                            v,
                            LocalDataViewType.SECURE_ALBUM_PLACEHOLDER,
                            v.getDrawable().getIntrinsicWidth(),
                            v.getDrawable().getIntrinsicHeight(),
                            0, 0));
            // Flush out all the original data.
            mDataAdapter.flush();
            mFilmstripController.setDataAdapter(mDataAdapter);
        }

        setupNfcBeamPush();

        mLocalImagesObserver = new LocalMediaObserver();
        mLocalVideosObserver = new LocalMediaObserver();

        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true,
                mLocalImagesObserver);
        getContentResolver().registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true,
                mLocalVideosObserver);
        mMemoryManager = getServices().getMemoryManager();

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                HashMap memoryData = mMemoryManager.queryMemory();
                UsageStatistics.instance().reportMemoryConsumed(memoryData,
                        MemoryQuery.REPORT_LABEL_LAUNCH);
            }
        });
        mMotionManager = getServices().getMotionManager();
    }

    /**
     * Get the current mode index from the Intent or from persistent
     * settings.
     */
    public int getModeIndex() {
        int modeIndex = -1;
        int photoIndex = getResources().getInteger(R.integer.camera_mode_photo);
        int videoIndex = getResources().getInteger(R.integer.camera_mode_video);
        int gcamIndex = getResources().getInteger(R.integer.camera_mode_gcam);
        if (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(getIntent().getAction())
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())) {
            modeIndex = videoIndex;
        } else if (MediaStore.ACTION_IMAGE_CAPTURE.equals(getIntent().getAction())) {
            // Capture intent.
            modeIndex = photoIndex;
        } else if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(getIntent().getAction())
                ||MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(getIntent()
                        .getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(getIntent().getAction())) {
            modeIndex = mSettingsManager.getInteger(SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_CAMERA_MODULE_LAST_USED);

            // For upgraders who have not seen the aspect ratio selection screen,
            // we need to drop them back in the photo module and have them select
            // aspect ratio.
            // TODO: Move this to SettingsManager as an upgrade procedure.
            if (!mSettingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                    Keys.KEY_USER_SELECTED_ASPECT_RATIO)) {
                modeIndex = photoIndex;
            }
        } else {
            // If the activity has not been started using an explicit intent,
            // read the module index from the last time the user changed modes
            modeIndex = mSettingsManager.getInteger(SettingsManager.SCOPE_GLOBAL,
                                                    Keys.KEY_STARTUP_MODULE_INDEX);
            if ((modeIndex == gcamIndex &&
                    !GcamHelper.hasGcamAsSeparateModule()) || modeIndex < 0) {
                modeIndex = photoIndex;
            }
        }
        return modeIndex;
    }

    /**
     * Call this whenever the mode drawer or filmstrip change the visibility
     * state.
     */
    private void updatePreviewVisibility() {
        if (mCurrentModule == null) {
            return;
        }

        int visibility = getPreviewVisibility();
        mCameraAppUI.onPreviewVisiblityChanged(visibility);
        updatePreviewRendering(visibility);
        mCurrentModule.onPreviewVisibilityChanged(visibility);
    }

    private void updatePreviewRendering(int visibility) {
        if (visibility == ModuleController.VISIBILITY_HIDDEN) {
            mCameraAppUI.pausePreviewRendering();
        } else {
            mCameraAppUI.resumePreviewRendering();
        }
    }

    private int getPreviewVisibility() {
        if (mFilmstripCoversPreview) {
            return ModuleController.VISIBILITY_HIDDEN;
        } else if (mModeListVisible){
            return ModuleController.VISIBILITY_COVERED;
        } else {
            return ModuleController.VISIBILITY_VISIBLE;
        }
    }

    private void setRotationAnimation() {
        int rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
        rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        Window win = getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        winParams.rotationAnimation = rotationAnimation;
        win.setAttributes(winParams);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (!isFinishing()) {
            keepScreenOnForAWhile();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean result = super.dispatchTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Real deletion is postponed until the next user interaction after
            // the gesture that triggers deletion. Until real deletion is
            // performed, users can click the undo button to bring back the
            // image that they chose to delete.
            if (mPendingDeletion && !mIsUndoingDeletion) {
                performDeletion();
            }
        }
        return result;
    }

    @Override
    public void onPauseTasks() {
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_PAUSE);

        /*
         * Save the last module index after all secure camera and icon launches,
         * not just on mode switches.
         *
         * Right now we exclude capture intents from this logic, because we also
         * ignore the cross-Activity recovery logic in onStart for capture intents.
         */
        if (!isCaptureIntent()) {
            mSettingsManager.set(SettingsManager.SCOPE_GLOBAL,
                                 Keys.KEY_STARTUP_MODULE_INDEX,
                mCurrentModeIndex);
        }

        mPaused = true;
        mPeekAnimationHandler = null;
        mPeekAnimationThread.quitSafely();
        mPeekAnimationThread = null;

        // Delete photos that are pending deletion
        performDeletion();
        mCurrentModule.pause();
        mOrientationManager.pause();
        mPanoramaViewHelper.onPause();

        mLocalImagesObserver.setForegroundChangeListener(null);
        mLocalImagesObserver.setActivityPaused(true);
        mLocalVideosObserver.setActivityPaused(true);
        mPreloader.cancelAllLoads();
        resetScreenOn();

        mMotionManager.stop();

        UsageStatistics.instance().backgrounded();

        // Camera is in fatal state. A fatal dialog is presented to users, but users just hit home
        // button. Let's just kill the process.
        if (mCameraFatalError && !isFinishing()) {
            Log.v(TAG, "onPause when camera is in fatal state, call Activity.finish()");
            finish();
        } else {
            // Close the camera and wait for the operation done.
            mCameraController.closeCamera(true);
        }
    }

    @Override
    public void onResumeTasks() {
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_RESUME);
        Log.v(TAG, "Build info: " + Build.DISPLAY);

        mPaused = false;
        updateStorageSpaceAndHint(null);

        mLastLayoutOrientation = getResources().getConfiguration().orientation;

        // TODO: Handle this in OrientationManager.
        // Auto-rotate off
        if (Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 0) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            mAutoRotateScreen = false;
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            mAutoRotateScreen = true;
        }

        // Foreground event logging.  ACTION_STILL_IMAGE_CAMERA and
        // INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE are double logged due to
        // lockscreen onResume->onPause->onResume sequence.
        int source;
        String action = getIntent().getAction();
        if (action == null) {
            source = ForegroundSource.UNKNOWN_SOURCE;
        } else {
            switch (action) {
                case MediaStore.ACTION_IMAGE_CAPTURE:
                    source = ForegroundSource.ACTION_IMAGE_CAPTURE;
                    break;
                case MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA:
                    // was UNKNOWN_SOURCE in Fishlake.
                    source = ForegroundSource.ACTION_STILL_IMAGE_CAMERA;
                    break;
                case MediaStore.INTENT_ACTION_VIDEO_CAMERA:
                    // was UNKNOWN_SOURCE in Fishlake.
                    source = ForegroundSource.ACTION_VIDEO_CAMERA;
                    break;
                case MediaStore.ACTION_VIDEO_CAPTURE:
                    source = ForegroundSource.ACTION_VIDEO_CAPTURE;
                    break;
                case MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE:
                    // was ACTION_IMAGE_CAPTURE_SECURE in Fishlake.
                    source = ForegroundSource.ACTION_STILL_IMAGE_CAMERA_SECURE;
                    break;
                case MediaStore.ACTION_IMAGE_CAPTURE_SECURE:
                    source = ForegroundSource.ACTION_IMAGE_CAPTURE_SECURE;
                    break;
                case Intent.ACTION_MAIN:
                    source = ForegroundSource.ACTION_MAIN;
                    break;
                default:
                    source = ForegroundSource.UNKNOWN_SOURCE;
                    break;
            }
        }
        UsageStatistics.instance().foregrounded(source, currentUserInterfaceMode());

        mGalleryIntent = IntentHelper.getGalleryIntent(mAppContext);
        if (ApiHelper.isLOrHigher()) {
            // hide the up affordance for L devices, it's not very Materially
            mActionBar.setDisplayShowHomeEnabled(false);
        }

        mOrientationManager.resume();
        mPeekAnimationThread = new HandlerThread("Peek animation");
        mPeekAnimationThread.start();
        mPeekAnimationHandler = new PeekAnimationHandler(mPeekAnimationThread.getLooper(),
                mMainHandler, mAboveFilmstripControlLayout);

        mCurrentModule.hardResetSettings(mSettingsManager);
        mCurrentModule.resume();
        UsageStatistics.instance().changeScreen(currentUserInterfaceMode(),
                NavigationChange.InteractionCause.BUTTON);
        setSwipingEnabled(true);

        if (!mResetToPreviewOnResume) {
            LocalData data = mDataAdapter.getLocalData(mFilmstripController.getCurrentId());
            if (data != null) {
                mDataAdapter.refresh(data.getUri());
            }
        }
        // The share button might be disabled to avoid double tapping.
        mCameraAppUI.getFilmstripBottomControls().setShareEnabled(true);
        // Default is showing the preview, unless disabled by explicitly
        // starting an activity we want to return from to the filmstrip rather
        // than the preview.
        mResetToPreviewOnResume = true;

        if (mLocalVideosObserver.isMediaDataChangedDuringPause()
                || mLocalImagesObserver.isMediaDataChangedDuringPause()) {
            if (!mSecureCamera) {
                // If it's secure camera, requestLoad() should not be called
                // as it will load all the data.
                if (!mFilmstripVisible) {
                    mDataAdapter.requestLoad(new Callback<Void>() {
                        @Override
                        public void onCallback(Void result) {
                            fillTemporarySessions();
                        }
                    });
                } else {
                    mDataAdapter.requestLoadNewPhotos();
                }
            }
        }
        mLocalImagesObserver.setActivityPaused(false);
        mLocalVideosObserver.setActivityPaused(false);
        if (!mSecureCamera) {
            mLocalImagesObserver.setForegroundChangeListener(
                    new LocalMediaObserver.ChangeListener() {
                @Override
                public void onChange() {
                    mDataAdapter.requestLoadNewPhotos();
                }
            });
        }

        keepScreenOnForAWhile();

        // Lights-out mode at all times.
        final View rootView = findViewById(R.id.activity_root_view);
        mLightsOutRunnable.run();
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                new OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        mMainHandler.removeCallbacks(mLightsOutRunnable);
                        mMainHandler.postDelayed(mLightsOutRunnable, LIGHTS_OUT_DELAY_MS);
                    }
                });

        mPanoramaViewHelper.onResume();
        ReleaseHelper.showReleaseInfoDialogOnStart(this, mSettingsManager);
        syncLocationManagerSetting();

        final int previewVisibility = getPreviewVisibility();
        updatePreviewRendering(previewVisibility);

        mMotionManager.start();
    }

    private void fillTemporarySessions() {
        if (mSecureCamera) {
            return;
        }
        // There might be sessions still in flight (processed by our service).
        // Make sure they're added to the filmstrip.
        getServices().getCaptureSessionManager().fillTemporarySession(mSessionListener);
    }

    @Override
    public void onStartTasks() {
        mIsActivityRunning = true;
        mPanoramaViewHelper.onStart();

        /*
         * If we're starting after launching a different Activity (lockscreen),
         * we need to use the last mode used in the other Activity, and
         * not the old one from this Activity.
         *
         * This needs to happen before CameraAppUI.resume() in order to set the
         * mode cover icon to the actual last mode used.
         *
         * Right now we exclude capture intents from this logic.
         */
        int modeIndex = getModeIndex();
        if (!isCaptureIntent() && mCurrentModeIndex != modeIndex) {
            onModeSelected(modeIndex);
        }

        if (mResetToPreviewOnResume) {
            mCameraAppUI.resume();
            mResetToPreviewOnResume = false;
        }
    }

    @Override
    protected void onStopTasks() {
        mIsActivityRunning = false;
        mPanoramaViewHelper.onStop();

        mLocationManager.disconnect();
    }

    @Override
    public void onDestroyTasks() {
        if (mSecureCamera) {
            unregisterReceiver(mShutdownReceiver);
        }
        mSettingsManager.removeAllListeners();
        mCameraController.removeCallbackReceiver();
        mCameraController.setCameraExceptionHandler(null);
        getContentResolver().unregisterContentObserver(mLocalImagesObserver);
        getContentResolver().unregisterContentObserver(mLocalVideosObserver);
        getServices().getCaptureSessionManager().removeSessionListener(mSessionListener);
        mCameraAppUI.onDestroy();
        mModeListView.setVisibilityChangedListener(null);
        mCameraController = null;
        mSettingsManager = null;
        mOrientationManager = null;
        mButtonManager = null;
        mSoundPlayer.release();
        CameraAgentFactory.recycle(CameraAgentFactory.CameraApi.API_1);
        CameraAgentFactory.recycle(CameraAgentFactory.CameraApi.AUTO);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        Log.v(TAG, "onConfigurationChanged");
        if (config.orientation == Configuration.ORIENTATION_UNDEFINED) {
            return;
        }

        if (mLastLayoutOrientation != config.orientation) {
            mLastLayoutOrientation = config.orientation;
            mCurrentModule.onLayoutOrientationChanged(
                    mLastLayoutOrientation == Configuration.ORIENTATION_LANDSCAPE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!mFilmstripVisible) {
            if (mCurrentModule.onKeyDown(keyCode, event)) {
                return true;
            }
            // Prevent software keyboard or voice search from showing up.
            if (keyCode == KeyEvent.KEYCODE_SEARCH
                    || keyCode == KeyEvent.KEYCODE_MENU) {
                if (event.isLongPress()) {
                    return true;
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!mFilmstripVisible) {
            // If a module is in the middle of capture, it should
            // consume the key event.
            if (mCurrentModule.onKeyUp(keyCode, event)) {
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MENU
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                // Let the mode list view consume the event.
                mCameraAppUI.openModeList();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mCameraAppUI.showFilmstrip();
                return true;
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                mFilmstripController.goToNextItem();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                boolean wentToPrevious = mFilmstripController.goToPreviousItem();
                if (!wentToPrevious) {
                  // at beginning of filmstrip, hide and go back to preview
                  mCameraAppUI.hideFilmstrip();
                }
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (!mCameraAppUI.onBackPressed()) {
            if (!mCurrentModule.onBackPressed()) {
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean isAutoRotateScreen() {
        // TODO: Move to OrientationManager.
        return mAutoRotateScreen;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.filmstrip_menu, menu);
        mActionBarMenu = menu;

        // add a button for launching the gallery
        if (mGalleryIntent != null) {
            CharSequence appName =  IntentHelper.getGalleryAppName(mAppContext, mGalleryIntent);
            if (appName != null) {
                MenuItem menuItem = menu.add(appName);
                menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menuItem.setIntent(mGalleryIntent);

                Drawable galleryLogo = IntentHelper.getGalleryIcon(mAppContext, mGalleryIntent);
                if (galleryLogo != null) {
                    menuItem.setIcon(galleryLogo);
                }
            }
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isSecureCamera() && !ApiHelper.isLOrHigher()) {
            // Compatibility pre-L: launching new activities right above
            // lockscreen does not reliably work, only show help if not secure
            menu.removeItem(R.id.action_help_and_feedback);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    protected long getStorageSpaceBytes() {
        synchronized (mStorageSpaceLock) {
            return mStorageSpaceBytes;
        }
    }

    protected interface OnStorageUpdateDoneListener {
        public void onStorageUpdateDone(long bytes);
    }

    protected void updateStorageSpaceAndHint(final OnStorageUpdateDoneListener callback) {
        /*
         * We execute disk operations on a background thread in order to
         * free up the UI thread.  Synchronizing on the lock below ensures
         * that when getStorageSpaceBytes is called, the main thread waits
         * until this method has completed.
         *
         * However, .execute() does not ensure this execution block will be
         * run right away (.execute() schedules this AsyncTask for sometime
         * in the future. executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
         * tries to execute the task in parellel with other AsyncTasks, but
         * there's still no guarantee).
         * e.g. don't call this then immediately call getStorageSpaceBytes().
         * Instead, pass in an OnStorageUpdateDoneListener.
         */
        (new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void ... arg) {
                synchronized (mStorageSpaceLock) {
                    mStorageSpaceBytes = Storage.getAvailableSpace();
                    return mStorageSpaceBytes;
                }
            }

            @Override
            protected void onPostExecute(Long bytes) {
                updateStorageHint(bytes);
                // This callback returns after I/O to check disk, so we could be
                // pausing and shutting down. If so, don't bother invoking.
                if (callback != null && !mPaused) {
                    callback.onStorageUpdateDone(bytes);
                } else {
                    Log.v(TAG, "ignoring storage callback after activity pause");
                }
            }
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    protected void updateStorageHint(long storageSpace) {
        if (!mIsActivityRunning) {
            return;
        }

        String message = null;
        if (storageSpace == Storage.UNAVAILABLE) {
            message = getString(R.string.no_storage);
        } else if (storageSpace == Storage.PREPARING) {
            message = getString(R.string.preparing_sd);
        } else if (storageSpace == Storage.UNKNOWN_SIZE) {
            message = getString(R.string.access_sd_fail);
        } else if (storageSpace <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            message = getString(R.string.spaceIsLow_content);
        }

        if (message != null) {
            Log.w(TAG, "Storage warning: " + message);
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(CameraActivity.this, message);
            } else {
                mStorageHint.setText(message);
            }
            mStorageHint.show();
            UsageStatistics.instance().storageWarning(storageSpace);

            // Disable all user interactions,
            mCameraAppUI.setDisableAllUserInteractions(true);
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;

            // Re-enable all user interactions.
            mCameraAppUI.setDisableAllUserInteractions(false);
        }
    }

    protected void setResultEx(int resultCode) {
        mResultCodeForTesting = resultCode;
        setResult(resultCode);
    }

    protected void setResultEx(int resultCode, Intent data) {
        mResultCodeForTesting = resultCode;
        mResultDataForTesting = data;
        setResult(resultCode, data);
    }

    public int getResultCode() {
        return mResultCodeForTesting;
    }

    public Intent getResultData() {
        return mResultDataForTesting;
    }

    public boolean isSecureCamera() {
        return mSecureCamera;
    }

    @Override
    public boolean isPaused() {
        return mPaused;
    }

    @Override
    public int getPreferredChildModeIndex(int modeIndex) {
        if (modeIndex == getResources().getInteger(R.integer.camera_mode_photo)) {
            boolean hdrPlusOn = Keys.isHdrPlusOn(mSettingsManager);
            if (hdrPlusOn && GcamHelper.hasGcamAsSeparateModule()) {
                modeIndex = getResources().getInteger(R.integer.camera_mode_gcam);
            }
        }
        return modeIndex;
    }

    @Override
    public void onModeSelected(int modeIndex) {
        if (mCurrentModeIndex == modeIndex) {
            return;
        }

        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.MODE_SWITCH_START);
        // Record last used camera mode for quick switching
        if (modeIndex == getResources().getInteger(R.integer.camera_mode_photo)
                || modeIndex == getResources().getInteger(R.integer.camera_mode_gcam)) {
            mSettingsManager.set(SettingsManager.SCOPE_GLOBAL,
                                 Keys.KEY_CAMERA_MODULE_LAST_USED,
                                 modeIndex);
        }

        closeModule(mCurrentModule);

        // Select the correct module index from the mode switcher index.
        modeIndex = getPreferredChildModeIndex(modeIndex);
        setModuleFromModeIndex(modeIndex);

        mCameraAppUI.resetBottomControls(mCurrentModule, modeIndex);
        mCameraAppUI.addShutterListener(mCurrentModule);
        openModule(mCurrentModule);
        mCurrentModule.onOrientationChanged(mLastRawOrientation);
        // Store the module index so we can use it the next time the Camera
        // starts up.
        mSettingsManager.set(SettingsManager.SCOPE_GLOBAL,
                             Keys.KEY_STARTUP_MODULE_INDEX, modeIndex);
    }

    /**
     * Shows the settings dialog.
     */
    @Override
    public void onSettingsSelected() {
        UsageStatistics.instance().controlUsed(
                eventprotos.ControlEvent.ControlType.OVERALL_SETTINGS);
        Intent intent = new Intent(this, CameraSettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void freezeScreenUntilPreviewReady() {
        mCameraAppUI.freezeScreenUntilPreviewReady();
    }

    /**
     * Sets the mCurrentModuleIndex, creates a new module instance for the given
     * index an sets it as mCurrentModule.
     */
    private void setModuleFromModeIndex(int modeIndex) {
        ModuleManagerImpl.ModuleAgent agent = mModuleManager.getModuleAgent(modeIndex);
        if (agent == null) {
            return;
        }
        if (!agent.requestAppForCamera()) {
            mCameraController.closeCamera(true);
        }
        mCurrentModeIndex = agent.getModuleId();
        mCurrentModule = (CameraModule) agent.createModule(this);
    }

    @Override
    public SettingsManager getSettingsManager() {
        return mSettingsManager;
    }

    @Override
    public CameraServices getServices() {
        return (CameraServices) getApplication();
    }

    public List<String> getSupportedModeNames() {
        List<Integer> indices = mModuleManager.getSupportedModeIndexList();
        List<String> supported = new ArrayList<String>();

        for (Integer modeIndex : indices) {
            String name = CameraUtil.getCameraModeText(modeIndex, mAppContext);
            if (name != null && !name.equals("")) {
                supported.add(name);
            }
        }
        return supported;
    }

    @Override
    public ButtonManager getButtonManager() {
        if (mButtonManager == null) {
            mButtonManager = new ButtonManager(this);
        }
        return mButtonManager;
    }

    @Override
    public SoundPlayer getSoundPlayer() {
        return mSoundPlayer;
    }

    /**
     * Creates an AlertDialog appropriate for choosing whether to enable
     * location on the first run of the app.
     */
    public AlertDialog getFirstTimeLocationAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder = SettingsUtil.getFirstTimeLocationAlertBuilder(builder, new Callback<Boolean>() {
            @Override
            public void onCallback(Boolean locationOn) {
                Keys.setLocation(mSettingsManager, locationOn, mLocationManager);
            }
        });
        if (builder != null) {
            return builder.create();
        } else {
            return null;
        }
    }

    /**
     * Launches an ACTION_EDIT intent for the given local data item. If
     * 'withTinyPlanet' is set, this will show a disambig dialog first to let
     * the user start either the tiny planet editor or another photo edior.
     *
     * @param data The data item to edit.
     */
    public void launchEditor(LocalData data) {
        Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(data.getUri(), data.getMimeType())
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            launchActivityByIntent(intent);
        } catch (ActivityNotFoundException e) {
            final String msgEditWith = getResources().getString(R.string.edit_with);
            launchActivityByIntent(Intent.createChooser(intent, msgEditWith));
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.filmstrip_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.tiny_planet_editor:
                mMyFilmstripBottomControlListener.onTinyPlanet();
                return true;
            case R.id.photo_editor:
                mMyFilmstripBottomControlListener.onEdit();
                return true;
        }
        return false;
    }

    /**
     * Launch the tiny planet editor.
     *
     * @param data The data must be a 360 degree stereographically mapped
     *            panoramic image. It will not be modified, instead a new item
     *            with the result will be added to the filmstrip.
     */
    public void launchTinyPlanetEditor(LocalData data) {
        TinyPlanetFragment fragment = new TinyPlanetFragment();
        Bundle bundle = new Bundle();
        bundle.putString(TinyPlanetFragment.ARGUMENT_URI, data.getUri().toString());
        bundle.putString(TinyPlanetFragment.ARGUMENT_TITLE, data.getTitle());
        fragment.setArguments(bundle);
        fragment.show(getFragmentManager(), "tiny_planet");
    }

    /**
     * Returns what UI mode (capture mode or filmstrip) we are in.
     * Returned number one of {@link com.google.common.logging.eventprotos.NavigationChange.Mode}
     */
    private int currentUserInterfaceMode() {
        int mode = NavigationChange.Mode.UNKNOWN_MODE;
        if (mCurrentModeIndex == getResources().getInteger(R.integer.camera_mode_photo)) {
            mode = NavigationChange.Mode.PHOTO_CAPTURE;
        }
        if (mCurrentModeIndex == getResources().getInteger(R.integer.camera_mode_video)) {
            mode = NavigationChange.Mode.VIDEO_CAPTURE;
        }
        if (mCurrentModeIndex == getResources().getInteger(R.integer.camera_mode_refocus)) {
            mode = NavigationChange.Mode.LENS_BLUR;
        }
        if (mCurrentModeIndex == getResources().getInteger(R.integer.camera_mode_gcam)) {
            mode = NavigationChange.Mode.HDR_PLUS;
        }
        if (mCurrentModeIndex == getResources().getInteger(R.integer.camera_mode_photosphere)) {
            mode = NavigationChange.Mode.PHOTO_SPHERE;
        }
        if (mCurrentModeIndex == getResources().getInteger(R.integer.camera_mode_panorama)) {
            mode = NavigationChange.Mode.PANORAMA;
        }
        if (mFilmstripVisible) {
            mode = NavigationChange.Mode.FILMSTRIP;
        }
        return mode;
    }

    private void openModule(CameraModule module) {
        module.init(this, isSecureCamera(), isCaptureIntent());
        module.hardResetSettings(mSettingsManager);
        if (!mPaused) {
            module.resume();
            UsageStatistics.instance().changeScreen(currentUserInterfaceMode(),
                    NavigationChange.InteractionCause.BUTTON);
            updatePreviewVisibility();
        }
    }

    private void closeModule(CameraModule module) {
        module.pause();
        mCameraAppUI.clearModuleUI();
    }

    private void performDeletion() {
        if (!mPendingDeletion) {
            return;
        }
        hideUndoDeletionBar(false);
        mDataAdapter.executeDeletion();
    }

    public void showUndoDeletionBar() {
        if (mPendingDeletion) {
            performDeletion();
        }
        Log.v(TAG, "showing undo bar");
        mPendingDeletion = true;
        if (mUndoDeletionBar == null) {
            ViewGroup v = (ViewGroup) getLayoutInflater().inflate(R.layout.undo_bar,
                    mAboveFilmstripControlLayout, true);
            mUndoDeletionBar = (ViewGroup) v.findViewById(R.id.camera_undo_deletion_bar);
            View button = mUndoDeletionBar.findViewById(R.id.camera_undo_deletion_button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mDataAdapter.undoDataRemoval();
                    hideUndoDeletionBar(true);
                }
            });
            // Setting undo bar clickable to avoid touch events going through
            // the bar to the buttons (eg. edit button, etc) underneath the bar.
            mUndoDeletionBar.setClickable(true);
            // When there is user interaction going on with the undo button, we
            // do not want to hide the undo bar.
            button.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        mIsUndoingDeletion = true;
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        mIsUndoingDeletion = false;
                    }
                    return false;
                }
            });
        }
        mUndoDeletionBar.setAlpha(0f);
        mUndoDeletionBar.setVisibility(View.VISIBLE);
        mUndoDeletionBar.animate().setDuration(200).alpha(1f).setListener(null).start();
    }

    private void hideUndoDeletionBar(boolean withAnimation) {
        Log.v(TAG, "Hiding undo deletion bar");
        mPendingDeletion = false;
        if (mUndoDeletionBar != null) {
            if (withAnimation) {
                mUndoDeletionBar.animate().setDuration(200).alpha(0f)
                        .setListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                // Do nothing.
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mUndoDeletionBar.setVisibility(View.GONE);
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                // Do nothing.
                            }

                            @Override
                            public void onAnimationRepeat(Animator animation) {
                                // Do nothing.
                            }
                        }).start();
            } else {
                mUndoDeletionBar.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onOrientationChanged(int orientation) {
        if (orientation != mLastRawOrientation) {
            Log.v(TAG, "orientation changed (from:to) " + mLastRawOrientation +
                    ":" + orientation);
        }

        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationManager.ORIENTATION_UNKNOWN) {
            return;
        }
        mLastRawOrientation = orientation;
        if (mCurrentModule != null) {
            mCurrentModule.onOrientationChanged(orientation);
        }
    }

    /**
     * Enable/disable swipe-to-filmstrip. Will always disable swipe if in
     * capture intent.
     *
     * @param enable {@code true} to enable swipe.
     */
    public void setSwipingEnabled(boolean enable) {
        // TODO: Bring back the functionality.
        if (isCaptureIntent()) {
            // lockPreview(true);
        } else {
            // lockPreview(!enable);
        }
    }

    // Accessor methods for getting latency times used in performance testing
    public long getFirstPreviewTime() {
        if (mCurrentModule instanceof PhotoModule) {
            long coverHiddenTime = getCameraAppUI().getCoverHiddenTime();
            if (coverHiddenTime != -1) {
                return coverHiddenTime - mOnCreateTime;
            }
        }
        return -1;
    }

    public long getAutoFocusTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mAutoFocusTime : -1;
    }

    public long getShutterLag() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mShutterLag : -1;
    }

    public long getShutterToPictureDisplayedTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mShutterToPictureDisplayedTime : -1;
    }

    public long getPictureDisplayedToJpegCallbackTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mPictureDisplayedToJpegCallbackTime : -1;
    }

    public long getJpegCallbackFinishTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mJpegCallbackFinishTime : -1;
    }

    public long getCaptureStartTime() {
        return (mCurrentModule instanceof PhotoModule) ?
                ((PhotoModule) mCurrentModule).mCaptureStartTime : -1;
    }

    public boolean isRecording() {
        return (mCurrentModule instanceof VideoModule) ?
                ((VideoModule) mCurrentModule).isRecording() : false;
    }

    public CameraAgent.CameraOpenCallback getCameraOpenErrorCallback() {
        return mCameraController;
    }

    // For debugging purposes only.
    public CameraModule getCurrentModule() {
        return mCurrentModule;
    }

    @Override
    public void showTutorial(AbstractTutorialOverlay tutorial) {
        mCameraAppUI.showTutorial(tutorial, getLayoutInflater());
    }

    @Override
    public void showErrorAndFinish(int messageId) {
        CameraUtil.showErrorAndFinish(this, messageId);
    }

    /**
     * Reads the current location recording settings and passes it on to the
     * location manager.
     */
    public void syncLocationManagerSetting() {
        Keys.syncLocationManager(mSettingsManager, mLocationManager);
    }

    private void keepScreenOnForAWhile() {
        if (mKeepScreenOn) {
            return;
        }
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_ON_FLAG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mMainHandler.sendEmptyMessageDelayed(MSG_CLEAR_SCREEN_ON_FLAG, SCREEN_DELAY_MS);
    }

    private void resetScreenOn() {
        mKeepScreenOn = false;
        mMainHandler.removeMessages(MSG_CLEAR_SCREEN_ON_FLAG);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * @return {@code true} if the Gallery is launched successfully.
     */
    private boolean startGallery() {
        if (mGalleryIntent == null) {
            return false;
        }
        try {
            UsageStatistics.instance().changeScreen(NavigationChange.Mode.GALLERY,
                    NavigationChange.InteractionCause.BUTTON);
            Intent startGalleryIntent = new Intent(mGalleryIntent);
            int currentDataId = mFilmstripController.getCurrentId();
            LocalData currentLocalData = mDataAdapter.getLocalData(currentDataId);
            if (currentLocalData != null) {
                GalleryHelper.setContentUri(startGalleryIntent, currentLocalData.getUri());
            }
            launchActivityByIntent(startGalleryIntent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Failed to launch gallery activity, closing");
        }
        return false;
    }

    private void setNfcBeamPushUriFromData(LocalData data) {
        final Uri uri = data.getUri();
        if (uri != Uri.EMPTY) {
            mNfcPushUris[0] = uri;
        } else {
            mNfcPushUris[0] = null;
        }
    }

    /**
     * Updates the visibility of the filmstrip bottom controls and action bar.
     */
    private void updateUiByData(final int dataId) {
        final LocalData currentData = mDataAdapter.getLocalData(dataId);
        if (currentData == null) {
            Log.w(TAG, "Current data ID not found.");
            hideSessionProgress();
            return;
        }
        updateActionBarMenu(currentData);

        /* Bottom controls. */
        updateBottomControlsByData(currentData);

        if (isSecureCamera()) {
            // We cannot show buttons in secure camera since go to other
            // activities might create a security hole.
            mCameraAppUI.getFilmstripBottomControls().hideControls();
            return;
        }


        setNfcBeamPushUriFromData(currentData);

        if (!mDataAdapter.isMetadataUpdated(dataId)) {
            mDataAdapter.updateMetadata(dataId);
        }
    }

    /**
     * Updates the bottom controls based on the data.
     */
    private void updateBottomControlsByData(final LocalData currentData) {

        final CameraAppUI.BottomPanel filmstripBottomPanel =
                mCameraAppUI.getFilmstripBottomControls();
        filmstripBottomPanel.showControls();
        filmstripBottomPanel.setEditButtonVisibility(
                currentData.isDataActionSupported(LocalData.DATA_ACTION_EDIT));
        filmstripBottomPanel.setShareButtonVisibility(
                currentData.isDataActionSupported(LocalData.DATA_ACTION_SHARE));
        filmstripBottomPanel.setDeleteButtonVisibility(
                currentData.isDataActionSupported(LocalData.DATA_ACTION_DELETE));

        /* Progress bar */

        Uri contentUri = currentData.getUri();
        CaptureSessionManager sessionManager = getServices()
                .getCaptureSessionManager();

        if (sessionManager.hasErrorMessage(contentUri)) {
            showProcessError(sessionManager.getErrorMesage(contentUri));
        } else {
            filmstripBottomPanel.hideProgressError();
            CaptureSession session = sessionManager.getSession(contentUri);

            if (session != null) {
                int sessionProgress = session.getProgress();

                if (sessionProgress < 0) {
                    hideSessionProgress();
                } else {
                    CharSequence progressMessage = session.getProgressMessage();
                    showSessionProgress(progressMessage);
                    updateSessionProgress(sessionProgress);
                }
            } else {
                hideSessionProgress();
            }
        }

        /* View button */

        // We need to add this to a separate DB.
        final int viewButtonVisibility;
        if (PanoramaMetadataLoader.isPanoramaAndUseViewer(currentData)) {
            viewButtonVisibility = CameraAppUI.BottomPanel.VIEWER_PHOTO_SPHERE;
        } else if (RgbzMetadataLoader.hasRGBZData(currentData)) {
            viewButtonVisibility = CameraAppUI.BottomPanel.VIEWER_REFOCUS;
        } else {
            viewButtonVisibility = CameraAppUI.BottomPanel.VIEWER_NONE;
        }

        filmstripBottomPanel.setTinyPlanetEnabled(
                PanoramaMetadataLoader.isPanorama360(currentData));
        filmstripBottomPanel.setViewerButtonVisibility(viewButtonVisibility);
    }

    private static class PeekAnimationHandler extends Handler {
        private class DataAndCallback {
            LocalData mData;
            com.android.camera.util.Callback<Bitmap> mCallback;

            public DataAndCallback(LocalData data, com.android.camera.util.Callback<Bitmap>
                    callback) {
                mData = data;
                mCallback = callback;
            }
        }

        private final Handler mMainHandler;
        private final FrameLayout mAboveFilmstripControlLayout;

        public PeekAnimationHandler(Looper looper, Handler mainHandler,
                FrameLayout aboveFilmstripControlLayout) {
            super(looper);
            mMainHandler = mainHandler;
            mAboveFilmstripControlLayout = aboveFilmstripControlLayout;
        }

        /**
         * Starts the animation decoding job and posts a {@code Runnable} back
         * when when the decoding is done.
         *
         * @param data The data item to decode the thumbnail for.
         * @param callback {@link com.android.camera.util.Callback} after the
         *                 decoding is done.
         */
        public void startDecodingJob(final LocalData data,
                final com.android.camera.util.Callback<Bitmap> callback) {
            PeekAnimationHandler.this.obtainMessage(0 /** dummy integer **/,
                    new DataAndCallback(data, callback)).sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            final LocalData data = ((DataAndCallback) msg.obj).mData;
            final com.android.camera.util.Callback<Bitmap> callback =
                    ((DataAndCallback) msg.obj).mCallback;
            if (data == null || callback == null) {
                return;
            }

            final Bitmap bitmap;
            switch (data.getLocalDataType()) {
                case LocalData.LOCAL_IN_PROGRESS_DATA:
                    byte[] jpegData = Storage.getJpegForSession(data.getUri());
                    if (jpegData != null) {
                        bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                    } else {
                        bitmap = null;
                    }
                    break;

                case LocalData.LOCAL_IMAGE:
                    FileInputStream stream;
                    try {
                        stream = new FileInputStream(data.getPath());
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "File not found:" + data.getPath());
                        return;
                    }
                    Point dim = CameraUtil.resizeToFill(data.getWidth(), data.getHeight(),
                            data.getRotation(), mAboveFilmstripControlLayout.getWidth(),
                            mAboveFilmstripControlLayout.getMeasuredHeight());
                    if (data.getRotation() % 180 != 0) {
                        int dummy = dim.x;
                        dim.x = dim.y;
                        dim.y = dummy;
                    }
                    bitmap = LocalDataUtil
                            .loadImageThumbnailFromStream(stream, data.getWidth(), data.getHeight(),
                                    (int) (dim.x * 0.7f), (int) (dim.y * 0.7),
                                    data.getRotation(), MAX_PEEK_BITMAP_PIXELS);
                    break;

                case LocalData.LOCAL_VIDEO:
                    bitmap = LocalDataUtil.loadVideoThumbnail(data.getPath());
                    break;

                default:
                    bitmap = null;
                    break;
            }

            if (bitmap == null) {
                return;
            }

            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onCallback(bitmap);
                }
            });
        }
    }

    private void showDetailsDialog(int dataId) {
        final LocalData data = mDataAdapter.getLocalData(dataId);
        if (data == null) {
            return;
        }
        MediaDetails details = data.getMediaDetails(getAndroidContext());
        if (details == null) {
            return;
        }
        Dialog detailDialog = DetailsDialog.create(CameraActivity.this, details);
        detailDialog.show();
        UsageStatistics.instance().mediaInteraction(
                fileNameFromDataID(dataId), MediaInteraction.InteractionType.DETAILS,
                NavigationChange.InteractionCause.BUTTON, fileAgeFromDataID(dataId));
    }

    /**
     * Show or hide action bar items depending on current data type.
     */
    private void updateActionBarMenu(LocalData data) {
        if (mActionBarMenu == null) {
            return;
        }

        MenuItem detailsMenuItem = mActionBarMenu.findItem(R.id.action_details);
        if (detailsMenuItem == null) {
            return;
        }

        int type = data.getLocalDataType();
        boolean showDetails = (type == LocalData.LOCAL_IMAGE) || (type == LocalData.LOCAL_VIDEO);
        detailsMenuItem.setVisible(showDetails);
    }
}
