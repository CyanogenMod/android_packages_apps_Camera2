package com.android.camera.cameradevice;

import android.hardware.Camera;

/**
 * Created by shkong on 6/2/14.
 */
public class AndroidCameraSettings extends CameraSettings {

    public AndroidCameraSettings(CameraCapabilities capabilities, Camera.Parameters params) {
        CameraCapabilities.Stringifier stringifier = capabilities.getStringifier();

        // Preview
        Camera.Size paramPreviewSize = params.getPreviewSize();
        setPreviewSize(new Size(paramPreviewSize.width, paramPreviewSize.height));
        setPreviewFrameRate(params.getPreviewFrameRate());
        int[] previewFpsRange = new int[2];
        params.getPreviewFpsRange(previewFpsRange);
        setPreviewFpsRange(previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);


        // Capture: Focus, flash, zoom, exposure, scene mode.
        if (capabilities.supports(CameraCapabilities.Feature.ZOOM)) {
            setZoomRatio(params.getZoomRatios().get(params.getZoom()) / 100f);
            setZoomIndex(params.getZoom());
        } else {
            setZoomRatio(1.0f);
            setZoomIndex(0);
        }
        setExposureCompensationIndex(params.getExposureCompensation());
        setFlashMode(stringifier.flashModeFromString(params.getFlashMode()));
        setFocusMode(stringifier.focusModeFromString(params.getFocusMode()));
        setSceneMode(stringifier.sceneModeFromString(params.getSceneMode()));

        // Video capture.
        if (capabilities.supports(CameraCapabilities.Feature.VIDEO_STABILIZATION)) {
            setVideoStabilization(isVideoStabilizationEnabled());
        }

        // Output: Photo size, compression quality, rotation.
        setPhotoRotationDegrees(0f);
        setPhotoJpegCompressionQuality(params.getJpegQuality());
        Camera.Size paramPictureSize = params.getPictureSize();
        setPhotoSize(new Size(paramPictureSize.width, paramPictureSize.height));
    }
}
