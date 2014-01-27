/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.util.Log;

import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import com.android.camera2.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.util.StringTokenizer;

/**
 *  Provides utilities and keys for Camera settings.
 */
public class CameraSettings {
    private static final int NOT_FOUND = -1;

    public static final String KEY_VERSION = "pref_version_key";
    public static final String KEY_LOCAL_VERSION = "pref_local_version_key";
    public static final String KEY_RECORD_LOCATION = "pref_camera_recordlocation_key";
    public static final String KEY_VIDEO_QUALITY = "pref_video_quality_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL = "pref_video_time_lapse_frame_interval_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera_picturesize_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FOCUS_TIME = "pref_camera_focustime_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_TIMER = "pref_camera_timer_key";
    public static final String KEY_TIMER_SOUND_EFFECTS = "pref_camera_timer_sound_key";
    public static final String KEY_VIDEO_EFFECT = "pref_video_effect_key";
    public static final String KEY_CAMERA_ID = "pref_camera_id_key";
    public static final String KEY_CAMERA_HDR = "pref_camera_hdr_key";
    public static final String KEY_CAMERA_HQ = "pref_camera_hq_key";
    public static final String KEY_CAMERA_HDR_PLUS = "pref_camera_hdr_plus_key";
    public static final String KEY_CAMERA_FIRST_USE_HINT_SHOWN = "pref_camera_first_use_hint_shown_key";
    public static final String KEY_VIDEO_FIRST_USE_HINT_SHOWN = "pref_video_first_use_hint_shown_key";
    public static final String KEY_PHOTOSPHERE_PICTURESIZE = "pref_photosphere_picturesize_key";
    public static final String KEY_STARTUP_MODULE_INDEX = "camera.startup_module";
    public static final String KEY_STORAGE = "pref_camera_storage_key";

    public static final String KEY_VIDEO_ENCODER = "pref_camera_videoencoder_key";
    public static final String KEY_AUDIO_ENCODER = "pref_camera_audioencoder_key";
    public static final String KEY_VIDEO_DURATION = "pref_camera_video_duration_key";
    public static final String KEY_POWER_MODE = "pref_camera_powermode_key";
    public static final String KEY_PICTURE_FORMAT = "pref_camera_pictureformat_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera_coloreffect_key";
    public static final String KEY_VIDEOCAMERA_COLOR_EFFECT = "pref_camera_video_coloreffect_key";
    public static final String KEY_FACE_DETECTION = "pref_camera_facedetection_key";
    public static final String KEY_SELECTABLE_ZONE_AF = "pref_camera_selectablezoneaf_key";
    public static final String KEY_SATURATION = "pref_camera_saturation_key";
    public static final String KEY_CONTRAST = "pref_camera_contrast_key";
    public static final String KEY_SHARPNESS = "pref_camera_sharpness_key";
    public static final String KEY_AUTOEXPOSURE = "pref_camera_autoexposure_key";
    public static final String KEY_ANTIBANDING = "pref_camera_antibanding_key";
    public static final String KEY_ISO = "pref_camera_iso_key";
    public static final String KEY_LENSSHADING = "pref_camera_lensshading_key";
    public static final String KEY_HISTOGRAM = "pref_camera_histogram_key";
    public static final String KEY_DENOISE = "pref_camera_denoise_key";
    public static final String KEY_REDEYE_REDUCTION = "pref_camera_redeyereduction_key";
    public static final String KEY_AE_BRACKET_HDR = "pref_camera_ae_bracket_hdr_key";

    public static final String KEY_VIDEO_SNAPSHOT_SIZE = "pref_camera_videosnapsize_key";
    public static final String KEY_VIDEO_HIGH_FRAME_RATE = "pref_camera_hfr_key";
    public static final String KEY_VIDEO_HDR = "pref_camera_video_hdr_key";
    public static final String DEFAULT_VIDEO_QUALITY_VALUE = "custom";
    public static final String KEY_SKIN_TONE_ENHANCEMENT = "pref_camera_skinToneEnhancement_key";
    public static final String KEY_SKIN_TONE_ENHANCEMENT_FACTOR = "pref_camera_skinToneEnhancement_factor_key";

