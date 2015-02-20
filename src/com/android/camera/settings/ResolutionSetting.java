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

package com.android.camera.settings;

import com.android.camera.debug.Log;
import com.android.camera.exif.Rational;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraManager;
import com.android.camera.util.Size;

import android.graphics.ImageFormat;

import java.util.List;

/**
 * Handles the picture resolution setting stored in SharedPreferences keyed by
 * Keys.KEY_PICTURE_SIZE_BACK and Keys.KEY_PICTURE_SIZE_FRONT.
 */
public class ResolutionSetting {
    private static final Log.Tag TAG = new Log.Tag("ResolutionSettings");

    private final SettingsManager mSettingsManager;

    private final OneCameraManager mOneCameraManager;

    public ResolutionSetting(SettingsManager settingsManager, OneCameraManager oneCameraManager) {
        mSettingsManager = settingsManager;
        mOneCameraManager = oneCameraManager;
    }

    /**
     * Changes the picture size settings for the cameras with specified facing.
     * Pick the largest picture size with the specified aspect ratio.
     *
     * @param cameraFacing The specified direction that the camera is facing.
     * @param aspectRatio The chosen aspect ratio.
     */
    public void setPictureAspectRatio(OneCamera.Facing cameraFacing, Rational aspectRatio)
            throws OneCameraAccessException {
        OneCameraCharacteristics cameraCharacteristics =
                mOneCameraManager.getCameraCharacteristics(cameraFacing);

        // Pick the largest picture size with the selected aspect ratio and save the choice for front camera.
        final String pictureSizeSettingKey = cameraFacing == OneCamera.Facing.FRONT ?
                Keys.KEY_PICTURE_SIZE_FRONT : Keys.KEY_PICTURE_SIZE_BACK;
        final List<Size> supportedPictureSizes =
                cameraCharacteristics.getSupportedPictureSizes(ImageFormat.JPEG);
        final Size chosenPictureSize =
                ResolutionUtil.getLargestPictureSize(aspectRatio, supportedPictureSizes);
        mSettingsManager.set(
                SettingsManager.SCOPE_GLOBAL,
                pictureSizeSettingKey,
                SettingsUtil.sizeToSettingString(chosenPictureSize));
    }

    /**
     * Reads the picture size setting for the cameras with specified facing.
     *
     * @param cameraFacing The specified direction that the camera is facing.
     * @return The preferred picture size.
     */
    public Size getPictureSize(OneCamera.Facing cameraFacing) throws OneCameraAccessException {
        final String pictureSizeSettingKey = cameraFacing == OneCamera.Facing.FRONT ?
                Keys.KEY_PICTURE_SIZE_FRONT : Keys.KEY_PICTURE_SIZE_BACK;

        /**
         * If there is no saved reference, pick a largest size with 4:3 aspect
         * ratio as a fallback.
         */
        final boolean isPictureSizeSettingSet =
                mSettingsManager.isSet(SettingsManager.SCOPE_GLOBAL, pictureSizeSettingKey);
        if (!isPictureSizeSettingSet) {
            final Rational aspectRatio = ResolutionUtil.ASPECT_RATIO_4x3;

            final OneCameraCharacteristics cameraCharacteristics =
                    mOneCameraManager.getCameraCharacteristics(cameraFacing);
            final List<Size> supportedPictureSizes =
                    cameraCharacteristics.getSupportedPictureSizes(ImageFormat.JPEG);
            final Size fallbackPictureSize =
                    ResolutionUtil.getLargestPictureSize(aspectRatio, supportedPictureSizes);
            mSettingsManager.set(
                    SettingsManager.SCOPE_GLOBAL,
                    pictureSizeSettingKey,
                    SettingsUtil.sizeToSettingString(fallbackPictureSize));
            Log.e(TAG, "Picture size setting is not set. Choose " + fallbackPictureSize);
        }

        /** Reads picture size setting from SettingsManager. */
        return SettingsUtil.sizeFromSettingString(
                mSettingsManager.getString(SettingsManager.SCOPE_GLOBAL, pictureSizeSettingKey));
    }

    /**
     * Obtains the preferred picture aspect ratio in terms of the picture size setting.
     *
     * @param cameraFacing The specified direction that the camera is facing.
     * @return The preferred picture aspect ratio.
     * @throws OneCameraAccessException
     */
    public Rational getPictureAspectRatio(OneCamera.Facing cameraFacing)
            throws OneCameraAccessException {
        Size pictureSize = getPictureSize(cameraFacing);
        return new Rational(pictureSize.getWidth(), pictureSize.getHeight());
    }
}