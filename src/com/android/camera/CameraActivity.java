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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.CameraPerformanceTracker;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ShareActionProvider;
import android.widget.TextView;

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
import com.android.camera.data.InProgressDataWrapper;
import com.android.camera.data.LocalData;
import com.android.camera.data.LocalDataAdapter;
import com.android.camera.data.LocalMediaObserver;
import com.android.camera.data.PanoramaMetadataLoader;
import com.android.camera.data.RgbzMetadataLoader;
import com.android.camera.data.SimpleViewData;
import com.android.camera.filmstrip.FilmstripContentPanel;
import com.android.camera.filmstrip.FilmstripController;
import com.android.camera.module.ModuleController;
import com.android.camera.module.ModulesInfo;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.session.CaptureSessionManager.SessionListener;
import com.android.camera.session.PlaceholderManager;
import com.android.camera.settings.CameraSettingsActivity;
import com.android.camera.settings.SettingsManager;
import com.android.camera.settings.SettingsManager.SettingsCapabilities;
import com.android.camera.settings.SettingsUtil;
import com.android.camera.tinyplanet.TinyPlanetFragment;
import com.android.camera.ui.MainActivityLayout;
import com.android.camera.ui.ModeListView;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.Callback;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.FeedbackHelper;
import com.android.camera.util.GalleryHelper;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.IntentHelper;
import com.android.camera.util.PhotoSphereHelper.PanoramaViewHelper;
import com.android.camera.util.UsageStatistics;
import com.android.camera.widget.FilmstripView;
import com.android.camera2.R;
import com.google.common.logging.eventprotos;
import com.google.common.logging.eventprotos.CameraEvent.InteractionCause;
import com.google.common.logging.eventprotos.NavigationChange;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends Activity
        implements AppController, CameraManager.CameraOpenCallback,
        ActionBar.OnMenuVisibilityListener, ShareActionProvider.OnShareTargetSelectedListener,
        OrientationManager.OnOrientationChangeListener {

    private static final String TAG = "CameraActivity";

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

    private static final int MSG_HIDE_ACTION_BAR = 1;
    private static final int MSG_CLEAR_SCREEN_ON_FLAG = 2;
    private static final long SCREEN_DELAY_MS = 2 * 60 * 1000; // 2 mins.

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
    private int mCurrentModeIndex;
    private CameraModule mCurrentModule;
    private ModuleManagerImpl mModuleManager;
    private FrameLayout mAboveFilmstripControlLayout;
    private FilmstripController mFilmstripController;
    private boolean mFilmstripVisible;
    private TextView mBottomProgressText;
    private ProgressBar mBottomProgressBar;
    private View mSessionProgressPanel;
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
    private boolean mUpAsGallery;
    private CameraAppUI mCameraAppUI;

    private FeedbackHelper mFeedbackHelper;

    private Intent mGalleryIntent;
    private long mOnCreateTime;

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
    private final CameraAppUI.BottomControls.Listener mMyFilmstripBottomControlListener =
            new CameraAppUI.BottomControls.Listener() {

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
                    final Uri contentUri = data.getContentUri();
                    if (contentUri == Uri.EMPTY) {
                        return;
                    }

                    if (PanoramaMetadataLoader.isPanorama(data)) {
                        mPanoramaViewHelper.showPanorama(contentUri);
                    } else if (RgbzMetadataLoader.hasRGBZData(data)) {
                        mPanoramaViewHelper.showRgbz(contentUri);
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
                    UsageStatistics.photoInteraction(
                            UsageStatistics.hashFileName(fileNameFromDataID(currentDataId)),
                            eventprotos.CameraEvent.InteractionType.DELETE,
                            InteractionCause.BUTTON);
                    removeData(currentDataId);
                }

                @Override
                public void onShare() {
                    final LocalData data = getCurrentLocalData();
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
                    final Uri contentUri = data.getContentUri();
                    if (PanoramaMetadataLoader.isPanorama360(data) &&
                            data.getContentUri() != Uri.EMPTY) {
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
            };

    private ComboPreferences mPreferences;
    private ContentResolver mContentResolver;

    @Override
    public void onCameraOpened(CameraManager.CameraProxy camera) {
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
        UsageStatistics.cameraFailure(eventprotos.CameraFailure.FailureReason.SECURITY);

        CameraUtil.showErrorAndFinish(this, R.string.camera_disabled);
    }

    @Override
    public void onDeviceOpenFailure(int cameraId) {
        UsageStatistics.cameraFailure(eventprotos.CameraFailure.FailureReason.OPEN_FAILURE);

        CameraUtil.showErrorAndFinish(this, R.string.cannot_connect_camera);
    }

    @Override
    public void onReconnectionFailure(CameraManager mgr) {
        UsageStatistics.cameraFailure(eventprotos.CameraFailure.FailureReason.RECONNECT_FAILURE);

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
                case MSG_HIDE_ACTION_BAR: {
                    removeMessages(MSG_HIDE_ACTION_BAR);
                    activity.setFilmstripUiVisibility(false);
                    break;
                }

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
                    UsageStatistics.changeScreen(eventprotos.NavigationChange.Mode.PHOTO_CAPTURE,
                            eventprotos.CameraEvent.InteractionCause.SWIPE_RIGHT);
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
                    if (mCurrentModule != null) {
                        mCurrentModule.onPreviewVisibilityChanged(true);
                    }
                }

                @Override
                public void onFilmstripShown() {
                    mFilmstripVisible = true;
                    updateUiByData(mFilmstripController.getCurrentId());
                    if (mCurrentModule != null) {
                        mCurrentModule.onPreviewVisibilityChanged(false);
                    }
                }

                @Override
                public void onDataPromoted(int dataID) {
                    UsageStatistics.photoInteraction(
                            UsageStatistics.hashFileName(fileNameFromDataID(dataID)),
                            eventprotos.CameraEvent.InteractionType.DELETE,
                            InteractionCause.SWIPE_UP);

                    removeData(dataID);
                }

                @Override
                public void onDataDemoted(int dataID) {
                    UsageStatistics.photoInteraction(
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
                    if (mGalleryIntent != null) {
                        mActionBar.setDisplayUseLogoEnabled(true);
                        mUpAsGallery = true;
                    }
                    if (mFilmstripVisible) {
                        CameraActivity.this.setFilmstripUiVisibility(true);
                    }
                }

                @Override
                public void onLeaveFilmstrip(int dataId) {
                    if (mGalleryIntent != null) {
                        mActionBar.setDisplayUseLogoEnabled(false);
                        mUpAsGallery = false;
                    }
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
        UsageStatistics.changeScreen(NavigationChange.Mode.FILMSTRIP,
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
    // TODO: This should not be called outside of the activity.
    public void setFilmstripUiVisibility(boolean visible) {
        mMainHandler.removeMessages(MSG_HIDE_ACTION_BAR);

        int currentSystemUIVisibility = mAboveFilmstripControlLayout.getSystemUiVisibility();
        int newSystemUIVisibility = (visible ? View.SYSTEM_UI_FLAG_VISIBLE
                : View.SYSTEM_UI_FLAG_FULLSCREEN);
        if (newSystemUIVisibility != currentSystemUIVisibility) {
            mAboveFilmstripControlLayout.setSystemUiVisibility(newSystemUIVisibility);
        }

        boolean currentActionBarVisibility = mActionBar.isShowing();
        mCameraAppUI.getFilmstripBottomControls().setVisible(visible);
        if (visible != currentActionBarVisibility) {
            if (visible) {
                mActionBar.show();
            } else {
                mActionBar.hide();
            }
        }
    }

    private void hideSessionProgress() {
        mSessionProgressPanel.setVisibility(View.GONE);
    }

    private void showSessionProgress(CharSequence message) {
        mBottomProgressText.setText(message);
        mSessionProgressPanel.setVisibility(View.VISIBLE);
    }

    private void updateSessionProgress(int progress) {
        mBottomProgressBar.setProgress(progress);
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
        UsageStatistics.photoInteraction(
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
                    notifyNewMedia(uri);
                    int dataID = mDataAdapter.findDataByContentUri(uri);
                    if (dataID != -1) {
                        // Don't allow special UI actions (swipe to
                        // delete, for example) on in-progress data.
                        LocalData d = mDataAdapter.getLocalData(dataID);
                        InProgressDataWrapper newData = new InProgressDataWrapper(d);
                        mDataAdapter.updateData(dataID, newData);
                    }
                }

                @Override
                public void onSessionDone(final Uri uri) {
                    Log.v(TAG, "onSessionDone:" + uri);
                    int doneID = mDataAdapter.findDataByContentUri(uri);
                    int currentDataId = mFilmstripController.getCurrentId();

                    if (currentDataId == doneID) {
                        hideSessionProgress();
                        updateSessionProgress(0);
                    }
                    mDataAdapter.refresh(uri, /* isInProgress */false);
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
                            mDataAdapter.getLocalData(currentDataId).getContentUri())) {
                        updateSessionProgress(progress);
                    }
                }

                @Override
                public void onSessionUpdated(Uri uri) {
                    mDataAdapter.refresh(uri, /* isInProgress */true);
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
            PreviewStatusListener.PreviewAreaSizeChangedListener listener) {
        mCameraAppUI.addPreviewAreaSizeChangedListener(listener);
    }

    @Override
    public void removePreviewAreaSizeChangedListener(
            PreviewStatusListener.PreviewAreaSizeChangedListener listener) {
        mCameraAppUI.removePreviewAreaSizeChangedListener(listener);
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
                });
    }

    @Override
    public void updatePreviewAspectRatio(float aspectRatio) {
        mCameraAppUI.updatePreviewAspectRatio(aspectRatio);
    }

    @Override
    public boolean shouldShowShimmy() {
        int remainingTimes = mSettingsManager.getInt(
                SettingsManager.SETTING_SHIMMY_REMAINING_PLAY_TIMES_INDEX);
        return remainingTimes > 0;
    }

    @Override
    public void decrementShimmyPlayTimes() {
        int remainingTimes = mSettingsManager.getInt(
                SettingsManager.SETTING_SHIMMY_REMAINING_PLAY_TIMES_INDEX) - 1;
        if (remainingTimes >= 0) {
            mSettingsManager.setInt(SettingsManager.SETTING_SHIMMY_REMAINING_PLAY_TIMES_INDEX,
                    remainingTimes);
        }
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
        mOrientationManager.lockOrientation();
    }

    @Override
    public void unlockOrientation() {
        mOrientationManager.unlockOrientation();
    }

    @Override
    public void notifyNewMedia(Uri uri) {
        ContentResolver cr = getContentResolver();
        String mimeType = cr.getType(uri);
        if (mimeType.startsWith("video/")) {
            sendBroadcast(new Intent(CameraUtil.ACTION_NEW_VIDEO, uri));
            mDataAdapter.addNewVideo(uri);
        } else if (mimeType.startsWith("image/")) {
            CameraUtil.broadcastNewPicture(mAppContext, uri);
            mDataAdapter.addNewPhoto(uri);
        } else if (mimeType.startsWith(PlaceholderManager.PLACEHOLDER_MIME_TYPE)) {
            mDataAdapter.addNewPhoto(uri);
        } else {
            android.util.Log.w(TAG, "Unknown new media with MIME type:"
                    + mimeType + ", uri:" + uri);
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
                if (mFilmstripVisible && mUpAsGallery && startGallery()) {
                    return true;
                }
                onBackPressed();
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

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_START);
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
        mPreferences = new ComboPreferences(mAppContext);
        mContentResolver = this.getContentResolver();

        mSettingsManager = new SettingsManager(mAppContext, this,
                mCameraController.getNumberOfCameras());

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
            UsageStatistics.foregrounded(
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
        mSessionProgressPanel = findViewById(R.id.pano_session_progress_panel);
        mBottomProgressBar = (ProgressBar) findViewById(R.id.pano_session_progress_bar);
        mBottomProgressText = (TextView) findViewById(R.id.pano_session_progress_text);
        mFilmstripController = ((FilmstripView) findViewById(R.id.filmstrip_view)).getController();
        mFilmstripController.setImageGap(
                getResources().getDimensionPixelSize(R.dimen.camera_film_strip_gap));
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        // Set up the camera preview first so the preview shows up ASAP.
        mDataAdapter = new CameraDataAdapter(mAppContext,
                new ColorDrawable(getResources().getColor(R.color.photo_placeholder)));
        mDataAdapter.setLocalDataListener(mLocalDataListener);

        mCameraAppUI.getFilmstripContentPanel().setFilmstripListener(mFilmstripListener);

        mLocationManager = new LocationManager(mAppContext);

        int modeIndex = -1;
        int photoIndex = getResources().getInteger(R.integer.camera_mode_photo);
        int videoIndex = getResources().getInteger(R.integer.camera_mode_video);
        int gcamIndex = getResources().getInteger(R.integer.camera_mode_gcam);
        if (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(getIntent().getAction())
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())) {
            modeIndex = videoIndex;
        } else if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(getIntent().getAction())
                || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(getIntent()
                        .getAction())) {
            modeIndex = photoIndex;
            if (mSettingsManager.getInt(SettingsManager.SETTING_STARTUP_MODULE_INDEX)
                        == gcamIndex && GcamHelper.hasGcamCapture()) {
                modeIndex = gcamIndex;
            }
        } else if (MediaStore.ACTION_IMAGE_CAPTURE.equals(getIntent().getAction())
                || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(getIntent().getAction())) {
            modeIndex = photoIndex;
        } else {
            // If the activity has not been started using an explicit intent,
            // read the module index from the last time the user changed modes
            modeIndex = mSettingsManager.getInt(SettingsManager.SETTING_STARTUP_MODULE_INDEX);
            if ((modeIndex == gcamIndex &&
                    !GcamHelper.hasGcamCapture()) || modeIndex < 0) {
                modeIndex = photoIndex;
            }
        }

        mOrientationManager = new OrientationManagerImpl(this);
        mOrientationManager.addOnOrientationChangeListener(mMainHandler, this);

        setModuleFromModeIndex(modeIndex);
        mCameraAppUI.prepareModuleUI();
        mCurrentModule.init(this, isSecureCamera(), isCaptureIntent());

        if (!mSecureCamera) {
            mFilmstripController.setDataAdapter(mDataAdapter);
            if (!isCaptureIntent()) {
                mDataAdapter.requestLoad();
            }
        } else {
            // Put a lock placeholder as the last image by setting its date to
            // 0.
            ImageView v = (ImageView) getLayoutInflater().inflate(
                    R.layout.secure_album_placeholder, null);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    UsageStatistics.changeScreen(NavigationChange.Mode.GALLERY,
                            InteractionCause.BUTTON);
                    startGallery();
                    finish();
                }
            });
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
        mPaused = true;
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_PAUSE);

        // Delete photos that are pending deletion
        performDeletion();
        mCurrentModule.pause();
        mOrientationManager.pause();
        // Close the camera and wait for the operation done.
        mCameraController.closeCamera();
        mPanoramaViewHelper.onPause();

        mLocalImagesObserver.setActivityPaused(true);
        mLocalVideosObserver.setActivityPaused(true);
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
        mPaused = false;
        CameraPerformanceTracker.onEvent(CameraPerformanceTracker.ACTIVITY_RESUME);

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
            UsageStatistics.foregrounded(
                    eventprotos.ForegroundEvent.ForegroundSource.INTENT_PICKER);
        } else if (!mSecureCamera) {
            // Foreground event that is not caused by an intent.
            UsageStatistics.foregrounded(
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
        mActionBar.setLogo(galleryLogo);
        mOrientationManager.resume();
        super.onResume();
        mCurrentModule.resume();
        setSwipingEnabled(true);

        if (mResetToPreviewOnResume) {
            mCameraAppUI.resume();
        } else {
            LocalData data = mDataAdapter.getLocalData(mFilmstripController.getCurrentId());
            if (data != null) {
                mDataAdapter.refresh(data.getContentUri(), false);
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
                    mDataAdapter.requestLoad();
                }
            }
        }
        mLocalImagesObserver.setActivityPaused(false);
        mLocalVideosObserver.setActivityPaused(false);

        keepScreenOnForAWhile();

        // Lights-out mode at all times.
        findViewById(R.id.activity_root_view)
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        mPanoramaViewHelper.onResume();
    }

    @Override
    public void onStart() {
        super.onStart();
        mPanoramaViewHelper.onStart();
        boolean recordLocation = RecordLocationPreference.get(
                mPreferences, mContentResolver);
        mLocationManager.recordLocation(recordLocation);
    }

    @Override
    protected void onStop() {
        mPanoramaViewHelper.onStop();
        if (mFeedbackHelper != null) {
            mFeedbackHelper.stopFeedback();
        }

        mLocationManager.disconnect();
        CameraManagerFactory.recycle();
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
        mCameraController = null;
        mSettingsManager = null;
        mCameraAppUI = null;
        mOrientationManager = null;
        mButtonManager = null;
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
        if (!mFilmstripVisible && mCurrentModule.onKeyUp(keyCode, event)) {
            return true;
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

    public boolean isAutoRotateScreen() {
        return mAutoRotateScreen;
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

        // Refocus and Gcam are modes that cannot be selected
        // from the mode list view, because they are not list items.
        // Check whether we should interpret MODULE_CRAFT as either.
        if (modeIndex == getResources().getInteger(R.integer.camera_mode_photo)) {
            boolean hdrPlusOn = mSettingsManager.isHdrPlusOn();
            if (hdrPlusOn && GcamHelper.hasGcamCapture()) {
                modeIndex = getResources().getInteger(R.integer.camera_mode_gcam);
            }
        }

        setModuleFromModeIndex(modeIndex);
        mCameraAppUI.clearModuleUI();

        mCameraAppUI.resetBottomControls(mCurrentModule, modeIndex);
        openModule(mCurrentModule);
        mCurrentModule.onOrientationChanged(mLastRawOrientation);
        // Store the module index so we can use it the next time the Camera
        // starts up.
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mAppContext);
        prefs.edit().putInt(CameraSettings.KEY_STARTUP_MODULE_INDEX, modeIndex).apply();
    }

    /**
     * Shows the settings dialog.
     */
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
                .setDataAndType(data.getContentUri(), data.getMimeType())
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
        bundle.putString(TinyPlanetFragment.ARGUMENT_URI, data.getContentUri().toString());
        bundle.putString(TinyPlanetFragment.ARGUMENT_TITLE, data.getTitle());
        fragment.setArguments(bundle);
        fragment.show(getFragmentManager(), "tiny_planet");
    }

    private void openModule(CameraModule module) {
        module.init(this, isSecureCamera(), isCaptureIntent());
        module.resume();
        module.onPreviewVisibilityChanged(!mFilmstripVisible);
    }

    private void closeModule(CameraModule module) {
        module.pause();
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
            UsageStatistics.changeScreen(NavigationChange.Mode.GALLERY, InteractionCause.BUTTON);
            Intent startGalleryIntent = new Intent(mGalleryIntent);
            int currentDataId = mFilmstripController.getCurrentId();
            LocalData currentLocalData = mDataAdapter.getLocalData(currentDataId);
            if (currentLocalData != null) {
                GalleryHelper.setContentUri(startGalleryIntent, currentLocalData.getContentUri());
            }
            launchActivityByIntent(startGalleryIntent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Failed to launch gallery activity, closing");
        }
        return false;
    }

    private void setNfcBeamPushUriFromData(LocalData data) {
        final Uri uri = data.getContentUri();
        if (uri != Uri.EMPTY) {
            mNfcPushUris[0] = uri;
        } else {
            mNfcPushUris[0] = null;
        }
    }

    /**
     * Updates the visibility of the filmstrip bottom controls.
     */
    private void updateUiByData(final int dataId) {
        if (isSecureCamera()) {
            // We cannot show buttons in secure camera since go to other
            // activities might create a security hole.
            return;
        }

        final LocalData currentData = mDataAdapter.getLocalData(dataId);
        if (currentData == null) {
            Log.w(TAG, "Current data ID not found.");
            hideSessionProgress();
            return;
        }

        setNfcBeamPushUriFromData(currentData);

        /* Bottom controls. */

        updateBottomControlsByData(currentData);
        if (!mDataAdapter.isMetadataUpdated(dataId)) {
            mDataAdapter.updateMetadata(dataId);
        }
    }

    /**
     * Updates the bottom controls based on the data.
     */
    private void updateBottomControlsByData(final LocalData currentData) {

        final CameraAppUI.BottomControls filmstripBottomControls =
                mCameraAppUI.getFilmstripBottomControls();
        filmstripBottomControls.setEditButtonVisibility(
                currentData.isDataActionSupported(LocalData.DATA_ACTION_EDIT));
        filmstripBottomControls.setShareButtonVisibility(
                currentData.isDataActionSupported(LocalData.DATA_ACTION_SHARE));
        filmstripBottomControls.setDeleteButtonVisibility(
                currentData.isDataActionSupported(LocalData.DATA_ACTION_DELETE));

        /* Progress bar */

        Uri contentUri = currentData.getContentUri();
        CaptureSessionManager sessionManager = getServices()
                .getCaptureSessionManager();
        int sessionProgress = sessionManager.getSessionProgress(contentUri);

        if (sessionProgress < 0) {
            hideSessionProgress();
        } else {
            CharSequence progressMessage = sessionManager
                    .getSessionProgressMessage(contentUri);
            showSessionProgress(progressMessage);
            updateSessionProgress(sessionProgress);
        }

        /* View button */

        // We need to add this to a separate DB.
        final int viewButtonVisibility;
        if (PanoramaMetadataLoader.isPanorama(currentData)) {
            viewButtonVisibility = CameraAppUI.BottomControls.VIEWER_PHOTO_SPHERE;
        } else if (RgbzMetadataLoader.hasRGBZData(currentData)) {
            viewButtonVisibility = CameraAppUI.BottomControls.VIEWER_REFOCUS;
        } else {
            viewButtonVisibility = CameraAppUI.BottomControls.VIEWER_NONE;
        }

        filmstripBottomControls.setTinyPlanetEnabled(
                PanoramaMetadataLoader.isPanorama360(currentData));
        filmstripBottomControls.setViewerButtonVisibility(viewButtonVisibility);
    }
}