    public static final String KEY_FACE_RECOGNITION = "pref_camera_facerc_key";
    public static final String KEY_DIS = "pref_camera_dis_key";

    private static final String KEY_QC_SUPPORTED_AE_BRACKETING_MODES = "ae-bracket-hdr-values";
    private static final String KEY_QC_SUPPORTED_FACE_RECOGNITION_MODES = "face-recognition-values";
    private static final String KEY_QC_SUPPORTED_DIS_MODES = "dis-values";
    public static final String KEY_QC_AE_BRACKETING = "ae-bracket-hdr";
    public static final String KEY_QC_FACE_RECOGNITION = "face-recognition";
    public static final String KEY_QC_DIS_MODE = "dis";

    //for flip
    public static final String KEY_QC_PREVIEW_FLIP = "preview-flip";
    public static final String KEY_QC_VIDEO_FLIP = "video-flip";
    public static final String KEY_QC_SNAPSHOT_PICTURE_FLIP = "snapshot-picture-flip";
    public static final String KEY_QC_SUPPORTED_FLIP_MODES = "flip-mode-values";

    public static final String FLIP_MODE_OFF = "off";
    public static final String FLIP_MODE_V = "flip-v";
    public static final String FLIP_MODE_H = "flip-h";
    public static final String FLIP_MODE_VH = "flip-vh";

    private static final String KEY_QC_PICTURE_FORMAT = "picture-format-values";
    private static final String VIDEO_QUALITY_HIGH = "high";
    private static final String VIDEO_QUALITY_MMS = "mms";
    private static final String VIDEO_QUALITY_YOUTUBE = "youtube";

    public static final String KEY_BURST_MODE = "pref_camera_burst_key";
    public static final String KEY_BEAUTY_MODE = "pref_camera_beauty_mode";
    public static final String KEY_SLOW_SHUTTER = "pref_camera_slow_shutter";
    public static final String KEY_ASD = "pref_camera_asd";

    public static final String EXPOSURE_DEFAULT_VALUE = "0";

    public static final int CURRENT_VERSION = 5;
    public static final int CURRENT_LOCAL_VERSION = 2;

    public static final int DEFAULT_VIDEO_DURATION = 0; // no limit
    private static final int MMS_VIDEO_DURATION = (CamcorderProfile.get(CamcorderProfile.QUALITY_LOW) != null) ?
          CamcorderProfile.get(CamcorderProfile.QUALITY_LOW).duration :30;
    private static final int YOUTUBE_VIDEO_DURATION = 15 * 60; // 15 mins

    private static final String TAG = "CameraSettings";

    private final Context mContext;
    private final Parameters mParameters;
    private final CameraInfo[] mCameraInfo;
    private final int mCameraId;

    public CameraSettings(Activity activity, Parameters parameters,
                          int cameraId, CameraInfo[] cameraInfo) {
        mContext = activity;
        mParameters = parameters;
        mCameraId = cameraId;
        mCameraInfo = cameraInfo;
    }

    public PreferenceGroup getPreferenceGroup(int preferenceRes) {
        PreferenceInflater inflater = new PreferenceInflater(mContext);
        PreferenceGroup group =
                (PreferenceGroup) inflater.inflate(preferenceRes);
        if (mParameters != null) initPreference(group);
        return group;
    }

    public static String getSupportedHighestVideoQuality(int cameraId,
            String defaultQuality,Parameters parameters) {
        // When launching the camera app first time, we will set the video quality
        // to the first one (i.e. highest quality) in the supported list
        List<String> supported = getSupportedVideoQuality(cameraId,parameters);
        if (supported == null) {
            Log.e(TAG, "No supported video quality is found");
            return defaultQuality;
        }
        return supported.get(0);
    }

    public static void initialCameraPictureSize(
            Context context, Parameters parameters) {
        // When launching the camera app first time, we will set the picture
        // size to the first one in the list defined in "arrays.xml" and is also
        // supported by the driver.
        List<Size> supported = parameters.getSupportedPictureSizes();
        if (supported == null) return;
        for (String candidate : context.getResources().getStringArray(
                R.array.pref_camera_picturesize_entryvalues)) {
            if (setCameraPictureSize(candidate, supported, parameters)) {
                SharedPreferences.Editor editor = ComboPreferences
                        .get(context).edit();
                editor.putString(KEY_PICTURE_SIZE, candidate);
                editor.apply();
                return;
            }
        }
        Log.e(TAG, "No supported picture size found");
    }

