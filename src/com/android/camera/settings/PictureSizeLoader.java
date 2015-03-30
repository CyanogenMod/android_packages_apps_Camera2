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
import android.content.Context;

import com.android.camera.settings.SettingsUtil.CameraDeviceSelector;
import com.android.camera.settings.SettingsUtil.SelectedVideoQualities;
import com.android.camera.util.GservicesHelper;
import com.android.camera.util.Size;
import com.android.ex.camera2.portability.CameraAgentFactory;
import com.android.ex.camera2.portability.CameraDeviceInfo;
import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Loads the camera picture sizes that can be set by the user.
 * <p>
 * This class is compatible with pre-Lollipop since it uses the compatibility
 * layer to access the camera metadata.
 */
@ParametersAreNonnullByDefault
public class PictureSizeLoader {
    /**
     * Holds the sizes for the back- and front cameras which will be available
     * to the user for selection form settings.
     */
    @ParametersAreNonnullByDefault
    public static class PictureSizes {
        public final List<Size> backCameraSizes;
        public final List<Size> frontCameraSizes;
        public final Optional<SelectedVideoQualities> videoQualitiesBack;
        public final Optional<SelectedVideoQualities> videoQualitiesFront;

        PictureSizes(List<Size> backCameraSizes,
                List<Size> frontCameraSizes,
                Optional<SelectedVideoQualities> videoQualitiesBack,
                Optional<SelectedVideoQualities> videoQualitiesFront) {
            this.backCameraSizes = backCameraSizes;
            this.frontCameraSizes = frontCameraSizes;
            this.videoQualitiesBack = videoQualitiesBack;
            this.videoQualitiesFront = videoQualitiesFront;
        }
    }

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final CameraDeviceInfo mCameraDeviceInfo;
    private final boolean mCachedOnly;

    /**
     * Initializes a new picture size loader.
     * <p>
     * This constructor will default to using the camera devices if the size
     * values were not found in cache.
     *
     * @param context used to load caches sizes from preferences.
     */
    public PictureSizeLoader(Context context) {
        this(context, false);
    }

    /**
     * Initializes a new picture size loader.
     *
     * @param context used to load caches sizes from preferences.
     * @param cachedOnly if set to true, this will only check the cache for
     *            sizes. If the cache is empty, this will NOT attempt to open
     *            the camera devices in order to obtain the sizes.
     */
    public PictureSizeLoader(Context context, boolean cachedOnly) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mCameraDeviceInfo = CameraAgentFactory
                .getAndroidCameraAgent(context, CameraAgentFactory.CameraApi.API_1)
                .getCameraDeviceInfo();
        mCachedOnly = cachedOnly;
    }

    /**
     * Computes the list of picture sizes that should be displayed by settings.
     * <p>
     * For this it will open the camera devices to determine the available
     * sizes, if the sizes are not already cached. This is to be compatible with
     * devices running Camera API 1.
     * <p>
     * We then calculate the resolutions that should be available and in the end
     * filter it in case a resolution is on the blacklist for this device.
     */
    public PictureSizes computePictureSizes() {
        List<Size> backCameraSizes = computeSizesForCamera(SettingsUtil.CAMERA_FACING_BACK);
        List<Size> frontCameraSizes = computeSizesForCamera(SettingsUtil.CAMERA_FACING_FRONT);
        Optional<SelectedVideoQualities> videoQualitiesBack =
                computeQualitiesForCamera(SettingsUtil.CAMERA_FACING_BACK);
        Optional<SelectedVideoQualities> videoQualitiesFront =
                computeQualitiesForCamera(SettingsUtil.CAMERA_FACING_FRONT);
        return new PictureSizes(backCameraSizes, frontCameraSizes, videoQualitiesBack,
                videoQualitiesFront);
    }

    private List<Size> computeSizesForCamera(CameraDeviceSelector facingSelector) {
        List<Size> sizes;
        int cameraId = SettingsUtil.getCameraId(mCameraDeviceInfo, facingSelector);
        if (cameraId >= 0) {
            if (mCachedOnly) {
                sizes = CameraPictureSizesCacher.getCachedSizesForCamera(cameraId, mContext)
                        .orNull();
            } else {
                sizes = CameraPictureSizesCacher.getSizesForCamera(cameraId, mContext);
            }

            if (sizes != null) {
                sizes = ResolutionUtil
                        .getDisplayableSizesFromSupported(sizes,
                                facingSelector == SettingsUtil.CAMERA_FACING_BACK);
                String blacklisted = GservicesHelper
                        .getBlacklistedResolutionsBack(mContentResolver);
                sizes = ResolutionUtil.filterBlackListedSizes(sizes, blacklisted);
                return sizes;
            }
        }
        return new ArrayList<>(0);
    }

    private Optional<SelectedVideoQualities> computeQualitiesForCamera(
            CameraDeviceSelector facingSelector) {
        int cameraId = SettingsUtil.getCameraId(mCameraDeviceInfo, facingSelector);
        if (cameraId >= 0) {
            // This is guaranteed not to be null/absent.
            return Optional.of(SettingsUtil.getSelectedVideoQualities(cameraId));
        }
        return Optional.absent();
    }
}
