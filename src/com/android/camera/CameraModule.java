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

import android.content.Intent;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.View;

import com.android.camera.app.CameraServices;
import com.android.camera.app.MediaSaver;

public abstract class CameraModule {

    /** Provides common services and functionality to the module. */
    private final CameraServices mServices;

    public CameraModule(CameraServices services) {
        mServices = services;
    }

    @Deprecated
    public abstract void init(CameraActivity activity, View frame);

    @Deprecated
    public abstract void onPreviewFocusChanged(boolean previewFocused);

    @Deprecated
    public abstract void onPauseBeforeSuper();

    @Deprecated
    public abstract void onPauseAfterSuper();

    @Deprecated
    public abstract void onResumeBeforeSuper();

    @Deprecated
    public abstract void onResumeAfterSuper();

    @Deprecated
    public abstract void onConfigurationChanged(Configuration config);

    @Deprecated
    public abstract void onStop();

    @Deprecated
    public abstract void installIntentFilter();

    @Deprecated
    public abstract void onActivityResult(int requestCode, int resultCode, Intent data);

    @Deprecated
    public abstract boolean onBackPressed();

    @Deprecated
    public abstract boolean onKeyDown(int keyCode, KeyEvent event);

    @Deprecated
    public abstract boolean onKeyUp(int keyCode, KeyEvent event);

    @Deprecated
    public abstract void onSingleTapUp(View view, int x, int y);

    @Deprecated
    public abstract void onPreviewTextureCopied();

    @Deprecated
    public abstract void onCaptureTextureCopied();

    @Deprecated
    public abstract void onUserInteraction();

    @Deprecated
    public abstract boolean updateStorageHintOnResume();

    @Deprecated
    public abstract void onOrientationChanged(int orientation);

    @Deprecated
    public abstract void onShowSwitcherPopup();

    @Deprecated
    public abstract void onMediaSaverAvailable(MediaSaver s);

    @Deprecated
    public abstract boolean arePreviewControlsVisible();

    /**
     * @return An instance containing common services to be used by the module.
     */
    protected CameraServices getServices() {
        return mServices;
    }
}