    public static void removePreferenceFromScreen(
            PreferenceGroup group, String key) {
        removePreference(group, key);
    }

    public static boolean setCameraPictureSize(
            String candidate, List<Size> supported, Parameters parameters) {
        int index = candidate.indexOf('x');
        if (index == NOT_FOUND) return false;
        int width = Integer.parseInt(candidate.substring(0, index));
        int height = Integer.parseInt(candidate.substring(index + 1));
        for (Size size : supported) {
            if (size.width == width && size.height == height) {
                parameters.setPictureSize(width, height);
                return true;
            }
        }
        return false;
    }

    public static int getMaxVideoDuration(Context context) {
        int duration = 0;  // in milliseconds, 0 means unlimited.
        try {
            duration = context.getResources().getInteger(R.integer.max_video_recording_length);
        } catch (Resources.NotFoundException ex) {
        }
        return duration;
    }

    public static List<String> getSupportedFaceRecognitionModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_FACE_RECOGNITION_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedDISModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_DIS_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    public static List<String> getSupportedAEBracketingModes(Parameters params) {
        String str = params.get(KEY_QC_SUPPORTED_AE_BRACKETING_MODES);
        if (str == null) {
            return null;
        }
        return split(str);
    }

    // Splits a comma delimited string to an ArrayList of String.
    // Return null if the passing string is null or the size is 0.
    private static ArrayList<String> split(String str) {
        if (str == null) return null;

        // Use StringTokenizer because it is faster than split.
        StringTokenizer tokenizer = new StringTokenizer(str, ",");
        ArrayList<String> substrings = new ArrayList<String>();
        while (tokenizer.hasMoreElements()) {
            substrings.add(tokenizer.nextToken());
        }
        return substrings;
    }
    private List<String> getSupportedPictureFormatLists() {
        String str = mParameters.get(KEY_QC_PICTURE_FORMAT);
        if (str == null) {
            str = "jpeg,raw"; // if not set, fall back to default behavior
        }
        return split(str);
    }

   public static List<String> getSupportedFlipMode(Parameters params){
        String str = params.get(KEY_QC_SUPPORTED_FLIP_MODES);
        if(str == null)
            return null;

        return split(str);
    }

