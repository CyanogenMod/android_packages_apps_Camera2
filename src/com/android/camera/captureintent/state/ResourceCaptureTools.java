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
import com.android.camera.debug.Log;
import com.android.camera.hardware.HeadingSensor;
import com.android.camera.one.OneCamera;
import com.android.camera.session.CaptureSession;
import com.android.camera.session.CaptureSessionManager;
import com.android.camera.ui.focus.FocusController;
import com.android.camera.util.CameraUtil;

import android.media.MediaActionSound;

public final class ResourceCaptureTools implements SafeCloseable {
    private static final Log.Tag TAG = new Log.Tag("ResCapTools");

    private final RefCountBase<ResourceConstructed> mResourceConstructed;
    private final RefCountBase<ResourceSurfaceTexture> mResourceSurfaceTexture;
    private final RefCountBase<ResourceOpenedCamera> mResourceOpenedCamera;

    private final CaptureSessionManager mCaptureSessionManager;
    private final FocusController mFocusController;
    private final LocationManager mLocationManager;
    private final HeadingSensor mHeadingSensor;
    private final SoundPlayer mSoundPlayer;
    private final MediaActionSound mMediaActionSound;
    private final OneCamera.PictureCallback mPictureCallback;
    private final OneCamera.PictureSaverCallback mPictureSaverCallback;

    /**
     * Creates a reference counted {@link ResourceCaptureTools} object.
     */
    public static RefCountBase<ResourceCaptureTools> create(
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera,
            CaptureSessionManager captureSessionManager,
            FocusController focusController,
            LocationManager locationManager,
            HeadingSensor headingSensor,
            SoundPlayer soundPlayer,
            MediaActionSound mediaActionSound,
            OneCamera.PictureCallback pictureCallback,
            OneCamera.PictureSaverCallback pictureSaverCallback) {
        ResourceCaptureTools resourceCaptureTools = new ResourceCaptureTools(
                resourceConstructed, resourceSurfaceTexture, resourceOpenedCamera,
                captureSessionManager, focusController, locationManager, headingSensor, soundPlayer,
                mediaActionSound, pictureCallback, pictureSaverCallback);
        return new RefCountBase<>(resourceCaptureTools);
    }

    private ResourceCaptureTools(
            RefCountBase<ResourceConstructed> resourceConstructed,
            RefCountBase<ResourceSurfaceTexture> resourceSurfaceTexture,
            RefCountBase<ResourceOpenedCamera> resourceOpenedCamera,
            CaptureSessionManager captureSessionManager,
            FocusController focusController,
            LocationManager locationManager,
            HeadingSensor headingSensor,
            SoundPlayer soundPlayer,
            MediaActionSound mediaActionSound,
            OneCamera.PictureCallback pictureCallback,
            OneCamera.PictureSaverCallback pictureSaverCallback) {
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();     // Will be balanced in close().
        mResourceSurfaceTexture = resourceSurfaceTexture;
        mResourceSurfaceTexture.addRef();  // Will be balanced in close().
        mResourceOpenedCamera = resourceOpenedCamera;
        mResourceOpenedCamera.addRef();    // Will be balanced in close().
        mCaptureSessionManager = captureSessionManager;
        mLocationManager = locationManager;
        mHeadingSensor = headingSensor;
        mSoundPlayer = soundPlayer;
        mMediaActionSound = mediaActionSound;
        mPictureCallback = pictureCallback;
        mPictureSaverCallback = pictureSaverCallback;
        mFocusController = focusController;
    }

    @Override
    public void close() {
        Log.d(TAG, "close");
        mResourceConstructed.close();
        mResourceSurfaceTexture.close();
        mResourceOpenedCamera.close();
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

    public FocusController getFocusController() {
        return mFocusController;
    }

    public MediaActionSound getMediaActionSound() {
        return mMediaActionSound;
    }

    public void takePictureNow() {
        // Create a new capture session.
        final long timestamp = System.currentTimeMillis();
        final String fileName = CameraUtil.instance().createJpegName(timestamp);
        final android.location.Location location = mLocationManager.getCurrentLocation();
        final CaptureSession session =
                mCaptureSessionManager.createNewSession(fileName, timestamp, location);
        session.startEmpty(mResourceOpenedCamera.get().getPictureSize());

        OneCamera.PhotoCaptureParameters params = new OneCamera.PhotoCaptureParameters(
                session.getTitle(),
                mResourceConstructed.get().getOrientationManager().getDeviceOrientation().getDegrees(),
                session.getLocation(),
                mResourceConstructed.get().getContext().getExternalCacheDir(),
                mPictureCallback,
                mPictureSaverCallback,
                mHeadingSensor.getCurrentHeading(),
                mResourceOpenedCamera.get().getZoomRatio(),
                0);
        mResourceOpenedCamera.get().getCamera().takePicture(params, session);
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
}
