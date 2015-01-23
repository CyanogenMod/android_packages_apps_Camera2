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

package com.android.camera.data;

import android.media.MediaMetadataRetriever;

import com.android.camera.debug.Log;

public class VideoRotationMetadataLoader {
    private static final Log.Tag TAG = new Log.Tag("VidRotDataLoader");

    private static final String ROTATE_90 = "90";
    private static final String ROTATE_270 = "270";

    static boolean isRotated(FilmstripItem filmstripItem) {
        final String rotation = filmstripItem.getMetadata().getVideoOrientation();
        return ROTATE_90.equals(rotation) || ROTATE_270.equals(rotation);
    }

    static boolean loadRotationMetadata(final FilmstripItem data) {
        final String path = data.getData().getFilePath();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            data.getMetadata().setVideoOrientation(retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));

            String val = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);

            data.getMetadata().setVideoWidth(Integer.parseInt(val));

            val = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

            data.getMetadata().setVideoHeight(Integer.parseInt(val));
        } catch (RuntimeException ex) {
            // setDataSource() can cause RuntimeException beyond
            // IllegalArgumentException. e.g: data contain *.avi file.
            Log.e(TAG, "MediaMetdataRetriever.setDataSource() fail", ex);
        }
        return true;
    }
}
