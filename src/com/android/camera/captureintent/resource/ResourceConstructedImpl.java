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

package com.android.camera.captureintent.resource;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;

import com.android.camera.FatalErrorHandler;
import com.android.camera.SoundPlayer;
import com.android.camera.app.AppController;
import com.android.camera.app.LocationManager;
import com.android.camera.app.OrientationManager;
import com.android.camera.async.MainThread;
import com.android.camera.async.RefCountBase;
import com.android.camera.burst.BurstFacade;
import com.android.camera.captureintent.CaptureIntentModuleUI;
import com.android.camera.one.OneCameraManager;
import com.android.camera.one.OneCameraOpener;
import com.android.camera.settings.CameraFacingSetting;
import com.android.camera.settings.ResolutionSetting;
import com.android.camera.settings.SettingsManager;

public final class ResourceConstructedImpl implements ResourceConstructed {
    private final Intent mIntent;
    private final CaptureIntentModuleUI mModuleUI;
    private final String mSettingScopeNamespace;
    private final MainThread mMainThread;
    private final Context mContext;
    private final OneCameraOpener mOneCameraOpener;
    private final OneCameraManager mOneCameraManager;
    private final LocationManager mLocationManager;
    private final OrientationManager mOrientationManager;
    private final SettingsManager mSettingsManager;
    private final BurstFacade mBurstFacade;
    private final CameraFacingSetting mCameraFacingSetting;
    private final ResolutionSetting mResolutionSetting;
    private final HandlerThread mCameraThread;
    private final Handler mCameraHandler;
    private final FatalErrorHandler mFatalErrorHandler;
    private final SoundPlayer mSoundPlayer;

    // TODO: Hope one day we could get rid of AppController.
    private final AppController mAppController;

    /**
     * Creates a reference counted {@link ResourceConstructedImpl} object.
     */
    public static RefCountBase<ResourceConstructed> create(
            Intent intent,
            CaptureIntentModuleUI moduleUI,
            String settingScopeNamespace,
            MainThread mainThread,
            Context context,
            OneCameraOpener oneCameraOpener,
            OneCameraManager oneCameraManager,
            LocationManager locationManager,
            OrientationManager orientationManager,
            SettingsManager settingsManager,
            BurstFacade burstFacade,
            AppController appController,
            FatalErrorHandler fatalErrorHandler) {
        final CameraFacingSetting cameraFacingSetting = new CameraFacingSetting(
                context.getResources(), settingsManager, settingScopeNamespace);
        final ResolutionSetting resolutionSetting = new ResolutionSetting(
                settingsManager, oneCameraManager, context.getContentResolver());
        return new RefCountBase<ResourceConstructed>(new ResourceConstructedImpl(
                intent, moduleUI, settingScopeNamespace, mainThread, context, oneCameraOpener,
                oneCameraManager, locationManager, orientationManager, settingsManager, burstFacade,
                cameraFacingSetting, resolutionSetting, appController, fatalErrorHandler));
    }

    private ResourceConstructedImpl(
            Intent intent,
            CaptureIntentModuleUI moduleUI,
            String settingScopeNamespace,
            MainThread mainThread,
            Context context,
            OneCameraOpener cameraManager,
            OneCameraManager hardwareManager,
            LocationManager locationManager,
            OrientationManager orientationManager,
            SettingsManager settingsManager,
            BurstFacade burstFacade,
            CameraFacingSetting cameraFacingSetting,
            ResolutionSetting resolutionSetting,
            AppController appController,
            FatalErrorHandler fatalErrorHandler) {
        mIntent = intent;
        mModuleUI = moduleUI;
        mSettingScopeNamespace = settingScopeNamespace;
        mMainThread = mainThread;
        mContext = context;
        mOneCameraOpener = cameraManager;
        mOneCameraManager = hardwareManager;
        mLocationManager = locationManager;
        mOrientationManager = orientationManager;
        mSettingsManager = settingsManager;
        mBurstFacade = burstFacade;
        mCameraFacingSetting = cameraFacingSetting;
        mResolutionSetting = resolutionSetting;
        mFatalErrorHandler = fatalErrorHandler;
        mSoundPlayer = new SoundPlayer(mContext);
        mAppController = appController;

        mCameraThread = new HandlerThread("ImageCaptureIntentModule.CameraHandler");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    @Override
    public void close() {
        mCameraThread.quit();
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public CaptureIntentModuleUI getModuleUI() {
        return mModuleUI;
    }

    @Override
    public String getSettingScopeNamespace() {
        return mSettingScopeNamespace;
    }

    @Override
    public MainThread getMainThread() {
        return mMainThread;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public OneCameraManager getOneCameraManager() {
        return mOneCameraManager;
    }

    @Override
    public OneCameraOpener getOneCameraOpener() {
        return mOneCameraOpener;
    }

    @Override
    public LocationManager getLocationManager() {
        return mLocationManager;
    }

    @Override
    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    @Override
    public SettingsManager getSettingsManager() {
        return mSettingsManager;
    }

    @Override
    public BurstFacade getBurstFacade() {
        return mBurstFacade;
    }

    @Override
    public CameraFacingSetting getCameraFacingSetting() {
        return mCameraFacingSetting;
    }

    @Override
    public ResolutionSetting getResolutionSetting() {
        return mResolutionSetting;
    }

    @Override
    public Handler getCameraHandler() {
        return mCameraHandler;
    }

    @Override
    public SoundPlayer getSoundPlayer() {
        return mSoundPlayer;
    }

    @Override
    public AppController getAppController() {
        return mAppController;
    }

    @Override
    public FatalErrorHandler getFatalErrorHandler() {
        return mFatalErrorHandler;
    }
}
