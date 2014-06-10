package com.android.camera.cameradevice;

import android.hardware.Camera;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A class which stores the camera settings.
 */
public class CameraSettings {

    protected final Map<String, String> mGeneralSetting = new TreeMap<>();
    protected final List<Camera.Area> mMeteringAreas = new ArrayList<>();
    protected final List<Camera.Area> mFocusAreas = new ArrayList<>();
    protected int mPreviewFpsRangeMin;
    protected int mPreviewFpsRangeMax;
    protected int mPreviewFrameRate;
    protected Size mCurrentPreviewSize;
    protected Size mCurrentPhotoSize;
    protected int mJpegCompressQuality;
    protected float mCurrentZoomRatio;
    protected int mCurrentZoomIndex;
    protected float mPhotoRotationDegrees;
    protected int mExposureCompensationIndex;
    protected CameraCapabilities.FlashMode mCurrentFlashMode;
    protected CameraCapabilities.FocusMode mCurrentFocusMode;
    protected CameraCapabilities.SceneMode mCurrentSceneMode;
    protected CameraCapabilities.WhiteBalance mWhiteBalance;
    protected boolean mVideoStabilizationEnabled;
    protected boolean mAutoExposureLocked;
    protected boolean mAutoWhiteBalanceLocked;
    protected GpsData mGpsData;

    /**
     * An immutable class storing GPS related information.
     * <p>It's a hack since we always use GPS time stamp but does not use other
     * fields sometimes. Setting processing method to null means the other
     * fields should not be used.</p>
     */
    public static class GpsData {
        public final double latitude;
        public final double longitude;
        public final double altitude;
        public final long timeStamp;
        public final String processingMethod;

        /** Constructor. */
        public GpsData(double latitude, double longitude, double altitude, long timeStamp,
                String processingMethod) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.timeStamp = timeStamp;
            this.processingMethod = processingMethod;
        }

