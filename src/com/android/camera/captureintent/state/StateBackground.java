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

import com.google.common.base.Optional;

import com.android.camera.app.AppController;
import com.android.camera.app.OrientationManager;
import com.android.camera.async.MainThread;
import com.android.camera.async.RefCountBase;
import com.android.camera.captureintent.CaptureIntentModuleUI;
import com.android.camera.one.OneCameraManager;
import com.android.camera.settings.CameraFacingSetting;
import com.android.camera.settings.ResolutionSetting;

import android.content.Context;
import android.content.Intent;

/**
 * Represents a state that module is inactive in background. This is also the
 * initial state of CaptureIntentModule.
 */
public final class StateBackground extends State {
    private final RefCountBase<ResourceConstructed> mResourceConstructed;

    public static StateBackground create(
            Intent intent,
            StateMachine stateMachine,
            CaptureIntentModuleUI moduleUI,
            MainThread mainThread,
            Context context,
            OneCameraManager cameraManager,
            OrientationManager orientationManager,
            CameraFacingSetting cameraFacingSetting,
            ResolutionSetting resolutionSetting,
            AppController appController) {
        return new StateBackground(intent, stateMachine, moduleUI, mainThread, context, cameraManager,
                orientationManager, cameraFacingSetting, resolutionSetting, appController);
    }

    public static StateBackground from(
            State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        return new StateBackground(previousState, resourceConstructed);
    }

    private StateBackground(
            Intent intent,
            StateMachine stateMachine,
            CaptureIntentModuleUI moduleUI,
            MainThread mainThread,
            Context context,
            OneCameraManager cameraManager,
            OrientationManager orientationManager,
            CameraFacingSetting cameraFacingSetting,
            ResolutionSetting resolutionSetting,
            AppController appController) {
        super(ID.Background, stateMachine);
        mResourceConstructed = new RefCountBase<>(new ResourceConstructed(
                intent, moduleUI, mainThread, context, cameraManager,
                orientationManager, cameraFacingSetting, resolutionSetting, appController));
    }

    private StateBackground(State previousState,
            RefCountBase<ResourceConstructed> resourceConstructed) {
        super(ID.Background, previousState);
        mResourceConstructed = resourceConstructed;
        mResourceConstructed.addRef();
    }

    @Override
    public void onLeave() {
        mResourceConstructed.close();
    }

    @Override
    public Optional<State> processResume() {
        return Optional.of((State) new StateForeground(this, mResourceConstructed));
    }
}
