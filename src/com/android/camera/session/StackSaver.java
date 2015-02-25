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

package com.android.camera.session;

import android.net.Uri;

import java.io.File;

/**
 * Used to store images that belong to the same stack.
 */
public interface StackSaver {

    /**
     * Save a single image from a stack/burst.
     *
     * @param inputImagePath the input image for the image.
     * @param title the title of this image, without the file extension
     * @param width the width of the image in pixels
     * @param height the height of the image in pixels
     * @param imageOrientation the image orientation in degrees
     * @param captureTimeEpoch the capture time in millis since epoch
     * @param mimeType the mime type of the image
     * @return The Uri of the saved image, or null, of the image could not be
     *         saved.
     */
    public Uri saveStackedImage(File inputImagePath, String title, int width, int height,
            int imageOrientation, long captureTimeEpoch, String mimeType);
}
