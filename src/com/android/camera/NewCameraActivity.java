/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.os.IBinder;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.android.camera.data.CameraDataAdapter;
import com.android.camera.ui.CameraSwitcher.CameraSwitchListener;
import com.android.camera.ui.FilmStripView;
import com.android.camera.ui.NewCameraRootView;
import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.util.LightCycleHelper;

public class NewCameraActivity extends Activity
    implements CameraSwitchListener {
    public static final int PHOTO_MODULE_INDEX = 0;
    public static final int VIDEO_MODULE_INDEX = 1;
    public static final int PANORAMA_MODULE_INDEX = 2;
    public static final int LIGHTCYCLE_MODULE_INDEX = 3;
    private static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE =
            "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE =
            "android.media.action.IMAGE_CAPTURE_SECURE";
    // The intent extra for camera from secure lock screen. True if the gallery
    // should only show newly captured pictures. sSecureAlbumId does not
    // increment. This is used when switching between camera, camcorder, and
    // panorama. If the extra is not set, it is in the normal camera mode.
    public static final String SECURE_CAMERA_EXTRA = "secure_camera";

    private static final String TAG = "CAM_Activity";
    private CameraDataAdapter mDataAdapter;
    private int mCurrentModuleIndex;
    private NewCameraModule mCurrentModule;
    private View mRootView;
    private FilmStripView mFilmStripView;
    private int mResultCodeForTesting;
    private Intent mResultDataForTesting;
    private OnScreenHint mStorageHint;
    private long mStorageSpace = Storage.LOW_STORAGE_THRESHOLD;
    private PhotoModule mController;
    private boolean mAutoRotateScreen;
    private boolean mSecureCamera;
    private int mLastRawOrientation;
    private MyOrientationEventListener mOrientationListener;
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

    public MediaSaveService getMediaSaveService() {
        return mMediaSaveService;
    }

    public void notifyNewMedia(Uri uri) {
        ContentResolver cr = getContentResolver();
        String mimeType = cr.getType(uri);
        if (mimeType.startsWith("video/")) {
            sendBroadcast(new Intent(Util.ACTION_NEW_VIDEO, uri));
            mDataAdapter.addNewVideo(cr, uri);
        } else if (mimeType.startsWith("image/")) {
            Util.broadcastNewPicture(this, uri);
            mDataAdapter.addNewPhoto(cr, uri);
        } else {
            android.util.Log.w(TAG, "Unknown new media with MIME type:"
                    + mimeType + ", uri:" + uri);
        }
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
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.camera_filmstrip);
        if (ApiHelper.HAS_ROTATION_ANIMATION) {
            setRotationAnimation();
        }
        // Check if this is in the secure camera mode.
        Intent intent = getIntent();
        String action = intent.getAction();
        if (INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action)) {
            mSecureCamera = true;
            // Use a new album when this is started from the lock screen.
        //TODO:    sSecureAlbumId++;
        } else if (ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            mSecureCamera = true;
        } else {
            mSecureCamera = intent.getBooleanExtra(SECURE_CAMERA_EXTRA, false);
        }
        /*TODO: if (mSecureCamera) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            registerReceiver(mScreenOffReceiver, filter);
            if (sScreenOffReceiver == null) {
                sScreenOffReceiver = new ScreenOffReceiver();
                getApplicationContext().registerReceiver(sScreenOffReceiver, filter);
            }
        }*/
        LayoutInflater inflater = getLayoutInflater();
        View rootLayout = inflater.inflate(R.layout.camera, null, false);
        mRootView = rootLayout.findViewById(R.id.camera_app_root);
        mDataAdapter = new CameraDataAdapter(
                new ColorDrawable(getResources().getColor(R.color.photo_placeholder)));
        mFilmStripView = (FilmStripView) findViewById(R.id.filmstrip_view);
        mFilmStripView.setViewGap(
                getResources().getDimensionPixelSize(R.dimen.camera_film_strip_gap));
        // Set up the camera preview first so the preview shows up ASAP.
        mDataAdapter.setCameraPreviewInfo(rootLayout,
                FilmStripView.ImageData.SIZE_FULL, FilmStripView.ImageData.SIZE_FULL);
        mFilmStripView.setDataAdapter(mDataAdapter);
        mFilmStripView.setListener(new FilmStripView.Listener() {
            @Override
            public void onDataPromoted(int dataID) {
                mDataAdapter.removeData(dataID);
            }

            @Override
            public void onDataDemoted(int dataID) {
                mDataAdapter.removeData(dataID);
            }

        });
        mCurrentModule = new NewPhotoModule();
        mCurrentModule.init(this, mRootView);
        mOrientationListener = new MyOrientationEventListener(this);
        bindMediaSaveService();
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

        // The loading is done in background and will update the filmstrip later.
        mDataAdapter.requestLoad(getContentResolver());
    }

    @Override
    public void onDestroy() {
        unbindMediaSaveService();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mCurrentModule.onConfigurationChanged(config);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        return mFilmStripView.dispatchTouchEvent(m);
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
            case VIDEO_MODULE_INDEX:
                mCurrentModule = new NewVideoModule();
                break;
            case PHOTO_MODULE_INDEX:
                mCurrentModule = new NewPhotoModule();
                break;
            /* TODO:
            case LIGHTCYCLE_MODULE_INDEX:
                mCurrentModule = LightCycleHelper.createPanoramaModule();
                break; */
           default:
               break;
        }

        openModule(mCurrentModule);
        mCurrentModule.onOrientationChanged(mLastRawOrientation);
        if (mMediaSaveService != null) {
            mCurrentModule.onMediaSaveServiceConnected(mMediaSaveService);
        }
    }

    private void openModule(NewCameraModule module) {
        module.init(this, mRootView);
        module.onResumeBeforeSuper();
        module.onResumeAfterSuper();
    }

    private void closeModule(NewCameraModule module) {
        module.onPauseBeforeSuper();
        module.onPauseAfterSuper();
        ((ViewGroup) mRootView).removeAllViews();
    }

    @Override
    public void onShowSwitcherPopup() {
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
