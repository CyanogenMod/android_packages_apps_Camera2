package com.android.camera.cameradevice;

import android.hardware.Camera;

/**
 * The camera device info.
 */
public interface CameraDeviceInfo {

    static final int NO_DEVICE = -1;

    /**
     * @return The camera info.
     * // TODO: Remove the dependency on API 1.
     */
    @Deprecated
    Camera.CameraInfo[] getCameraInfos();

    /**
     * @return The total number of the available camera devices.
     */
    int getNumberOfCameras();

    /**
     * @return The first (lowest) ID of the back cameras or {@code NO_DEVICE}
     *         if not available.
     */
    int getFirstBackCameraId();

    /**
     * @return The first (lowest) ID of the front cameras or {@code NO_DEVICE}
     *         if not available.
     */
    int getFirstFrontCameraId();
}
