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

import com.android.camera.async.RefCountBase;
import com.android.camera.debug.Log;
import com.android.camera.device.CameraId;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraCaptureSetting;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.util.Size;

public final class ResourceOpenedCameraImpl implements ResourceOpenedCamera {
    private static final Log.Tag TAG = new Log.Tag("ResOpenedCam");

    /** The camera object. */
    private final OneCamera mCamera;

    /** The camera device key. */
    private final CameraId mCameraId;

    /** The camera facing. */
    private final OneCamera.Facing mCameraFacing;

    /** The camera characteristics. */
    private final OneCameraCharacteristics mCameraCharacteristics;

    /** The desired picture size. */
    private final Size mPictureSize;

    /** The current zoom ratio. */
    private float mZoomRatio;

    private final OneCameraCaptureSetting mOneCameraCaptureSetting;

    /**
     * Creates a reference counted {@link ResourceOpenedCameraImpl} object.
     */
    public static RefCountBase<ResourceOpenedCamera> create(
            OneCamera camera,
            CameraId cameraId,
            OneCamera.Facing cameraFacing,
            OneCameraCharacteristics cameraCharacteristics,
            Size pictureSize,
            OneCameraCaptureSetting captureSetting) {
        ResourceOpenedCamera resourceOpenedCamera = new ResourceOpenedCameraImpl(
                camera, cameraId, cameraFacing, cameraCharacteristics, pictureSize, captureSetting);
        return new RefCountBase<>(resourceOpenedCamera);
    }

    private ResourceOpenedCameraImpl(
            OneCamera camera,
            CameraId cameraId,
            OneCamera.Facing cameraFacing,
            OneCameraCharacteristics cameraCharacteristics,
            Size pictureSize,
            OneCameraCaptureSetting captureSetting) {
        mCamera = camera;
        mCameraId = cameraId;
        mCameraFacing = cameraFacing;
        mCameraCharacteristics = cameraCharacteristics;
        mPictureSize = pictureSize;
        mZoomRatio = mCamera.getMaxZoom();
        mOneCameraCaptureSetting = captureSetting;
    }

    @Override
    public void close() {
        Log.d(TAG, "close");
        mCamera.setFocusStateListener(null);
        mCamera.close();
    }

    @Override
    public OneCamera getCamera() {
        return mCamera;
    }

    @Override
    public CameraId getCameraId() {
        return mCameraId;
    }

    @Override
    public OneCamera.Facing getCameraFacing() {
        return mCameraFacing;
    }

    @Override
    public OneCameraCharacteristics getCameraCharacteristics() {
        return mCameraCharacteristics;
    }

    @Override
    public Size getPictureSize() {
        return mPictureSize;
    }

    @Override
    public OneCameraCaptureSetting getCaptureSetting() {
        return mOneCameraCaptureSetting;
    }

    @Override
    public float getZoomRatio() {
        return mZoomRatio;
    }

    @Override
    public void setZoomRatio(float zoomRatio) {
        mZoomRatio = zoomRatio;
        mCamera.setZoom(zoomRatio);
    }

    @Override
    public void startPreview(
            Surface previewSurface, OneCamera.CaptureReadyCallback captureReadyCallback) {
        mCamera.startPreview(previewSurface, captureReadyCallback);
    }

    @Override
    public void triggerFocusAndMeterAtPoint(PointF point) {
        mCamera.triggerFocusAndMeterAtPoint(point.x, point.y);
    }
}
