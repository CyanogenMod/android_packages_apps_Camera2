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

package com.android.camera.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Build;
import android.preference.PreferenceManager;

import com.android.camera.util.Size;
import com.google.common.base.Optional;

import java.util.List;

/**
 * Facilitates caching of camera supported picture sizes, which is slow
 * to query.  Will update cache if Build ID changes.
 */
public class CameraPictureSizesCacher {
    private static final String PICTURE_SIZES_BUILD_KEY = "CachedSupportedPictureSizes_Build_Camera";
    private static final String PICTURE_SIZES_SIZES_KEY = "CachedSupportedPictureSizes_Sizes_Camera";

    /**
     * Opportunistically update the picture sizes cache, if needed.
     *
     * @param cameraId cameraID we have sizes for.
     * @param sizes List of valid sizes.
     */
    public static void updateSizesForCamera(Context context, int cameraId, List<Size> sizes) {
        String key_build = PICTURE_SIZES_BUILD_KEY + cameraId;
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String thisCameraCachedBuild = defaultPrefs.getString(key_build, null);
        // Write to cache.
        if (thisCameraCachedBuild == null) {
            String key_sizes = PICTURE_SIZES_SIZES_KEY + cameraId;
            SharedPreferences.Editor editor = defaultPrefs.edit();
            editor.putString(key_build, Build.DISPLAY);
            editor.putString(key_sizes, Size.listToString(sizes));
            editor.apply();
        }
    }

    /**
     * Return list of Sizes for provided cameraId.  Check first to see if we
     * have it in the cache for the current android.os.Build.
     * Note: This method calls Camera.open(), so the camera must be closed
     * before calling or null will be returned if sizes were not previously
     * cached.
     *
     * @param cameraId cameraID we would like sizes for.
     * @param context valid android application context.
     * @return List of valid sizes, or null if the Camera can not be opened.
     */
    public static List<Size> getSizesForCamera(int cameraId, Context context) {
        Optional<List<Size>> cachedSizes = getCachedSizesForCamera(cameraId, context);
        if (cachedSizes.isPresent()) {
            return cachedSizes.get();
        }

        // No cached value, so need to query Camera API.
        Camera thisCamera;
        try {
            thisCamera = Camera.open(cameraId);
        } catch (RuntimeException e) {
            // Camera open will fail if already open.
            return null;
        }
        if (thisCamera != null) {
            String key_build = PICTURE_SIZES_BUILD_KEY + cameraId;
            String key_sizes = PICTURE_SIZES_SIZES_KEY + cameraId;
            SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);

            List<Size> sizes = Size.buildListFromCameraSizes(thisCamera.getParameters()
                    .getSupportedPictureSizes());
            thisCamera.release();
            SharedPreferences.Editor editor = defaultPrefs.edit();
            editor.putString(key_build, Build.DISPLAY);
            editor.putString(key_sizes, Size.listToString(sizes));
            editor.apply();
            return sizes;
        }
        return null;
    }

    /**
     * Returns the cached sizes for the current camera. See
     * {@link #getSizesForCamera} for details.
     *
     * @param cameraId cameraID we would like sizes for.
     * @param context valid android application context.
     * @return Optional ist of valid sizes. Not present if the sizes for the
     *         given camera were not cached.
     */
    public static Optional<List<Size>> getCachedSizesForCamera(int cameraId, Context context) {
        String key_build = PICTURE_SIZES_BUILD_KEY + cameraId;
        String key_sizes = PICTURE_SIZES_SIZES_KEY + cameraId;
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Return cached value for cameraId and current build, if available.
        String thisCameraCachedBuild = defaultPrefs.getString(key_build, null);
        if (thisCameraCachedBuild != null && thisCameraCachedBuild.equals(Build.DISPLAY)) {
            String thisCameraCachedSizeList = defaultPrefs.getString(key_sizes, null);
            if (thisCameraCachedSizeList != null) {
                return Optional.of(Size.stringToList(thisCameraCachedSizeList));
            }
        }
        return Optional.absent();
    }
}