    private void qcomInitPreferences(PreferenceGroup group){
        //Qcom Preference add here
        ListPreference powerMode = group.findPreference(KEY_POWER_MODE);
        ListPreference colorEffect = group.findPreference(KEY_COLOR_EFFECT);
        ListPreference faceDetection = group.findPreference(KEY_FACE_DETECTION);
        ListPreference selectableZoneAf = group.findPreference(KEY_SELECTABLE_ZONE_AF);
        ListPreference saturation = group.findPreference(KEY_SATURATION);
        ListPreference contrast = group.findPreference(KEY_CONTRAST);
        ListPreference sharpness = group.findPreference(KEY_SHARPNESS);
        ListPreference autoExposure = group.findPreference(KEY_AUTOEXPOSURE);
        ListPreference antiBanding = group.findPreference(KEY_ANTIBANDING);
        ListPreference mIso = group.findPreference(KEY_ISO);
        ListPreference lensShade = group.findPreference(KEY_LENSSHADING);
        ListPreference histogram = group.findPreference(KEY_HISTOGRAM);
        ListPreference denoise = group.findPreference(KEY_DENOISE);
        ListPreference redeyeReduction = group.findPreference(KEY_REDEYE_REDUCTION);
        ListPreference aeBracketing = group.findPreference(KEY_AE_BRACKET_HDR);
        ListPreference faceRC = group.findPreference(KEY_FACE_RECOGNITION);
        ListPreference jpegQuality = group.findPreference(KEY_JPEG_QUALITY);
        ListPreference videoSnapSize = group.findPreference(KEY_VIDEO_SNAPSHOT_SIZE);
        ListPreference pictureFormat = group.findPreference(KEY_PICTURE_FORMAT);

        if (!mParameters.isPowerModeSupported() && powerMode != null) {
            removePreference(group, powerMode.getKey());
        }

        if (selectableZoneAf != null) {
            filterUnsupportedOptions(group,
                    selectableZoneAf, mParameters.getSupportedSelectableZoneAf());
        }

        if (mIso != null) {
            filterUnsupportedOptions(group,
                    mIso, mParameters.getSupportedIsoValues());
        }

        if (redeyeReduction != null) {
            filterUnsupportedOptions(group,
                    redeyeReduction, mParameters.getSupportedRedeyeReductionModes());
        }

        if (denoise != null) {
            filterUnsupportedOptions(group,
                    denoise, mParameters.getSupportedDenoiseModes());
        }

        if (colorEffect != null) {
            filterUnsupportedOptions(group,
                    colorEffect, mParameters.getSupportedColorEffects());
        }

        if (aeBracketing != null) {
            filterUnsupportedOptions(group,
                     aeBracketing, getSupportedAEBracketingModes(mParameters));
        }

        if (antiBanding != null) {
            filterUnsupportedOptions(group,
                    antiBanding, mParameters.getSupportedAntibanding());
        }

        if (faceRC != null) {
            filterUnsupportedOptions(group,
                    faceRC, getSupportedFaceRecognitionModes(mParameters));
        }

        if (autoExposure != null) {
            filterUnsupportedOptions(group,
                    autoExposure, mParameters.getSupportedAutoexposure());
        }

        if (!mParameters.isPowerModeSupported()){
            filterUnsupportedOptions(group,
                    videoSnapSize, null);
        } else {
            filterUnsupportedOptions(group, videoSnapSize, sizeListToStringList(
                        mParameters.getSupportedPictureSizes()));
        }

        if (histogram!= null) {
            filterUnsupportedOptions(group,
                    histogram, mParameters.getSupportedHistogramModes());
        }

        if (pictureFormat!= null) {
            filterUnsupportedOptions(group,
                    pictureFormat, getSupportedPictureFormatLists());
        }

        if (contrast != null && !CameraUtil.isSupported(mParameters, "contrast") &&
                !CameraUtil.isSupported(mParameters, "contrast-max")) {
            removePreference(group, contrast.getKey());
        }

        if (sharpness != null && !CameraUtil.isSupported(mParameters, "sharpness") &&
                !CameraUtil.isSupported(mParameters, "sharpness-max")) {
            removePreference(group, sharpness.getKey());
        }

        if (saturation != null && !CameraUtil.isSupported(mParameters, "saturation") &&
                !CameraUtil.isSupported(mParameters, "saturation-max")) {
            removePreference(group, saturation.getKey());
        }
    }

