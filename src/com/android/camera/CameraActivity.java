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

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.android.camera.data.CameraDataAdapter;
import com.android.camera.data.CameraPreviewData;
import com.android.camera.data.FixedFirstDataAdapter;
import com.android.camera.data.FixedLastDataAdapter;
import com.android.camera.data.LocalData;
import com.android.camera.data.LocalDataAdapter;
import com.android.camera.data.SimpleViewData;
import com.android.camera.util.ApiHelper;
import com.android.camera.ui.CameraSwitcher;
import com.android.camera.ui.CameraSwitcher.CameraSwitchListener;
import com.android.camera.ui.FilmStripView;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.PhotoSphereHelper.PanoramaViewHelper;
import com.android.camera.util.PhotoSphereHelper;
import com.android.camera2.R;

public class CameraActivity extends Activity
    implements CameraSwitchListener {

    private static final String TAG = "CAM_Activity";

    private static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE =
            "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE =
            "android.media.action.IMAGE_CAPTURE_SECURE";

    // The intent extra for camera from secure lock screen. True if the gallery
    // should only show newly captured pictures. sSecureAlbumId does not
    // increment. This is used when switching between camera, camcorder, and
    // panorama. If the extra is not set, it is in the normal camera mode.
    public static final String SECURE_CAMERA_EXTRA = "secure_camera";

    // Supported operations at FilmStripView. Different data has different
    // set of supported operations.
    private static final int SUPPORT_DELETE = 1 << 0;
    private static final int SUPPORT_ROTATE = 1 << 1;
    private static final int SUPPORT_INFO = 1 << 2;
    private static final int SUPPORT_CROP = 1 << 3;
    private static final int SUPPORT_SETAS = 1 << 4;
    private static final int SUPPORT_EDIT = 1 << 5;
    private static final int SUPPORT_TRIM = 1 << 6;
    private static final int SUPPORT_MUTE = 1 << 7;
    private static final int SUPPORT_SHOW_ON_MAP = 1 << 8;
    private static final int SUPPORT_ALL = 0xffffffff;

    /** This data adapter is used by FilmStirpView. */
    private LocalDataAdapter mDataAdapter;
    /** This data adapter represents the real local camera data. */
    private LocalDataAdapter mWrappedDataAdapter;

    private PanoramaStitchingManager mPanoramaManager;
    private int mCurrentModuleIndex;
    private CameraModule mCurrentModule;
    private View mRootView;
    private FilmStripView mFilmStripView;
    private ProgressBar mBottomProgress;
    private View mPanoStitchingPanel;
    private int mResultCodeForTesting;
    private Intent mResultDataForTesting;
    private OnScreenHint mStorageHint;
    private long mStorageSpace = Storage.LOW_STORAGE_THRESHOLD;
    private boolean mAutoRotateScreen;
    private boolean mSecureCamera;
    // This is a hack to speed up the start of SecureCamera.
    private static boolean sFirstStartAfterScreenOn = true;
    private boolean mShowCameraPreview;
    private int mLastRawOrientation;
    private MyOrientationEventListener mOrientationListener;
    private Handler mMainHandler;
    private PanoramaViewHelper mPanoramaViewHelper;
    private CameraPreviewData mCameraPreviewData;
    private ActionBar mActionBar;
    private Menu mActionBarMenu;

    private class MyOrientationEventListener
        extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mLastRawOrientation = orientation;
            mCurrentModule.onOrientationChanged(orientation);
        }
    }

    private MediaSaveService mMediaSaveService;
    private ServiceConnection mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder b) {
                mMediaSaveService = ((MediaSaveService.LocalBinder) b).getService();
                mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
            }
            @Override
            public void onServiceDisconnected(ComponentName className) {
                mMediaSaveService = null;
            }};

    // close activity when screen turns off
    private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    private static BroadcastReceiver sScreenOffReceiver;
    private static class ScreenOffReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            sFirstStartAfterScreenOn = true;
        }
    }

    public static boolean isFirstStartAfterScreenOn() {
        return sFirstStartAfterScreenOn;
    }

    public static void resetFirstStartAfterScreenOn() {
        sFirstStartAfterScreenOn = false;
    }

    private FilmStripView.Listener mFilmStripListener =
            new FilmStripView.Listener() {
                @Override
                public void onDataPromoted(int dataID) {
                    removeData(dataID);
                }

                @Override
                public void onDataDemoted(int dataID) {
                    removeData(dataID);
                }

                @Override
                public void onDataFullScreenChange(int dataID, boolean full) {
                }

                @Override
                public void onSwitchMode(boolean toCamera) {
                    mCurrentModule.onSwitchMode(toCamera);
                    if (toCamera) {
                        mActionBar.hide();
                    } else {
                        mActionBar.show();
                    }
                }

                @Override
                public void onCurrentDataChanged(int dataID, boolean current) {
                    if (!current) {
                        hidePanoStitchingProgress();
                    } else {
                        LocalData currentData = mDataAdapter.getLocalData(dataID);
                        if (currentData == null) {
                            Log.w(TAG, "Current data ID not found.");
                            hidePanoStitchingProgress();
                            return;
                        }
                        int type = currentData.getLocalDataType(dataID);
                        updateActionBarMenu(type);

                        Uri contentUri = currentData.getContentUri();
                        if (contentUri == null) {
                            hidePanoStitchingProgress();
                            return;
                        }
                        int panoStitchingProgress = mPanoramaManager.getTaskProgress(contentUri);
                        if (panoStitchingProgress < 0) {
                            hidePanoStitchingProgress();
                            return;
                        }
                        showPanoStitchingProgress();
                        updateStitchingProgress(panoStitchingProgress);
                    }
                }
            };

    private void hidePanoStitchingProgress() {
        mPanoStitchingPanel.setVisibility(View.GONE);
    }

    private void showPanoStitchingProgress() {
        mPanoStitchingPanel.setVisibility(View.VISIBLE);
    }

    private void updateStitchingProgress(int progress) {
        mBottomProgress.setProgress(progress);
    }

    /**
     * According to the data type, make the menu items for supported operations
     * visible.
     * @param type : the data type for the current local data.
     */
    private void updateActionBarMenu(int type) {
        if (mActionBarMenu == null) {
            return;
        }

        int supported = 0;
        switch (type) {
            case LocalData.LOCAL_IMAGE:
                supported |= SUPPORT_DELETE | SUPPORT_ROTATE | SUPPORT_INFO
                        | SUPPORT_CROP | SUPPORT_SETAS | SUPPORT_EDIT
                        | SUPPORT_SHOW_ON_MAP;
                break;
            case LocalData.LOCAL_VIDEO:
                supported |= SUPPORT_DELETE | SUPPORT_INFO | SUPPORT_TRIM
                        | SUPPORT_MUTE;
                break;
            case LocalData.LOCAL_PHOTO_SPHERE:
                supported |= SUPPORT_DELETE | SUPPORT_ROTATE | SUPPORT_INFO
                        | SUPPORT_CROP | SUPPORT_SETAS | SUPPORT_EDIT
                        | SUPPORT_SHOW_ON_MAP;
                break;
            default:
                break;
        }

        setMenuItemVisible(mActionBarMenu, R.id.action_delete,
                (supported & SUPPORT_DELETE) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_rotate_ccw,
                (supported & SUPPORT_ROTATE) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_rotate_cw,
                (supported & SUPPORT_ROTATE) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_crop,
                (supported & SUPPORT_CROP) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_trim,
                (supported & SUPPORT_TRIM) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_mute,
                (supported & SUPPORT_MUTE) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_setas,
                (supported & SUPPORT_SETAS) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_show_on_map,
                (supported & SUPPORT_SHOW_ON_MAP) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_edit,
                (supported & SUPPORT_EDIT) != 0);
        setMenuItemVisible(mActionBarMenu, R.id.action_details,
                (supported & SUPPORT_INFO) != 0);
    }

    private void setMenuItemVisible(Menu menu, int itemId, boolean visible) {
        MenuItem item = menu.findItem(itemId);
        if (item != null)
            item.setVisible(visible);
    }

    private Runnable mDeletionRunnable = new Runnable() {
            @Override
            public void run() {
                mDataAdapter.executeDeletion(CameraActivity.this);
            }
        };

    private ImageTaskManager.TaskListener mStitchingListener =
            new ImageTaskManager.TaskListener() {
                @Override
                public void onTaskQueued(String filePath, final Uri imageUri) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            notifyNewMedia(imageUri);
                        }
                    });
                }

                @Override
                public void onTaskDone(String filePath, final Uri imageUri) {
                    Log.v(TAG, "onTaskDone:" + filePath);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            int doneID = mDataAdapter.findDataByContentUri(imageUri);
                            int currentDataId = mFilmStripView.getCurrentId();

                            if (currentDataId == doneID) {
                                hidePanoStitchingProgress();
                                updateStitchingProgress(0);
                            }

                            mDataAdapter.refresh(getContentResolver(), imageUri);
                        }
                    });
                }

                @Override
                public void onTaskProgress(
                        String filePath, final Uri imageUri, final int progress) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            int currentDataId = mFilmStripView.getCurrentId();
                            if (currentDataId == -1) {
                                return;
                            }
                            if (imageUri.equals(
                                    mDataAdapter.getLocalData(currentDataId).getContentUri())) {
                                updateStitchingProgress(progress);
                            }
                        }
                    });
                }
            };

    public MediaSaveService getMediaSaveService() {
        return mMediaSaveService;
    }

    public void notifyNewMedia(Uri uri) {
        ContentResolver cr = getContentResolver();
        String mimeType = cr.getType(uri);
        if (mimeType.startsWith("video/")) {
            sendBroadcast(new Intent(CameraUtil.ACTION_NEW_VIDEO, uri));
            mDataAdapter.addNewVideo(cr, uri);
        } else if (mimeType.startsWith("image/")) {
            CameraUtil.broadcastNewPicture(this, uri);
            mDataAdapter.addNewPhoto(cr, uri);
        } else if (mimeType.startsWith("application/stitching-preview")) {
            mDataAdapter.addNewPhoto(cr, uri);
        } else {
            android.util.Log.w(TAG, "Unknown new media with MIME type:"
                    + mimeType + ", uri:" + uri);
        }
    }

    private void removeData(int dataID) {
        mDataAdapter.removeData(CameraActivity.this, dataID);
        mMainHandler.removeCallbacks(mDeletionRunnable);
        mMainHandler.postDelayed(mDeletionRunnable, 3000);
    }

    private void bindMediaSaveService() {
        Intent intent = new Intent(this, MediaSaveService.class);
        startService(intent);  // start service before binding it so the
                               // service won't be killed if we unbind it.
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindMediaSaveService() {
        if (mMediaSaveService != null) {
            mMediaSaveService.setListener(null);
        }
        if (mConnection != null) {
            unbindService(mConnection);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.operations, menu);
        mActionBarMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_delete:
                // TODO: add the functionality.
                return true;
            case R.id.action_edit:
                // TODO: add the functionality.
                return true;
            case R.id.action_trim:
                // TODO: add the functionality.
                return true;
            case R.id.action_mute:
                // TODO: add the functionality.
                return true;
            case R.id.action_rotate_ccw:
                // TODO: add the functionality.
                return true;
            case R.id.action_rotate_cw:
                // TODO: add the functionality.
                return true;
            case R.id.action_crop:
                // TODO: add the functionality.
                return true;
            case R.id.action_setas:
                // TODO: add the functionality.
                return true;
            case R.id.action_details:
                // TODO: add the functionality.
                return true;
            case R.id.action_show_on_map:
                // TODO: add the functionality.
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.camera_filmstrip);
        mActionBar = getActionBar();
        // Hide action bar first since we are in full screen mode first.
        mActionBar.hide();

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
            // Change the window flags so that secure camera can show when locked
            Window win = getWindow();
            WindowManager.LayoutParams params = win.getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            win.setAttributes(params);

            // Filter for screen off so that we can finish secure camera activity
            // when screen is off.
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(mScreenOffReceiver, filter);
            // TODO: This static screen off event receiver is a workaround to the
            // double onResume() invocation (onResume->onPause->onResume). We should
            // find a better solution to this.
            if (sScreenOffReceiver == null) {
                sScreenOffReceiver = new ScreenOffReceiver();
                registerReceiver(sScreenOffReceiver, filter);
            }
        }
        mPanoramaManager = new PanoramaStitchingManager(CameraActivity.this);
        mPanoramaManager.addTaskListener(mStitchingListener);
        LayoutInflater inflater = getLayoutInflater();
        View rootLayout = inflater.inflate(R.layout.camera, null, false);
        mRootView = rootLayout.findViewById(R.id.camera_app_root);
        mPanoStitchingPanel = findViewById(R.id.pano_stitching_progress_panel);
        mBottomProgress = (ProgressBar) findViewById(R.id.pano_stitching_progress_bar);
        mCameraPreviewData = new CameraPreviewData(rootLayout,
                FilmStripView.ImageData.SIZE_FULL,
                FilmStripView.ImageData.SIZE_FULL);
        // Put a CameraPreviewData at the first position.
        mWrappedDataAdapter = new FixedFirstDataAdapter(
                new CameraDataAdapter(new ColorDrawable(
                        getResources().getColor(R.color.photo_placeholder))),
                mCameraPreviewData);
        mFilmStripView = (FilmStripView) findViewById(R.id.filmstrip_view);
        mFilmStripView.setViewGap(
                getResources().getDimensionPixelSize(R.dimen.camera_film_strip_gap));
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        mFilmStripView.setPanoramaViewHelper(mPanoramaViewHelper);
        // Set up the camera preview first so the preview shows up ASAP.
        mFilmStripView.setListener(mFilmStripListener);
        if (MediaStore.INTENT_ACTION_VIDEO_CAMERA.equals(getIntent().getAction())
                || MediaStore.ACTION_VIDEO_CAPTURE.equals(getIntent().getAction())) {
            mCurrentModule = new VideoModule();
            mCurrentModuleIndex = CameraSwitcher.VIDEO_MODULE_INDEX;
        } else {
            mCurrentModule = new PhotoModule();
        }
        mCurrentModule.init(this, mRootView);
        mOrientationListener = new MyOrientationEventListener(this);
        mMainHandler = new Handler(getMainLooper());
        bindMediaSaveService();

        if (!mSecureCamera) {
            mDataAdapter = mWrappedDataAdapter;
            mFilmStripView.setDataAdapter(mDataAdapter);
            mDataAdapter.requestLoad(getContentResolver());
        } else {
            // Put a lock placeholder as the last image by setting its date to 0.
            ImageView v = (ImageView) getLayoutInflater().inflate(
                    R.layout.secure_album_placeholder, null);
            mDataAdapter = new FixedLastDataAdapter(
                    mWrappedDataAdapter,
                    new SimpleViewData(
                            v,
                            v.getDrawable().getIntrinsicWidth(),
                            v.getDrawable().getIntrinsicHeight(),
                            0, 0));
            // Flush out all the original data.
            mDataAdapter.flush();
            mFilmStripView.setDataAdapter(mDataAdapter);
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
        mCurrentModule.onUserInteraction();
    }

    @Override
    public void onPause() {
        mOrientationListener.disable();
        mCurrentModule.onPauseBeforeSuper();
        super.onPause();
        mCurrentModule.onPauseAfterSuper();
    }

    @Override
    public void onResume() {
        if (Settings.System.getInt(getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0) == 0) {// auto-rotate off
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            mAutoRotateScreen = false;
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
            mAutoRotateScreen = true;
        }
        mOrientationListener.enable();
        mCurrentModule.onResumeBeforeSuper();
        super.onResume();
        mCurrentModule.onResumeAfterSuper();

        setSwipingEnabled(true);
    }

    @Override
    public void onStart() {
        super.onStart();

        mPanoramaViewHelper.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPanoramaViewHelper.onStop();
    }

    @Override
    public void onDestroy() {
        unbindMediaSaveService();
        if (mSecureCamera) unregisterReceiver(mScreenOffReceiver);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mCurrentModule.onConfigurationChanged(config);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mCurrentModule.onKeyDown(keyCode, event)) return true;
        // Prevent software keyboard or voice search from showing up.
        if (keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.isLongPress()) return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU && mShowCameraPreview) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mCurrentModule.onKeyUp(keyCode, event)) return true;
        if (keyCode == KeyEvent.KEYCODE_MENU && mShowCameraPreview) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public boolean isAutoRotateScreen() {
        return mAutoRotateScreen;
    }

    protected void updateStorageSpace() {
        mStorageSpace = Storage.getAvailableSpace();
    }

    protected long getStorageSpace() {
        return mStorageSpace;
    }

    protected void updateStorageSpaceAndHint() {
        updateStorageSpace();
        updateStorageHint(mStorageSpace);
    }

    protected void updateStorageHint() {
        updateStorageHint(mStorageSpace);
    }

    protected boolean updateStorageHintOnResume() {
        return true;
    }

    protected void updateStorageHint(long storageSpace) {
        String message = null;
        if (storageSpace == Storage.UNAVAILABLE) {
            message = getString(R.string.no_storage);
        } else if (storageSpace == Storage.PREPARING) {
            message = getString(R.string.preparing_sd);
        } else if (storageSpace == Storage.UNKNOWN_SIZE) {
            message = getString(R.string.access_sd_fail);
        } else if (storageSpace <= Storage.LOW_STORAGE_THRESHOLD) {
            message = getString(R.string.spaceIsLow_content);
        }

        if (message != null) {
            if (mStorageHint == null) {
                mStorageHint = OnScreenHint.makeText(this, message);
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
    public void onCameraSelected(int i) {
        if (mCurrentModuleIndex == i) return;

        CameraHolder.instance().keep();
        closeModule(mCurrentModule);
        mCurrentModuleIndex = i;
        switch (i) {
            case CameraSwitcher.VIDEO_MODULE_INDEX:
                mCurrentModule = new VideoModule();
                break;
            case CameraSwitcher.PHOTO_MODULE_INDEX:
                mCurrentModule = new PhotoModule();
                break;
            case CameraSwitcher.LIGHTCYCLE_MODULE_INDEX:
                mCurrentModule = PhotoSphereHelper.createPanoramaModule();
                break;
           default:
               break;
        }

        openModule(mCurrentModule);
        mCurrentModule.onOrientationChanged(mLastRawOrientation);
        if (mMediaSaveService != null) {
            mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
        }
    }

    private void openModule(CameraModule module) {
        module.init(this, mRootView);
        module.onResumeBeforeSuper();
        module.onResumeAfterSuper();
    }

    private void closeModule(CameraModule module) {
        module.onPauseBeforeSuper();
        module.onPauseAfterSuper();
        ((ViewGroup) mRootView).removeAllViews();
    }

    @Override
    public void onShowSwitcherPopup() {
    }

    public void setSwipingEnabled(boolean enable) {
        mCameraPreviewData.lockPreview(!enable);
    }

    // Accessor methods for getting latency times used in performance testing
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
}
