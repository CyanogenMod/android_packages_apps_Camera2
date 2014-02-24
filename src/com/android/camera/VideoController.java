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

import android.view.View;

import com.android.camera.ShutterButton.OnShutterButtonListener;
import com.android.camera.PauseButton.OnPauseButtonListener;

public interface VideoController extends OnShutterButtonListener, OnPauseButtonListener {

    public void onReviewDoneClicked(View view);
    public void onReviewCancelClicked(View viwe);
    public void onReviewPlayClicked(View view);

    public boolean isVideoCaptureIntent();
    public boolean isInReviewMode();
    public int onZoomChanged(int index);

    public void onSingleTapUp(View view, int x, int y);

    public void stopPreview();

    public void updateCameraOrientation();

    // Callbacks for camera preview UI events.
    public void onPreviewUIReady();
    public void onPreviewUIDestroyed();

    public void onScreenSizeChanged(int width, int height, int previewWidth, int previewHeight);
}
