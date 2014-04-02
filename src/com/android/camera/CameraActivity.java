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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
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
import android.util.CameraPerformanceTracker;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ShareActionProvider;

import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.CameraController;
import com.android.camera.app.CameraManager;
import com.android.camera.app.CameraManagerFactory;
import com.android.camera.app.CameraProvider;
import com.android.camera.app.CameraServices;
import com.android.camera.app.LocationManager;
import com.android.camera.app.ModuleManagerImpl;
import com.android.camera.app.OrientationManager;
import com.android.camera.app.OrientationManagerImpl;
import com.android.camera.data.CameraDataAdapter;
import com.android.camera.data.FixedLastDataAdapter;
import com.android.camera.data.LocalData;
import com.android.camera.data.LocalDataAdapter;
import com.android.camera.data.LocalDataUtil;
import com.android.camera.data.LocalMediaData;
import com.android.camera.data.LocalMediaObserver;
import com.android.camera.data.LocalSessionData;
import com.android.camera.data.MediaDetails;
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
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.session.CaptureSessionManager.SessionListener;
import com.android.camera.settings.CameraSettingsActivity;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsManager.SettingsCapabilities;
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
import com.android.camera.util.FeedbackHelper;
import com.android.camera.util.GalleryHelper;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.IntentHelper;
import com.android.camera.util.PhotoSphereHelper.PanoramaViewHelper;
import com.android.camera.util.ReleaseDialogHelper;
import com.android.camera.util.UsageStatistics;
import com.android.camera.widget.FilmstripView;
import com.android.camera.widget.Preloader;
import com.android.camera2.R;
import com.google.common.logging.eventprotos;
import com.google.common.logging.eventprotos.CameraEvent.InteractionCause;
import com.google.common.logging.eventprotos.NavigationChange;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends Activity
        implements AppController, CameraManager.CameraOpenCallback,
        ActionBar.OnMenuVisibilityListener, ShareActionProvider.OnShareTargetSelectedListener,
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

    /**
     * Request code from an activity we started that indicated that we do not
     * want to reset the view to the preview in onResume.
     */
    public static final int REQ_CODE_DONT_SWITCH_TO_PREVIEW = 142;

    public static final int REQ_CODE_GCAM_DEBUG_POSTCAPTURE = 999;

    private static final int MSG_CLEAR_SCREEN_ON_FLAG = 2;
    private static final long SCREEN_DELAY_MS = 2 * 60 * 1000; // 2 mins.
    private static final int MAX_PEEK_BITMAP_PIXELS = 1600000; // 1.6 * 4 MBs.
    /** Load metadata for 10 items ahead of our current. */
    private static final int FILMSTRIP_PRELOAD_AHEAD_ITEMS = 10;

    /** Should be used wherever a context is needed. */
    private Context mAppContext;

    /**
     * Whether onResume should reset the view to the preview.
     */
    private boolean mResetToPreviewOnResume = true;

    /**
     * This data adapter is used by FilmStripView.
     */
    private LocalDataAdapter mDataAdapter;

    /**
     * TODO: This should be moved to the app level.
     */
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

    private final Uri[] mNfcPushUris = new Uri[1];

    private LocalMediaObserver mLocalImagesObserver;
    private LocalMediaObserver mLocalVideosObserver;

    private boolean mPendingDeletion = false;

    private CameraController mCameraController;
    private boolean mPaused;
    private CameraAppUI mCameraAppUI;

    private PeekAnimationHandler mPeekAnimationHandler;
    private HandlerThread mPeekAnimationThread;

    private FeedbackHelper mFeedbackHelper;

    private Intent mGalleryIntent;
    private long mOnCreateTime;

    private Menu mActionBarMenu;
    private Preloader<Integer, AsyncTask> mPreloader;

    @Override
    public CameraAppUI getCameraAppUI() {
        return mCameraAppUI;
    }

    // close activity when screen turns off
    private final BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
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
                        return;
                    }
                    final Uri contentUri = data.getUri();
                    if (contentUri == Uri.EMPTY) {
                        return;
                    }

                    if (PanoramaMetadataLoader.isPanoramaAndUseViewer(data)) {
                        mPanoramaViewHelper.showPanorama(CameraActivity.this, contentUri);
                    } else if (RgbzMetadataLoader.hasRGBZData(data)) {
                        mPanoramaViewHelper.showRgbz(contentUri);
                        if (mSettingsManager.getBoolean(
                                SettingsManager.SETTING_SHOULD_SHOW_REFOCUS_VIEWER_CLING)) {
                            mSettingsManager.setBoolean(
                                    SettingsManager.SETTING_SHOULD_SHOW_REFOCUS_VIEWER_CLING,
                                    false);
                            mCameraAppUI.clearClingForViewer(
                                    CameraAppUI.BottomPanel.VIEWER_REFOCUS);
                        }
                    }
                }

                @Override
                public void onEdit() {
                    LocalData data = getCurrentLocalData();
                    if (data == null) {
                        return;
                    }
                    launchEditor(data);
                }

                @Override
                public void onTinyPlanet() {
                    LocalData data = getCurrentLocalData();
                    if (data == null) {
                        return;
                    }
                    launchTinyPlanetEditor(data);
                }

                @Override
                public void onDelete() {
                    final int currentDataId = getCurrentDataId();
                    UsageStatistics.instance().photoInteraction(
                            UsageStatistics.hashFileName(fileNameFromDataID(currentDataId)),
                            eventprotos.CameraEvent.InteractionType.DELETE,
                            InteractionCause.BUTTON);
                    removeData(currentDataId);
                }

                @Override
                public void onShare() {
                    final LocalData data = getCurrentLocalData();

                    // If applicable, show release information before this item
                    // is shared.
                    if (PanoramaMetadataLoader.isPanorama(data)
                            || RgbzMetadataLoader.hasRGBZData(data)) {
                        ReleaseDialogHelper.showReleaseInfoDialog(CameraActivity.this,
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
                        intent = Intent.createChooser(intent, null);
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

    private ComboPreferences mPreferences;

    @Override
    public void onCameraOpened(CameraManager.CameraProxy camera) {
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
        if (!mSettingsManager.isSet(SettingsManager.SETTING_FLASH_SUPPORTED_BACK_CAMERA)) {
            HardwareSpec hardware = new HardwareSpecImpl(camera.getParameters());
            mSettingsManager.setBoolean(SettingsManager.SETTING_FLASH_SUPPORTED_BACK_CAMERA,
                hardware.isFlashSupported());
        }

        if (!mModuleManager.getModuleAgent(mCurrentModeIndex).requestAppForCamera()) {
            // We shouldn't be here. Just close the camera and leave.
            camera.release(false);
            throw new IllegalStateException("Camera opened but the module shouldn't be " +
                    "requesting");
        }
        if (mCurrentModule != null) {
            SettingsCapabilities capabilities =
                    SettingsUtil.getSettingsCapabilities(camera);
            mSettingsManager.changeCamera(camera.getCameraId(), capabilities);
            mCurrentModule.onCameraAvailable(camera);
        }
        mCameraAppUI.onChangeCamera();
    }

    @Override
    public void onCameraDisabled(int cameraId) {
        UsageStatistics.instance().cameraFailure(
                eventprotos.CameraFailure.FailureReason.SECURITY);
        CameraUtil.showErrorAndFinish(this, R.string.camera_disabled);
    }

    @Override
    public void onDeviceOpenFailure(int cameraId) {
        UsageStatistics.instance().cameraFailure(
                eventprotos.CameraFailure.FailureReason.OPEN_FAILURE);
        CameraUtil.showErrorAndFinish(this, R.string.cannot_connect_camera);
    }

    @Override
    public void onDeviceOpenedAlready(int cameraId) {
        CameraUtil.showErrorAndFinish(this, R.string.cannot_connect_camera);
    }

    @Override
    public void onReconnectionFailure(CameraManager mgr) {
        UsageStatistics.instance().cameraFailure(
                eventprotos.CameraFailure.FailureReason.RECONNECT_FAILURE);
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

        File localFile = new File(localData.getPath());
        return localFile.getName();
    }

    private final FilmstripContentPanel.Listener mFilmstripListener =
            new FilmstripContentPanel.Listener() {

                @Override
                public void onSwipeOut() {
                    UsageStatistics.instance().changeScreen(
                            eventprotos.NavigationChange.Mode.PHOTO_CAPTURE,
                            eventprotos.CameraEvent.InteractionCause.SWIPE_RIGHT);
                }

                @Override
                public void onSwipeOutBegin() {
                    mActionBar.hide();
                    mFilmstripCoversPreview = false;
                    updatePreviewVisibility();
                }

                @Override
                public void onFilmstripHidden() {
                    mFilmstripVisible = false;
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
                    updateUiByData(mFilmstripController.getCurrentId());
                }

                @Override
                public void onFocusedDataLongPressed(int dataId) {
                    // Do nothing.
                }

                @Override
                public void onFocusedDataPromoted(int dataID) {
                    UsageStatistics.instance().photoInteraction(
                            UsageStatistics.hashFileName(fileNameFromDataID(dataID)),
                            eventprotos.CameraEvent.InteractionType.DELETE,
                            InteractionCause.SWIPE_UP);

                    removeData(dataID);
                }

                @Override
                public void onFocusedDataDemoted(int dataID) {
                    UsageStatistics.instance().photoInteraction(
                            UsageStatistics.hashFileName(fileNameFromDataID(dataID)),
                            eventprotos.CameraEvent.InteractionType.DELETE,
                            InteractionCause.SWIPE_DOWN);

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
                    int currentDataId = mFilmstripController.getCurrentId();
                    for (Integer dataId : updatedData) {
                        if (dataId == currentDataId) {
                            updateBottomControlsByData(mDataAdapter.getLocalData(dataId));
                        }
                    }
                }
            };

    public void gotoGallery() {
        UsageStatistics.instance().changeScreen(NavigationChange.Mode.FILMSTRIP,
                InteractionCause.BUTTON);

        mFilmstripController.goToNextItem();
    }

    /**
     * If 'visible' is false, this hides the action bar and switches the
     * filmstrip UI to lights-out mode.
     *
     * @param visible is false, this hides the action bar and switches the
     *            filmstrip UI to lights-out mode.
     */
    private void setFilmstripUiVisibility(boolean visible) {
        int currentSystemUIVisibility = mAboveFilmstripControlLayout.getSystemUiVisibility();
        int newSystemUIVisibility = (visible ? View.SYSTEM_UI_FLAG_VISIBLE
                : View.SYSTEM_UI_FLAG_FULLSCREEN);
        if (newSystemUIVisibility != currentSystemUIVisibility) {
            mAboveFilmstripControlLayout.setSystemUiVisibility(newSystemUIVisibility);
        }

        mCameraAppUI.getFilmstripBottomControls().setVisible(visible);
        if (visible != mActionBar.isShowing()) {
            if (visible) {
                mActionBar.show();
            } else {
                mActionBar.hide();
            }
        }
        mFilmstripCoversPreview = visible;
        updatePreviewVisibility();
    }

    private void hideSessionProgress() {
        mCameraAppUI.getFilmstripBottomControls().hideProgress();
    }

    private void showSessionProgress(CharSequence message) {
        CameraAppUI.BottomPanel controls =  mCameraAppUI.getFilmstripBottomControls();
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
    public void onMenuVisibilityChanged(boolean isVisible) {
        // TODO: Remove this or bring back the original implementation: cancel
        // auto-hide actionbar.
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider shareActionProvider, Intent intent) {
        int currentDataId = mFilmstripController.getCurrentId();
        if (currentDataId < 0) {
            return false;
        }
        UsageStatistics.instance().photoInteraction(
                UsageStatistics.hashFileName(fileNameFromDataID(currentDataId)),
                eventprotos.CameraEvent.InteractionType.SHARE,
                InteractionCause.BUTTON);
        // TODO add intent.getComponent().getPackageName()
        return true;
    }

    // Note: All callbacks come back on the main thread.
    private final SessionListener mSessionListener =
            new SessionListener() {
                @Override
                public void onSessionQueued(final Uri uri) {
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

                    final int pos = mDataAdapter.findDataByContentUri(sessionUri);
                    if (pos == -1) {
                        // We do not have a placeholder for this image, perhaps due to the
                        // activity crashing or being killed.
                        mDataAdapter.addData(newData);
                    }  else  {
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
                    mDataAdapter.refresh(uri);
                }

                @Override
                public void onSessionPreviewAvailable(Uri uri) {
                    mDataAdapter.refresh(uri);
                    int dataId = mDataAdapter.findDataByContentUri(uri);
                    if (dataId != -1) {
                        startPeekAnimation(mDataAdapter.getLocalData(dataId));
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
        startActivityForResult(intent, REQ_CODE_DONT_SWITCH_TO_PREVIEW);
    }

    @Override
    public int getCurrentModuleIndex() {
        return mCurrentModeIndex;
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
                new CameraManager.CameraPreviewDataCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, CameraManager.CameraProxy camera) {
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
        // TODO: implement this
    }

    @Override
    public boolean isShutterEnabled() {
        // TODO: implement this
        return false;
    }

    @Override
    public void startPreCaptureAnimation() {
        mCameraAppUI.startPreCaptureAnimation();
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
     */
    private void startPeekAnimation(final LocalData data) {
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
                mCameraAppUI.startPeekAnimation(result, true);
            }
        });
    }

    @Override
    public void notifyNewMedia(Uri uri) {
        ContentResolver cr = getContentResolver();
        String mimeType = cr.getType(uri);
        if (LocalDataUtil.isMimeTypeVideo(mimeType)) {
            sendBroadcast(new Intent(CameraUtil.ACTION_NEW_VIDEO, uri));
            LocalData newData = LocalMediaData.VideoData.fromContentUri(getContentResolver(), uri);
            if (newData == null) {
                Log.e(TAG, "Can't find video data in content resolver:" + uri);
                return;
            }
            if (mDataAdapter.addData(newData)) {
                startPeekAnimation(newData);
            }
        } else if (LocalDataUtil.isMimeTypeImage(mimeType)) {
            CameraUtil.broadcastNewPicture(mAppContext, uri);
            LocalData newData = LocalMediaData.PhotoData.fromContentUri(getContentResolver(), uri);
            if (newData == null) {
                Log.e(TAG, "Can't find photo data in content resolver:" + uri);
                return;
            }
            if (mDataAdapter.addData(newData)) {
                startPeekAnimation(newData);
            }
        } else {
            Log.w(TAG, "Unknown new media with MIME type:" + mimeType + ", uri:" + uri);
        }
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
                if (mFilmstripVisible && startGallery()) {
                    return true;
                }
                onBackPressed();
                return true;
            case R.id.action_details:
                showDetailsDialog(mFilmstripController.getCurrentId());
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

    private final SettingsManager.StrictUpgradeCallback mStrictUpgradeCallback
        = new SettingsManager.StrictUpgradeCallback() {
                @Override
                public void upgrade(SettingsManager settingsManager, int version) {
                    // Show the location dialog on upgrade if
                    //  (a) the user has never set this option (status quo).
                    //  (b) the user opt'ed out previously.
                    if (settingsManager.isSet(SettingsManager.SETTING_RECORD_LOCATION)) {
                        // Location is set in the source file defined for this setting.
                        // Remove the setting if the value is false to launch the dialog.
                        if (!settingsManager.getBoolean(SettingsManager.SETTING_RECORD_LOCATION)) {
                            settingsManager.remove(SettingsManager.SETTING_RECORD_LOCATION);
                        }
                    } else {
                        // Location is not set, check to see if we're upgrading from
                        // a different source file.
                        if (settingsManager.isSet(SettingsManager.SETTING_RECORD_LOCATION,
                                                  SettingsManager.SOURCE_GLOBAL)) {
                            boolean location = settingsManager.getBoolean(
                                SettingsManager.SETTING_RECORD_LOCATION,
                                SettingsManager.SOURCE_GLOBAL);
                            if (location) {
                                // Set the old setting only if the value is true, to prevent
                                // launching the dialog.
                                settingsManager.setBoolean(
                                    SettingsManager.SETTING_RECORD_LOCATION, location);
                            }
                        }
                    }

                    settingsManager.remove(SettingsManager.SETTING_STARTUP_MODULE_INDEX);
                }
            };

    private final CameraManager.CameraExceptionCallback mCameraDefaultExceptionCallback
        = new CameraManager.CameraExceptionCallback() {
                @Override
                public void onCameraException(RuntimeException e) {
                    Log.d(TAG, "Camera Exception", e);
                    CameraUtil.showErrorAndFinish(CameraActivity.this,
                            R.string.cannot_connect_camera);
                }
            };

    @Override
    public void onCreate(Bundle state) {
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_START);

        super.onCreate(state);
        mOnCreateTime = System.currentTimeMillis();
        mAppContext = getApplicationContext();
        GcamHelper.init(getContentResolver());

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.activity_main);
        mActionBar = getActionBar();
        mActionBar.addOnMenuVisibilityListener(this);
        mMainHandler = new MainHandler(this, getMainLooper());
        mCameraController =
                new CameraController(mAppContext, this, mMainHandler,
                        CameraManagerFactory.getAndroidCameraManager());
        mCameraController.setCameraDefaultExceptionCallback(mCameraDefaultExceptionCallback,
                mMainHandler);

        mPreferences = new ComboPreferences(mAppContext);

        mSettingsManager = new SettingsManager(mAppContext, this,
                mCameraController.getNumberOfCameras(), mStrictUpgradeCallback);

        // Remove this after we get rid of ComboPreferences.
        int cameraId = Integer.parseInt(mSettingsManager.get(SettingsManager.SETTING_CAMERA_ID));
        mPreferences.setLocalId(mAppContext, cameraId);
        CameraSettings.upgradeGlobalPreferences(mPreferences,
                mCameraController.getNumberOfCameras());
        // TODO: Try to move all the resources allocation to happen as soon as
        // possible so we can call module.init() at the earliest time.
        mModuleManager = new ModuleManagerImpl();
        ModulesInfo.setupModules(mAppContext, mModuleManager);

        mModeListView = (ModeListView) findViewById(R.id.mode_list_layout);
        mModeListView.init(mModuleManager.getSupportedModeIndexList());
        if (ApiHelper.HAS_ROTATION_ANIMATION) {
            setRotationAnimation();
        }
        mModeListView.setVisibilityChangedListener(new ModeListVisibilityChangedListener() {
            @Override
            public void onVisibilityChanged(boolean visible) {
                mModeListVisible = visible;
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
            // Foreground event caused by lock screen startup.
            // It is necessary to log this in onCreate, to avoid the
            // onResume->onPause->onResume sequence.
            UsageStatistics.instance().foregrounded(
                    eventprotos.ForegroundEvent.ForegroundSource.LOCK_SCREEN);

            // Change the window flags so that secure camera can show when
            // locked
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            win.setAttributes(params);

            // Filter for screen off so that we can finish secure camera
            // activity
            // when screen is off.
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(mScreenOffReceiver, filter);
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
        mDataAdapter = new CameraDataAdapter(mAppContext,
                new ColorDrawable(getResources().getColor(R.color.photo_placeholder)));
        mDataAdapter.setLocalDataListener(mLocalDataListener);

        mPreloader = new Preloader<Integer, AsyncTask>(FILMSTRIP_PRELOAD_AHEAD_ITEMS, mDataAdapter,
                mDataAdapter);

        mCameraAppUI.getFilmstripContentPanel().setFilmstripListener(mFilmstripListener);
        if (mSettingsManager.getBoolean(SettingsManager.SETTING_SHOULD_SHOW_REFOCUS_VIEWER_CLING)) {
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
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    UsageStatistics.instance().changeScreen(NavigationChange.Mode.GALLERY,
                            InteractionCause.BUTTON);
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
        if (FeedbackHelper.feedbackAvailable()) {
            mFeedbackHelper = new FeedbackHelper(mAppContext);
        }
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
        } else if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE.equals(getIntent().getAction())) {
            // TODO: synchronize mode options with photo module without losing
            // HDR+ preferences.
            modeIndex = photoIndex;
        } else if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(getIntent()
                        .getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(getIntent().getAction())) {
            modeIndex = mSettingsManager.getInt(
                SettingsManager.SETTING_KEY_CAMERA_MODULE_LAST_USED_INDEX);
        } else {
            // If the activity has not been started using an explicit intent,
            // read the module index from the last time the user changed modes
            modeIndex = mSettingsManager.getInt(SettingsManager.SETTING_STARTUP_MODULE_INDEX);
            if ((modeIndex == gcamIndex &&
                    !GcamHelper.hasGcamCapture()) || modeIndex < 0) {
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
        updatePreviewRendering(visibility);
        updateCaptureControls(visibility);
        mCurrentModule.onPreviewVisibilityChanged(visibility);
    }

    private void updateCaptureControls(int visibility) {
        if (visibility == ModuleController.VISIBILITY_HIDDEN) {
            mCameraAppUI.setIndicatorBottomBarWrapperVisible(false);
        } else {
            mCameraAppUI.setIndicatorBottomBarWrapperVisible(true);
        }
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
    public void onPause() {
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_PAUSE);

        /*
         * Save the last module index after all secure camera and icon launches,
         * not just on mode switches.
         *
         * Right now we exclude capture intents from this logic, because we also
         * ignore the cross-Activity recovery logic in onStart for capture intents.
         */
        if (!isCaptureIntent()) {
            mSettingsManager.setInt(SettingsManager.SETTING_STARTUP_MODULE_INDEX,
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
        // Close the camera and wait for the operation done.
        mCameraController.closeCamera();
        mPanoramaViewHelper.onPause();

        mLocalImagesObserver.setForegroundChangeListener(null);
        mLocalImagesObserver.setActivityPaused(true);
        mLocalVideosObserver.setActivityPaused(true);
        mPreloader.cancelAllLoads();
        resetScreenOn();
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CODE_DONT_SWITCH_TO_PREVIEW) {
            mResetToPreviewOnResume = false;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onResume() {
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_RESUME);

        mPaused = false;

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

        if (isCaptureIntent()) {
            // Foreground event caused by photo or video capure intent.
            UsageStatistics.instance().foregrounded(
                    eventprotos.ForegroundEvent.ForegroundSource.INTENT_PICKER);
        } else if (!mSecureCamera) {
            // Foreground event that is not caused by an intent.
            UsageStatistics.instance().foregrounded(
                    eventprotos.ForegroundEvent.ForegroundSource.ICON_LAUNCHER);
        }

        Drawable galleryLogo;
        if (mSecureCamera) {
            mGalleryIntent = null;
            galleryLogo = null;
        } else {
            mGalleryIntent = IntentHelper.getDefaultGalleryIntent(mAppContext);
            galleryLogo = IntentHelper.getGalleryIcon(mAppContext, mGalleryIntent);
        }
        if (galleryLogo == null) {
            try {
                galleryLogo = getPackageManager().getActivityLogo(getComponentName());
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Can't get the activity logo");
            }
        }
        if (mGalleryIntent != null) {
            mActionBar.setDisplayUseLogoEnabled(true);
        }
        mActionBar.setLogo(galleryLogo);
        mOrientationManager.resume();
        super.onResume();
        mPeekAnimationThread = new HandlerThread("Peek animation");
        mPeekAnimationThread.start();
        mPeekAnimationHandler = new PeekAnimationHandler(mPeekAnimationThread.getLooper());

        mCurrentModule.hardResetSettings(mSettingsManager);
        mCurrentModule.resume();
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

        mPanoramaViewHelper.onResume();
        ReleaseDialogHelper.showReleaseInfoDialogOnStart(this, mSettingsManager);
        syncLocationManagerSetting();

        final int previewVisibility = getPreviewVisibility();
        updatePreviewRendering(previewVisibility);
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
    public void onStart() {
        super.onStart();
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
    protected void onStop() {
        mPanoramaViewHelper.onStop();
        if (mFeedbackHelper != null) {
            mFeedbackHelper.stopFeedback();
        }

        mLocationManager.disconnect();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (mSecureCamera) {
            unregisterReceiver(mScreenOffReceiver);
        }
        mActionBar.removeOnMenuVisibilityListener(this);
        mSettingsManager.removeAllListeners();
        mCameraController.removeCallbackReceiver();
        getContentResolver().unregisterContentObserver(mLocalImagesObserver);
        getContentResolver().unregisterContentObserver(mLocalVideosObserver);
        getServices().getCaptureSessionManager().removeSessionListener(mSessionListener);
        mCameraAppUI.onDestroy();
        mModeListView.setVisibilityChangedListener(null);
        mCameraController = null;
        mSettingsManager = null;
        mCameraAppUI = null;
        mOrientationManager = null;
        mButtonManager = null;
        CameraManagerFactory.recycle();
        super.onDestroy();
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
                mModeListView.onMenuPressed();
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
        return super.onCreateOptionsMenu(menu);
    }

    protected void updateStorageSpace() {
        mStorageSpaceBytes = Storage.getAvailableSpace();
    }

    protected long getStorageSpaceBytes() {
        return mStorageSpaceBytes;
    }

    protected void updateStorageSpaceAndHint() {
        updateStorageSpace();
        updateStorageHint(mStorageSpaceBytes);
    }

    protected void updateStorageHint(long storageSpace) {
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
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(mAppContext, message);
            } else {
                mStorageHint.setText(message);
            }
            mStorageHint.show();
        } else if (mStorageHint != null) {
            mStorageHint.cancel();
            mStorageHint = null;
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
            boolean hdrPlusOn = mSettingsManager.isHdrPlusOn();
            if (hdrPlusOn && GcamHelper.hasGcamCapture()) {
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
            mSettingsManager.setInt(SettingsManager.SETTING_KEY_CAMERA_MODULE_LAST_USED_INDEX,
                    modeIndex);
        }

        closeModule(mCurrentModule);
        int oldModuleIndex = mCurrentModeIndex;

        // Select the correct module index from the mode switcher index.
        modeIndex = getPreferredChildModeIndex(modeIndex);
        setModuleFromModeIndex(modeIndex);

        mCameraAppUI.resetBottomControls(mCurrentModule, modeIndex);
        mCameraAppUI.addShutterListener(mCurrentModule);
        openModule(mCurrentModule);
        mCurrentModule.onOrientationChanged(mLastRawOrientation);
        // Store the module index so we can use it the next time the Camera
        // starts up.
        mSettingsManager.setInt(SettingsManager.SETTING_STARTUP_MODULE_INDEX, modeIndex);
    }

    /**
     * Shows the settings dialog.
     */
    @Override
    public void onSettingsSelected() {
        Intent intent = new Intent(this, CameraSettingsActivity.class);
        startActivity(intent);
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
            mCameraController.closeCamera();
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

    /**
     * Creates an AlertDialog appropriate for choosing whether to enable
     * location on the first run of the app.
     */
    public AlertDialog getFirstTimeLocationAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder = SettingsUtil.getFirstTimeLocationAlertBuilder(builder, new Callback<Boolean>() {
            @Override
            public void onCallback(Boolean locationOn) {
                mSettingsManager.setLocation(locationOn, mLocationManager);
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
            launchActivityByIntent(Intent.createChooser(intent, null));
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

    private void openModule(CameraModule module) {
        module.init(this, isSecureCamera(), isCaptureIntent());
        module.hardResetSettings(mSettingsManager);
        module.resume();
        updatePreviewVisibility();
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

    public CameraManager.CameraOpenCallback getCameraOpenErrorCallback() {
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

    /**
     * Reads the current location recording settings and passes it on to the
     * location manager.
     */
    public void syncLocationManagerSetting() {
        mSettingsManager.syncLocationManager(mLocationManager);
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
                    InteractionCause.BUTTON);
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

    private class PeekAnimationHandler extends Handler {
        private class DataAndCallback {
            LocalData mData;
            com.android.camera.util.Callback<Bitmap> mCallback;

            public DataAndCallback(LocalData data, com.android.camera.util.Callback<Bitmap>
                    callback) {
                mData = data;
                mCallback = callback;
            }
        }

        public PeekAnimationHandler(Looper looper) {
            super(looper);
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

        UsageStatistics.instance().photoInteraction(
                UsageStatistics.hashFileName(fileNameFromDataID(dataId)),
                eventprotos.CameraEvent.InteractionType.DETAILS,
                InteractionCause.BUTTON);
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
