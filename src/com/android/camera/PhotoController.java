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

import android.view.SurfaceHolder;
import android.view.View;

import com.android.camera.ShutterButton.OnShutterButtonListener;


public interface PhotoController extends OnShutterButtonListener {

    public static final int PREVIEW_STOPPED = 0;
    public static final int IDLE = 1;  // preview is active
    // Focus is in progress. The exact focus state is in Focus.java.
    public static final int FOCUSING = 2;
    public static final int SNAPSHOT_IN_PROGRESS = 3;
    // Switching between cameras.
    public static final int SWITCHING_CAMERA = 4;

    // returns the actual set zoom value
    public int onZoomChanged(int requestedZoom);

    public boolean isImageCaptureIntent();

    public boolean isCameraIdle();

    public void onCaptureDone();

    public void onCaptureCancelled();

    public void onCaptureRetake();

    public void cancelAutoFocus();

    public void stopPreview();

    public int getCameraState();

    public void onSingleTapUp(View view, int x, int y);

    public void onSurfaceCreated(SurfaceHolder holder);

    public void onCountDownFinished();

    public void onScreenSizeChanged(int width, int height, int previewWidth, int previewHeight);

}
