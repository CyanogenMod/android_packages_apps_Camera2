/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.camera.data;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.android.camera.util.PhotoSphereHelper;

/**
 * This class breaks out the off-thread panorama support.
 */
public class PanoramaMetadataLoader {
    /**
     * The key for the metadata in {@link com.android.camera.data.LocalData} to
     * indicate whether the data is a 360-degrees panorama.
     */
    private static final String KEY_PANORAMA_360 = "metadata_key_panorama_360";

    /**
     * The key for the metadata in {@link com.android.camera.data.LocalData} to
     * indicate whether the data is a panorama and the panorama viewer should be
     * used to consume it.
     */
    private static final String KEY_USE_PANORAMA_VIEWER = "metadata_key_panorama_viewer";

    /**
     * The key for the metadata in {@link com.android.camera.data.LocalData} to
     * indicate whether the data is a panorama with it's metadata.
     */
    private static final String KEY_IS_PANORAMA = "metadata_key_is_panorama";

    /**
     * @return whether the {@code data} is a panorama.
     */
    public static boolean isPanorama(final LocalData data) {
        return data.getMetadata().getBoolean(KEY_IS_PANORAMA);
    }

    /**
     * @return whether the {@code data} is a panorama and the panorama viewer
     *         should be used to consume it.
     */
    public static boolean isPanoramaAndUseViewer(final LocalData data) {
        return data.getMetadata().getBoolean(KEY_USE_PANORAMA_VIEWER);
    }

    /**
     * @return whether the {@code data} is a 360-degrees panorama.
     */
    public static boolean isPanorama360(final LocalData data) {
        return data.getMetadata().getBoolean(KEY_PANORAMA_360);
    }

    /**
     * Extracts panorama metadata from the item with the given URI and fills the
     * {@code metadata}.
     */
    public static void loadPanoramaMetadata(final Context context, Uri contentUri,
            Bundle metadata) {
        PhotoSphereHelper.PanoramaMetadata panoramaMetadata =
                PhotoSphereHelper.getPanoramaMetadata(context, contentUri);
        if (panoramaMetadata == null) {
            return;
        }

        // Note: The use of '!=' here is in purpose as this is a singleton that
        // is returned if this is not a panorama, so pointer comparison works.
        boolean hasMetadata = panoramaMetadata != PhotoSphereHelper.NOT_PANORAMA;
        metadata.putBoolean(KEY_IS_PANORAMA, hasMetadata);
        metadata.putBoolean(KEY_PANORAMA_360, panoramaMetadata.mIsPanorama360);
        metadata.putBoolean(KEY_USE_PANORAMA_VIEWER,
                panoramaMetadata.mUsePanoramaViewer);
    }
}
