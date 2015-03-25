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

import android.graphics.PointF;
import android.view.Surface;

import com.android.camera.async.SafeCloseable;
import com.android.camera.device.CameraId;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCaptureSetting;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.util.Size;

/**
 * Defines an interface that any implementation of this is responsible for
 * retaining and releasing an opened {@link com.android.camera.one.OneCamera}.
 */
public interface ResourceOpenedCamera extends SafeCloseable {
    /**
     * Obtains the opened camera.
     *
     * @return A {@link com.android.camera.one.OneCamera} object.
     */
    public OneCamera getCamera();

    /**
     * Obtains key for this one camera object
     *
     * @return A {@link com.android.camera.one.OneCamera} object.
     */
    public CameraId getCameraId();

    /**
     * Obtains the facing of the opened camera.
     *
     * @return A {@link com.android.camera.one.OneCamera.Facing}.
     */
    public OneCamera.Facing getCameraFacing();

    /**
     * Obtains the characteristics of the opened camera.
     *
     * @return A {@link com.android.camera.one.OneCameraCharacteristics}
     *         object.
     */
    public OneCameraCharacteristics getCameraCharacteristics();

    /**
     * Obtains the chosen size for any picture taken by this camera.
     *
     * @return A {@link com.android.camera.util.Size} object.
     */
    public Size getPictureSize();

    /**
     * Obtains the capture setting of the opened camera.
     *
     * @return A {@link com.android.camera.one.OneCameraCaptureSetting} object.
     */
    public OneCameraCaptureSetting getCaptureSetting();

    /**
     * Obtains the current zoom ratio applied on this camera.
     *
     * @return The current zoom ratio.
     */
    public float getZoomRatio();

    /**
     * Changes the zoom ratio on this camera.
     *
     * @param zoomRatio The new zoom ratio to be applied.
     */
    public void setZoomRatio(float zoomRatio);

    /**
     * Starts preview video on a particular surface.
     *
     * @param previewSurface A {@link android.view.Surface} that the preview
     *                       will be displayed on.
     * @param captureReadyCallback A {@link com.android.camera.one.OneCamera.CaptureReadyCallback}.
     */
    public void startPreview(
            Surface previewSurface, OneCamera.CaptureReadyCallback captureReadyCallback);

    /**
     * Trigger active focus at a specific point.
     *
     * @param point The focus point.
     */
    public void triggerFocusAndMeterAtPoint(PointF point);
}
