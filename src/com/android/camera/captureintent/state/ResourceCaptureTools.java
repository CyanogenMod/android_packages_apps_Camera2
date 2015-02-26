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

package com.android.camera.captureintent.state;

import com.android.camera.SoundPlayer;
import com.android.camera.app.LocationManager;
import com.android.camera.async.MainThread;
import com.android.camera.async.RefCountBase;
import com.android.camera.async.SafeCloseable;
import com.android.camera.captureintent.CaptureIntentModuleUI;
import com.android.camera.captureintent.CaptureIntentSessionFactory;
import com.android.camera.debug.Log;
import com.android.camera.hardware.HeadingSensor;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.session.CaptureSessionManagerImpl;
import com.android.camera.session.SessionStorageManagerImpl;
import com.android.camera.ui.focus.FocusController;
import com.android.camera.ui.focus.FocusSound;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.CameraUtil;
import com.android.camera2.R;

import android.media.MediaActionSound;

public final class ResourceCaptureTools implements SafeCloseable {
    private static final Log.Tag TAG = new Log.Tag("ResCapTools");

    private final RefCountBase<ResourceConstructed> mResourceConstructed;
    private final RefCountBase<ResourceSurfaceTexture> mResourceSurfaceTexture;
    private final RefCountBase<ResourceOpenedCamera> mResourceOpenedCamera;

    private final CaptureSessionManager mCaptureSessionManager;
    private final FocusController mFocusController;
    private final HeadingSensor mHeadingSensor;
    private final SoundPlayer mSoundPlayer;
    private final MediaActionSound mMediaActionSound;

    /**
     * Creates a reference counted {@link ResourceCaptureTools} object.
     */
    public static RefCountBase<ResourceCaptureTools> create(
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera) {
        CaptureSessionManager captureSessionManager = new CaptureSessionManagerImpl(
                new CaptureIntentSessionFactory(),
                SessionStorageManagerImpl.create(resourceConstructed.get().getContext()),
                resourceConstructed.get().getMainThread());
        HeadingSensor headingSensor =
                new HeadingSensor(AndroidServices.instance().provideSensorManager());
        SoundPlayer soundPlayer = new SoundPlayer(resourceConstructed.get().getContext());
        FocusSound focusSound = new FocusSound(soundPlayer, R.raw.material_camera_focus);
        FocusController focusController = new FocusController(
                resourceConstructed.get().getModuleUI().getFocusRing(),
                focusSound,
                resourceConstructed.get().getMainThread());
        MediaActionSound mediaActionSound = new MediaActionSound();
        ResourceCaptureTools resourceCaptureTools = new ResourceCaptureTools(
                resourceConstructed, resourceSurfaceTexture, resourceOpenedCamera,
                captureSessionManager, focusController, headingSensor, soundPlayer,
                mediaActionSound);
        return new RefCountBase<>(resourceCaptureTools);
    }

    private ResourceCaptureTools(
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera,
            CaptureSessionManager captureSessionManager,
            FocusController focusController,
            HeadingSensor headingSensor,
            SoundPlayer soundPlayer,
            MediaActionSound mediaActionSound) {
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();     // Will be balanced in close().
        mResourceSurfaceTexture = resourceSurfaceTexture;
        mResourceSurfaceTexture.addRef();  // Will be balanced in close().
        mResourceOpenedCamera = resourceOpenedCamera;
        mResourceOpenedCamera.addRef();    // Will be balanced in close().
        mCaptureSessionManager = captureSessionManager;
        mHeadingSensor = headingSensor;
        mHeadingSensor.activate();  // Will be balanced in close().
        mSoundPlayer = soundPlayer;
        mSoundPlayer.loadSound(R.raw.timer_final_second);  // Will be balanced in close().
        mSoundPlayer.loadSound(R.raw.timer_increment);     // Will be balanced in close().
        mMediaActionSound = mediaActionSound;
        mFocusController = focusController;
    }

    @Override
    public void close() {
        Log.d(TAG, "close");
        mResourceConstructed.close();
        mResourceSurfaceTexture.close();
        mResourceOpenedCamera.close();
        mHeadingSensor.deactivate();
        mSoundPlayer.unloadSound(R.raw.timer_increment);
        mSoundPlayer.unloadSound(R.raw.timer_final_second);
    }

    public RefCountBase<ResourceConstructed> getResourceConstructed() {
        return mResourceConstructed;
    }

    public RefCountBase<ResourceSurfaceTexture> getResourceSurfaceTexture() {
        return mResourceSurfaceTexture;
    }

    public RefCountBase<ResourceOpenedCamera> getResourceOpenedCamera() {
        return mResourceOpenedCamera;
    }

    public CaptureSessionManager getCaptureSessionManager() {
        return mCaptureSessionManager;
    }

    public FocusController getFocusController() {
        return mFocusController;
    }

    public MediaActionSound getMediaActionSound() {
        return mMediaActionSound;
    }

    public void takePictureNow(OneCamera.PictureCallback pictureCallback) {
        // Create a new capture session.
        final long timestamp = System.currentTimeMillis();
        final String fileName = CameraUtil.instance().createJpegName(timestamp);
        final android.location.Location location =
                mResourceConstructed.get().getLocationManager().getCurrentLocation();
        final CaptureSession session =
                mCaptureSessionManager.createNewSession(fileName, timestamp, location);
        session.startEmpty(mResourceOpenedCamera.get().getPictureSize());

        OneCamera.PhotoCaptureParameters params = new OneCamera.PhotoCaptureParameters(
                session.getTitle(),
                mResourceConstructed.get().getOrientationManager().getDeviceOrientation().getDegrees(),
                session.getLocation(),
                mResourceConstructed.get().getContext().getExternalCacheDir(),
                pictureCallback,
                mPictureSaverCallback,
                mHeadingSensor.getCurrentHeading(),
                mResourceOpenedCamera.get().getZoomRatio(),
                0);
        mResourceOpenedCamera.get().getCamera().takePicture(params, session);
    }

    public void playCountDownSound(int remainingSeconds) {
        if (remainingSeconds == 1) {
            mSoundPlayer.play(R.raw.timer_final_second, 0.6f);
        } else if (remainingSeconds == 2 || remainingSeconds == 3) {
            mSoundPlayer.play(R.raw.timer_increment, 0.6f);
        }
    }

    public MainThread getMainThread() {
        return mResourceConstructed.get().getMainThread();
    }

    public CaptureIntentModuleUI getModuleUI() {
        return mResourceConstructed.get().getModuleUI();
    }

    public OneCamera getCamera() {
        return mResourceOpenedCamera.get().getCamera();
    }

    private final OneCamera.PictureSaverCallback mPictureSaverCallback =
            new OneCamera.PictureSaverCallback() {
                @Override
                public void onRemoteThumbnailAvailable(byte[] jpegImage) {
                }
            };
}