    private void initPreference(PreferenceGroup group) {
        ListPreference videoQuality = group.findPreference(KEY_VIDEO_QUALITY);
        ListPreference timeLapseInterval = group.findPreference(KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
        ListPreference pictureSize = group.findPreference(KEY_PICTURE_SIZE);
        ListPreference whiteBalance =  group.findPreference(KEY_WHITE_BALANCE);
        ListPreference sceneMode = group.findPreference(KEY_SCENE_MODE);
        ListPreference flashMode = group.findPreference(KEY_FLASH_MODE);
        ListPreference focusMode = group.findPreference(KEY_FOCUS_MODE);
        IconListPreference exposure =
                (IconListPreference) group.findPreference(KEY_EXPOSURE);
        IconListPreference cameraIdPref =
                (IconListPreference) group.findPreference(KEY_CAMERA_ID);
        ListPreference videoFlashMode =
                group.findPreference(KEY_VIDEOCAMERA_FLASH_MODE);
        ListPreference videoEffect = group.findPreference(KEY_VIDEO_EFFECT);
        ListPreference cameraHdr = group.findPreference(KEY_CAMERA_HDR);
        ListPreference disMode = group.findPreference(KEY_DIS);
        ListPreference cameraHdrPlus = group.findPreference(KEY_CAMERA_HDR_PLUS);
        ListPreference beautyMode = group.findPreference(KEY_BEAUTY_MODE);
        ListPreference slowShutter = group.findPreference(KEY_SLOW_SHUTTER);
        ListPreference asd = group.findPreference(KEY_ASD);
        ListPreference storage = group.findPreference(KEY_STORAGE);

        // Since the screen could be loaded from different resources, we need
        // to check if the preference is available here
        if (videoQuality != null) {
            filterUnsupportedOptions(group, videoQuality, getSupportedVideoQuality(
                   mCameraId,mParameters));
        }

        if (pictureSize != null) {
            filterUnsupportedOptions(group, pictureSize, sizeListToStringList(
                    mParameters.getSupportedPictureSizes()));
            filterSimilarPictureSize(group, pictureSize);
        }
        if (whiteBalance != null) {
            filterUnsupportedOptions(group,
                    whiteBalance, mParameters.getSupportedWhiteBalance());
        }
        if (sceneMode != null) {
            filterUnsupportedOptions(group,
                    sceneMode, mParameters.getSupportedSceneModes());
        }
        if (flashMode != null) {
            filterUnsupportedOptions(group,
                    flashMode, mParameters.getSupportedFlashModes());
        }
        if (disMode != null) {
            filterUnsupportedOptions(group,
                    disMode, getSupportedDISModes(mParameters));
        }
        if (focusMode != null) {
            filterUnsupportedOptions(group,
                    focusMode, mParameters.getSupportedFocusModes());
        }
        if (videoFlashMode != null) {
            filterUnsupportedOptions(group,
                    videoFlashMode, mParameters.getSupportedFlashModes());
        }
        if (exposure != null) buildExposureCompensation(group, exposure);
        if (cameraIdPref != null) buildCameraId(group, cameraIdPref);

        if (timeLapseInterval != null) {
            resetIfInvalid(timeLapseInterval);
        }
        if (videoEffect != null) {
            filterUnsupportedOptions(group, videoEffect, null);
        }
        if (cameraHdr != null && (!ApiHelper.HAS_CAMERA_HDR
                || !CameraUtil.isCameraHdrSupported(mParameters))) {
            removePreference(group, cameraHdr.getKey());
        }
        int frontCameraId = CameraHolder.instance().getFrontCameraId();
        boolean isFrontCamera = (frontCameraId == mCameraId);
        if (cameraHdrPlus != null && (!ApiHelper.HAS_CAMERA_HDR_PLUS ||
                !GcamHelper.hasGcamCapture() || isFrontCamera)) {
            removePreference(group, cameraHdrPlus.getKey());
        }
        if (beautyMode != null) {
            if (!isBeautyModeSupported(mParameters)) {
                removePreference(group, beautyMode.getKey());
            }
        }
        if (slowShutter != null) {
            filterUnsupportedOptions(group, slowShutter, getSupportedSlowShutter(mParameters));
        }
        if (asd != null && !CameraUtil.isAutoSceneDetectionSupported(mParameters)) {
            removePreference(group, asd.getKey());
        }
        if (storage != null) {
            buildStorage(group, storage);
        }
        qcomInitPreferences(group);
    }

    private void buildStorage(PreferenceGroup group, ListPreference storage) {
        StorageManager sm = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        StorageVolume[] volumes = sm.getVolumeList();
        List<String> entries = new ArrayList<String>(volumes.length);
        List<String> entryValues = new ArrayList<String>(volumes.length);
        int primary = 0;

        for (int i = 0; i < volumes.length; i++) {
            StorageVolume v = volumes[i];
            // Hide unavailable volumes
            if (sm.getVolumeState(v.getPath())
                    .equals(Environment.MEDIA_MOUNTED)) {
                entries.add(v.getDescription(mContext));
                entryValues.add(v.getPath());
                if (v.isPrimary()) {
                    primary = i;
                }
            }
        }

        if (entries.size() < 2) {
            // No need for storage setting
            removePreference(group, storage.getKey());
            return;
        }
        storage.setEntries(entries.toArray(new String[entries.size()]));
        storage.setEntryValues(entryValues.toArray(new String[entryValues.size()]));

        // Filter saved invalid value
        if (storage.findIndexOfValue(storage.getValue()) < 0) {
            // Default to the primary storage
            storage.setValueIndex(primary);
        }
    }

    private void buildExposureCompensation(
            PreferenceGroup group, IconListPreference exposure) {
        int max = mParameters.getMaxExposureCompensation();
        int min = mParameters.getMinExposureCompensation();
        if (max == 0 && min == 0) {
            removePreference(group, exposure.getKey());
            return;
        }
        float step = mParameters.getExposureCompensationStep();

        // show only integer values for exposure compensation
        int maxValue = Math.min(3, (int) Math.floor(max * step));
        int minValue = Math.max(-3, (int) Math.ceil(min * step));
        String explabel = mContext.getResources().getString(R.string.pref_exposure_label);
        CharSequence entries[] = new CharSequence[maxValue - minValue + 1];
        CharSequence entryValues[] = new CharSequence[maxValue - minValue + 1];
        CharSequence labels[] = new CharSequence[maxValue - minValue + 1];
        int[] icons = new int[maxValue - minValue + 1];
        TypedArray iconIds = mContext.getResources().obtainTypedArray(
                R.array.pref_camera_exposure_icons);
        for (int i = minValue; i <= maxValue; ++i) {
            entryValues[i - minValue] = Integer.toString(Math.round(i / step));
            StringBuilder builder = new StringBuilder();
            if (i > 0) builder.append('+');
            entries[i - minValue] = builder.append(i).toString();
            labels[i - minValue] = explabel + " " + builder.toString();
            icons[i - minValue] = iconIds.getResourceId(3 + i, 0);
        }
        exposure.setUseSingleIcon(true);
        exposure.setEntries(entries);
        exposure.setLabels(labels);
        exposure.setEntryValues(entryValues);
        exposure.setLargeIconIds(icons);
    }

    private void buildCameraId(
            PreferenceGroup group, IconListPreference preference) {
        int numOfCameras = mCameraInfo.length;
        if (numOfCameras < 2) {
            removePreference(group, preference.getKey());
            return;
        }

        CharSequence[] entryValues = new CharSequence[numOfCameras];
        for (int i = 0; i < numOfCameras; ++i) {
            entryValues[i] = "" + i;
        }
        preference.setEntryValues(entryValues);
    }

    private static boolean removePreference(PreferenceGroup group, String key) {
        for (int i = 0, n = group.size(); i < n; i++) {
            CameraPreference child = group.get(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, key)) {
                    return true;
                }
            }
            if (child instanceof ListPreference &&
                    ((ListPreference) child).getKey().equals(key)) {
                group.removePreference(i);
                return true;
            }
        }
        return false;
    }

    private void filterUnsupportedOptions(PreferenceGroup group,
            ListPreference pref, List<String> supported) {

        // Remove the preference if the parameter is not supported or there is
        // only one options for the settings.
        if (supported == null || supported.size() <= 1) {
            removePreference(group, pref.getKey());
            return;
        }

        pref.filterUnsupported(supported);
        if (pref.getEntries().length <= 1) {
            removePreference(group, pref.getKey());
            return;
        }

        resetIfInvalid(pref);
    }

    private void filterSimilarPictureSize(PreferenceGroup group,
            ListPreference pref) {
        pref.filterDuplicated();
        if (pref.getEntries().length <= 1) {
            removePreference(group, pref.getKey());
            return;
        }
        resetIfInvalid(pref);
    }

    private void resetIfInvalid(ListPreference pref) {
        // Set the value to the first entry if it is invalid.
        String value = pref.getValue();
        if (pref.findIndexOfValue(value) == NOT_FOUND) {
            pref.setValueIndex(0);
        }
    }

    private static List<String> sizeListToStringList(List<Size> sizes) {
        ArrayList<String> list = new ArrayList<String>();
        for (Size size : sizes) {
            list.add(String.format(Locale.ENGLISH, "%dx%d", size.width, size.height));
        }
        return list;
    }

    public static void upgradeLocalPreferences(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_LOCAL_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_LOCAL_VERSION) return;

        SharedPreferences.Editor editor = pref.edit();
        if (version == 1) {
            // We use numbers to represent the quality now. The quality definition is identical to
            // that of CamcorderProfile.java.
            editor.remove("pref_video_quality_key");
        }
        editor.putInt(KEY_LOCAL_VERSION, CURRENT_LOCAL_VERSION);
        editor.apply();
    }

    public static void upgradeGlobalPreferences(SharedPreferences pref) {
        upgradeOldVersion(pref);
        upgradeCameraId(pref);
    }

    private static void upgradeOldVersion(SharedPreferences pref) {
        int version;
        try {
            version = pref.getInt(KEY_VERSION, 0);
        } catch (Exception ex) {
            version = 0;
        }
        if (version == CURRENT_VERSION) return;

        SharedPreferences.Editor editor = pref.edit();
        if (version == 0) {
            // We won't use the preference which change in version 1.
            // So, just upgrade to version 1 directly
            version = 1;
        }
        if (version == 1) {
            // Change jpeg quality {65,75,85} to {normal,fine,superfine}
            String quality = pref.getString(KEY_JPEG_QUALITY, "85");
            if (quality.equals("65")) {
                quality = "normal";
            } else if (quality.equals("75")) {
                quality = "fine";
            } else {
                quality = "superfine";
            }
            editor.putString(KEY_JPEG_QUALITY, quality);
            version = 2;
        }
        if (version == 2) {
            editor.putString(KEY_RECORD_LOCATION,
                    pref.getBoolean(KEY_RECORD_LOCATION, false)
                    ? RecordLocationPreference.VALUE_ON
                    : RecordLocationPreference.VALUE_NONE);
            version = 3;
        }
        if (version == 3) {
            // Just use video quality to replace it and
            // ignore the current settings.
            editor.remove("pref_camera_videoquality_key");
            editor.remove("pref_camera_video_duration_key");
        }

        editor.putInt(KEY_VERSION, CURRENT_VERSION);
        editor.apply();
    }

    private static void upgradeCameraId(SharedPreferences pref) {
        // The id stored in the preference may be out of range if we are running
        // inside the emulator and a webcam is removed.
        // Note: This method accesses the global preferences directly, not the
        // combo preferences.
        int cameraId = readPreferredCameraId(pref);
        if (cameraId == 0) return;  // fast path

        int n = CameraHolder.instance().getNumberOfCameras();
        if (cameraId < 0 || cameraId >= n) {
            writePreferredCameraId(pref, 0);
        }
    }

    public static int readPreferredCameraId(SharedPreferences pref) {
        return Integer.parseInt(pref.getString(KEY_CAMERA_ID, "0"));
    }

    public static void writePreferredCameraId(SharedPreferences pref,
            int cameraId) {
        Editor editor = pref.edit();
        editor.putString(KEY_CAMERA_ID, Integer.toString(cameraId));
        editor.apply();
    }

    public static int readExposure(ComboPreferences preferences) {
        String exposure = preferences.getString(
                CameraSettings.KEY_EXPOSURE,
                EXPOSURE_DEFAULT_VALUE);
        try {
            return Integer.parseInt(exposure);
        } catch (Exception ex) {
            Log.e(TAG, "Invalid exposure: " + exposure);
        }
        return 0;
    }

    public static void restorePreferences(Context context,
            ComboPreferences preferences, Parameters parameters) {
        int currentCameraId = readPreferredCameraId(preferences);

        // Clear the preferences of both cameras.
        int backCameraId = CameraHolder.instance().getBackCameraId();
        if (backCameraId != -1) {
            preferences.setLocalId(context, backCameraId);
            Editor editor = preferences.edit();
            editor.clear();
            editor.apply();
        }
        int frontCameraId = CameraHolder.instance().getFrontCameraId();
        if (frontCameraId != -1) {
            preferences.setLocalId(context, frontCameraId);
            Editor editor = preferences.edit();
            editor.clear();
            editor.apply();
        }

        // Switch back to the preferences of the current camera. Otherwise,
        // we may write the preference to wrong camera later.
        preferences.setLocalId(context, currentCameraId);

        upgradeGlobalPreferences(preferences.getGlobal());
        upgradeLocalPreferences(preferences.getLocal());

        // Write back the current camera id because parameters are related to
        // the camera. Otherwise, we may switch to the front camera but the
        // initial picture size is that of the back camera.
        initialCameraPictureSize(context, parameters);
        writePreferredCameraId(preferences, currentCameraId);
    }
    private static boolean checkSupportedVideoQuality(Parameters parameters,int width, int height){
        List <Size> supported = parameters.getSupportedVideoSizes();
        if (supported == null) {
            // video-size not specified in parameter list. just go along with the profile.
            return true;
        }
        int flag = 0;
        for (Size size : supported){
            //since we are having two profiles with same height, we are checking with height
            if (size.height == 480) {
                if (size.height == height && size.width == width) {
                    flag = 1;
                    break;
                }
            } else {
                if (size.width == width) {
                    flag = 1;
                    break;
                }
            }
        }
        if (flag == 1)
            return true;

        return false;
    }
    private static ArrayList<String> getSupportedVideoQuality(int cameraId,Parameters parameters) {
        ArrayList<String> supported = new ArrayList<String>();
        // Check for supported quality
        if (ApiHelper.HAS_FINE_RESOLUTION_QUALITY_LEVELS) {
            getFineResolutionQuality(supported,cameraId,parameters);
        } else {
            supported.add(Integer.toString(CamcorderProfile.QUALITY_HIGH));
            CamcorderProfile high = CamcorderProfile.get(
                    cameraId, CamcorderProfile.QUALITY_HIGH);
            CamcorderProfile low = CamcorderProfile.get(
                    cameraId, CamcorderProfile.QUALITY_LOW);
            if (high.videoFrameHeight * high.videoFrameWidth >
                    low.videoFrameHeight * low.videoFrameWidth) {
                supported.add(Integer.toString(CamcorderProfile.QUALITY_LOW));
            }
        }

        return supported;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static void getFineResolutionQuality(ArrayList<String> supported,
                                                 int cameraId,Parameters parameters) {
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_4kDCI)) {
           if (checkSupportedVideoQuality(parameters,4096,2160)) {
              supported.add(Integer.toString(CamcorderProfile.QUALITY_4kDCI));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_4kUHD)) {
           if (checkSupportedVideoQuality(parameters,3840,2160)) {
              supported.add(Integer.toString(CamcorderProfile.QUALITY_4kUHD));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
           if (checkSupportedVideoQuality(parameters,1920,1080)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_1080P));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
           if (checkSupportedVideoQuality(parameters,1280,720)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_720P));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
           if (checkSupportedVideoQuality(parameters,720,480)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_480P));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_FWVGA)) {
           if (checkSupportedVideoQuality(parameters,864,480)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_FWVGA));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_WVGA)) {
           if (checkSupportedVideoQuality(parameters,800,480)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_WVGA));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_VGA)) {
           if (checkSupportedVideoQuality(parameters,640,480)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_VGA));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_CIF)) {
           if (checkSupportedVideoQuality(parameters,352,288)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_CIF));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QVGA)) {
           if (checkSupportedVideoQuality(parameters,320,240)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_QVGA));
           }
        }
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_QCIF)) {
           if (checkSupportedVideoQuality(parameters,176,144)){
              supported.add(Integer.toString(CamcorderProfile.QUALITY_QCIF));
           }
        }

    }
    public static int getVideoDurationInMillis(String quality) {
        if (VIDEO_QUALITY_MMS.equals(quality)) {
            return MMS_VIDEO_DURATION * 1000;
        } else if (VIDEO_QUALITY_YOUTUBE.equals(quality)) {
            return YOUTUBE_VIDEO_DURATION * 1000;
        }
        return DEFAULT_VIDEO_DURATION * 1000;
    }

    /**
     * Beauty mode
     */
    public static void setBeautyMode(Parameters params, boolean enable) {
        if (isBeautyModeSupported(params)) {
            params.set("video-skinbeauty-mode", enable ? "on" : "off");
            params.set("face-beautify", enable ? "3" : "0");
        }
    }

    public static boolean isBeautyModeSupported(Parameters params) {
        return params.get("face-beautify") != null;
    }

    public static List<String> getSupportedSlowShutter(Parameters params) {
        String p = params.get("slow-shutter-values");
        if (p != null) {
            return Arrays.asList(p.split(","));
        }
        return null;
    }

    public static void setSlowShutter(Parameters params, String value) {
        if (getSupportedSlowShutter(params) != null) {
            params.set("slow-shutter", value);
        }
    }

    public static boolean isSlowShutterEnabled(Parameters params) {
        return (getSupportedSlowShutter(params) != null) &&
                !"slow-shutter-off".equals(params.get("slow-shutter"));
    }

}
