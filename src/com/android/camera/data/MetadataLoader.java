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

/**
 * A helper class to load the metadata of
 * {@link com.android.camera.data.LocalData}.
 */
class MetadataLoader {

    private static final String KEY_METADATA_UPDATED = "metadata_updated";

    static void loadMetadata(final Context context, final LocalData data) {
        PanoramaMetadataLoader.loadPanoramaMetadata(context, data.getUri(),
                data.getMetadata());
        RgbzMetadataLoader.loadRgbzMetadata(context, data.getUri(), data.getMetadata());
        data.getMetadata().putBoolean(MetadataLoader.KEY_METADATA_UPDATED, true);
    }

    static boolean isMetadataLoaded(final LocalData data) {
        return data.getMetadata().getBoolean(MetadataLoader.KEY_METADATA_UPDATED);
    }
}
