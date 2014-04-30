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
public class MetadataLoader {

    private static final String KEY_METADATA_CACHED = "metadata_cached";

    /**
     * Adds information to the data's metadata bundle if any is available and returns
     * true if metadata was added and false otherwise. In either case, sets
     * a flag indicating that we've cached any available metadata and don't need to
     * load metadata again for this particular item.
     *
     * @param context A context.
     * @param data The data to update metadata for.
     * @return true if any metadata was added to the data, false otherwise.
     */
    public static boolean loadMetadata(final Context context, final LocalData data) {
        boolean metadataAdded = false;
        if (data.getLocalDataType() == LocalData.LOCAL_IMAGE) {
            PanoramaMetadataLoader.loadPanoramaMetadata(context, data.getUri(),
                    data.getMetadata());
            RgbzMetadataLoader.loadRgbzMetadata(context, data.getUri(), data.getMetadata());
            metadataAdded = true;
        } else if (data.getLocalDataType() == LocalData.LOCAL_VIDEO) {
            VideoRotationMetadataLoader.loadRotationMetdata(data);
            metadataAdded = true;
        }
        data.getMetadata().putBoolean(MetadataLoader.KEY_METADATA_CACHED, true);
        return metadataAdded;
    }

    static boolean isMetadataCached(final LocalData data) {
        return data.getMetadata().getBoolean(MetadataLoader.KEY_METADATA_CACHED);
    }
}
