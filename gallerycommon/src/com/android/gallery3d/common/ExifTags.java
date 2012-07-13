/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.gallery3d.common;

/**
 * The class holds the EXIF tag names that are not available in
 * {@link android.media.ExifInterface} prior to API level 11.
 */
public interface ExifTags {
    static final String TAG_ISO = "ISOSpeedRatings";
    static final String TAG_EXPOSURE_TIME = "ExposureTime";
    static final String TAG_APERTURE = "FNumber";
}
