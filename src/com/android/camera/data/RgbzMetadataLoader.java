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

import com.android.camera.exif.ExifInterface;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Asynchronously loads RGBZ data.
 */
public class RgbzMetadataLoader {
    private static final String KEY_RGBZ_INFO = "metadata_key_rgbz_info";
    private static final String EXIF_SOFTWARE_VALUE = "RGBZ";

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
        boolean isRgbz = false;

        try {
            InputStream input;
            input = context.getContentResolver().openInputStream(contentUri);
            isRgbz = isRgbz(input);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (isRgbz) {
            metadata.putBoolean(KEY_RGBZ_INFO, true);
        }
    }

    /**
     * @return Whether the file is an RGBZ file.
     */
    private static boolean isRgbz(InputStream input) {
        ExifInterface exif = new ExifInterface();
        try {
            exif.readExif(input);
            // TODO: Rather than this, check for the presence of the XMP.
            String software = exif.getTagStringValue(ExifInterface.TAG_SOFTWARE);
            return software != null && software.startsWith(EXIF_SOFTWARE_VALUE);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
