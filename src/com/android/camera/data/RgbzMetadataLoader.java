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

import com.android.camera.util.RefocusHelper;

/**
 * Asynchronously loads RGBZ data.
 */
public class RgbzMetadataLoader {
    private static final String KEY_RGBZ_INFO = "metadata_key_rgbz_info";

    /**
     * @return whether the data has RGBZ metadata.
     */
    public static boolean hasRGBZData(final LocalData data) {
        return data.getMetadata().getBoolean(KEY_RGBZ_INFO);
    }

    /**
     * Checks whether this file is an RGBZ file and fill in the metadata.
     *
     * @param context  The app context.
     */
    public static void loadRgbzMetadata(final Context context, Uri contentUri, Bundle metadata) {
        if (RefocusHelper.isRGBZ(context, contentUri)) {
            metadata.putBoolean(KEY_RGBZ_INFO, true);
        }
    }
}
