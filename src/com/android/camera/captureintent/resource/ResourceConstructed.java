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

import com.android.camera.app.AppController;
import com.android.camera.app.LocationManager;
import com.android.camera.app.OrientationManager;
import com.android.camera.async.MainThread;
import com.android.camera.async.SafeCloseable;
import com.android.camera.captureintent.CaptureIntentModuleUI;
import com.android.camera.one.OneCameraManager;
import com.android.camera.settings.CameraFacingSetting;
import com.android.camera.settings.ResolutionSetting;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

/**
 * Defines an interface that any implementation of this should retain basic
 * resources to construct a state machine of
 * {@link com.android.camera.captureintent.CaptureIntentModule}.
 */
public interface ResourceConstructed extends SafeCloseable {
    /**
     * Obtains the intent that starts this activity.
     *
     * @return An {@link android.content.Intent} object.
     */
    public Intent getIntent();

    /**
     * Obtains the UI object associated with this module.
     *
     * @return A {@link com.android.camera.captureintent.CaptureIntentModuleUI}
     *         object.
     */
    public CaptureIntentModuleUI getModuleUI();

    /**
     * Obtains the main thread.
     *
     * @return A {@link com.android.camera.async.MainThread} object.
     */
    public MainThread getMainThread();

    /**
     * Obtains the Android application context.
     *
     * @return A {@link android.content.Context} object.
     */
    public Context getContext();

    /**
     * Obtains the camera manager that controls camera devices.
     *
     * @return An {@link com.android.camera.one.OneCameraManager} object.
     */
    public OneCameraManager getCameraManager();

    /**
     * Obtains the location manager that is able to report device current
     * location.
     *
     * @return A {@link com.android.camera.app.LocationManager} object.
     */
    public LocationManager getLocationManager();

    /**
     * Obtains the orientation manager that is able to report device current
     * orientation.
     *
     * @return An {@link com.android.camera.app.OrientationManager} object.
     */
    public OrientationManager getOrientationManager();

    /**
     * Obtains the current camera facing setting.
     *
     * @return A {@link com.android.camera.settings.CameraFacingSetting} object.
     */
    public CameraFacingSetting getCameraFacingSetting();

    /**
     * Obtains the current resolution setting.
     *
     * @return A {@link com.android.camera.settings.ResolutionSetting} object.
     */
    public ResolutionSetting getResolutionSetting();

    /**
     * Obtains a handler to perform camera related operations.
     *
     * @return A {@link android.os.Handler} object.
     */
    public Handler getCameraHandler();

    /**
     * Obtains the app controller
     *
     * @deprecated Please avoid to use app controller.
     * @return An {@link com.android.camera.app.AppController} object.
     */
    @Deprecated
    public AppController getAppController();
}
