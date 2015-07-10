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

import android.content.ContentResolver;
import android.graphics.ImageFormat;

import com.android.camera.debug.Log;
import com.android.camera.device.CameraId;
import com.android.camera.exif.Rational;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCamera.Facing;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraCharacteristics;
import com.android.camera.one.OneCameraManager;
import com.android.camera.util.GservicesHelper;
import com.android.camera.util.Size;

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Handles the picture resolution setting stored in SharedPreferences keyed by
 * Keys.KEY_PICTURE_SIZE_BACK and Keys.KEY_PICTURE_SIZE_FRONT.
 */
public class ResolutionSetting {
    private static final Log.Tag TAG = new Log.Tag("ResolutionSettings");

    private final SettingsManager mSettingsManager;
    private final OneCameraManager mOneCameraManager;
    private final String mResolutionBlackListBack;
    private final String mResolutionBlackListFront;

    public ResolutionSetting(SettingsManager settingsManager,
            OneCameraManager oneCameraManager,
            ContentResolver contentResolver) {
        mSettingsManager = settingsManager;
        mOneCameraManager = oneCameraManager;

        mResolutionBlackListBack = GservicesHelper.getBlacklistedResolutionsBack(contentResolver);
        mResolutionBlackListFront = GservicesHelper.getBlacklistedResolutionsFront(contentResolver);
    }

    /**
     * Changes the picture size settings for the cameras with specified facing.
     * Pick the largest picture size with the specified aspect ratio.
     *
     * @param cameraId The specific camera device.
     * @param aspectRatio The chosen aspect ratio.
     */
    public void setPictureAspectRatio(CameraId cameraId, Rational aspectRatio)
            throws OneCameraAccessException {
        OneCameraCharacteristics cameraCharacteristics =
                mOneCameraManager.getOneCameraCharacteristics(cameraId);

        Facing cameraFacing = cameraCharacteristics.getCameraDirection();

        // Pick the largest picture size with the selected aspect ratio and save
        // the choice for front camera.
        final String pictureSizeSettingKey = cameraFacing == OneCamera.Facing.FRONT ?
                Keys.KEY_PICTURE_SIZE_FRONT : Keys.KEY_PICTURE_SIZE_BACK;
        final String blacklist = cameraFacing == OneCamera.Facing.FRONT ? mResolutionBlackListFront
                : mResolutionBlackListBack;

        // All resolutions supported by the camera.
        List<Size> supportedPictureSizes = cameraCharacteristics
                .getSupportedPictureSizes(ImageFormat.JPEG);

        // Filter sizes which we are showing to the user in settings.
        // This might also add some new resolution we support on some devices
        // non-natively.
        supportedPictureSizes = ResolutionUtil.getDisplayableSizesFromSupported(
                supportedPictureSizes, cameraFacing == OneCamera.Facing.BACK);

        // Filter the remaining sizes through our backlist.
        supportedPictureSizes = ResolutionUtil.filterBlackListedSizes(supportedPictureSizes,
                blacklist);

        final Size chosenPictureSize =
                ResolutionUtil.getLargestPictureSize(aspectRatio, supportedPictureSizes);
        mSettingsManager.set(
                SettingsManager.SCOPE_GLOBAL,
                pictureSizeSettingKey,
                SettingsUtil.sizeToSettingString(chosenPictureSize));
    }

    /**
     * Reads the picture size setting for the cameras with specified facing.
     * This specifically avoids reading camera characteristics unless the size
     * is blacklisted or is not cached to prevent a crash.
     */
    public Size getPictureSize(CameraId cameraId, Facing cameraFacing)
            throws OneCameraAccessException {
        final String pictureSizeSettingKey = cameraFacing == OneCamera.Facing.FRONT ?
                Keys.KEY_PICTURE_SIZE_FRONT : Keys.KEY_PICTURE_SIZE_BACK;

        Size pictureSize = null;

        String blacklist = "";
        if (cameraFacing == OneCamera.Facing.BACK) {
            blacklist = mResolutionBlackListBack;
        } else if (cameraFacing == OneCamera.Facing.FRONT) {
            blacklist = mResolutionBlackListFront;
        }

        // If there is no saved picture size preference or the saved on is
        // blacklisted., pick a largest size with 4:3 aspect
        boolean isPictureSizeSettingSet =
                mSettingsManager.isSet(SettingsManager.SCOPE_GLOBAL, pictureSizeSettingKey);
        boolean isPictureSizeBlacklisted = false;

        // If a picture size is set, check whether it's blacklisted.
        if (isPictureSizeSettingSet) {
            pictureSize = SettingsUtil.sizeFromSettingString(
                    mSettingsManager.getString(SettingsManager.SCOPE_GLOBAL,
                            pictureSizeSettingKey));
            isPictureSizeBlacklisted = pictureSize == null ||
                    ResolutionUtil.isBlackListed(pictureSize, blacklist);
        }

        // Due to b/21758681, it is possible that an invalid picture size has
        // been saved to the settings. Therefore, picture size is set AND is not
        // blacklisted, but completely invalid. In these cases, need to take the
        // fallback, instead of the saved value. This logic should now save a
        // valid picture size to the settings and self-correct the state of the
        // settings.
        final boolean isPictureSizeFromSettingsValid = pictureSize != null &&
                pictureSize.width() > 0 && pictureSize.height() > 0;

        if (!isPictureSizeSettingSet || isPictureSizeBlacklisted || !isPictureSizeFromSettingsValid) {
            final Rational aspectRatio = ResolutionUtil.ASPECT_RATIO_4x3;

            OneCameraCharacteristics cameraCharacteristics =
                    mOneCameraManager.getOneCameraCharacteristics(cameraId);

            final List<Size> supportedPictureSizes =
                    ResolutionUtil.filterBlackListedSizes(
                            cameraCharacteristics.getSupportedPictureSizes(ImageFormat.JPEG),
                            blacklist);
            final Size fallbackPictureSize =
                    ResolutionUtil.getLargestPictureSize(aspectRatio, supportedPictureSizes);
            mSettingsManager.set(
                    SettingsManager.SCOPE_GLOBAL,
                    pictureSizeSettingKey,
                    SettingsUtil.sizeToSettingString(fallbackPictureSize));
            pictureSize = fallbackPictureSize;
            Log.e(TAG, "Picture size setting is not set. Choose " + fallbackPictureSize);
            // Crash here if invariants are violated
            Preconditions.checkNotNull(fallbackPictureSize);
            Preconditions.checkState(fallbackPictureSize.width() > 0
                    && fallbackPictureSize.height() > 0);
        }
        return pictureSize;
    }

    /**
     * Obtains the preferred picture aspect ratio in terms of the picture size
     * setting.
     *
     * @param cameraId The specific camera device.
     * @return The preferred picture aspect ratio.
     * @throws OneCameraAccessException
     */
    public Rational getPictureAspectRatio(CameraId cameraId, Facing facing)
            throws OneCameraAccessException {
        Size pictureSize = getPictureSize(cameraId, facing);
        return new Rational(pictureSize.getWidth(), pictureSize.getHeight());
    }
}
