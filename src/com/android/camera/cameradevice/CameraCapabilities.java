/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.camera.cameradevice;

import android.graphics.Point;

import com.android.camera.debug.Log;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class CameraCapabilities {

    private static Log.Tag TAG = new Log.Tag("CameraCapabilities");

    protected final ArrayList<int[]> mSupportedPreviewFpsRange = new ArrayList<int[]>();
    protected final ArrayList<Point> mSupportedPreviewSizes = new ArrayList<Point>();
    protected final TreeSet<Integer> mSupportedPreviewFormats = new TreeSet<Integer>();
    protected final ArrayList<Point> mSupportedVideoSizes = new ArrayList<Point>();
    protected final ArrayList<Point> mSupportedPictureSizes = new ArrayList<Point>();
    protected final TreeSet<Integer> mSupportedPictureFormats = new TreeSet<Integer>();
    protected final EnumSet<SceneMode> mSupportedSceneModes = EnumSet.noneOf(SceneMode.class);
    protected final EnumSet<FlashMode> mSupportedFlashModes = EnumSet.noneOf(FlashMode.class);
    protected final EnumSet<FocusMode> mSupportedFocusModes = EnumSet.noneOf(FocusMode.class);
    protected final EnumSet<WhiteBalance> mSupportedWhiteBalances =
            EnumSet.noneOf(WhiteBalance.class);
    protected final EnumSet<Feature> mSupportedFeatures = EnumSet.noneOf(Feature.class);
    protected int mMinExposureCompensation;
    protected int mMaxExposureCompensation;
    protected float mExposureCompensationStep;
    protected int mMaxNumOfFacesSupported;
    protected int mMaxNumOfFocusAreas;
    protected int mMaxNumOfMeteringArea;
    private final Stringifier mStringifier;

    // Focus modes.
    public enum FocusMode {
        /**
         * Continuous auto focus mode intended for taking pictures.
         * @see {@link android.hardware.Camera.Parameters#FOCUS_MODE_AUTO}.
         */
        AUTO,
        /**
         * Continuous auto focus mode intended for taking pictures.
         * @see {@link android.hardware.Camera.Parameters#FOCUS_MODE_CONTINUOUS_PICTURE}.
         */
        CONTINUOUS_PICTURE,
        /**
         * Continuous auto focus mode intended for video recording.
         * @see {@link android.hardware.Camera.Parameters#FOCUS_MODE_CONTINUOUS_VIDEO}.
         */
        CONTINUOUS_VIDEO,
        /**
         * Extended depth of field (EDOF).
         * @see {@link android.hardware.Camera.Parameters#FOCUS_MODE_EDOF}.
         */
        EXTENDED_DOF,
        /**
         * Focus is fixed.
         * @see {@link android.hardware.Camera.Parameters#FOCUS_MODE_FIXED}.
         */
        FIXED,
        /**
         * Focus is set at infinity.
         * @see {@link android.hardware.Camera.Parameters#FOCUS_MODE_INFINITY}.
         */
        INFINITY,
        /**
         * Macro (close-up) focus mode.
         * @see {@link android.hardware.Camera.Parameters#FOCUS_MODE_MACRO}.
         */
        MACRO,
    }

    // Flash modes.
    public enum FlashMode {
        /**
         * Flash will be fired automatically when required.
         * @see {@link android.hardware.Camera.Parameters#FLASH_MODE_OFF}.
         */
        AUTO,
        /**
         * Flash will not be fired.
         * @see {@link android.hardware.Camera.Parameters#FLASH_MODE_OFF}.
         */
        OFF,
        /**
         * Flash will always be fired during snapshot.
         * @see {@link android.hardware.Camera.Parameters#FLASH_MODE_ON}.
         */
        ON,
        /**
         * Constant emission of light during preview, auto-focus and snapshot.
         * @see {@link android.hardware.Camera.Parameters#FLASH_MODE_TORCH}.
         */
        TORCH,
        /**
         * Flash will be fired in red-eye reduction mode.
         * @see {@link android.hardware.Camera.Parameters#FLASH_MODE_RED_EYE}.
         */
        RED_EYE,
    }

    public enum SceneMode {
        /**
         * Scene mode is off.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_AUTO}.
         */
        AUTO,
        /**
         * Take photos of fast moving objects.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_ACTION}.
         */
        ACTION,
        /**
         * Applications are looking for a barcode.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_BARCODE}.
         */
        BARCODE,
        /**
         * Take pictures on the beach.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_BEACH}.
         */
        BEACH,
        /**
         * Capture the naturally warm color of scenes lit by candles.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_CANDLELIGHT}.
         */
        CANDLELIGHT,
        /**
         * For shooting firework displays.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_FIREWORKS}.
         */
        FIREWORKS,
        /**
         * Capture a scene using high dynamic range imaging techniques.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_HDR}.
         */
        HDR,
        /**
         * Take pictures on distant objects.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_LANDSCAPE}.
         */
        LANDSCAPE,
        /**
         * Take photos at night.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_NIGHT}.
         */
        NIGHT,
        /**
         * Take people pictures at night.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_NIGHT_PORTRAIT}.
         */
        NIGHT_PORTRAIT,
        /**
         * Take indoor low-light shot.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_PARTY}.
         */
        PARTY,
        /**
         * Take people pictures.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_PORTRAIT}.
         */
        PORTRAIT,
        /**
         * Take pictures on the snow.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_SNOW}.
         */
        SNOW,
        /**
         * Take photos of fast moving objects.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_SPORTS}.
         */
        SPORTS,
        /**
         * Avoid blurry pictures (for example, due to hand shake).
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_STEADYPHOTO}.
         */
        STEADYPHOTO,
        /**
         * Take sunset photos.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_SUNSET}.
         */
        SUNSET,
        /**
         * Take photos in a theater.
         * @see {@link android.hardware.Camera.Parameters#SCENE_MODE_THEATRE}.
         */
        THEATRE,
    }

    // White balances.
    public enum WhiteBalance {
        /**
         * @see {@link android.hardware.Camera.Parameters#WHITE_BALANCE_AUTO}.
         */
        AUTO,
        /**
         * @see {@link android.hardware.Camera.Parameters#WHITE_BALANCE_CLOUDY_DAYLIGHT}.
         */
        CLOUDY_DAYLIGHT,
        /**
         * @see {@link android.hardware.Camera.Parameters#WHITE_BALANCE_DAYLIGHT}.
         */
        DAYLIGHT,
        /**
         * @see {@link android.hardware.Camera.Parameters#WHITE_BALANCE_FLUORESCENT}.
         */
        FLUORESCENT,
        /**
         * @see {@link android.hardware.Camera.Parameters#WHITE_BALANCE_INCANDESCENT}.
         */
        INCANDESCENT,
        /**
         * @see {@link android.hardware.Camera.Parameters#WHITE_BALANCE_SHADE}.
         */
        SHADE,
        /**
         * @see {@link android.hardware.Camera.Parameters#WHITE_BALANCE_TWILIGHT}.
         */
        TWILIGHT,
        /**
         * @see {@link android.hardware.Camera.Parameters#WHITE_BALANCE_WARM_FLUORESCENT}.
         */
        WARM_FLUORESCENT,
    }

    public enum Feature {
        /**
         * Support zoom-related methods.
         */
        ZOOM,
        /**
         * Support for photo capturing during video recording.
         */
        VIDEO_SNAPSHOT,
        /**
         * Support for focus area settings.
         */
        FOCUS_AREA,
        /**
         * Support for metering area settings.
         */
        METERING_AREA,
        /**
         * Support for automatic exposure lock.
         */
        AUTO_EXPOSURE_LOCK,
        /**
         * Support for automatic white balance lock.
         */
        AUTO_WHITE_BALANCE_LOCK,
    }

    /**
     * A interface stringifier to convert abstract representations to API
     * related string representation.
     */
    public interface Stringifier {
        /**
         * Converts the focus mode to API-related string representation.
         *
         * @param focus The focus mode to convert.
         * @return The string used by the camera framework API to represent the
         * focus mode.
         */
        String stringify(FocusMode focus);

        /**
         * Converts the API-related string representation of the focus mode to the
         * abstract representation.
         *
         * @param val The string representation.
         * @return The focus mode represented by the input string.
         */
        FocusMode focusModeFromString(String val);

        /**
         * Converts the flash mode to API-related string representation.
         *
         * @param flash The focus mode to convert.
         * @return The string used by the camera framework API to represent the
         * flash mode.
         */
        String stringify(FlashMode flash);

        /**
         * Converts the API-related string representation of the flash mode to the
         * abstract representation.
         *
         * @param val The string representation.
         * @return The flash mode represented by the input string.
         */
        FlashMode flashModeFromString(String val);

        /**
         * Converts the scene mode to API-related string representation.
         *
         * @param scene The focus mode to convert.
         * @return The string used by the camera framework API to represent the
         * scene mode.
         */
        String stringify(SceneMode scene);

        /**
         * Converts the API-related string representation of the scene mode to the
         * abstract representation.
         *
         * @param val The string representation.
         * @return The scene mode represented by the input string.
         */
        SceneMode sceneModeFromString(String val);

        /**
         * Converts the white balance to API-related string representation.
         *
         * @param wb The focus mode to convert.
         * @return The string used by the camera framework API to represent the
         * white balance.
         */
        String stringify(WhiteBalance wb);

        /**
         * Converts the API-related string representation of the white balance to
         * the abstract representation.
         *
         * @param val The string representation.
         * @return The white balance represented by the input string.
         */
        WhiteBalance whiteBalanceFromString(String val);
    }

    /**
     * constructor.
     * @param mStringifier The stringifier used by this instance.
     */
    CameraCapabilities(Stringifier mStringifier) {
        this.mStringifier = mStringifier;
    }

    /**
     * Copy constructor.
     * @param src The source instance.
     */
    public CameraCapabilities(CameraCapabilities src) {
        mSupportedPreviewFpsRange.addAll(src.mSupportedPreviewFpsRange);
        mSupportedPreviewSizes.addAll(src.mSupportedPreviewSizes);
        mSupportedPreviewFormats.addAll(src.mSupportedPreviewFormats);
        mSupportedVideoSizes.addAll(src.mSupportedVideoSizes);
        mSupportedPictureSizes.addAll(src.mSupportedPictureSizes);
        mSupportedPictureFormats.addAll(src.mSupportedPictureFormats);
        mSupportedSceneModes.addAll(src.mSupportedSceneModes);
        mSupportedFlashModes.addAll(src.mSupportedFlashModes);
        mSupportedFocusModes.addAll(src.mSupportedFocusModes);
        mSupportedWhiteBalances.addAll(src.mSupportedWhiteBalances);
        mSupportedFeatures.addAll(src.mSupportedFeatures);
        mMaxExposureCompensation = src.mMaxExposureCompensation;
        mMinExposureCompensation = src.mMinExposureCompensation;
        mExposureCompensationStep = src.mExposureCompensationStep;
        mMaxNumOfFacesSupported = src.mMaxNumOfFacesSupported;
        mMaxNumOfFocusAreas = src.mMaxNumOfFocusAreas;
        mStringifier = src.mStringifier;
    }

    /**
     * @return the supported picture formats. See {@link android.graphics.ImageFormat}.
     */
    public Set<Integer> getSupportedPictureFormats() {
        return new TreeSet<Integer>(mSupportedPictureFormats);
    }

    /**
     * Gets the supported preview formats.
     * @return The supported preview {@link android.graphics.ImageFormat}s.
     */
    public Set<Integer> getSupportedPreviewFormats() {
        return new TreeSet<Integer>(mSupportedPreviewFormats);
    }

    /**
     * Gets the supported picture sizes.
     */
    public List<Point> getSupportedPictureSizes() {
        return new ArrayList<Point>(mSupportedPictureSizes);
    }


    /**
     * @return The supported preview fps (frame-per-second) ranges. The returned
     * list is sorted by maximum fps then minimum fps in a descending order.
     * The values are multiplied by 1000.
     */
    public final List<int[]> getSupportedPreviewFpsRange() {
        return new ArrayList<int[]>(mSupportedPreviewFpsRange);
    }

    /**
     * @return The supported preview sizes. The width and height are stored in
     * Point.x and Point.y respectively and the list is sorted by width then
     * height in a descending order.
     */
    public final List<Point> getSupportedPreviewSizes() {
        return new ArrayList<Point>(mSupportedPreviewSizes);
    }

    /**
     * @return The supported video frame sizes that can be used by MediaRecorder.
     * The width and height are stored in Point.x and Point.y respectively and
     * the list is sorted by width then height in a descending order.
     */
    public final List<Point> getSupportedVideoSizes() {
        return new ArrayList<Point>(mSupportedVideoSizes);
    }

    /**
     * @return The supported scene modes.
     */
    public final Set<SceneMode> getSupportedSceneModes() {
        return new HashSet<SceneMode>(mSupportedSceneModes);
    }

    /**
     * @return Whether the scene mode is supported.
     */
    public final boolean supports(SceneMode scene) {
        return (scene != null && mSupportedSceneModes.contains(scene));
    }

    /**
     * @return The supported flash modes.
     */
    public final Set<FlashMode> getSupportedFlashModes() {
        return new HashSet<FlashMode>(mSupportedFlashModes);
    }

    /**
     * @return Whether the flash mode is supported.
     */
    public final boolean supports(FlashMode flash) {
        return (flash != null && mSupportedFlashModes.contains(flash));
    }

    /**
     * @return The supported focus modes.
     */
    public final Set<FocusMode> getSupportedFocusModes() {
        return new HashSet<FocusMode>(mSupportedFocusModes);
    }

    /**
     * @return Whether the focus mode is supported.
     */
    public final boolean supports(FocusMode focus) {
        return (focus != null && mSupportedFocusModes.contains(focus));
    }

    /**
     * @return The supported white balanceas.
     */
    public final Set<WhiteBalance> getSupportedWhiteBalance() {
        return new HashSet<WhiteBalance>(mSupportedWhiteBalances);
    }

    /**
     * @return Whether the white balance is supported.
     */
    public boolean supports(WhiteBalance wb) {
        return (wb != null && mSupportedWhiteBalances.contains(wb));
    }

    public final Set<Feature> getSupportedFeature() {
        return new HashSet<Feature>(mSupportedFeatures);
    }

    public boolean supports(Feature ft) {
        return (ft != null && mSupportedFeatures.contains(ft));
    }

    /**
     * @return The min exposure compensation index. The EV is the compensation
     * index multiplied by the step value. If unsupported, both this method and
     * {@link #getMaxExposureCompensation()} return 0.
     */
    public final int getMinExposureCompensation() {
        return mMinExposureCompensation;
    }

    /**
     * @return The max exposure compensation index. The EV is the compensation
     * index multiplied by the step value. If unsupported, both this method and
     * {@link #getMinExposureCompensation()} return 0.
     */
    public final int getMaxExposureCompensation() {
        return mMaxExposureCompensation;
    }

    /**
     * @return The exposure compensation step. The EV is the compensation index
     * multiplied by the step value.
     */
    public final float getExposureCompensationStep() {
        return mExposureCompensationStep;
    }

    /**
     * @return The max number of faces supported by the face detection. 0 if
     * unsupported.
     */
    public final int getMaxNumOfFacesSupported() {
        return mMaxNumOfFacesSupported;
    }

    /**
     * @return The stringifier used by this instance.
     */
    public Stringifier getStringifier() {
        return mStringifier;
    }
}