        /** Copy constructor. */
        public GpsData(GpsData src) {
            this.latitude = src.latitude;
            this.longitude = src.longitude;
            this.altitude = src.altitude;
            this.timeStamp = src.timeStamp;
            this.processingMethod = src.processingMethod;
        }
    }

    protected CameraSettings() {
    }

    /**
     * Copy constructor.
     *
     * @param src The source settings.
     * @return The copy of the source.
     */
    public CameraSettings(CameraSettings src) {
        mGeneralSetting.putAll(src.mGeneralSetting);
        mMeteringAreas.addAll(src.mMeteringAreas);
        mFocusAreas.addAll(src.mFocusAreas);
        mPreviewFpsRangeMin = src.mPreviewFpsRangeMin;
        mPreviewFpsRangeMax = src.mPreviewFpsRangeMax;
        mPreviewFrameRate = src.mPreviewFrameRate;
        mCurrentPreviewSize =
                (src.mCurrentPreviewSize == null ? null : new Size(src.mCurrentPreviewSize));
        mCurrentPhotoSize =
                (src.mCurrentPhotoSize == null ? null : new Size(src.mCurrentPhotoSize));
        mJpegCompressQuality = src.mJpegCompressQuality;
        mCurrentZoomRatio = src.mCurrentZoomRatio;
        mCurrentZoomIndex = src.mCurrentZoomIndex;
        mPhotoRotationDegrees = src.mPhotoRotationDegrees;
        mExposureCompensationIndex = src.mExposureCompensationIndex;
        mCurrentFlashMode = src.mCurrentFlashMode;
        mCurrentFocusMode = src.mCurrentFocusMode;
        mCurrentSceneMode = src.mCurrentSceneMode;
        mWhiteBalance = src.mWhiteBalance;
        mVideoStabilizationEnabled = src.mVideoStabilizationEnabled;
        mAutoExposureLocked = src.mAutoExposureLocked;
        mAutoWhiteBalanceLocked = src.mAutoWhiteBalanceLocked;
        mGpsData = src.mGpsData;
    }

    /** General setting **/
    @Deprecated
    public void setSetting(String key, String value) {
        mGeneralSetting.put(key, value);
    }

    /**  Preview **/

    /**
     * Sets the preview FPS range. This call will invalidate prior calls to
     * {@link #setPreviewFrameRate(int)}.
     *
     * @param min The min FPS.
     * @param max The max FPS.
     */
    public void setPreviewFpsRange(int min, int max) {
        if (min > max) {
            int temp = max;
            max = min;
            min = temp;
        }
        mPreviewFpsRangeMax = max;
        mPreviewFpsRangeMin = min;
        mPreviewFrameRate = -1;
    }

    /**
     * @return The min of the preview FPS range.
     */
    public int getPreviewFpsRangeMin() {
        return mPreviewFpsRangeMin;
    }

    /**
     * @return The max of the preview FPS range.
     */
    public int getPreviewFpsRangeMax() {
        return mPreviewFpsRangeMax;
    }

    /**
     * Sets the preview FPS. This call will invalidate prior calls to
     * {@link #setPreviewFpsRange(int, int)}.
     *
     * @param frameRate The target frame rate.
     */
    public void setPreviewFrameRate(int frameRate) {
        if (frameRate > 0) {
            mPreviewFrameRate = frameRate;
            mPreviewFpsRangeMax = frameRate;
            mPreviewFpsRangeMin = frameRate;
        }
    }

    public int getPreviewFrameRate() {
        return mPreviewFrameRate;
    }

    /**
     * @return The current preview size.
     */
    public Size getCurrentPreviewSize() {
        return new Size(mCurrentPreviewSize);
    }

    /**
     * @param previewSize The size to use for preview.
     */
    public void setPreviewSize(Size previewSize) {
        mCurrentPreviewSize = new Size(previewSize);
    }

    /** Picture **/

    /**
     * @return The current photo size.
     */
    public Size getCurrentPhotoSize() {
        return new Size(mCurrentPhotoSize);
    }

    /**
     * Sets the size for the photo.
     *
     * @param photoSize The photo size.
     */
    public void setPhotoSize(Size photoSize) {
        mCurrentPhotoSize = new Size(photoSize);
    }

    /**
     * Sets the JPEG compression quality.
     *
     * @param quality The quality for JPEG.
     */
    public void setPhotoJpegCompressionQuality(int quality) {
        mJpegCompressQuality = quality;
    }

    public int getPhotoJpegCompressionQuality() {
        return mJpegCompressQuality;
    }

    /** Zoom **/

    /**
     * @return The current zoom ratio. The min is 1.0f.
     */
    public float getCurrentZoomRatio() {
        return mCurrentZoomRatio;
    }

    /**
     * Sets the zoom ratio.
     * @param ratio The new zoom ratio. Should be in the range between 1.0 to
     *              the value returned from {@link
     *              com.android.camera.cameradevice.CameraCapabilities#getMaxZoomRatio()}.
     * @throws java.lang.UnsupportedOperationException if the ratio is not
     *         supported.
     */
    public void setZoomRatio(float ratio) {
        mCurrentZoomRatio = ratio;
    }

    @Deprecated
    public int getCurrentZoomIndex() {
        return mCurrentZoomIndex;
    }

    @Deprecated
    public void setZoomIndex(int index) {
        mCurrentZoomIndex = index;
    }

    /** Transformation **/

    public void setPhotoRotationDegrees(float photoRotationDegrees) {
        mPhotoRotationDegrees = photoRotationDegrees;
    }

    public float getCurrentPhotoRotationDegrees() {
        return mPhotoRotationDegrees;
    }

    /** Exposure **/

    public void setExposureCompensationIndex(int index) {
        mExposureCompensationIndex = index;
    }

    public int getExposureCompensationIndex() {
        return mExposureCompensationIndex;
    }

    public void setAutoExposureLock(boolean locked) {
        mAutoExposureLocked = locked;
    }

    public boolean isAutoExposureLocked() {
        return mAutoExposureLocked;
    }

    public void setMeteringAreas(List<Camera.Area> areas) {
        mMeteringAreas.clear();
        if (areas != null) {
            mMeteringAreas.addAll(areas);
        }
    }

    public List<Camera.Area> getMeteringAreas() {
        return new ArrayList<Camera.Area>(mMeteringAreas);
    }

    /** Flash **/

    public CameraCapabilities.FlashMode getCurrentFlashMode() {
        return mCurrentFlashMode;
    }

    public void setFlashMode(CameraCapabilities.FlashMode flashMode) {
        mCurrentFlashMode = flashMode;
    }

    /** Focus **/

    /**
     * Sets the focus mode.
     * @param focusMode The focus mode to use.
     */
    public void setFocusMode(CameraCapabilities.FocusMode focusMode) {
        mCurrentFocusMode = focusMode;
    }

    /**
     * @return The current focus mode.
     */
    public CameraCapabilities.FocusMode getCurrentFocusMode() {
        return mCurrentFocusMode;
    }

    /**
     * @param areas The areas to focus.
     */
    public void setFocusAreas(List<Camera.Area> areas) {
        mFocusAreas.clear();
        if (areas != null) {
            mFocusAreas.addAll(areas);
        }
    }

    public List<Camera.Area> getFocusAreas() {
        return new ArrayList<Camera.Area>(mFocusAreas);
    }

    /** White balance **/

    public void setWhiteBalance(CameraCapabilities.WhiteBalance whiteBalance) {
        mWhiteBalance = whiteBalance;
    }

    public CameraCapabilities.WhiteBalance getWhiteBalance() {
        return mWhiteBalance;
    }

    public void setAutoWhiteBalanceLock(boolean locked) {
        mAutoWhiteBalanceLocked = locked;
    }

    public boolean isAutoWhiteBalanceLocked() {
        return mAutoWhiteBalanceLocked;
    }

    /** Scene mode **/

    /**
     * @return The current scene mode.
     */
    public CameraCapabilities.SceneMode getCurrentSceneMode() {
        return mCurrentSceneMode;
    }

    /**
     * Sets the scene mode for capturing.
     *
     * @param sceneMode The scene mode to use.
     * @throws java.lang.UnsupportedOperationException if it's not supported.
     */
    public void setSceneMode(CameraCapabilities.SceneMode sceneMode) {
        mCurrentSceneMode = sceneMode;
    }

    /** Other Features **/

    public void setVideoStabilization(boolean enabled) {
        mVideoStabilizationEnabled = enabled;
    }

    public boolean isVideoStabilizationEnabled() {
        return mVideoStabilizationEnabled;
    }

    public void setGpsData(GpsData data) {
        mGpsData = new GpsData(data);
    }

    public GpsData getGpsData() {
        return (mGpsData == null ? null : new GpsData(mGpsData));
    }

    public void clearGpsData() {
        mGpsData = null;
    }
}
